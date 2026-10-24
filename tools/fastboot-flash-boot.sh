#!/usr/bin/env bash
# Bash, file: tools/fastboot-flash-boot.sh, runtime: Linux/macOS shell + Android platform-tools, target: a connected device already in compatible Fastboot mode
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/boot.img" >&2
  exit 2
fi
image="$1"
[[ -f "$image" ]] || { echo "ERROR: image file not found: $image" >&2; exit 3; }
command -v fastboot >/dev/null 2>&1 || { echo "ERROR: fastboot is not installed (Android platform-tools)." >&2; exit 4; }
python3 - "$image" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1])
with p.open('rb') as f:
    magic = f.read(8)
if magic != b'ANDROID!':
    raise SystemExit('ERROR: expected a classic Android boot image with ANDROID! magic; no flash attempted.')
print(f'Image: {p} ({p.stat().st_size} bytes)')
PY

echo "Detected Fastboot devices:"
fastboot devices
count="$(fastboot devices | awk 'NF >= 2 {n++} END {print n+0}')"
[[ "$count" -eq 1 ]] || { echo "ERROR: expected exactly one Fastboot device; no flash attempted." >&2; exit 5; }
fastboot getvar product 2>&1 || true
unlocked="$(fastboot getvar unlocked 2>&1 || true)"
printf '%s\n' "$unlocked"
if printf '%s' "$unlocked" | grep -Eqi 'unlocked: (no|false)|is_unlocked: no'; then
  echo "ERROR: bootloader reports locked; no flash attempted." >&2
  exit 6
fi
if ! printf '%s' "$unlocked" | grep -Eqi 'unlocked: (yes|true)|is_unlocked: yes'; then
  echo "WARNING: Fastboot did not confirm the unlocked state. Verify the device screen yourself." >&2
fi
echo
echo "ONLY the 'boot' partition will be targeted. A wrong image can make the device unbootable."
read -r -p "Type exactly FLASH boot to continue: " confirmation
[[ "$confirmation" == "FLASH boot" ]] || { echo "Cancelled; nothing was flashed."; exit 7; }
fastboot flash boot "$image"
echo "Flash command completed. Rebooting device."
fastboot reboot
