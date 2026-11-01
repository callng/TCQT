param([Parameter(Mandatory = $true)][string]$Serial, [string]$SdkPath = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$SdkPath) {
    $line = Get-Content (Join-Path $repo 'local.properties') | Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
    if (!$line) { throw 'Pass -SdkPath or configure sdk.dir.' }
    $SdkPath = $line.Substring(8).Replace('\:', ':').Replace('\\', '\')
}
$build = Get-Content (Join-Path $repo 'build.gradle.kts') -Raw
$ndk = [regex]::Match($build, 'val androidNdkVersion = "([^"]+)"').Groups[1].Value
$compiler = Join-Path $SdkPath "ndk/$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android27-clang++.cmd"
$adb = Join-Path $SdkPath 'platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim() -ne 'arm64-v8a') { throw 'ART test requires arm64.' }
$output = Join-Path $repo 'app/build/native-tests/art-memory-test'
New-Item -ItemType Directory -Force (Split-Path $output) | Out-Null
& $compiler '-std=c++20' '-Wall' '-Wextra' '-Werror' '-Wno-unused-parameter' '-static-libstdc++' '-pthread' `
    "-I$(Join-Path $repo 'app/src/main/cpp')" `
    (Join-Path $repo 'app/src/test/cpp/art_memory_test.cpp') `
    (Join-Path $repo 'app/src/main/cpp/zygisk/art/art_memory.cpp') `
    (Join-Path $repo 'app/src/main/cpp/zygisk/logging/log_file.cpp') '-llog' '-o' $output
if ($LASTEXITCODE -ne 0) { throw 'ART memory test compilation failed.' }
$remote = '/data/local/tmp/tcqt-art-memory-' + [guid]::NewGuid().ToString('N')
try {
    & $adb -s $Serial push $output $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot deploy test.' }
    & $adb -s $Serial shell chmod 755 $remote
    & $adb -s $Serial shell $remote
    if ($LASTEXITCODE -ne 0) { throw 'ART memory regression failed.' }
} finally { & $adb -s $Serial shell rm -f $remote }
