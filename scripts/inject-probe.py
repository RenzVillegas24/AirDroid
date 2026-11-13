#!/data/data/com.termux/files/usr/bin/python3
"""Send 802.11 probe requests from a monitor interface and count responses.

Run as root on the phone, with wlan0 already in monitor mode and tuned to the
target channel:

    su -c 'python3 /sdcard/inject-probe.py wlan0 36 5'

This is a Phase-B test for the patched qcacld monitor TX path. If the driver
transmits the injected frame, the AP on that channel should answer with a probe
response; the script prints the responses it sees.
"""
import argparse
import socket
import struct
import sys
import time


def mac_bytes(s: str) -> bytes:
    return bytes(int(x, 16) for x in s.split(":"))


def build_probe(sa: bytes, ssid: bytes = b"") -> bytes:
    # 802.11 probe request: FC 0x40, flags 0, duration 0, DA broadcast,
    # SA, BSSID broadcast, sequence control 0, SSID IE.
    hdr = struct.pack("<BBH", 0x40, 0x00, 0)
    hdr += b"\xff" * 6 + sa + b"\xff" * 6 + b"\x00\x00"
    body = b"\x00" + bytes([len(ssid)]) + ssid
    return hdr + body


def radiotap() -> bytes:
    # version 0, pad 0, length 8, present flags 0
    return struct.pack("<BBHI", 0, 0, 8, 0)


def parse_frame(pkt: bytes):
    if len(pkt) < 8:
        return None
    rt_len = struct.unpack_from("<H", pkt, 2)[0]
    if rt_len > len(pkt) or len(pkt) < rt_len + 24:
        return None
    frame = pkt[rt_len:]
    fc = frame[0]
    # management frame, probe response subtype 0x50
    if (fc & 0x0C) != 0 or ((fc >> 4) & 0x0F) != 0x08:
        return None
    sa = frame[10:16]
    bssid = frame[16:22]
    return sa, bssid


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("iface", nargs="?", default="wlan0")
    ap.add_argument("channel", nargs="?", type=int, default=36)
    ap.add_argument("attempts", nargs="?", type=int, default=5)
    args = ap.parse_args()

    with open(f"/sys/class/net/{args.iface}/address") as f:
        sa = mac_bytes(f.read().strip())
    # monitor channel is write-only: "<channel> <bandwidth>"
    with open(f"/sys/class/net/{args.iface}/monitor_mode_channel", "w") as f:
        f.write(f"{args.channel} 0\n")
    time.sleep(0.5)

    tx = socket.socket(socket.AF_PACKET, socket.SOCK_RAW, socket.htons(3))
    tx.bind((args.iface, 0))
    rx = socket.socket(socket.AF_PACKET, socket.SOCK_RAW, socket.htons(3))
    rx.bind((args.iface, 0))
    rx.settimeout(0.4)

    frame = radiotap() + build_probe(sa)
    seen = set()
    for i in range(args.attempts):
        try:
            tx.send(frame)
        except OSError as e:
            print(f"send error: {e}")
            return 1
        deadline = time.time() + 1.0
        while time.time() < deadline:
            try:
                pkt = rx.recv(65535)
            except socket.timeout:
                break
            parsed = parse_frame(pkt)
            if parsed:
                sa_r, bssid = parsed
                key = (sa_r.hex(), bssid.hex())
                seen.add(key)
                print(f"probe response: SA={sa_r.hex(':')} BSSID={bssid.hex(':')}")
        time.sleep(0.2)

    print(f"attempts={args.attempts} responses={len(seen)}")
    return 0 if seen else 2


if __name__ == "__main__":
    sys.exit(main())
