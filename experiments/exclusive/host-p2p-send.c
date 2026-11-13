/* Send an AWDL action frame from a P2P-device wdev (no netdev).
 * Usage: host-p2p-send <wdev-id-hex> <freq> <frame-file>
 */
#include <errno.h>
#include <linux/genetlink.h>
#include <linux/netlink.h>
#include <linux/nl80211.h>
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
#ifndef NLA_TYPE_MASK
#define NLA_TYPE_MASK 0x3fff
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

static int addattr64(struct nlmsghdr *n, size_t maxlen, int type, uint64_t v)
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
            if ((na->nla_type & NLA_TYPE_MASK) == CTRL_ATTR_FAMILY_ID)
                return *(uint16_t *)((char *)na + NLA_HDRLEN);
        }
    }
    return -1;
}

static int send_cmd(int nl, int family, int seq, int cmd, uint64_t wdev,
                    uint32_t freq, const void *frame, int frame_len)
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
    addattr64(&req.n, sizeof(req), NL80211_ATTR_WDEV, wdev);
    addattr32(&req.n, sizeof(req), NL80211_ATTR_WIPHY_FREQ, freq);
    if (cmd == NL80211_CMD_REMAIN_ON_CHANNEL) {
        addattr32(&req.n, sizeof(req), NL80211_ATTR_DURATION, 5000);
    } else {
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME, frame, frame_len);
        addattr_l(&req.n, sizeof(req), NL80211_ATTR_OFFCHANNEL_TX_OK,
                  NULL, 0);
        addattr32(&req.n, sizeof(req), NL80211_ATTR_DURATION, 5000);
    }
    return sendto(nl, &req.n, req.n.nlmsg_len, 0, (void *)&sa, sizeof(sa));
}

static void read_ack(int nl, const char *what)
{
    char buf[4096];
    ssize_t len = recv(nl, buf, sizeof(buf), 0);
    if (len < 0) {
        perror(what);
        return;
    }
    for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
         n = NLMSG_NEXT(n, len)) {
        if (n->nlmsg_type == NLMSG_ERROR) {
            struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
            printf("%s rc=%d %s\n", what, e->error,
                   e->error ? strerror(-e->error) : "ok");
        }
    }
}

int main(int argc, char **argv)
{
    uint64_t wdev;
    uint32_t freq;
    const char *path;
    uint8_t frame[2048];
    int frame_len, nl, family;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    struct timeval tv = { .tv_sec = 3, .tv_usec = 0 };
    FILE *f;

    if (argc < 4) {
        fprintf(stderr, "usage: %s <wdev-hex> <freq> <frame-file>\n", argv[0]);
        return 2;
    }
    wdev = strtoull(argv[1], NULL, 0);
    freq = (uint32_t)strtoul(argv[2], NULL, 0);
    path = argv[3];
    f = fopen(path, "rb");
    if (!f) { perror(path); return 1; }
    frame_len = fread(frame, 1, sizeof(frame), f);
    fclose(f);
    if (frame_len < 24) { fprintf(stderr, "short frame\n"); return 1; }

    nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
    if (nl < 0) { perror("socket"); return 1; }
    bind(nl, (void *)&sa, sizeof(sa));
    setsockopt(nl, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    family = get_family(nl);
    if (family < 0) { fprintf(stderr, "no nl80211 family\n"); return 1; }

    printf("wdev=0x%llx freq=%u frame_len=%d\n",
           (unsigned long long)wdev, freq, frame_len);
    if (send_cmd(nl, family, 2, NL80211_CMD_REMAIN_ON_CHANNEL,
                 wdev, freq, NULL, 0) < 0) {
        perror("send ROC");
        return 1;
    }
    read_ack(nl, "ROC");
    usleep(200000);

    if (send_cmd(nl, family, 3, NL80211_CMD_FRAME,
                 wdev, freq, frame, frame_len) < 0) {
        perror("send frame");
        return 1;
    }
    read_ack(nl, "FRAME");

    /* Wait for the driver's TX status and report whether the AP ACKed. */
    for (;;) {
        char buf[4096];
        ssize_t len = recv(nl, buf, sizeof(buf), 0);
        if (len < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK)
                printf("no TX status event\n");
            else
                perror("recv status");
            break;
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
                    printf("status error=%d (%s)\n", e->error,
                           strerror(-e->error));
                continue;
            }
            if (n->nlmsg_type != family)
                continue;
            g = (struct genlmsghdr *)NLMSG_DATA(n);
            if (g->cmd != NL80211_CMD_FRAME_TX_STATUS)
                continue;
            alen = n->nlmsg_len - NLMSG_LENGTH(GENL_HDRLEN);
            for (na = (struct nlattr *)((char *)g + GENL_HDRLEN);
                 alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na);
                 alen -= NLA_ALIGN(na->nla_len),
                 na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
                if ((na->nla_type & NLA_TYPE_MASK) == NL80211_ATTR_ACK)
                    acked = 1;
            }
            printf("TX status: %s\n", acked ? "ACK" : "no ACK");
            return 0;
        }
    }
    return 0;
}
