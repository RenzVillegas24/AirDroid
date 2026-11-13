#!/usr/bin/env bash
# Run a root command on the phone without Termux.
#
#   scripts/adb-root.sh -c 'id; lsmod | grep qca'
#   scripts/adb-root.sh /path/to/script.sh
#
# Requires the owly-rootd2 loop to be running on the phone.
set -euo pipefail

ADB=(adb -d)
TMPDIR="${TMPDIR:-/home/renepi/Codes/tmp}"
mkdir -p "$TMPDIR"
PREFIX="${PREFIX:-owly-root2}"

if [[ "${1:-}" == "-c" ]]; then
    [[ $# -ge 2 ]] || { echo "usage: $0 -c <command> | $0 <script>" >&2; exit 2; }
    TMP="$(mktemp "$TMPDIR/owly-root.XXXXXX")"
    trap 'rm -f "$TMP"' EXIT
    printf '%s\n' "$2" > "$TMP"
elif [[ $# -eq 1 && -f "$1" ]]; then
    TMP="$1"
else
    echo "usage: $0 -c <command> | $0 <script>" >&2
    exit 2
fi

"${ADB[@]}" push "$TMP" "/data/local/tmp/${PREFIX}-cmd.sh" >/dev/null
"${ADB[@]}" shell "rm -f /data/local/tmp/${PREFIX}-go /data/local/tmp/${PREFIX}-done /data/local/tmp/${PREFIX}-rc /data/local/tmp/${PREFIX}-out; touch /data/local/tmp/${PREFIX}-go" >/dev/null

for _ in $(seq 1 300); do
    if "${ADB[@]}" shell "test -f /data/local/tmp/${PREFIX}-done" >/dev/null 2>&1; then
        break
    fi
    sleep 0.2
done

if ! "${ADB[@]}" shell "test -f /data/local/tmp/${PREFIX}-done" >/dev/null 2>&1; then
    echo "rootd2 did not finish; check /data/local/tmp/owly-rootd2.log" >&2
    "${ADB[@]}" shell 'tail -50 /data/local/tmp/owly-rootd2.log' 2>/dev/null || true
    exit 1
fi

OUT="$TMPDIR/owly-root.out"
"${ADB[@]}" pull "/data/local/tmp/${PREFIX}-out" "$OUT" >/dev/null 2>&1 || true
cat "$OUT" 2>/dev/null || true
RC="$("${ADB[@]}" shell "cat /data/local/tmp/${PREFIX}-rc" 2>/dev/null | tr -d '\r')"
rm -f "$OUT"
exit "${RC:-0}"
