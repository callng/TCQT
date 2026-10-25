param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial,
    [string]$SdkPath = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (-not $SdkPath) {
    $sdkLine = Get-Content -LiteralPath (Join-Path $repo 'local.properties') |
        Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
    if (-not $sdkLine) { throw 'Set ANDROID_HOME or pass -SdkPath.' }
    $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
}
$buildScript = Get-Content -Raw -LiteralPath (Join-Path $repo 'build.gradle.kts')
$ndk = [regex]::Match($buildScript, 'val androidNdkVersion = "([^"]+)"').Groups[1].Value
if (-not $ndk) { throw 'Cannot read pinned androidNdkVersion.' }
$adb = Join-Path $SdkPath 'platform-tools/adb.exe'
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot query the selected Android device.' }
$target = switch ($abi) {
    'arm64-v8a' { 'aarch64-linux-android27' }
    'x86_64' { 'x86_64-linux-android27' }
    default { throw "Unsupported test ABI: $abi" }
}
$compiler = Join-Path $SdkPath "ndk/$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/$target-clang++.cmd"
$output = Join-Path $repo "app/build/native-tests/zygisk-companion-test-$abi"
New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null
$compilerArgs = @(
    '-std=c++20', '-Wall', '-Wextra', '-Werror', '-pthread', '-static-libstdc++',
    "-I$(Join-Path $repo 'app/src/main/cpp')",
    (Join-Path $repo 'app/src/test/cpp/zygisk_companion_test.cpp'),
    (Join-Path $repo 'app/src/main/cpp/zygisk_companion.cpp'), '-o', $output
)
& $compiler @compilerArgs
if ($LASTEXITCODE -ne 0) { throw 'Companion regression compilation failed.' }

# A serial is mandatory so a connected personal phone is never selected implicitly.
$remote = '/data/local/tmp/tcqt-companion-tests-' + [guid]::NewGuid().ToString('N')
if ($remote -notmatch '^/data/local/tmp/tcqt-companion-tests-[a-f0-9]{32}$') {
    throw 'Invalid temporary test directory.'
}
try {
    & $adb -s $Serial shell mkdir $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create test directory.' }
    & $adb -s $Serial push $output "$remote/test"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot push test executable.' }
    & $adb -s $Serial shell chmod 755 "$remote/test"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot mark test executable runnable.' }
    & $adb -s $Serial shell "$remote/test" $remote
    if ($LASTEXITCODE -ne 0) { throw 'Companion regressions failed.' }
} finally {
    & $adb -s $Serial shell rm -rf $remote
}
