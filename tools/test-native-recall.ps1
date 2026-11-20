param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Ndk,
    [Parameter(Mandatory = $true)][string]$DobbyBuild,
    [Parameter(Mandatory = $true)][string]$DobbySource
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskOutput = Join-Path $taskRoot 'app/build/analysis/native-recall-probe'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskCompiler = Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android27-clang++.cmd'
$taskLib = Join-Path $DobbyBuild 'libdobby.a'
$taskProbe = Join-Path $taskOutput 'recall-probe'
& $taskCompiler '-std=c++20' '-O2' '-fno-exceptions' '-fno-rtti' '-static-libstdc++' `
    "-I$(Join-Path $DobbySource 'include')" (Join-Path $taskRoot 'tests/native/kernel_recall_probe.cpp') `
    (Join-Path $taskRoot 'app/src/main/cpp/recall/kernel_scan.cpp') `
    $taskLib '-llog' '-ldl' '-o' $taskProbe
if ($LASTEXITCODE -ne 0) { throw 'Native probe compilation failed' }
$taskRemote = '/data/local/tmp/tcqt-native-recall-probe'
& adb -s $Serial shell mkdir -p $taskRemote
& adb -s $Serial push $taskProbe "$taskRemote/recall-probe"
& adb -s $Serial shell "chmod 755 $taskRemote/recall-probe"
& adb -s $Serial shell "$taskRemote/recall-probe"
if ($LASTEXITCODE -ne 0) { throw 'Native probe failed' }
