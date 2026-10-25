# Termux on the Note 4 (Android 6) — what's set up

A real Linux environment runs on the phone itself. No PC needed.

## Installed

- **Termux** (`com.termux`) — legacy v0.79 with offline bootstraps (the last build that supports Android 6)
- **Termux:API** (`com.termux.api`) — so scripts can reach the camera, sensors, etc. via `termux-*` commands
- A working **package repo** — pointed at the Bzi-Han community mirror of the 2019 legacy snapshot
  (the official `termux.net` / Bintray repos are dead since 2020)

## Linux tools installed and verified working

- shells: bash, dash, busybox
- coreutils, findutils, diffutils, grep, gawk, sed, patch, tar, gzip, bzip2, xz, unzip
- curl 7.67, wget 1.20, openssh 8.1p1 (ssh + scp + sftp), openssl, gnupg
- nano 4.6, tree, less, procps, psmisc, net-tools, inetutils, util-linux
- dpkg / apt package manager

That is a complete everyday Linux userland on the phone.

## Not available on the mirror (the index lists them but the .deb files 404)

- `python` / `python2`, `git` — the Bzi-Han mirror is partial; these specific .deb files are missing.

To get them, download the full 2019 snapshot from archive.org and serve it locally:
`https://archive.org/download/termux-repositories-legacy/termux-package-sources-legacy-24.12.2019.tar` (3.9 GB)
Extract, serve the `termux-packages` directory over HTTP, and point Termux at it in
`$PREFIX/etc/apt/sources.list`.

## How to use it

- Tap the **Termux** icon in the home grid (or `am start -n com.termux/.app.TermuxActivity`)
- You're at a real bash shell. `pkg install <name>` / `apt-get install <name>` to add tools.
- SSH into the phone: `sshd` can be started with `sshd` (set a password with `passwd` first).
- Reach hardware from scripts: `termux-camera-photo`, `termux-sensor`, `termux-toast` etc. (from the `termux-api` package, when installed).

## Files

- `work/note4/termux/setup.sh` — first-run source setup (historical)
- `work/note4/termux/fix-sources.sh` — the working source fix (Bzi-Han mirror, trusted)
- `work/note4/termux/install-tools.sh` — installs the verified toolset
- `work/note4/termux/termux.apk` / `termux-api.apk` — the installed APKs
