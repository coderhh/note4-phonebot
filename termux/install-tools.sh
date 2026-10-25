#!/data/data/com.termux/files/usr/bin/bash
# Install the tools that are available on the legacy mirror, and report what else exists.
PREFIX=/data/data/com.termux/files/usr
echo "=== installing available tools ==="
apt-get install -y openssh curl nano tree wget
echo "=== what python variants exist? ==="
apt-cache search python | head -20
echo "=== what git variants exist? ==="
apt-cache search git | head -10
echo "=== installed so far ==="
dpkg -l | tail -30
echo "=== DONE ==="
