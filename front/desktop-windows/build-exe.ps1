$ErrorActionPreference = "Stop"

$jfxVersion = "21.0.6"
$m2 = "$env:USERPROFILE\.m2\repository\org\openjfx"
$resources = "src\main\resources\com\example\desktopwindows"

# --- PNG -> ICO conversion (proper binary format, multi-size) ---
function ConvertTo-Ico($pngPath, $icoPath) {
    Add-Type -AssemblyName System.Drawing
    $src   = [System.Drawing.Bitmap]::new((Resolve-Path $pngPath).Path)
    $sizes = @(16, 32, 48, 256)

    $images = foreach ($sz in $sizes) {
        $bmp = New-Object System.Drawing.Bitmap($sz, $sz)
        $g   = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($src, 0, 0, $sz, $sz)
        $g.Dispose()
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        , $ms.ToArray()
    }
    $src.Dispose()

    $out = New-Object System.IO.MemoryStream
    $bw  = New-Object System.IO.BinaryWriter($out)
    $bw.Write([uint16]0); $bw.Write([uint16]1); $bw.Write([uint16]$sizes.Count)

    $offset = 6 + $sizes.Count * 16
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        $sz = $sizes[$i]
        $bw.Write([byte]$(if ($sz -eq 256) { 0 } else { $sz }))
        $bw.Write([byte]$(if ($sz -eq 256) { 0 } else { $sz }))
        $bw.Write([byte]0); $bw.Write([byte]0)
        $bw.Write([uint16]1); $bw.Write([uint16]32)
        $bw.Write([uint32]$images[$i].Length)
        $bw.Write([uint32]$offset)
        $offset += $images[$i].Length
    }
    foreach ($img in $images) { $bw.Write($img) }
    $bw.Flush()
    [System.IO.File]::WriteAllBytes($icoPath, $out.ToArray())
    $bw.Dispose(); $out.Dispose()
}

$icoPath = (Resolve-Path "$resources\icon_tasks_section.png" | Split-Path) + "\taskpilot.ico"
Write-Host "Converting PNG to ICO..."
ConvertTo-Ico "$resources\icon_tasks_section.png" $icoPath

# --- Build JAR ---
Write-Host "Building JAR..."
.\mvnw.cmd clean package
if ($LASTEXITCODE -ne 0) { Write-Error "Maven build failed"; exit 1 }

# --- Prepare input directory ---
New-Item -ItemType Directory -Force -Path dist-input | Out-Null
Remove-Item dist-input\* -ErrorAction SilentlyContinue
Copy-Item "target\desktop-windows-1.0-SNAPSHOT.jar" "dist-input\app.jar" -Force

foreach ($name in @("javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml")) {
    Copy-Item "$m2\$name\$jfxVersion\$name-$jfxVersion-win.jar" dist-input\ -Force
}

# --- Find JAVA_HOME ---
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaExe  = (Get-Command java).Source
    $javaHome = (Get-Item $javaExe).Directory.Parent.FullName
}
Write-Host "Using JDK: $javaHome"

# $APPDIR is expanded by jpackage at launch time (single quotes = NOT expanded by PowerShell)
$jfxModPath = '$APPDIR\javafx-base-21.0.6-win.jar;$APPDIR\javafx-graphics-21.0.6-win.jar;$APPDIR\javafx-controls-21.0.6-win.jar;$APPDIR\javafx-fxml-21.0.6-win.jar'

Remove-Item dist -Recurse -Force -ErrorAction SilentlyContinue

# --- jpackage ---
Write-Host "Running jpackage..."
jpackage `
    --type app-image `
    --name TaskPilot `
    --input dist-input `
    --main-jar app.jar `
    --main-class com.example.desktopwindows.Launcher `
    --runtime-image $javaHome `
    --icon (Resolve-Path $icoPath) `
    --java-options "--module-path $jfxModPath --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8" `
    --dest dist

if ($LASTEXITCODE -ne 0) { Write-Error "jpackage failed"; exit 1 }
Write-Host ""
Write-Host "Done! Run: dist\TaskPilot\TaskPilot.exe"
