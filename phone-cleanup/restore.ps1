# Re-enable every package disabled by debloat.txt on the Note 4 (SM-N9108V).
# Also restores the animation scales and stay-awake setting recorded before the debloat.
# Usage: powershell -ExecutionPolicy Bypass -File work\note4\restore.ps1
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
$list = Join-Path $PSScriptRoot 'debloat.txt'
$pkgs = Get-Content $list | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
foreach ($p in $pkgs) {
    $out = (& $adb shell "su -c 'pm enable $p'" 2>&1 | Out-String).Trim()
    Write-Output "$p :: $out"
}
# Bring back the stock TouchWiz home that was disabled so CleanHome could own Home.
$tw = 'com.sec.android.app.launcher'
$out = (& $adb shell "su -c 'pm enable $tw'" 2>&1 | Out-String).Trim()
Write-Output "$tw :: $out"
Write-Output "TouchWiz Home is back. Press Home and pick it (or uninstall CleanHome: pm uninstall com.hack.note4.launcher)."
# Restore the recorded animation scales (were 1.0) and stay-awake (was 0).
& $adb shell settings put global window_animation_scale 1.0
& $adb shell settings put global transition_animation_scale 1.0
& $adb shell settings put global animator_duration_scale 1.0
& $adb shell settings put global stay_on_while_plugged_in 0
Write-Output "animation scales and stay-awake restored to pre-debloat values"
