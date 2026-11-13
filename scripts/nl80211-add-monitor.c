/* Add a monitor interface through nl80211 and report the kernel error.
 * Usage: nl80211-add-monitor <phy-index> <ifname>
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
#ifndef NLMSGERR_ATTR_MSG
#define NLMSGERR_ATTR_MSG 1
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
            printf("extack: %.*s\n", len, (char *)na + NLA_HDRLEN);
        }
    }
}

int main(int argc, char **argv)
{
    uint32_t phy = argc > 1 ? (uint32_t)strtoul(argv[1], NULL, 0) : 0;
    const char *ifname = argc > 2 ? argv[2] : "mon0";
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[512]; } req;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    char buf[4096];
    int nl, family;
    ssize_t len;

    nl = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
    if (nl < 0) {
        perror("socket");
        return 1;
    }
    if (bind(nl, (void *)&sa, sizeof(sa)) < 0) {
        perror("bind");
        return 1;
    }
    family = get_family(nl);
    if (family < 0) {
        fprintf(stderr, "no nl80211 family\n");
        return 1;
    }

    memset(&req, 0, sizeof(req));
    req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    req.n.nlmsg_type = family;
    req.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL;
    req.n.nlmsg_seq = 2;
    req.g.cmd = NL80211_CMD_NEW_INTERFACE;
    req.g.version = 0;
    addattr32(&req.n, sizeof(req), NL80211_ATTR_WIPHY, phy);
    addattr_l(&req.n, sizeof(req), NL80211_ATTR_IFNAME,
              ifname, strlen(ifname) + 1);
    addattr32(&req.n, sizeof(req), NL80211_ATTR_IFTYPE,
              NL80211_IFTYPE_MONITOR);
    {
        struct nlattr mon_flag;

        memset(&mon_flag, 0, sizeof(mon_flag));
        mon_flag.nla_type = NL80211_MNTR_FLAG_OTHER_BSS;
        mon_flag.nla_len = NLA_HDRLEN;
        addattr_l(&req.n, sizeof(req),
                  NL80211_ATTR_MNTR_FLAGS | NLA_F_NESTED,
                  &mon_flag, sizeof(mon_flag));
    }

    if (sendto(nl, &req, req.n.nlmsg_len, 0, (void *)&sa, sizeof(sa)) < 0) {
        perror("sendto");
        return 1;
    }
    len = recv(nl, buf, sizeof(buf), 0);
    if (len < 0) {
        perror("recv");
        return 1;
    }
    for (struct nlmsghdr *n = (struct nlmsghdr *)buf; NLMSG_OK(n, len);
         n = NLMSG_NEXT(n, len)) {
        if (n->nlmsg_type == NLMSG_ERROR) {
            struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(n);
            printf("nl80211 error=%d (%s)\n", e->error,
                   strerror(-e->error));
            if (e->error)
                print_extack(n);
            return e->error ? 1 : 0;
        }
    }
    printf("no ack\n");
    return 0;
}
