/* Receive 802.11 action frames from a radiotap monitor interface.
 * Usage: monitor-recv <iface>
 */
#include <arpa/inet.h>
#include <errno.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static void print_mac(const unsigned char *m)
{
    printf("%02x:%02x:%02x:%02x:%02x:%02x",
           m[0], m[1], m[2], m[3], m[4], m[5]);
}

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    unsigned char buf[4096];
    int ifindex, fd;

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

    printf("listening on %s ifindex=%d\n", iface, ifindex);
    fflush(stdout);
    for (;;) {
        ssize_t n = recv(fd, buf, sizeof(buf), 0);
        unsigned int rtlen, fc;
        unsigned char *f;
        int flen;

        if (n < 24)
            continue;
        rtlen = (unsigned int)buf[2] | ((unsigned int)buf[3] << 8);
        if (rtlen < 8 || rtlen >= (unsigned int)n)
            continue;
        f = buf + rtlen;
        flen = (int)n - (int)rtlen;
        if (flen < 24)
            continue;
        fc = (unsigned int)f[0] | ((unsigned int)f[1] << 8);
        if ((fc & 0x00fc) != 0x00d0)
            continue;
        printf("action len=%d sa=", flen);
        print_mac(f + 10);
        printf(" da=");
        print_mac(f + 4);
        printf(" bssid=");
        print_mac(f + 16);
        printf(" bytes=");
        for (int i = 0; i < flen && i < 40; i++)
            printf("%02x", f[i]);
        printf("\n");
        fflush(stdout);
    }
}
