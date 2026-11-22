# Builds the Microsoft Store package: build\EasyNotes92-<version>.msix
# Run build.ps1 first (it makes the app folder this script packs).
# Needs the Windows SDK (makeappx.exe):  winget install Microsoft.WindowsSDK.10.0.26100
# The package is NOT signed: the Microsoft Store signs it after certification.

$ErrorActionPreference = 'Stop'
$root   = $PSScriptRoot
$build  = Join-Path $root 'build'
$app    = Join-Path $env:LOCALAPPDATA 'Programs\EasyNotes92'
$layout = Join-Path $build 'msix-layout'

if (-not (Test-Path "$app\EasyNotes92.exe")) { throw "Run build.ps1 first ($app not found)." }
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\makeappx.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $makeappx) { throw 'makeappx.exe not found: install the Windows SDK (see the top of this script).' }

[xml]$manifest = Get-Content "$root\msix\AppxManifest.xml"
$version = $manifest.Package.Identity.Version
$msix = Join-Path $build "EasyNotes92-$version.msix"

Write-Host '1/3 Copying the app...'
if (Test-Path $layout) { Remove-Item -Recurse -Force $layout }
Copy-Item -Recurse $app $layout
Copy-Item "$root\msix\AppxManifest.xml", "$root\LICENSE.md", "$root\NOTICE.md" $layout

Write-Host '2/3 Store logos...'
$cp = (@("$build\classes") + (Get-ChildItem "$root\lib\*.jar").FullName) -join ';'
& java -cp $cp com.esisa.notepad.AppIcon --store-assets "$layout\Assets"
if ($LASTEXITCODE -ne 0) { throw 'logo rendering failed' }

Write-Host '3/3 Packing with makeappx...'
if (Test-Path $msix) { Remove-Item -Force $msix }
& $makeappx.FullName pack /d $layout /p $msix /o
if ($LASTEXITCODE -ne 0) { throw 'makeappx failed' }

Write-Host ''
Write-Host "Done:  $msix"
Write-Host 'Upload it in Partner Center > EasyNotes92 > Start submission > Packages.'
