# Build + install RobotBrain on the Note 4. Runs entirely on the phone (camera eye + sensors + HTTP API).
$bt = "C:\Program Files (x86)\Android\android-sdk\build-tools\33.0.2"
$plat = "C:\Program Files (x86)\Android\android-sdk\platforms\android-33"
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
$work = "D:\Projects\cleanyourtv\work\note4\robot"
$build = "$work\build"
New-Item -ItemType Directory -Force -Path $build, "$build\classes", "$build\dex" | Out-Null
$env:PATH = "$bt;$env:PATH"

Write-Output "=== resources ==="
aapt2 link --manifest "$work\AndroidManifest.xml" -I "$plat\android.jar" -o "$build\base.apk"
Write-Output "=== javac ==="
javac -source 8 -target 8 -bootclasspath "$plat\android.jar" -d "$build\classes" (Get-ChildItem "$work\src\com\hack\note4\robot\*.java" | ForEach-Object { $_.FullName })
Write-Output "=== d8 ==="
Remove-Item "$build\dex\*" -ErrorAction SilentlyContinue
$classes = (Get-ChildItem "$build\classes\com\hack\note4\robot\*.class" | ForEach-Object { $_.FullName })
java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib "$plat\android.jar" --output "$build\dex" $classes 2>&1 | Select-Object -First 2
Write-Output "=== package ==="
Copy-Item "$build\base.apk" "$build\withdex.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$build\withdex.apk", 'Update')
$bytes = [System.IO.File]::ReadAllBytes("$build\dex\classes.dex")
$entry = $zip.CreateEntry("classes.dex")
$os = $entry.Open(); $os.Write($bytes, 0, $bytes.Length); $os.Close(); $zip.Dispose()
Write-Output "=== align + sign ==="
zipalign -f 4 "$build\withdex.apk" "$build\aligned.apk"
apksigner sign --ks "D:\Projects\cleanyourtv\work\note4\launcher\build\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$build\RobotBrain.apk" "$build\aligned.apk"
apksigner verify "$build\RobotBrain.apk"
Write-Output ("RobotBrain.apk " + (Get-Item "$build\RobotBrain.apk").Length + " bytes")
Write-Output "=== install ==="
& $adb install -r "$build\RobotBrain.apk"
Write-Output "=== grant camera permission (root) ==="
& $adb shell "su -c 'pm grant com.hack.note4.robot android.permission.CAMERA'"
Write-Output "installed (not started)"
