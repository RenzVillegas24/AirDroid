#!/bin/bash
set -euo pipefail
ROOT=/home/renepi/Codes/owly-droid
WS="$ROOT/workspaces/raphael"
FB="$ROOT/workspaces/shared/tools/fastboot/root/usr/lib/android-sdk/platform-tools/fastboot"
export LD_LIBRARY_PATH="$ROOT/workspaces/shared/tools/fastboot/root/usr/lib/aarch64-linux-gnu/android:$ROOT/workspaces/shared/tools/fastboot/root/usr/lib/android-sdk/platform-tools"
adb reboot bootloader
sleep 8
"$FB" devices
"$FB" flash boot "$WS/boot-images/boot-owly-monitor-fixed.img"
"$FB" reboot
