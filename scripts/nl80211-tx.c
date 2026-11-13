/*
 * Send a unicast 802.11 probe request through nl80211 and wait for the
 * driver's mgmt-TX status. If NL80211_ATTR_ACK is present, the firmware/AP
 * acknowledged the frame at the 802.11 MAC layer.
 *
 * Build:
 *   aarch64-linux-gnu-gcc -static -O2 -o owly-nl80211-tx nl80211-tx.c
 *
 * Run as root:
 *   owly-nl80211-tx wlan0 <bssid> 5180 <ssid>
 */
#include <errno.h>
#include <linux/genetlink.h>
#include <linux/netlink.h>
#include <linux/nl80211.h>
#include <net/if.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#ifndef NLA_ALIGNTO
#define NLA_ALIGNTO 4
#endif
#ifndef NLA_ALIGN
#define NLA_ALIGN(len) (((len) + NLA_ALIGNTO - 1) & ~(NLA_ALIGNTO - 1))
#endif
#ifndef NLA_HDRLEN
#define NLA_HDRLEN ((int)NLA_ALIGN(sizeof(struct nlattr)))
#endif

static int addattr_l(struct nlmsghdr *n, size_t maxlen, int type,
                     const void *data, size_t alen)
{
    size_t len = NLA_HDRLEN + alen;
    size_t newlen = NLMSG_ALIGN(n->nlmsg_len) + NLA_ALIGN(len);
    struct nlattr *na;

    if (newlen > maxlen)
        return -1;
    na = (struct nlattr *)((char *)n + NLMSG_ALIGN(n->nlmsg_len));
    na->nla_type = type;
    na->nla_len = len;
    if (alen)
        memcpy((char *)na + NLA_HDRLEN, data, alen);
    memset((char *)na + len, 0, NLA_ALIGN(len) - len);
    n->nlmsg_len = newlen;
    return 0;
}

static int addattr32(struct nlmsghdr *n, size_t maxlen, int type, uint32_t val)
{
    return addattr_l(n, maxlen, type, &val, sizeof(val));
}

static int parse_mac(const char *s, uint8_t mac[6])
{
    unsigned int v[6];
    if (sscanf(s, "%x:%x:%x:%x:%x:%x", &v[0], &v[1], &v[2],
               &v[3], &v[4], &v[5]) != 6)
        return -1;
    for (int i = 0; i < 6; i++)
        mac[i] = (uint8_t)v[i];
    return 0;
}

static int read_mac(const char *iface, uint8_t mac[6])
{
    char path[256], buf[64];
    FILE *f;
    snprintf(path, sizeof(path), "/sys/class/net/%s/address", iface);
    f = fopen(path, "r");
    if (!f)
        return -1;
    if (!fgets(buf, sizeof(buf), f)) {
        fclose(f);
        return -1;
    }
    fclose(f);
    return parse_mac(buf, mac);
}

static int get_nl80211_family(int nl)
{
    struct {
        struct nlmsghdr n;
        struct genlmsghdr g;
        char buf[256];
    } req;
    char buf[4096];
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    ssize_t len;
    int seq = 1;

    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = GENL_ID_CTRL;
    req.n.nlmsg_flags = NLM_F_REQUEST;
    req.n.nlmsg_seq = seq;
    req.g.cmd = CTRL_CMD_GETFAMILY;
    req.g.version = 1;
    if (addattr_l(&req.n, sizeof(req), CTRL_ATTR_FAMILY_NAME,
                  "nl80211", sizeof("nl80211")) < 0)
        return -1;
    if (sendto(nl, &req, req.n.nlmsg_len, 0,
               (struct sockaddr *)&sa, sizeof(sa)) < 0)
        return -1;
    len = recv(nl, buf, sizeof(buf), 0);
    if (len < 0)
        return -1;
    for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
         n = NLMSG_NEXT(n, len)) {
        struct genlmsghdr *g;
        int alen;
        struct nlattr *na;
        if (n->nlmsg_type == NLMSG_ERROR)
            return -1;
        g = (struct genlmsghdr *)NLMSG_DATA(n);
        alen = n->nlmsg_len - NLMSG_LENGTH(GENL_HDRLEN);
        for (na = (struct nlattr *)((char *)g + GENL_HDRLEN);
             alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na);
             alen -= NLA_ALIGN(na->nla_len),
             na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
            if (na->nla_type == CTRL_ATTR_FAMILY_ID)
                return *(uint16_t *)((char *)na + NLA_HDRLEN);
        }
    }
    return -1;
}

static int register_probe_response(int nl, int family, int ifindex)
{
    struct {
        struct nlmsghdr n;
        struct genlmsghdr g;
        char buf[256];
    } req;
    char buf[4096];
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    uint16_t frame_type = 0x0050; /* IEEE80211_FTYPE_MGMT | probe response */
    uint8_t match = 0x50;

    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = family;
    req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    req.n.nlmsg_seq = 2;
    req.g.cmd = NL80211_CMD_REGISTER_FRAME;
    req.g.version = 0;
    addattr32(&req.n, sizeof(req), NL80211_ATTR_IFINDEX, ifindex);
    addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_TYPE,
              &frame_type, sizeof(frame_type));
    addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_MATCH,
              &match, sizeof(match));
    if (sendto(nl, &req, req.n.nlmsg_len, 0,
               (struct sockaddr *)&sa, sizeof(sa)) < 0) {
        perror("register frame send");
        return -1;
    }
    for (;;) {
        ssize_t len = recv(nl, buf, sizeof(buf), 0);
        if (len < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK)
                return 0;
            perror("register frame recv");
            return -1;
        }
        for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
             n = NLMSG_NEXT(n, len)) {
            if (n->nlmsg_type == NLMSG_ERROR) {
                struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
                if (e->error) {
                    printf("register frame error: %s (%d)\n",
                           strerror(-e->error), e->error);
                    return -1;
                }
                printf("registered for probe responses\n");
                return 0;
            }
        }
    }
}

static int build_probe(uint8_t *buf, const uint8_t sa[6],
                       const uint8_t bssid[6], const char *ssid)
{
    size_t ssid_len = strlen(ssid);
    int n = 0;
    buf[n++] = 0x40; /* probe request */
    buf[n++] = 0x00;
    buf[n++] = 0; buf[n++] = 0;
    memcpy(buf + n, bssid, 6); n += 6;
    memcpy(buf + n, sa, 6); n += 6;
    memcpy(buf + n, bssid, 6); n += 6;
    buf[n++] = 0; buf[n++] = 0;
    buf[n++] = 0; /* SSID IE */
    buf[n++] = (uint8_t)ssid_len;
    memcpy(buf + n, ssid, ssid_len); n += ssid_len;
    return n;
}

static int build_action(uint8_t *buf, const uint8_t sa[6],
                        const uint8_t bssid[6])
{
    int n = 0;
    buf[n++] = 0xd0; /* action */
    buf[n++] = 0x00;
    buf[n++] = 0; buf[n++] = 0;
    memcpy(buf + n, bssid, 6); n += 6;
    memcpy(buf + n, sa, 6); n += 6;
    memcpy(buf + n, bssid, 6); n += 6;
    buf[n++] = 0; buf[n++] = 0;
    buf[n++] = 0x04; /* public action */
    buf[n++] = 0x00;
    return n;
}

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    if (argc <= 2) {
        fprintf(stderr, "usage: %s <ifname> <target-mac> <freq> [ssid]\n", argv[0]);
        return 2;
    }
    const char *target_s = argv[2];
    int freq = argc > 3 ? atoi(argv[3]) : 5180;
    const char *ssid = argc > 4 ? argv[4] : "";
    int action = argc > 5 && !strcmp(argv[5], "action");
    uint8_t target[6], sa[6], frame[128];
    int frame_len, nl, family, ifindex;
    struct sockaddr_nl sa_nl = { .nl_family = AF_NETLINK };
    struct timeval tv = { .tv_sec = 2, .tv_usec = 0 };

    if (parse_mac(target_s, target) < 0 || read_mac(iface, sa) < 0) {
        fprintf(stderr, "bad target MAC or cannot read %s MAC\n", iface);
        return 1;
    }
    ifindex = if_nametoindex(iface);
    if (!ifindex) {
        fprintf(stderr, "if_nametoindex(%s): %s\n", iface, strerror(errno));
        return 1;
    }
    frame_len = action ? build_action(frame, sa, target) :
                         build_probe(frame, sa, target, ssid);
    printf("iface=%s ifindex=%d sa=%02x:%02x:%02x:%02x:%02x:%02x "
           "target=%02x:%02x:%02x:%02x:%02x:%02x freq=%d frame_len=%d type=%s\n",
           iface, ifindex, sa[0], sa[1], sa[2], sa[3], sa[4], sa[5],
           target[0], target[1], target[2], target[3], target[4], target[5],
           freq, frame_len, action ? "action" : "probe");

    nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
    if (nl < 0) {
        perror("netlink");
        return 1;
    }
    if (bind(nl, (struct sockaddr *)&sa_nl, sizeof(sa_nl)) < 0) {
        perror("bind");
        return 1;
    }
    setsockopt(nl, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    family = get_nl80211_family(nl);
    if (family < 0) {
        fprintf(stderr, "could not resolve nl80211 family\n");
        return 1;
    }
    printf("nl80211 family=%d\n", family);
    if (!action)
        register_probe_response(nl, family, ifindex);

    {
        struct {
            struct nlmsghdr n;
            struct genlmsghdr g;
            char buf[1024];
        } req;
        char buf[4096];
        int seq = 2;
        memset(&req, 0, sizeof(req));
        req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
        req.n.nlmsg_type = family;
        req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
        req.n.nlmsg_seq = seq;
        req.g.cmd = NL80211_CMD_FRAME;
        req.g.version = 0;
        addattr32(&req.n, sizeof(req), NL80211_ATTR_IFINDEX, ifindex);
        addattr32(&req.n, sizeof(req), NL80211_ATTR_WIPHY_FREQ, freq);
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME, frame, frame_len);
        if (sendto(nl, &req, req.n.nlmsg_len, 0,
                   (struct sockaddr *)&sa_nl, sizeof(sa_nl)) < 0) {
            perror("send mgmt frame");
            return 1;
        }
        for (;;) {
            ssize_t len = recv(nl, buf, sizeof(buf), 0);
            if (len < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    printf("no TX status event (timeout)\n");
                    return 2;
                }
                perror("recv");
                return 1;
            }
            for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
                 n = NLMSG_NEXT(n, len)) {
                struct genlmsghdr *g;
                int alen;
                struct nlattr *na;
                int acked = 0;
                if (n->nlmsg_type == NLMSG_ERROR) {
                    struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
                    if (e->error)
                        printf("netlink error: %s (%d)\n",
                               strerror(-e->error), e->error);
                    else
                        printf("command accepted\n");
                    continue;
                }
                if (n->nlmsg_type != family)
                    continue;
                g = (struct genlmsghdr *)NLMSG_DATA(n);
                alen = n->nlmsg_len - NLMSG_LENGTH(GENL_HDRLEN);
                if (g->cmd == NL80211_CMD_FRAME) {
                    for (na = (struct nlattr *)((char *)g + GENL_HDRLEN);
                         alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na);
                         alen -= NLA_ALIGN(na->nla_len),
                         na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
                        if (na->nla_type == NL80211_ATTR_FRAME) {
                            const uint8_t *f = (const uint8_t *)na + NLA_HDRLEN;
                            int flen = na->nla_len - NLA_HDRLEN;
                            if (flen >= 24) {
                                printf("RX mgmt frame fc=0x%02x SA=%02x:%02x:%02x:%02x:%02x:%02x BSSID=%02x:%02x:%02x:%02x:%02x:%02x\n",
                                       f[0], f[10], f[11], f[12], f[13], f[14], f[15],
                                       f[16], f[17], f[18], f[19], f[20], f[21]);
                                if ((f[0] & 0xfc) == 0x50) {
                                    printf("probe response received -> TX/ACK works\n");
                                    return 0;
                                }
                            }
                        }
                    }
                    continue;
                }
                if (g->cmd != NL80211_CMD_FRAME_TX_STATUS)
                    continue;
                for (na = (struct nlattr *)((char *)g + GENL_HDRLEN);
                     alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na);
                     alen -= NLA_ALIGN(na->nla_len),
                     na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
                    if (na->nla_type == NL80211_ATTR_ACK)
                        acked = 1;
                }
                printf("mgmt TX status: ack=%s\n", acked ? "YES" : "NO");
                if (acked)
                    return 0;
            }
        }
    }
}
