/* Send a raw 802.11 frame (no radiotap) from a monitor interface by
 * prepending an 8-byte radiotap header. Requires the owly_monitor_tx hook
 * in monitor mode.
 *
 * Usage: monitor-send-frame <iface> <frame-file> [count] [interval_ms]
 */
#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    const char *path = argc > 2 ? argv[2] : "/data/local/tmp/awdl_monitor_broadcast.bin";
    int count = argc > 3 ? atoi(argv[3]) : 1;
    int interval_ms = argc > 4 ? atoi(argv[4]) : 20;
    uint8_t frame[2048];
    uint8_t pkt[2064];
    int frame_len, ifindex, fd, i;
    struct sockaddr_ll sll;
    FILE *f;

    f = fopen(path, "rb");
    if (!f) {
        perror(path);
        return 1;
    }
    frame_len = fread(frame, 1, sizeof(frame), f);
    fclose(f);
    if (frame_len < 24) {
        fprintf(stderr, "short frame\n");
        return 1;
    }

    /* 8-byte radiotap: version 0, pad 0, len 8, present 0. */
    memset(pkt, 0, 8);
    pkt[2] = 8;
    memcpy(pkt + 8, frame, frame_len);

    ifindex = if_nametoindex(iface);
    if (!ifindex) {
        perror("if_nametoindex");
        return 1;
    }

    fd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (fd < 0) {
        perror("socket");
        return 1;
    }
    memset(&sll, 0, sizeof(sll));
    sll.sll_family = AF_PACKET;
    sll.sll_protocol = htons(ETH_P_ALL);
    sll.sll_ifindex = ifindex;
    if (bind(fd, (struct sockaddr *)&sll, sizeof(sll)) < 0) {
        perror("bind");
        close(fd);
        return 1;
    }

    printf("iface=%s ifindex=%d file=%s len=%d count=%d\n",
           iface, ifindex, path, frame_len, count);
    for (i = 0; i < count; i++) {
        ssize_t n = send(fd, pkt, 8 + frame_len, 0);
        if (n != (ssize_t)(8 + frame_len)) {
            fprintf(stderr, "send: n=%zd errno=%s\n", n, strerror(errno));
            close(fd);
            return 1;
        }
        if (interval_ms > 0)
            usleep(interval_ms * 1000);
    }
    printf("sent %d frame(s)\n", count);
    close(fd);
    return 0;
}
