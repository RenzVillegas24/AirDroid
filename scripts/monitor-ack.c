/* Capture 802.11 ACK control frames addressed to the hidden injection MAC.
 * Usage: monitor-ack <iface> [hidden-mac]
 */
#include <arpa/inet.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static unsigned char hidden[6] = {0x2a, 0xbc, 0xe2, 0xf8, 0xa3, 0x8e};

static int parse_mac(const char *s, unsigned char out[6])
{
    unsigned int v[6];
    int i;

    if (!s || sscanf(s, "%2x:%2x:%2x:%2x:%2x:%2x",
                     &v[0], &v[1], &v[2], &v[3], &v[4], &v[5]) != 6)
        return -1;
    for (i = 0; i < 6; i++)
        out[i] = (unsigned char)v[i];
    return 0;
}

static void print_mac(const unsigned char *m)
{
    printf("%02x:%02x:%02x:%02x:%02x:%02x",
           m[0], m[1], m[2], m[3], m[4], m[5]);
}

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    unsigned char buf[4096];
    int ifindex, fd, acks = 0;

    if (argc > 2)
        parse_mac(argv[2], hidden);
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
    {
        struct sockaddr_ll sll;
        memset(&sll, 0, sizeof(sll));
        sll.sll_family = AF_PACKET;
        sll.sll_protocol = htons(ETH_P_ALL);
        sll.sll_ifindex = ifindex;
        if (bind(fd, (struct sockaddr *)&sll, sizeof(sll)) < 0) {
            perror("bind");
            close(fd);
            return 1;
        }
    }

    printf("watching ACKs to ");
    print_mac(hidden);
    printf(" on %s\n", iface);
    fflush(stdout);
    for (;;) {
        ssize_t n = recv(fd, buf, sizeof(buf), 0);
        unsigned int rtlen, fc;
        unsigned char *f;
        int flen;

        if (n < 10)
            continue;
        rtlen = (unsigned int)buf[2] | ((unsigned int)buf[3] << 8);
        if (rtlen < 8 || rtlen >= (unsigned int)n)
            continue;
        f = buf + rtlen;
        flen = (int)n - (int)rtlen;
        if (flen < 10)
            continue;
        fc = (unsigned int)f[0] | ((unsigned int)f[1] << 8);
        /* Control frame, ACK subtype (0xd4), RA at offset 4. */
        if ((fc & 0x000c) != 0x0004 || (fc & 0x00f0) != 0x00d0)
            continue;
        if (memcmp(f + 4, hidden, 6) != 0)
            continue;
        acks++;
        printf("ACK #%d ra=", acks);
        print_mac(f + 4);
        printf(" len=%d\n", flen);
        fflush(stdout);
    }
}
