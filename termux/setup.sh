#!/data/data/com.termux/files/usr/bin/bash
# Point legacy Termux (Android 6) at the Bzi-Han community mirror of the 2019 package
# snapshot, then update and install a set of Linux tools. Run inside Termux.
set -e
PREFIX=/data/data/com.termux/files/usr
SL=$PREFIX/etc/apt/sources.list
SLD=$PREFIX/etc/apt/sources.list.d

# Back up the dead default sources.
[ -f "$SL" ] && ! [ -f "$SL.bak" ] && cp "$SL" "$SL.bak"
for f in game.list science.list root.list; do
  [ -f "$SLD/$f" ] && ! [ -f "$SLD/$f.bak" ] && cp "$SLD/$f" "$SLD/$f.bak"
done

# Main + root + game + science from the community mirror.
M="https://bzi-han.github.io/TermuxLegacy/termux-repositories-legacy/webroot"
echo "deb $M/termux-packages stable main" > "$SL"
echo "deb $M/termux-root-packages-21 root stable" > "$SLD/termux-legacy-root.list"
echo "deb $M/game-packages-21 games stable" > "$SLD/termux-legacy-game.list"
echo "deb $M/science-packages-21 science stable" > "$SLD/termux-legacy-science.list"

echo "=== sources set, running apt-get update ==="
apt-get update
echo "=== installing tools ==="
apt-get install -y python openssh git curl wget nano tree
echo "=== done ==="
