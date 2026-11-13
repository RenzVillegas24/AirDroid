/*
 * Static aarch64 raw 802.11 probe injector for owly-droid.
 *
 * Run as root with wlan0 already in monitor mode (con_mode=4):
 *   /data/local/tmp/owly-inject-probe wlan0 36 5
 *
 * Build on the host:
 *   aarch64-linux-gnu-gcc -static -O2 -o owly-inject-probe inject-probe.c
 */
#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_packet.h>
#include <linux/if_ether.h>
#include <net/if.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

#define PROBE_LEN 8 + 24 + 2

static int get_mac(const char *iface, uint8_t mac[6])
{
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    struct ifreq ifr;
    if (fd < 0)
        return -1;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, iface, IFNAMSIZ - 1);
    if (ioctl(fd, SIOCGIFHWADDR, &ifr) < 0) {
        close(fd);
        return -1;
    }
    memcpy(mac, ifr.ifr_hwaddr.sa_data, 6);
    close(fd);
    return 0;
}

static int set_channel(const char *iface, int channel)
{
    char path[256];
    snprintf(path, sizeof(path), "/sys/class/net/%s/monitor_mode_channel", iface);
    FILE *f = fopen(path, "w");
    if (!f) {
        fprintf(stderr, "open %s: %s\n", path, strerror(errno));
        return -1;
    }
    fprintf(f, "%d 0\n", channel);
    fclose(f);
    return 0;
}

static int open_raw(const char *iface)
{
    int fd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (fd < 0)
        return -1;
    struct sockaddr_ll sll;
    memset(&sll, 0, sizeof(sll));
    sll.sll_family = AF_PACKET;
    sll.sll_protocol = htons(ETH_P_ALL);
    sll.sll_ifindex = if_nametoindex(iface);
    if (!sll.sll_ifindex) {
        close(fd);
        errno = ENODEV;
        return -1;
    }
    if (bind(fd, (struct sockaddr *)&sll, sizeof(sll)) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static void build_probe(uint8_t *buf, const uint8_t sa[6], const uint8_t *ssid, size_t ssid_len)
{
    /* radiotap: version 0, pad 0, length 8, present 0 */
    buf[0] = 0; buf[1] = 0; buf[2] = 8; buf[3] = 0;
    buf[4] = buf[5] = buf[6] = buf[7] = 0;
    uint8_t *f = buf + 8;
    f[0] = 0x40; /* probe request */
    f[1] = 0x00;
    f[2] = f[3] = 0; /* duration */
    memset(f + 4, 0xff, 6); /* DA broadcast */
    memcpy(f + 10, sa, 6);
    memset(f + 16, 0xff, 6); /* BSSID broadcast */
    f[22] = f[23] = 0; /* sequence */
    f[24] = 0; /* SSID IE */
    f[25] = (uint8_t)ssid_len;
    memcpy(f + 26, ssid, ssid_len);
}

static int frame_is_probe_response(const uint8_t *pkt, ssize_t len,
                                   const uint8_t **sa, const uint8_t **bssid)
{
    if (len < 8)
        return 0;
    unsigned rt_len = pkt[2] | (pkt[3] << 8);
    if ((ssize_t)(rt_len + 24) > len)
        return 0;
    const uint8_t *f = pkt + rt_len;
    uint8_t fc = f[0];
    if ((fc & 0x0c) != 0) /* management frame */
        return 0;
    if (((fc >> 4) & 0x0f) != 0x05) /* probe response */
        return 0;
    *sa = f + 10;
    *bssid = f + 16;
    return 1;
}

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    int channel = argc > 2 ? atoi(argv[2]) : 36;
    int attempts = argc > 3 ? atoi(argv[3]) : 5;
    uint8_t sa[6];
    uint8_t frame[PROBE_LEN];

    if (get_mac(iface, sa) < 0) {
        fprintf(stderr, "get_mac(%s): %s\n", iface, strerror(errno));
        return 1;
    }
    if (set_channel(iface, channel) < 0)
        return 1;

    /* monitor_mode_channel triggers an asynchronous vdev-start command. Give
     * the firmware time to finish before injecting the first frame. */
    sleep(3);

    int tx = open_raw(iface);
    int rx = open_raw(iface);
    if (tx < 0 || rx < 0) {
        fprintf(stderr, "raw sockets: %s\n", strerror(errno));
        return 1;
    }

    build_probe(frame, sa, NULL, 0);
    printf("iface=%s channel=%d sa=%02x:%02x:%02x:%02x:%02x:%02x attempts=%d\n",
           iface, channel, sa[0], sa[1], sa[2], sa[3], sa[4], sa[5], attempts);

    int responses = 0;
    for (int i = 0; i < attempts; i++) {
        ssize_t n = send(tx, frame, sizeof(frame), 0);
        if (n != (ssize_t)sizeof(frame)) {
            fprintf(stderr, "send: n=%zd errno=%s\n", n, strerror(errno));
            return 1;
        }
        struct pollfd pfd = { .fd = rx, .events = POLLIN };
        int pr;
        while ((pr = poll(&pfd, 1, 500)) > 0) {
            uint8_t pkt[65536];
            ssize_t len = recv(rx, pkt, sizeof(pkt), 0);
            const uint8_t *rsa, *bssid;
            if (len > 0 && frame_is_probe_response(pkt, len, &rsa, &bssid)) {
                printf("probe response: SA=%02x:%02x:%02x:%02x:%02x:%02x BSSID=%02x:%02x:%02x:%02x:%02x:%02x\n",
                       rsa[0], rsa[1], rsa[2], rsa[3], rsa[4], rsa[5],
                       bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
                responses++;
            }
        }
        if (pr < 0 && errno != EINTR)
            fprintf(stderr, "poll: %s\n", strerror(errno));
        usleep(200000);
    }

    printf("attempts=%d responses=%d\n", attempts, responses);
    close(tx);
    close(rx);
    return responses ? 0 : 2;
}
