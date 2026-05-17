$ErrorActionPreference = "Stop"

$jfxVersion = "21.0.6"
$m2 = "$env:USERPROFILE\.m2\repository\org\openjfx"

Write-Host "Building JAR..."
.\mvnw.cmd clean package
if ($LASTEXITCODE -ne 0) { Write-Error "Maven build failed"; exit 1 }

$modulePath = @(
    "$m2\javafx-base\$jfxVersion\javafx-base-$jfxVersion-win.jar",
    "$m2\javafx-graphics\$jfxVersion\javafx-graphics-$jfxVersion-win.jar",
    "$m2\javafx-controls\$jfxVersion\javafx-controls-$jfxVersion-win.jar",
    "$m2\javafx-fxml\$jfxVersion\javafx-fxml-$jfxVersion-win.jar"
) -join ";"

New-Item -ItemType Directory -Force -Path dist-input | Out-Null
Copy-Item "target\desktop-windows-1.0-SNAPSHOT.jar" "dist-input\app.jar" -Force

Write-Host "Running jpackage..."
jpackage `
    --type exe `
    --name TaskPilot `
    --app-version 1.0 `
    --input dist-input `
    --main-jar app.jar `
    --main-class com.example.desktopwindows.Launcher `
    --module-path $modulePath `
    --add-modules "javafx.controls,javafx.fxml" `
    --java-options "-Dfile.encoding=UTF-8" `
    --win-dir-chooser `
    --win-shortcut `
    --win-menu `
    --dest dist

if ($LASTEXITCODE -ne 0) {
    Write-Host "jpackage --type exe failed (possibly no WiX). Trying app-image..."
    jpackage `
        --type app-image `
        --name TaskPilot `
        --input dist-input `
        --main-jar app.jar `
        --main-class com.example.desktopwindows.Launcher `
        --module-path $modulePath `
        --add-modules "javafx.controls,javafx.fxml" `
        --java-options "-Dfile.encoding=UTF-8" `
        --dest dist
}

Write-Host "Done! Check the 'dist' folder."
