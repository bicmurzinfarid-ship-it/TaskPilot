$path = 'c:\pre\front\desktop-windows\src\main\java\com\example\desktopwindows\MainController.java'
$bytes = [System.IO.File]::ReadAllBytes($path)
# replace each 0x00 byte with 0x20 (space)
for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -eq [byte]0) {
        $bytes[$i] = [byte]32
    }
}
[System.IO.File]::WriteAllBytes($path, $bytes)
# verify
$remaining = ($bytes | Where-Object { $_ -eq 0 }).Count
Write-Host "Remaining nulls: $remaining"
