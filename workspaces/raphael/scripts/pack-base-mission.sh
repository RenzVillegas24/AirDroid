#!/bin/bash
# Pack the base monitor+hidden-STA kernel as a normal Wi-Fi (mission-mode)
# boot image. It intentionally omits wlan.con_mode=4; the early-boot service
# writes con_mode=4 only when /data/adb/owly-airdrop-mode exists.
set -euo pipefail
ROOT=/home/renepi/Codes/owly-droid
WS="$ROOT/workspaces/raphael"
CAP="$WS/boot-images"
K="$WS/kernel/arch/arm64/boot/Image.gz"
MKBOOTIMG="$ROOT/workspaces/shared/tools/fastboot/root/usr/bin/mkbootimg"
UNPACK="$ROOT/workspaces/shared/tools/fastboot/root/usr/bin/unpack_bootimg"
[ -s "$K" ] || { echo "missing $K"; exit 1; }
[ -s "$CAP/boot-unpack/ramdisk" ] || { echo "missing ramdisk"; exit 1; }
CMDLINE='androidboot.hardware=qcom androidboot.console=ttyMSM0 androidboot.memcg=1 lpm_levels.sleep_disabled=1 msm_rtb.filter=0x237 service_locator.enable=1 swiotlb=2048 loop.max_part=7 androidboot.usbcontroller=a600000.dwc3 androidboot.fstab_suffix=qcom androidboot.init_fatal_reboot_target=recovery androidboot.super_partition=system buildvariant=user'
python3 "$MKBOOTIMG" \
  --kernel "$K" \
  --ramdisk "$CAP/boot-unpack/ramdisk" \
  --dtb "$CAP/boot-unpack/dtb" \
  --header_version 2 --pagesize 4096 --base 0x0 \
  --kernel_offset 0x00008000 --ramdisk_offset 0x01000000 \
  --tags_offset 0x00000100 --dtb_offset 0x01f00000 \
  --os_version 17.0.0 --os_patch_level 2026-11 \
  --cmdline "$CMDLINE" --id -o "$CAP/boot-owly-base-mission.img"
ls -lh "$CAP/boot-owly-base-mission.img" "$K"
sha256sum "$CAP/boot-owly-base-mission.img" "$K"
python3 "$UNPACK" --boot_img "$CAP/boot-owly-base-mission.img" | head -14
