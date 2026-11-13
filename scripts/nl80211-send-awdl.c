/* Send an arbitrary management/action frame from a file through nl80211,
 * off-channel, and listen for action responses. */
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
static int addattr32(struct nlmsghdr *n, size_t maxlen, int type, uint32_t v)
{
    return addattr_l(n, maxlen, type, &v, sizeof(v));
}

static int get_family(int nl)
{
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[256]; } req;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    char buf[4096];
    ssize_t len;
    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = GENL_ID_CTRL;
    req.n.nlmsg_flags = NLM_F_REQUEST;
    req.n.nlmsg_seq = 1;
    req.g.cmd = CTRL_CMD_GETFAMILY;
    req.g.version = 1;
    addattr_l(&req.n, sizeof(req), CTRL_ATTR_FAMILY_NAME, "nl80211", 8);
    if (sendto(nl, &req, req.n.nlmsg_len, 0, (void *)&sa, sizeof(sa)) < 0)
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

static uint32_t g_freq = 2437;

#ifndef NLMSGERR_ATTR_MSG
#define NLMSGERR_ATTR_MSG 1
#endif
#ifndef NLA_TYPE_MASK
#define NLA_TYPE_MASK 0x3fff
#endif

/* Print the kernel's extended ACK reason, if one was supplied. */
static void print_extack(struct nlmsghdr *n)
{
    struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
    char *base = (char *)e + NLMSG_ALIGN(sizeof(*e));
    int orig = e->msg.nlmsg_len;
    int remain = n->nlmsg_len - NLMSG_LENGTH(sizeof(*e));
    char *p;
    int alen;
    struct nlattr *na;

    if (orig > 0 && NLMSG_ALIGN(orig) <= remain) {
        p = base + NLMSG_ALIGN(orig);
        alen = remain - NLMSG_ALIGN(orig);
    } else {
        p = base;
        alen = remain;
    }
    for (na = (struct nlattr *)p;
         alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na) &&
         na->nla_len <= alen;
         alen -= NLA_ALIGN(na->nla_len),
         na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
        if ((na->nla_type & NLA_TYPE_MASK) == NLMSGERR_ATTR_MSG) {
            int len = na->nla_len - NLA_HDRLEN;
            printf("extack: %.*s\n", len,
                   (char *)na + NLA_HDRLEN);
        }
    }
}

static int send_cmd(int nl, int family, int seq, int cmd, int ifindex,
                    const void *frame, int frame_len, int offchannel)
{
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[4096]; } req;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = family;
    req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    req.n.nlmsg_seq = seq;
    req.g.cmd = cmd;
    req.g.version = 0;
    if (cmd == NL80211_CMD_REGISTER_FRAME) {
        uint16_t ft = 0x00d0;
        uint8_t match = 0xd0;
        addattr32(&req.n, sizeof(req), NL80211_ATTR_IFINDEX, ifindex);
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_TYPE, &ft, 2);
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_MATCH, &match, 1);
    } else {
        addattr32(&req.n, sizeof(req), NL80211_ATTR_IFINDEX, ifindex);
        addattr32(&req.n, sizeof(req), NL80211_ATTR_WIPHY_FREQ, g_freq);
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME, frame, frame_len);
        if (offchannel) {
            addattr_l(&req.n, sizeof(req), NL80211_ATTR_OFFCHANNEL_TX_OK, NULL, 0);
            addattr32(&req.n, sizeof(req), NL80211_ATTR_DURATION, 5000);
        }
    }
    return sendto(nl, &req, req.n.nlmsg_len, 0, (void *)&sa, sizeof(sa));
}

int main(int argc, char **argv)
{
    const char *iface = argc > 1 ? argv[1] : "wlan0";
    const char *path = argc > 2 ? argv[2] : "/data/local/tmp/awdl_psf_broadcast.bin";
    int freq = argc > 3 ? atoi(argv[3]) : 2437;
    int offchannel = getenv("ONCHANNEL") == NULL;
    int count = getenv("COUNT") ? atoi(getenv("COUNT")) : 1;
    int interval_ms = getenv("INTERVAL_MS") ? atoi(getenv("INTERVAL_MS")) : 20;
    int rcvbuf = 1 << 20;
    g_freq = freq;
    uint8_t frame[2048];
    int frame_len, ifindex, nl, family, i;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    struct timeval tv = { .tv_sec = 8, .tv_usec = 0 };

    FILE *f = fopen(path, "rb");
    if (!f) { perror(path); return 1; }
    frame_len = fread(frame, 1, sizeof(frame), f);
    fclose(f);
    if (frame_len < 24) { fprintf(stderr, "short frame\n"); return 1; }
    ifindex = if_nametoindex(iface);
    if (!ifindex) { perror("if_nametoindex"); return 1; }
    printf("file=%s len=%d iface=%s ifindex=%d freq=%d\n",
           path, frame_len, iface, ifindex, freq);

    nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
    bind(nl, (void *)&sa, sizeof(sa));
    setsockopt(nl, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(nl, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));
    family = get_family(nl);
    if (family < 0) { fprintf(stderr, "no nl80211 family\n"); return 1; }
    printf("family=%d\n", family);

    send_cmd(nl, family, 2, NL80211_CMD_REGISTER_FRAME, ifindex, NULL, 0, 0);
    /* consume register ACK */
    {
        char buf[4096];
        ssize_t len = recv(nl, buf, sizeof(buf), 0);
        if (len < 0) { perror("register recv"); return 1; }
        for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
             n = NLMSG_NEXT(n, len)) {
            if (n->nlmsg_type == NLMSG_ERROR) {
                struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
                printf("register rc=%d %s\n", e->error,
                       e->error ? strerror(-e->error) : "ok");
                if (e->error)
                    print_extack(n);
            }
        }
    }

    if (count < 1) count = 1;
    for (i = 0; i < count; i++) {
        if (send_cmd(nl, family, 3 + i, NL80211_CMD_FRAME, ifindex,
                     frame, frame_len, offchannel) < 0) {
            perror("send frame"); return 1;
        }
        if (interval_ms > 0)
            usleep(interval_ms * 1000);
    }
    printf("sent %d %s action frame(s)\n", count,
           offchannel ? "off-channel" : "on-channel");

    for (;;) {
        char buf[4096];
        ssize_t len = recv(nl, buf, sizeof(buf), 0);
        if (len < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                printf("timeout waiting for TX status/action response\n");
                return 2;
            }
            perror("recv"); return 1;
        }
        for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
             n = NLMSG_NEXT(n, len)) {
            struct genlmsghdr *g;
            int alen;
            struct nlattr *na;
            int acked = 0;
            if (n->nlmsg_type == NLMSG_ERROR) {
                struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
                if (e->error) {
                    printf("netlink error: %s (%d)\n",
                           strerror(-e->error), e->error);
                    print_extack(n);
                } else {
                    printf("command accepted\n");
                }
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
                        const uint8_t *p = (const uint8_t *)na + NLA_HDRLEN;
                        int plen = na->nla_len - NLA_HDRLEN;
                        if (plen >= 24) {
                            printf("RX action fc=0x%02x SA=%02x:%02x:%02x:%02x:%02x:%02x BSSID=%02x:%02x:%02x:%02x:%02x:%02x\n",
                                   p[0], p[10],p[11],p[12],p[13],p[14],p[15],
                                   p[16],p[17],p[18],p[19],p[20],p[21]);
                            return 0;
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
            printf("TX status: ack=%s\n", acked ? "YES" : "NO");
            if (acked) return 0;
        }
    }
}
