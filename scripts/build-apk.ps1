[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('preview', 'release')]
    [string]$Mode,

    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$VersionName,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionFile = Join-Path $repoRoot 'version.properties'
$gradleFile = Join-Path $repoRoot 'gradlew.bat'
$expectedPackage = 'com.coursetable.app'
$expectedCertSha256 = 'bebf94b3520ca8392225b108d466885c5dec4fcff1389e780b5b32c61a2b76c2'

function Read-VersionState {
    $state = @{}
    foreach ($line in [IO.File]::ReadAllLines($versionFile)) {
        if ($line -match '^([^#=]+)=(.*)$') {
            $state[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    foreach ($required in 'VERSION_NAME', 'VERSION_CODE', 'LAST_RELEASE_VERSION') {
        if (-not $state.ContainsKey($required)) {
            throw "Missing $required in $versionFile"
        }
    }
    return $state
}

function Write-VersionState([string]$name, [int]$code, [string]$lastRelease) {
    $text = @(
        "VERSION_NAME=$name"
        "VERSION_CODE=$code"
        "LAST_RELEASE_VERSION=$lastRelease"
        ''
    ) -join "`n"
    $temporaryFile = "$versionFile.tmp"
    [IO.File]::WriteAllText($temporaryFile, $text, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryFile -Destination $versionFile -Force
}

function Next-PatchVersion([string]$name) {
    $parts = $name.Split('.')
    return '{0}.{1}.{2}' -f $parts[0], $parts[1], ([int]$parts[2] + 1)
}

function Resolve-AndroidSdk {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT)) {
        return [IO.Path]::GetFullPath($env:ANDROID_SDK_ROOT)
    }
    $localProperties = Join-Path $repoRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = [IO.File]::ReadAllLines($localProperties) |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdk = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path -LiteralPath $sdk) { return [IO.Path]::GetFullPath($sdk) }
        }
    }
    throw 'Android SDK not found. Set ANDROID_SDK_ROOT or sdk.dir in local.properties.'
}

function Configure-Java {
    $criteriaFile = Join-Path $repoRoot 'gradle\gradle-daemon-jvm.properties'
    $requiredMajor = 25
    if (Test-Path -LiteralPath $criteriaFile) {
        $versionLine = Get-Content -LiteralPath $criteriaFile |
            Where-Object { $_ -match '^toolchainVersion=\d+$' } |
            Select-Object -First 1
        if (-not $versionLine) { throw "Missing toolchainVersion in $criteriaFile" }
        $requiredMajor = [int]($versionLine -replace '^toolchainVersion=', '')
    }

    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Write-Host "Gradle launcher JAVA_HOME: $env:JAVA_HOME"
        Write-Host "Gradle build JVM requirement: Java $requiredMajor (selected by Gradle)"
        return
    }
    $gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $jdkRoots = @((Join-Path $gradleUserHome 'jdks'), (Join-Path $env:USERPROFILE '.jdks'))
    $candidate = $jdkRoots |
        ForEach-Object { Get-ChildItem -LiteralPath $_ -Directory -ErrorAction SilentlyContinue } |
        Sort-Object FullName |
        Where-Object {
            $javaExecutable = Join-Path $_.FullName 'bin\java.exe'
            $releaseFile = Join-Path $_.FullName 'release'
            if ((Test-Path -LiteralPath $javaExecutable) -and (Test-Path -LiteralPath $releaseFile)) {
                $javaVersionLine = Get-Content -LiteralPath $releaseFile |
                    Where-Object { $_ -match '^JAVA_VERSION="' } |
                    Select-Object -First 1
                $javaVersionLine -match ('^JAVA_VERSION="{0}(?:[.\-"])' -f $requiredMajor)
            }
        } |
        Select-Object -First 1
    if (-not $candidate) { throw "JDK $requiredMajor not found in Gradle/user JDK directories. Set JAVA_HOME before running this script." }
    $env:JAVA_HOME = $candidate.FullName
    Write-Host "Gradle launcher JAVA_HOME: $env:JAVA_HOME (fallback)"
    Write-Host "Gradle build JVM requirement: Java $requiredMajor (selected by Gradle)"
}

$state = Read-VersionState
$currentName = [string]$state.VERSION_NAME
$lastRelease = [string]$state.LAST_RELEASE_VERSION
$nextCode = [int]$state.VERSION_CODE + 1

if ($VersionName) {
    $nextName = $VersionName
} elseif ($Mode -eq 'preview' -and ([version]$currentName -le [version]$lastRelease)) {
    $nextName = Next-PatchVersion $lastRelease
} else {
    $nextName = $currentName
}

if ($Mode -eq 'release' -and ([version]$nextName -le [version]$lastRelease)) {
    throw "Release version $nextName must be newer than the last release $lastRelease. Pass -VersionName x.y.z."
}

$displayName = if ($Mode -eq 'preview') { "$nextName-preview" } else { $nextName }
Write-Host "Planned build: $displayName (versionCode $nextCode)"
Configure-Java
if ($DryRun) { return }

Write-VersionState -name $nextName -code $nextCode -lastRelease $lastRelease
$buildTempDir = Join-Path $repoRoot '.gradle\tmp'
[IO.Directory]::CreateDirectory($buildTempDir) | Out-Null
$env:TEMP = $buildTempDir
$env:TMP = $buildTempDir
$env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + " `"-Djava.io.tmpdir=$buildTempDir`" `"-Djdk.net.unixdomain.tmpdir=$buildTempDir`"").Trim()

$variantTask = if ($Mode -eq 'preview') { 'assemblePreview' } else { 'assembleRelease' }
$variantDirectory = if ($Mode -eq 'preview') { 'preview' } else { 'release' }
$gradleArguments = if ($Mode -eq 'preview') {
    @(
        $variantTask
        '-PcourseTableArm64Only=true'
        '--quiet'
        '--no-daemon'
        '--offline'
    )
} else {
    @(
        'testDebugUnitTest'
        ':app:compileDebugAndroidTestKotlin'
        'lintDebug'
        $variantTask
        '--quiet'
        '--no-daemon'
        '--offline'
    )
}
$logDirectory = Join-Path $repoRoot 'app\build\logs'
[IO.Directory]::CreateDirectory($logDirectory) | Out-Null
$buildLog = Join-Path $logDirectory "$Mode-b$nextCode.log"

Push-Location $repoRoot
try {
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradleFile @gradleArguments *> $buildLog
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $buildLog -Tail 80
        throw "Gradle failed with exit code $LASTEXITCODE. Full log: $buildLog"
    }
} finally {
    Pop-Location
}

$outputDirectory = Join-Path $repoRoot "app\build\outputs\apk\$variantDirectory"
$metadataFile = Join-Path $outputDirectory 'output-metadata.json'
$metadata = Get-Content -LiteralPath $metadataFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($metadata.applicationId -ne $expectedPackage) { throw "Unexpected package: $($metadata.applicationId)" }
if ($metadata.variantName -ne $variantDirectory) { throw "Unexpected variant: $($metadata.variantName)" }

$sdk = Resolve-AndroidSdk
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object {
        (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe')) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat'))
    } |
    Select-Object -First 1
if (-not $buildTools) { throw 'No Android build-tools installation with aapt and apksigner was found.' }
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

$results = @()
foreach ($element in $metadata.elements) {
    if ([int]$element.versionCode -ne $nextCode) { throw "Unexpected versionCode in $($element.outputFile)" }
    if ([string]$element.versionName -ne $displayName) { throw "Unexpected versionName in $($element.outputFile)" }
    $apk = Join-Path $outputDirectory $element.outputFile
    if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
    if ($Mode -eq 'release' -and (Get-Item -LiteralPath $apk).Length -gt 31500000) {
        throw "Release APK exceeds the approximate 30 MB budget (31,500,000-byte guard): $apk"
    }

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $badging = (& $aapt dump badging $apk 2>&1) -join "`n"
        $signature = (& $apksigner verify --verbose --print-certs $apk 2>&1) -join "`n"
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if ($badging -notmatch "package: name='$([regex]::Escape($expectedPackage))'") { throw "Package check failed: $apk" }
    if ($badging -notmatch "versionCode='$nextCode'") { throw "versionCode check failed: $apk" }
    if ($badging -notmatch "versionName='$([regex]::Escape($displayName))'") { throw "versionName check failed: $apk" }
    if ($badging -notmatch "sdkVersion:'26'") { throw "minSdk check failed: $apk" }
    if ($badging -notmatch "targetSdkVersion:'34'") { throw "targetSdk check failed: $apk" }

    $filter = $element.filters | Where-Object { $_.filterType -eq 'ABI' } | Select-Object -First 1
    $abi = if ($filter) { [string]$filter.value } else { 'universal' }
    if ($abi -ne 'universal' -and $badging -notmatch "native-code: '$([regex]::Escape($abi))'") {
        throw "ABI check failed for $abi in $apk"
    }

    if ($signature -notmatch 'Verified using v2 scheme \(APK Signature Scheme v2\): true') {
        throw "APK v2 signature check failed: $apk"
    }
    if ($signature -notmatch "V2 Signer: certificate SHA-256 digest: $expectedCertSha256") {
        throw "Signing certificate check failed: $apk"
    }

    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $results += [pscustomobject]@{
        ABI = $abi
        File = $element.outputFile
        Bytes = (Get-Item -LiteralPath $apk).Length
        SHA256 = $hash
        Path = $apk
    }
}

$checksumFile = Join-Path $outputDirectory 'SHA256SUMS.txt'
$checksumLines = $results | Sort-Object File | ForEach-Object { "$($_.SHA256)  $($_.File)" }
[IO.File]::WriteAllLines($checksumFile, $checksumLines, [Text.UTF8Encoding]::new($false))

$arm64 = $results | Where-Object ABI -eq 'arm64-v8a' | Select-Object -First 1
if (-not $arm64) { throw 'arm64-v8a APK was not generated.' }
$rootCopy = Join-Path $repoRoot $arm64.File
Copy-Item -LiteralPath $arm64.Path -Destination $rootCopy -Force
$copiedHash = (Get-FileHash -LiteralPath $rootCopy -Algorithm SHA256).Hash.ToLowerInvariant()
if ($copiedHash -ne $arm64.SHA256) { throw 'Root APK copy hash mismatch.' }

if ($Mode -eq 'release') {
    Write-VersionState -name $nextName -code $nextCode -lastRelease $nextName
}

$results | Sort-Object ABI | Format-Table ABI, File, Bytes, SHA256 -AutoSize
Write-Host "Root arm64 APK: $rootCopy"
Write-Host "Checksums: $checksumFile"
Write-Host "Build log: $buildLog"
