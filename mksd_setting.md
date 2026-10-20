# MKSD home screen: settings, benefits and how-to

My notes for the MKSD home screen on our TV. It is built on [TV Detox](https://github.com/Ansub/tv-detox),
with extra features made for this TV.

- **TV:** Redmi / Xiaomi Mi TV, Android TV 10, MediaTek chip, 1.8 GB RAM
- **Soundbar:** an eARC soundbar on the TV's **HDMI 2** (the eARC port)
- **Code:** branch `mksd-home`, pushed to `main` of this repo
- **TV notes and screenshots:** [docs/xiaomi-redmi.md](docs/xiaomi-redmi.md)

---

## What "MKSD mode" (Detox on) gives you

| Benefit | Before (Google home) | With MKSD |
| --- | --- | --- |
| **No ads** | "Sponsored" banner at the top, "Sponsored by JioHotstar" row, Ambient Mode ads | None. Only your apps |
| **Less memory used** | The Google home screen alone used about **200 MB** of the TV's 1.8 GB RAM, plus about 15 MB for the sponsored row service | Those apps are off, so apps like Netflix and YouTube get the memory |
| **Faster feel** | Menus animate at 1x | Animations at **0.5x**, so menus feel snappier |
| **Your apps, your order** | Rows reshuffled by Google | Fixed row: **Sun NXT, Netflix, JioHotstar, Prime Video, Media Player, PatchWall**, order locked. New apps can still be added at the end |
| **Soundbar volume shown** | In eARC mode the TV shows **no** volume bar | A **"Volume 42%"** box shows the soundbar's real level over any app |
| **One-press switch** | Switching needed a laptop | **⏻ Detox** icon flips between MKSD and Google home from the remote, offline |
| **Clean screen** | Search, Shop and Discover tabs, trending rows | Clock, MKSD title, apps, HDMI inputs, Settings |
| **Reversible** | | Every app is only *disabled*, never uninstalled. One press, or `undo.sh`, brings it all back |

PatchWall (Xiaomi's own content app) is **not touched**. It is on the app row.

---

## The home screen, icon by icon

Top right, reached with **Up** from the apps:

| Icon | What it does |
| --- | --- |
| **⏻ Detox** | Bright = Detox on (MKSD is home). Dim = off (Google home). Press **OK** to switch. The first time, the TV asks "Allow USB debugging?" for `mksd@tv`. Tick *Always allow*. |
| **🔊 Volume box** | Bright = the volume box is on. Dim = off. Press **OK** to switch. |
| **Wi-Fi** | Opens network settings. |

Bottom row: Play Store, HDMI 1, Soundbar, HDMI 3, Settings, Add app.
With the lock on, the **order** is protected: Move and Replace (long-press or Menu on an app) show "App order is locked".
**Add app** still works and puts the new app at the end, and **Remove from home** and Uninstall / App info still work.

**Getting back to MKSD from Google mode:** on the Google home screen, open **Apps**, then **MKSD** (purple tile),
then **⏻**, then **OK**.

---

## The volume box

- Shows the soundbar's level in **%**, top right, for 2.5 seconds after each change.
- The number is the soundbar's own 0–100 scale. The TV keeps the soundbar's CEC volume as its music volume, and
  the TV's maximum is 100, so nothing is converted.
- **Holding** the button: the number keeps moving, then settles on the soundbar's real level when you let go.
  While held, the TV's number runs ahead of the soundbar, because the soundbar only acts on some of the repeated presses.
  That's how the soundbar works, not a fault.
- **TV speakers:** the box hides itself, because the TV shows its own bar.
- **ARC (not eARC):** the TV also shows its own bar, but it doesn't tell apps whether it's ARC or eARC. Turn the box
  off with the 🔊 icon while you're on ARC.

| Sound output | TV's own bar | MKSD volume box |
| --- | --- | --- |
| eARC soundbar | hidden | **shows**: this is what it's for |
| ARC soundbar | shows | shows too. Turn it off with 🔊 if you don't want two |
| TV speakers | shows | hidden automatically |

---

## Settings script: `mksd.sh`

Run it after any reinstall to put everything back: app order, title, volume box and lock.

```sh
sh mksd.sh <TV_IP>
```

Edit the values at the top of `mksd.sh` to change them:

```sh
APPS="com.suntv.sunnxt,com.netflix.ninja,in.startv.hotstar,com.amazon.amazonvideo.livingroom,com.xiaomi.mitv.mediaexplorer,com.mitv.tvhome.atv"
TITLE="MKSD"
VOLUME=on   # volume box
LOCK=on     # app order can't be moved or replaced from the remote; Add app still works
```

To rearrange apps on the TV: set `LOCK=off`, run it, move the apps with the remote, set `LOCK=on`, and run it again.

`mksd.sh` sets the row to exactly the `APPS` list. If you added apps on the TV, add their package names to `APPS`
too, or running `mksd.sh` will take them off the row.

### Single settings (from the Mac)

```sh
H="adb -s <TV_IP>:5555 shell am start -n com.ansub.tvhome/.MainActivity"
$H --es title "MKSD"          # name above the clock ("" hides it)
$H --es apps "pkg1,pkg2,..."  # app row, in order
$H --es volume on|off         # volume box
$H --es lock on|off           # lock the app order (Add and Remove still work)
$H --es quick "bluetooth,sound,picture"   # extra settings icons by the clock
```

---

## What was changed on the TV

| Change | Why | How to undo |
| --- | --- | --- |
| `com.google.android.tvlauncher` (Google home) disabled | So Home opens MKSD. Xiaomi always lets its own home win when it's on, even when MKSD is set as default. | ⏻ Detox off, or `undo.sh` |
| Disabled: `tvrecommendations` (sponsored row), `backdrop` (Ambient Mode ads), `videos` (Play Movies), `youtube.tvmusic`, `play.games`, `feedback` | Ads and unused Google apps | ⏻ Detox off, or `pm enable <pkg>` |
| Screensaver set to `com.android.dreams.basic/.Colors` | With Ambient Mode off the TV had **no screensaver and went to standby after 5 minutes**. Detox off switches back to Ambient Mode. | ⏻ Detox off |
| Animations at 0.5x (`window`, `transition`, `animator_duration_scale`) | Faster feel | `settings put global <name> 1.0` |
| App caches cleared once (about 500 MB freed) | Free storage | Nothing to undo: caches refill |
| `SYSTEM_ALERT_WINDOW` allowed for MKSD | The volume box draws over other apps | `appops set com.ansub.tvhome SYSTEM_ALERT_WINDOW deny` |
| Debugging key `mksd@tv` allowed | The ⏻ switch talks to the TV's own debugging | Developer options → Revoke USB debugging authorisations |

Not changed: PatchWall, Xiaomi system apps, the Xiaomi analytics apps (left on: they weren't put through a restart
test), Live TV and inputs, the keyboard, setup apps.

---

## Going fully back to the stock Google home screen

Easiest: press **⏻** (Detox off). Or, from the Mac:

```sh
sh undo.sh <TV_IP>
for p in tvrecommendations backdrop videos youtube.tvmusic play.games feedback; do
  adb -s <TV_IP>:5555 shell pm enable com.google.android.$p
done
adb -s <TV_IP>:5555 shell settings put secure screensaver_components com.google.android.backdrop/.Backdrop
# optional: remove MKSD completely
adb -s <TV_IP>:5555 uninstall com.ansub.tvhome
```

---

## Developer mode

- **Needed for:** the ⏻ Detox switch, and for installing updates from the Mac.
- **Not needed for:** the MKSD home, app order, lock and volume box. These keep working with it off.
- If it's off, ⏻ shows "Couldn't switch Detox" and changes nothing.
- Leaving network debugging on is reasonably safe: every new device must be approved with "Allow" on the TV.

---

## Known issues and fixes

- **eARC falls back to ARC, or the soundbar shows "HDMI-In", after standby.** This is the TV and soundbar handshake,
  not MKSD: it happened with every app back on too. The CEC log shows the soundbar answering with plain ARC.
  To try, in order:
  1. A certified **Ultra High Speed (HDMI 2.1, 48 Gbps)** cable from TV **HDMI 2** to the soundbar's **eARC** port.
  2. Update the soundbar firmware with the soundbar maker's phone app.
  3. In the TV's sound settings, set **eARC to Auto** (this TV only offers Auto / Off; Auto is the "on" setting:
     eARC when the handshake works, ARC when it doesn't), and keep HDMI-CEC on.
  4. Quick fix: switch the soundbar off and on, or toggle eARC in the TV's sound settings.
- **The TV starts on Live TV or HDMI instead of home.** That's the Xiaomi "start on last input" setting. Set it to Home
  in Settings → Device Preferences.
- **After an update that adds network permission, ⏻ fails with `socket failed: EPERM`.** Android 10 bug: the network
  permission is only registered on a fresh install. Fix: `adb uninstall com.ansub.tvhome`, install again, run
  `mksd.sh`.
- **A restart can upset the soundbar link.** If there's no sound after a restart, switch the soundbar off and on.

---

## Building and installing (from the Mac)

Needs Java 17 and the Android SDK (both installed on this Mac with Homebrew):

```sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
sh build.sh                                            # makes out/tvhome.apk
adb -s <TV_IP>:5555 install -r out/tvhome.apk      # update the TV
sh mksd.sh <TV_IP>                                 # only needed after a fresh install
```

**Keep `key.jks` (in the project folder, not in git) safe.** Every update must be signed with the same key, or
the TV refuses it. If it's lost: uninstall MKSD, install the new build, and run `mksd.sh`.

### Code map

| File | What it is |
| --- | --- |
| `src/com/ansub/tvhome/MainActivity.java` | The home screen: clock, title, apps, icons, lock |
| `src/com/ansub/tvhome/VolumeOverlay.java` | The volume box (a foreground service with an overlay) |
| `src/com/ansub/tvhome/Detox.java` | What ⏻ turns on and off |
| `src/com/ansub/tvhome/AdbShell.java` | A small client for the TV's own debugging on 127.0.0.1:5555 |
| `src/com/ansub/tvhome/Boot.java` | Starts the volume box at boot and after updates |
| `res/drawable/banner.xml`, `icon.xml` | The purple MKSD tile and icon |
| `apps.txt` | Default app order built into the app |
| `mksd.sh` | Puts our settings back |
