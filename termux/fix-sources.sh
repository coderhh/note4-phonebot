#!/data/data/com.termux/files/usr/bin/bash
# Fix Termux sources for Android 6 (correct paths). Drop dead Bintray lists,
# keep only the Bzi-Han community mirror (main + root) as trusted, then install tools.
PREFIX=/data/data/com.termux/files/usr
SL=$PREFIX/etc/apt/sources.list
SLD=$PREFIX/etc/apt/sources.list.d

# Remove every source list (dead Bintray + unsigned science) so only our two clean ones remain.
rm -f "$SLD"/*.list "$SLD"/*.list.bak 2>/dev/null

M="https://bzi-han.github.io/TermuxLegacy/termux-repositories-legacy/webroot"
echo "deb [trusted=yes] $M/termux-packages stable main" > "$SL"
echo "deb [trusted=yes] $M/termux-root-packages-21 root stable" > "$SLD/termux-legacy-root.list"

echo "=== apt-get update ==="
apt-get update
echo "=== installing tools ==="
apt-get install -y python openssh git curl wget nano tree
echo "=== DONE ==="
