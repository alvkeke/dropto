#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="cn.alvkeke.dropto.debug"
# StartupActivity is the only launcher; MainActivity cold-starts without an active user.
LAUNCH_COMPONENT="$PKG/cn.alvkeke.dropto.ui.activity.StartupActivity"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
DISCOVER_TIMEOUT="${DISCOVER_TIMEOUT:-15}"

usage() {
    cat <<EOF
usage: $(basename "$0") [-w] [-s serial] [-h]

build the debug apk, install it on the device, launch it and follow its logcat.

  -w         also wait for a wireless-debugging device on the same network
             (adb discovers it via mDNS, which takes a few seconds)
  -s serial  device serial to target (default: \$ANDROID_SERIAL, else the only online device)
  -h         show this help

env:
  DISCOVER_TIMEOUT  seconds to wait for a device with -w (default: 15)
EOF
}

online_devices() {
    adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

list_devices() {
    # -l keeps the model visible, so a phone listed twice is recognizable
    adb devices -l | sed '1d;/^[[:space:]]*$/d;s/^/    /' >&2
}

device_help() {
    list_devices
    if adb devices | grep -qw unauthorized; then
        echo "A device is waiting for authorization: accept the RSA prompt on the phone." >&2
        return
    fi
    if [ "$WIRELESS" -eq 0 ]; then
        echo "Pass -w to wait for a wireless-debugging device on the same network." >&2
        return
    fi
    cat >&2 <<'EOF'
Check USB debugging, or turn on Settings > Developer options > Wireless debugging
and keep the phone on the same network: adb discovers wireless devices via mDNS
on its own.
A phone that was never paired needs one manual pairing: adb pair <ip>:<port> <code>
If mDNS is blocked, connect manually first: adb connect <ip>:<port>
EOF
}

# Without -w only already-connected devices are used. With -w adb is left a few
# seconds to auto-connect wireless-debugging devices it finds through mDNS.
find_device() {
    local start=$SECONDS
    local online
    local announced=0

    while :; do
        mapfile -t online < <(online_devices)

        if [ -n "$SERIAL" ]; then
            if printf '%s\n' "${online[@]}" | grep -qxF -- "$SERIAL"; then
                return 0
            fi
        else
            case "${#online[@]}" in
                1)
                    SERIAL="${online[0]}"
                    return 0
                    ;;
                0) ;;
                *)
                    echo "ERROR: multiple devices online, pick one with -s serial:" >&2
                    list_devices
                    echo "note: the same phone shows up twice if it is connected manually too (adb connect)." >&2
                    exit 1
                    ;;
            esac
        fi

        if [ "$WIRELESS" -eq 0 ] || [ "$((SECONDS - start))" -ge "$DISCOVER_TIMEOUT" ]; then
            if [ -n "$SERIAL" ]; then
                echo "ERROR: device '$SERIAL' is not online." >&2
            elif [ "$WIRELESS" -eq 0 ]; then
                echo "ERROR: no adb device online." >&2
            else
                echo "ERROR: no adb device within ${DISCOVER_TIMEOUT}s." >&2
            fi
            device_help
            exit 1
        fi

        if [ "$announced" -eq 0 ]; then
            echo "==> Waiting up to ${DISCOVER_TIMEOUT}s for a device (wireless debugging shows up via mDNS)" >&2
            announced=1
        fi
        sleep 1
    done
}

SERIAL="${ANDROID_SERIAL:-}"
WIRELESS=0
while getopts ":s:wh" opt; do
    case "$opt" in
        s) SERIAL="$OPTARG" ;;
        w) WIRELESS=1 ;;
        h) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done

command -v adb >/dev/null || { echo "ERROR: adb not found in PATH." >&2; exit 1; }
find_device
echo "==> Using device $SERIAL"

echo "==> Building debug APK"
( cd "$PROJECT_DIR" && ./gradlew assembleDebug )

[ -f "$APK_PATH" ] || { echo "ERROR: APK not found at $APK_PATH" >&2; exit 1; }

echo
echo "==> Installing to $SERIAL"
if ! adb -s "$SERIAL" install -r "$APK_PATH"; then
    echo "ERROR: install failed. If the signing key changed, uninstall first:" >&2
    echo "    adb -s $SERIAL uninstall $PKG" >&2
    exit 1
fi

echo
echo "==> Launching $LAUNCH_COMPONENT"
adb -s "$SERIAL" shell am force-stop "$PKG"
adb -s "$SERIAL" logcat -c
start_out="$(adb -s "$SERIAL" shell am start -W -n "$LAUNCH_COMPONENT" 2>&1 || true)"
case "$start_out" in
    *Error* | *Exception*)
        echo "$start_out" >&2
        exit 1
        ;;
esac

pid=""
for _ in {1..20}; do
    pid="$(adb -s "$SERIAL" shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$pid" ]; then
        break
    fi
    sleep 0.5
done

if [ -z "$pid" ]; then
    echo "WARN: '$PKG' is not running, dumping the logs collected so far:" >&2
    adb -s "$SERIAL" logcat -d -v threadtime
    exit 1
fi

echo
echo "==> Following logcat of $PKG (pid $pid), press Ctrl-C to stop"
echo
exec adb -s "$SERIAL" logcat -v threadtime --pid="$pid"
