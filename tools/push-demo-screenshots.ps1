# Copies the demo screenshots into Screenshots/ButlerDemo on the phone and asks Android to index them.
# The app's Demo mode (Settings, on by default in debug builds) reads only that folder.
# Requires adb (Android Studio: SDK/platform-tools) and a phone with USB debugging on.
# Usage:  powershell -ExecutionPolicy Bypass -File tools\push-demo-screenshots.ps1
param(
    [string]$Source = (Join-Path $PSScriptRoot '..\demo-screenshots'),
    [string]$Target = '/sdcard/Pictures/Screenshots/ButlerDemo'
)
$ErrorActionPreference = 'Stop'

adb shell mkdir -p $Target
foreach ($f in Get-ChildItem $Source -Filter *.png) {
    adb push -a $f.FullName "$Target/$($f.Name)" | Out-Null
    # Ask MediaStore to pick the file up so the app can see it.
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$Target/$($f.Name)" | Out-Null
    Write-Host "pushed $($f.Name)"
}
# Files scanned this way stay "pending" (is_pending=1), which hides them from apps. Clear the flag one row at
# a time (MediaStore rejects a bulk update on the images table). The commands run as a script ON the phone:
# Windows PowerShell 5.1 mangles the nested quotes the --where clause needs when passing them to adb.
$script = @'
content query --uri content://media/external/images/media --projection _id --where "relative_path LIKE '%ButlerDemo%' AND is_pending=1" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | while read id; do
  content update --uri content://media/external/images/media/$id --bind is_pending:i:0
done
content query --uri content://media/external/images/media --projection _id --where "relative_path LIKE '%ButlerDemo%' AND is_pending=0" | grep -c Row
'@
$tmp = Join-Path $env:TEMP 'butler-unpend.sh'
[System.IO.File]::WriteAllText($tmp, ($script -replace "`r`n", "`n"))
adb push $tmp /data/local/tmp/butler-unpend.sh | Out-Null
$visible = adb shell sh /data/local/tmp/butler-unpend.sh
adb shell rm /data/local/tmp/butler-unpend.sh | Out-Null
Remove-Item $tmp

Write-Host ''
Write-Host "Done. $visible demo screenshots are visible to apps."
