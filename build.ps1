# Builds EasyNotes92.exe and puts a shortcut on the Desktop.
# Run from PowerShell:   powershell -ExecutionPolicy Bypass -File build.ps1
# Needs a JDK (javac, jar, jpackage) on the PATH.

$ErrorActionPreference = 'Stop'
$root  = $PSScriptRoot
$build = Join-Path $root 'build'
$name  = 'EasyNotes92'
$dest  = Join-Path $env:LOCALAPPDATA 'Programs'
$app   = Join-Path $dest $name

if (Get-Process -Name $name -ErrorAction SilentlyContinue) {
    throw "$name is running - close it first, then run build.ps1 again."
}

Write-Host '1/4 Compiling...'
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force "$build\classes", "$build\input" | Out-Null
$libs = Get-ChildItem "$root\lib\*.jar"
$cp = ($libs.FullName) -join ';'
$sources = (Get-ChildItem "$root\src" -Recurse -Filter *.java).FullName
& javac --release 25 -encoding UTF-8 -cp $cp -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '2/4 Packaging the jar + icon...'
& jar --create --file "$build\input\easynotes92.jar" --main-class com.esisa.notepad.Main -C "$build\classes" .
Copy-Item $libs.FullName "$build\input\"
& java -cp "$build\classes;$cp" com.esisa.notepad.AppIcon "$build\easynotes92.ico" | Out-Null

Write-Host "3/4 Building $name.exe with jpackage (takes a minute)..."
if (Test-Path $app) { Remove-Item -Recurse -Force $app }
New-Item -ItemType Directory -Force $dest | Out-Null
& jpackage --type app-image --name $name --app-version 1.0 `
    --input "$build\input" --main-jar easynotes92.jar --main-class com.esisa.notepad.Main `
    --icon "$build\easynotes92.ico" --dest $dest `
    --java-options '-Dfile.encoding=UTF-8' --java-options '--enable-native-access=ALL-UNNAMED' `
    --java-options '-Xms16m' --java-options '-Xmx48m' --java-options '-XX:+UseSerialGC' `
    --java-options '-XX:TieredStopAtLevel=1' --java-options '-XX:ReservedCodeCacheSize=24m' `
    --java-options '-XX:MaxMetaspaceSize=64m' --java-options '-Xss512k' `
    --java-options '-XX:+UseStringDeduplication' --java-options '-XX:+UseCompactObjectHeaders' `
    --java-options '-XX:MinHeapFreeRatio=10' --java-options '-XX:MaxHeapFreeRatio=30' `
    --java-options '-Dsun.java2d.d3d=false' `
    --description 'Bloc-notes pour la prise de note' `
    --vendor 'Mohamed Iraqi Houssaini' `
    --copyright 'Copyright (c) 2026 Mohamed Iraqi Houssaini. PolyForm Noncommercial 1.0.0 - free, never for sale.'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }

Write-Host '4/4 Desktop shortcut...'
$desktop = [Environment]::GetFolderPath('Desktop')
$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut((Join-Path $desktop "$name.lnk"))
$lnk.TargetPath = Join-Path $app "$name.exe"
$lnk.WorkingDirectory = $app
$lnk.IconLocation = (Join-Path $app "$name.exe") + ',0'
$lnk.Description = 'EasyNotes92 - prise de note'
$lnk.Save()

Write-Host ''
Write-Host "Done:  $app\$name.exe"
Write-Host "Shortcut:  $desktop\$name.lnk"
