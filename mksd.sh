#!/bin/sh
# Puts the MKSD home screen back the way we like it: app order, title, volume box, lock.
# Run it after installing or reinstalling the app. Changes nothing else on the TV.
#
#   sh mksd.sh <TV_IP>
#
# Edit the values below to change them.
set -e

APPS="com.suntv.sunnxt,com.netflix.ninja,in.startv.hotstar,com.amazon.amazonvideo.livingroom,com.xiaomi.mitv.mediaexplorer,com.mitv.tvhome.atv"
TITLE="MKSD"
VOLUME=on   # on: show the volume box over apps (for eARC). off: hide it.
LOCK=on     # on: the app order can't be moved or replaced from the remote (Add app and Remove still work).
# Note: this sets the row to exactly APPS, so add any apps you added on the TV here too.

TARGET="$1"
[ -n "$TARGET" ] || { echo "Usage: sh mksd.sh <TV_IP>" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }

# The volume box draws over other apps, which needs this once per install.
adb -s "$TV" shell appops set com.ansub.tvhome SYSTEM_ALERT_WINDOW allow </dev/null
adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es stamp "$(date +%s)" \
  --es apps "\"$APPS\"" --es title "\"$TITLE\"" --es volume $VOLUME --es lock $LOCK </dev/null >/dev/null
echo "Done: $TITLE, apps in order, volume box $VOLUME, lock $LOCK."
