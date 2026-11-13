#!/system/bin/sh
exec > /sdcard/Download/dump-boot.out 2>&1
id
ls -l /dev/block/by-name/boot
dd if=/dev/block/by-name/boot of=/sdcard/Download/boot-current.img bs=1M
sync
ls -l /sdcard/Download/boot-current.img
sha256sum /sdcard/Download/boot-current.img
