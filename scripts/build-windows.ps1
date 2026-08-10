[CmdletBinding()]
param(
    [ValidateSet("exe", "msix", "all")]
    [string]$Target = "exe",

    [string]$Version = $env:PACKAGE_VERSION,
    [string]$IdentityName = $env:MSIX_IDENTITY_NAME,
    [string]$Publisher = $env:MSIX_PUBLISHER,
    [string]$PublisherDisplayName = $env:MSIX_PUBLISHER_DISPLAY_NAME,

    [ValidateSet("x64", "x86", "arm64")]
    [string]$Architecture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $Command"
    }
}

function Convert-ToMsixVersion {
    param([Parameter(Mandatory)][string]$Value)

    $numericVersion = ($Value -split "-", 2)[0]
    $parts = [System.Collections.Generic.List[string]]::new()
    foreach ($part in ($numericVersion -split "\.")) {
        $parts.Add($part)
    }
    if ($parts.Count -lt 1 -or $parts.Count -gt 4) {
        throw "Version must contain one to four numeric components."
    }
    while ($parts.Count -lt 4) {
        $parts.Add("0")
    }
    foreach ($part in $parts) {
        $parsed = 0
        if (-not [int]::TryParse($part, [ref]$parsed) -or $parsed -lt 0 -or $parsed -gt 65535) {
            throw "Each MSIX version component must be an integer from 0 through 65535."
        }
    }
    if ([int]$parts[0] -eq 0) {
        throw "The first MSIX version component must be greater than zero."
    }
    if ([int]$parts[3] -ne 0) {
        throw "The fourth MSIX version component is reserved by Microsoft Store and must be zero."
    }
    return $parts -join "."
}

function Convert-ToXmlText {
    param([Parameter(Mandatory)][string]$Value)
    return [System.Security.SecurityElement]::Escape($Value)
}

function Find-MakeAppx {
    $fromPath = Get-Command "MakeAppx.exe" -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    $programFilesX86 = [Environment]::GetFolderPath("ProgramFilesX86")
    $kitsBin = Join-Path $programFilesX86 "Windows Kits\10\bin"
    if (Test-Path $kitsBin) {
        $sdkDirectories = Get-ChildItem $kitsBin -Directory | Sort-Object Name -Descending
        foreach ($sdkDirectory in $sdkDirectories) {
            foreach ($toolArchitecture in @("x64", "x86")) {
                $candidate = Join-Path $sdkDirectory.FullName "$toolArchitecture\MakeAppx.exe"
                if (Test-Path $candidate) {
                    return $candidate
                }
            }
        }
    }

    $appCertificationKit = Join-Path $programFilesX86 "Windows Kits\10\App Certification Kit\MakeAppx.exe"
    if (Test-Path $appCertificationKit) {
        return $appCertificationKit
    }

    throw "MakeAppx.exe was not found. Install the Windows 10/11 SDK."
}

function Find-Maven {
    param([Parameter(Mandatory)][string]$Root)

    $wrapper = Join-Path $Root "mvnw.cmd"
    if (Test-Path $wrapper) {
        return $wrapper
    }
    $fromPath = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if (-not $fromPath) {
        $fromPath = Get-Command "mvn" -ErrorAction SilentlyContinue
    }
    if ($fromPath) {
        return $fromPath.Source
    }
    throw "Maven was not found. Install Maven 3.9+ or add mvn to PATH."
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$PomPath = Join-Path $ProjectRoot "pom.xml"
[xml]$Pom = Get-Content -Raw $PomPath
$NamespaceManager = [System.Xml.XmlNamespaceManager]::new($Pom.NameTable)
$NamespaceManager.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
$ProjectVersion = $Pom.SelectSingleNode("/m:project/m:version", $NamespaceManager).InnerText

if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = ($ProjectVersion -split "-", 2)[0]
}
$JpackageVersion = ($Version -split "-", 2)[0]
$MsixVersion = Convert-ToMsixVersion $Version

if ($Target -in @("msix", "all")) {
    if ([string]::IsNullOrWhiteSpace($IdentityName)) {
        throw "MSIX_IDENTITY_NAME (or -IdentityName) is required. Copy it from Partner Center > Product identity."
    }
    if ([string]::IsNullOrWhiteSpace($Publisher)) {
        throw "MSIX_PUBLISHER (or -Publisher) is required. Copy it exactly from Partner Center."
    }
}

if ([string]::IsNullOrWhiteSpace($Architecture)) {
    $NativeArchitecture = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "arm64" } elseif ($env:PROCESSOR_ARCHITECTURE -eq "x86") { "x86" } else { "x64" }
    $Architecture = $NativeArchitecture
} else {
    $NativeArchitecture = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "arm64" } elseif ($env:PROCESSOR_ARCHITECTURE -eq "x86") { "x86" } else { "x64" }
    if ($Architecture -ne $NativeArchitecture) {
        throw "jpackage cannot cross-build. Use a $Architecture JDK on a matching Windows environment."
    }
}

Assert-Command "java"
Assert-Command "javac"
Assert-Command "jpackage"
$MavenCommand = Find-Maven $ProjectRoot

if ($Target -in @("exe", "all")) {
    Assert-Command "candle.exe"
    Assert-Command "light.exe"
}

$AppName = "PDF Meta Viewer"
$Vendor = if ([string]::IsNullOrWhiteSpace($env:APP_VENDOR)) { "Alexxfromgit" } else { $env:APP_VENDOR }
$Description = "Inspect, search, and export PDF metadata locally."
$OutputDirectory = Join-Path $ProjectRoot "dist\windows"
$WorkDirectory = Join-Path $ProjectRoot "dist\.work\windows"
$InputDirectory = Join-Path $WorkDirectory "input"
$JarPath = Join-Path $ProjectRoot "target\pdf-meta-viewer.jar"
$IconPath = Join-Path $WorkDirectory "PDFMetaViewer.ico"
$IconGenerator = Join-Path $ProjectRoot "packaging\IconGenerator.java"
$IconToolClasses = Join-Path $WorkDirectory "icon-tool"

Write-Host "Building the application JAR and running tests..."
Push-Location $ProjectRoot
try {
    Invoke-CheckedCommand $MavenCommand @("clean", "package")
} finally {
    Pop-Location
}

if (-not (Test-Path $JarPath)) {
    throw "Expected application JAR was not created: $JarPath"
}

if (Test-Path $WorkDirectory) {
    Remove-Item -Recurse -Force $WorkDirectory
}
New-Item -ItemType Directory -Force $InputDirectory, $IconToolClasses, $OutputDirectory | Out-Null
Copy-Item $JarPath (Join-Path $InputDirectory "pdf-meta-viewer.jar")

Write-Host "Generating Windows application artwork..."
Invoke-CheckedCommand "javac" @("-d", $IconToolClasses, $IconGenerator)
Invoke-CheckedCommand "java" @("-Djava.awt.headless=true", "-cp", $IconToolClasses, "IconGenerator", "ico", $IconPath)

$CommonJpackageArguments = @(
    "--name", $AppName,
    "--input", $InputDirectory,
    "--main-jar", "pdf-meta-viewer.jar",
    "--main-class", "gui.MetaViewerForm",
    "--app-version", $JpackageVersion,
    "--vendor", $Vendor,
    "--description", $Description,
    "--copyright", "Copyright © $([DateTime]::Now.Year) $Vendor",
    "--icon", $IconPath,
    "--java-options", "-Dfile.encoding=UTF-8"
)

if ($Target -in @("exe", "all")) {
    $ExeStagingDirectory = Join-Path $WorkDirectory "exe"
    New-Item -ItemType Directory -Force $ExeStagingDirectory | Out-Null

    Write-Host "Creating the local Windows installer..."
    $ExeArguments = @(
        "--type", "exe",
        "--dest", $ExeStagingDirectory,
        "--win-dir-chooser",
        "--win-menu",
        "--win-menu-group", $AppName,
        "--win-shortcut",
        "--win-per-user-install"
    ) + $CommonJpackageArguments
    Invoke-CheckedCommand "jpackage" $ExeArguments

    $GeneratedExe = Get-ChildItem $ExeStagingDirectory -Filter "*.exe" | Select-Object -First 1
    if (-not $GeneratedExe) {
        throw "jpackage did not create an EXE installer."
    }
    $ExeOutput = Join-Path $OutputDirectory "PDF-Meta-Viewer-$JpackageVersion-windows-$Architecture.exe"
    Copy-Item -Force $GeneratedExe.FullName $ExeOutput
    Write-Host "Created: $ExeOutput"
}

if ($Target -in @("msix", "all")) {
    if ([string]::IsNullOrWhiteSpace($PublisherDisplayName)) {
        $PublisherDisplayName = $Vendor
    }

    $AppImageDirectory = Join-Path $WorkDirectory "app-image"
    New-Item -ItemType Directory -Force $AppImageDirectory | Out-Null

    Write-Host "Creating the packaged Windows application image..."
    $AppImageArguments = @(
        "--type", "app-image",
        "--dest", $AppImageDirectory
    ) + $CommonJpackageArguments
    Invoke-CheckedCommand "jpackage" $AppImageArguments

    $GeneratedAppImage = Join-Path $AppImageDirectory $AppName
    if (-not (Test-Path (Join-Path $GeneratedAppImage "$AppName.exe"))) {
        throw "jpackage did not create the expected application image."
    }

    $LayoutDirectory = Join-Path $WorkDirectory "msix-layout"
    $LayoutAppDirectory = Join-Path $LayoutDirectory "App"
    $AssetsDirectory = Join-Path $LayoutDirectory "Assets"
    New-Item -ItemType Directory -Force $LayoutAppDirectory, $AssetsDirectory | Out-Null
    Copy-Item -Recurse -Force (Join-Path $GeneratedAppImage "*") $LayoutAppDirectory

    Invoke-CheckedCommand "java" @("-Djava.awt.headless=true", "-cp", $IconToolClasses, "IconGenerator", "png", (Join-Path $AssetsDirectory "StoreLogo.png"), "50")
    Invoke-CheckedCommand "java" @("-Djava.awt.headless=true", "-cp", $IconToolClasses, "IconGenerator", "png", (Join-Path $AssetsDirectory "Square44x44Logo.png"), "44")
    Invoke-CheckedCommand "java" @("-Djava.awt.headless=true", "-cp", $IconToolClasses, "IconGenerator", "png", (Join-Path $AssetsDirectory "Square150x150Logo.png"), "150")

    $ManifestTemplatePath = Join-Path $ProjectRoot "packaging\windows\AppxManifest.xml.template"
    $Manifest = Get-Content -Raw $ManifestTemplatePath
    $Manifest = $Manifest.Replace("@@IDENTITY_NAME@@", (Convert-ToXmlText $IdentityName))
    $Manifest = $Manifest.Replace("@@PUBLISHER@@", (Convert-ToXmlText $Publisher))
    $Manifest = $Manifest.Replace("@@PUBLISHER_DISPLAY_NAME@@", (Convert-ToXmlText $PublisherDisplayName))
    $Manifest = $Manifest.Replace("@@VERSION@@", $MsixVersion)
    $Manifest = $Manifest.Replace("@@ARCHITECTURE@@", $Architecture)
    $Utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $LayoutDirectory "AppxManifest.xml"), $Manifest, $Utf8WithoutBom)

    $MakeAppx = Find-MakeAppx
    $MsixOutput = Join-Path $OutputDirectory "PDF-Meta-Viewer-$MsixVersion-windows-$Architecture.msix"
    if (Test-Path $MsixOutput) {
        Remove-Item -Force $MsixOutput
    }

    Write-Host "Creating the unsigned Microsoft Store package..."
    Invoke-CheckedCommand $MakeAppx @("pack", "/d", $LayoutDirectory, "/p", $MsixOutput, "/o")
    $Hash = Get-FileHash -Algorithm SHA256 $MsixOutput

    $UploadContentDirectory = Join-Path $WorkDirectory "msix-upload"
    New-Item -ItemType Directory -Force $UploadContentDirectory | Out-Null
    Copy-Item $MsixOutput $UploadContentDirectory
    $UploadZip = Join-Path $WorkDirectory "PDF-Meta-Viewer-$MsixVersion-windows-$Architecture.zip"
    $MsixUploadOutput = Join-Path $OutputDirectory "PDF-Meta-Viewer-$MsixVersion-windows-$Architecture.msixupload"
    Compress-Archive -Path (Join-Path $UploadContentDirectory "*") -DestinationPath $UploadZip -Force
    Move-Item -Force $UploadZip $MsixUploadOutput

    Write-Host "Created: $MsixOutput"
    Write-Host "Created: $MsixUploadOutput"
    Write-Host "SHA-256: $($Hash.Hash)"
    Write-Host "The Store signs this package after certification; sign it yourself only for sideload testing."
}
