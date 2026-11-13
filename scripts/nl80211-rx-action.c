/* Register for action frames on an interface and print received frames.
 * Usage: nl80211-rx-action <ifname> <seconds>
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
#include <time.h>
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

static int register_match(int nl, int family, int seq, int ifindex,
                          const uint8_t *match, size_t match_len)
{
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[256]; } req;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    uint16_t frame_type = 0x00d0;
    char buf[4096];
    ssize_t len;

    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = family;
    req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    req.n.nlmsg_seq = seq;
    req.g.cmd = NL80211_CMD_REGISTER_FRAME;
    req.g.version = 0;
    addattr32(&req.n, sizeof(req), NL80211_ATTR_IFINDEX, ifindex);
    addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_TYPE,
              &frame_type, sizeof(frame_type));
    addattr_l(&req.n, sizeof(req), NL80211_ATTR_FRAME_MATCH,
              match, match_len);
    if (sendto(nl, &req.n, req.n.nlmsg_len, 0, (void *)&sa, sizeof(sa)) < 0) {
        perror("send register");
        return -1;
    }
    len = recv(nl, buf, sizeof(buf), 0);
    if (len < 0) {
        perror("recv register");
        return -1;
    }
    for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
         n = NLMSG_NEXT(n, len)) {
        if (n->nlmsg_type == NLMSG_ERROR) {
            struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
            printf("register match rc=%d %s\n", e->error,
                   e->error ? strerror(-e->error) : "ok");
            return e->error;
        }
    }
    return 0;
}

static void print_mac(const unsigned char *m)
{
    printf("%02x:%02x:%02x:%02x:%02x:%02x",
           m[0], m[1], m[2], m[3], m[4], m[5]);
}

int main(int argc, char **argv)
{
    const char *ifname = argc > 1 ? argv[1] : "owlyap0";
    unsigned int seconds = argc > 2 ? (unsigned int)strtoul(argv[2], NULL, 0) : 10;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    struct timeval tv = { .tv_sec = 1, .tv_usec = 0 };
    int nl, family, ifindex, seq = 2;
    time_t end;

    ifindex = if_nametoindex(ifname);
    if (!ifindex) { perror("if_nametoindex"); return 1; }
    nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
    if (nl < 0) { perror("socket"); return 1; }
    if (bind(nl, (void *)&sa, sizeof(sa)) < 0) { perror("bind"); return 1; }
    setsockopt(nl, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    family = get_family(nl);
    if (family < 0) { fprintf(stderr, "no nl80211 family\n"); return 1; }

    printf("listen %s ifindex=%d family=%d seconds=%u\n",
           ifname, ifindex, family, seconds);
    fflush(stdout);
    {
        static const uint8_t awdl[] = { 0x7f, 0x00, 0x17, 0xf2 };
        static const uint8_t public_action[] = { 0x04, 0x00 };
        register_match(nl, family, seq++, ifindex, awdl, sizeof(awdl));
        register_match(nl, family, seq++, ifindex, public_action,
                       sizeof(public_action));
    }

    end = time(NULL) + seconds;
    while (time(NULL) < end) {
        char buf[8192];
        ssize_t len = recv(nl, buf, sizeof(buf), 0);
        if (len < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)
                continue;
            perror("recv");
            break;
        }
        for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
             n = NLMSG_NEXT(n, len)) {
            struct genlmsghdr *g;
            int alen;
            struct nlattr *na;
            const uint8_t *frame = NULL;
            int frame_len = 0;
            uint32_t freq = 0;
            int32_t signal = 0;

            if (n->nlmsg_type != family)
                continue;
            g = (struct genlmsghdr *)NLMSG_DATA(n);
            if (g->cmd != NL80211_CMD_FRAME)
                continue;
            alen = n->nlmsg_len - NLMSG_LENGTH(GENL_HDRLEN);
            for (na = (struct nlattr *)((char *)g + GENL_HDRLEN);
                 alen >= (int)sizeof(*na) && na->nla_len >= sizeof(*na);
                 alen -= NLA_ALIGN(na->nla_len),
                 na = (struct nlattr *)((char *)na + NLA_ALIGN(na->nla_len))) {
                int type = na->nla_type & NLA_TYPE_MASK;
                if (type == NL80211_ATTR_FRAME) {
                    frame = (const uint8_t *)na + NLA_HDRLEN;
                    frame_len = na->nla_len - NLA_HDRLEN;
                } else if (type == NL80211_ATTR_WIPHY_FREQ) {
                    freq = *(uint32_t *)((char *)na + NLA_HDRLEN);
                } else if (type == NL80211_ATTR_RX_SIGNAL_DBM) {
                    signal = *(int32_t *)((char *)na + NLA_HDRLEN);
                }
            }
            if (!frame || frame_len < 24)
                continue;
            printf("RX len=%d fc=0x%02x freq=%u rssi=%d sa=",
                   frame_len, frame[0], freq, signal);
            print_mac(frame + 10);
            printf(" da=");
            print_mac(frame + 4);
            printf(" bssid=");
            print_mac(frame + 16);
            printf(" cat=0x%02x\n", frame[24]);
            fflush(stdout);
        }
    }
    return 0;
}
