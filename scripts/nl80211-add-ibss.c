/* Create and join a native IBSS interface through nl80211.
 * Usage: nl80211-add-ibss <phy> <ifname> <ssid> <freq>
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

static int send_ack(int nl, struct nlmsghdr *n)
{
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    char buf[4096];
    ssize_t len;

    if (sendto(nl, n, n->nlmsg_len, 0, (void *)&sa, sizeof(sa)) < 0) {
        perror("sendto");
        return -1;
    }
    len = recv(nl, buf, sizeof(buf), 0);
    if (len < 0) {
        perror("recv");
        return -1;
    }
    for (struct nlmsghdr *r = (struct nlmsghdr *)buf; NLMSG_OK(r, len);
         r = NLMSG_NEXT(r, len)) {
        if (r->nlmsg_type == NLMSG_ERROR) {
            struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(r);
            if (e->error)
                fprintf(stderr, "nl80211 error=%d (%s)\n",
                        e->error, strerror(-e->error));
            return e->error;
        }
    }
    return 0;
}

int main(int argc, char **argv)
{
    uint32_t phy = argc > 1 ? (uint32_t)strtoul(argv[1], NULL, 0) : 0;
    const char *ifname = argc > 2 ? argv[2] : "ibss0";
    const char *ssid = argc > 3 ? argv[3] : "owlyibss0";
    uint32_t freq = argc > 4 ? (uint32_t)strtoul(argv[4], NULL, 0) : 2437;
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[512]; } req;
    struct { struct nlmsghdr n; struct genlmsghdr g; char buf[512]; } join;
    struct sockaddr_nl sa = { .nl_family = AF_NETLINK };
    int nl, family, ifindex, ret;

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
              NL80211_IFTYPE_ADHOC);
    ret = send_ack(nl, &req.n);
    if (ret) {
        fprintf(stderr, "add IBSS interface failed\n");
        return 1;
    }
    sleep(1);
    ifindex = if_nametoindex(ifname);
    if (!ifindex) {
        perror("if_nametoindex");
        return 1;
    }

    memset(&join, 0, sizeof(join));
    join.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
    join.n.nlmsg_type = family;
    join.n.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    join.n.nlmsg_seq = 3;
    join.g.cmd = NL80211_CMD_JOIN_IBSS;
    join.g.version = 0;
    addattr32(&join.n, sizeof(join), NL80211_ATTR_IFINDEX, ifindex);
    addattr_l(&join.n, sizeof(join), NL80211_ATTR_SSID,
              ssid, strlen(ssid));
    addattr32(&join.n, sizeof(join), NL80211_ATTR_WIPHY_FREQ, freq);
    addattr32(&join.n, sizeof(join), NL80211_ATTR_BEACON_INTERVAL, 100);
    addattr32(&join.n, sizeof(join), NL80211_ATTR_DTIM_PERIOD, 2);
    addattr_l(&join.n, sizeof(join), NL80211_ATTR_FREQ_FIXED, NULL, 0);
    ret = send_ack(nl, &join.n);
    if (ret) {
        fprintf(stderr, "join IBSS failed\n");
        return 1;
    }
    printf("IBSS %s joined on %u MHz (ifindex=%d)\n",
           ssid, freq, ifindex);
    return 0;
}
