#!/system/bin/sh
# FlashDock raw image writer. Only boot and a separate recovery partition are accepted.
set -eu
kind="${1:-}"
img="${2:-}"
case "$kind" in
  boot) pname="boot" ;;
  recovery) pname="recovery" ;;
  *) echo "ERROR: unsupported target"; exit 20 ;;
esac
[ -f "$img" ] || { echo "ERROR: image file missing"; exit 21; }
magic="$(dd if="$img" bs=1 count=8 2>/dev/null || true)"
[ "$magic" = "ANDROID!" ] || { echo "ERROR: unsupported image header; expected Android boot image (ANDROID!)"; exit 22; }
part=""
for candidate in \
  "/dev/block/by-name/$pname" \
  "/dev/block/bootdevice/by-name/$pname" \
  /dev/block/platform/*/by-name/"$pname" \
  /dev/block/platform/*/*/by-name/"$pname"; do
  if [ -e "$candidate" ]; then part="$candidate"; break; fi
done
[ -n "$part" ] || { echo "ERROR: partition '$pname' not found on this device; no write attempted"; exit 23; }
real_part="$(readlink -f "$part" 2>/dev/null || echo "$part")"
[ -b "$real_part" ] || { echo "ERROR: target is not a block device: $real_part"; exit 24; }
image_size="$(wc -c < "$img" | tr -d ' ')"
[ "$image_size" -gt 0 ] || { echo "ERROR: empty image"; exit 25; }
part_size="$(blockdev --getsize64 "$real_part" 2>/dev/null || true)"
case "$part_size" in ''|*[!0-9]*)
  sys_name="${real_part##*/}"
  sectors="$(cat "/sys/class/block/$sys_name/size" 2>/dev/null || echo 0)"
  case "$sectors" in ''|*[!0-9]*) sectors=0 ;; esac
  part_size=$((sectors * 512))
  ;;
esac
[ "$part_size" -gt 0 ] || { echo "ERROR: cannot determine partition size; no write attempted"; exit 26; }
[ "$image_size" -le "$part_size" ] || { echo "ERROR: image ($image_size bytes) is larger than partition ($part_size bytes)"; exit 27; }
backup_dir="/sdcard/FlashDock/backups"
mkdir -p "$backup_dir" || { echo "ERROR: cannot create backup directory"; exit 28; }
free_kb="$(df -Pk /sdcard | awk 'NR==2 {print $4}')"
case "$free_kb" in ''|*[!0-9]*) free_kb=0 ;; esac
[ "$((free_kb * 1024))" -ge "$((part_size + 1048576))" ] || { echo "ERROR: insufficient free shared storage for a full partition backup"; exit 29; }
backup="$backup_dir/${pname}_$(date +%Y%m%d-%H%M%S).img"
echo "TARGET=$real_part"
echo "PARTITION_BYTES=$part_size"
echo "IMAGE_BYTES=$image_size"
echo "BACKUP=$backup"
echo "Creating full partition backup..."
dd if="$real_part" of="$backup" bs=4M || { echo "ERROR: backup read failed; flash not attempted"; exit 30; }
sync
backup_size="$(wc -c < "$backup" | tr -d ' ')"
[ "$backup_size" -ge "$part_size" ] || { echo "ERROR: backup is incomplete; flash not attempted"; exit 31; }
echo "Backup verified by size. Writing image..."
dd if="$img" of="$real_part" bs=4M || { echo "ERROR: write command failed. Backup is at $backup"; exit 32; }
sync
echo "Reading back image for verification..."
if cmp -n "$image_size" "$img" "$real_part"; then
  echo "SUCCESS: image bytes match the target partition."
  echo "BACKUP=$backup"
  exit 0
fi
echo "VERIFY_FAILED: read-back differs. Attempting to restore the pre-flash backup..."
if dd if="$backup" of="$real_part" bs=4M; then
  sync
  echo "RESTORE_ATTEMPTED: backup was written back; reboot only after reviewing device status."
else
  echo "RESTORE_FAILED: keep the device powered and use device-specific recovery instructions."
fi
exit 33
