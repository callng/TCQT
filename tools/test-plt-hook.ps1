param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string[]]$Cases = @('parser', 'rela', 'packed', 'relr', 'late', 'retry', 'concurrent', 'default', 'disabled')
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$SdkPath) {
    $line = Get-Content (Join-Path $repo 'local.properties') | Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
    if (!$line) { throw 'Pass -SdkPath or configure sdk.dir.' }
    $SdkPath = $line.Substring(8).Replace('\:', ':').Replace('\\', '\')
}
$build = Get-Content (Join-Path $repo 'build.gradle.kts') -Raw
$ndk = [regex]::Match($build, 'val androidNdkVersion = "([^"]+)"').Groups[1].Value
$toolDir = Join-Path $SdkPath "ndk/$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin"
$compiler = Join-Path $toolDir 'aarch64-linux-android33-clang++.cmd'
$adb = Join-Path $SdkPath 'platform-tools/adb.exe'
if ((& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim() -ne 'arm64-v8a') { throw 'PLT test requires arm64.' }
$api = [int](& $adb -s $Serial shell getprop ro.build.version.sdk)
if ($api -lt 33 -or $api -gt 37) { throw "Unsupported Android API $api (33..37 required)." }
$safeSerial = $Serial -replace '[^a-zA-Z0-9._-]', '_'
$output = Join-Path $repo "app/build/native-tests/plt/$safeSerial/$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')"
New-Item -ItemType Directory -Force $output | Out-Null
$common = @('-std=c++20', '-O2', '-Wall', '-Wextra', '-Werror', '-Wno-unused-parameter',
    '-static-libstdc++', '-pthread', '-mbranch-protection=standard',
    '-Wl,-z,max-page-size=16384', '-Wl,-z,relro,-z,now')
$fixtures = [ordered]@{
    'libtcqt_plt_rela.so' = 'none'
    'libtcqt_plt_packed.so' = 'android'
    'libtcqt_plt_relr.so' = 'android+relr'
    'libtcqt_plt_decoy.so' = 'none'
    'libfekit.so' = 'android+relr'
}
foreach ($fixture in $fixtures.GetEnumerator()) {
    & $compiler @common '-shared' '-fPIC' "-Wl,--pack-dyn-relocs=$($fixture.Value)" `
        (Join-Path $repo 'app/src/test/cpp/plt_fixture.cpp') '-o' (Join-Path $output $fixture.Key)
    if ($LASTEXITCODE -ne 0) { throw "Fixture build failed: $($fixture.Key)" }
    & (Join-Path $toolDir 'llvm-readelf.exe') '-d' '-r' '-n' (Join-Path $output $fixture.Key) |
        Set-Content (Join-Path $output "$($fixture.Key).elf.txt")
}
& $compiler @common '-shared' '-fPIC' '-DTCQT_NO_IMPORTS' `
    (Join-Path $repo 'app/src/test/cpp/plt_fixture.cpp') '-o' (Join-Path $output 'libtcqt_plt_packed.so.noimports')
if ($LASTEXITCODE -ne 0) { throw 'No-import fixture build failed.' }
$sources = @('plt_hook.cpp', 'default_plt_hooks.cpp', 'plt_elf.cpp')
$sources = $sources | ForEach-Object { Join-Path $repo "app/src/main/cpp/zygisk/plt/$_" }
& $compiler @common "-I$(Join-Path $repo 'app/src/main/cpp')" `
    (Join-Path $repo 'app/src/test/cpp/plt_hook_test.cpp') @sources `
    (Join-Path $repo 'app/src/main/cpp/zygisk/logging/log_file.cpp') '-ldl' '-llog' '-o' (Join-Path $output 'plt-hook-test')
if ($LASTEXITCODE -ne 0) { throw 'Production PLT test build failed.' }
& $compiler @common "-I$(Join-Path $repo 'app/src/main/cpp')" `
    (Join-Path $repo 'app/src/test/cpp/plt_elf_test.cpp') `
    (Join-Path $repo 'app/src/main/cpp/zygisk/plt/plt_elf.cpp') '-o' (Join-Path $output 'plt-elf-test')
if ($LASTEXITCODE -ne 0) { throw 'ELF parser test build failed.' }
$remote = '/data/local/tmp/tcqt-plt-test-' + [guid]::NewGuid().ToString('N')
$files = @(Get-ChildItem $output -File | Where-Object { $_.Name -notlike '*.txt' })
$matrix = @{
    parser = @('', '')
    rela = @('libtcqt_plt_rela.so', 'initial')
    packed = @('libtcqt_plt_packed.so', 'initial')
    relr = @('libtcqt_plt_relr.so', 'initial')
    late = @('libtcqt_plt_packed.so', 'late')
    retry = @('libtcqt_plt_packed.so', 'retry')
    concurrent = @('libtcqt_plt_rela.so', 'concurrent')
    default = @('libfekit.so', 'default')
    disabled = @('libfekit.so', 'disabled')
}
try {
    & $adb -s $Serial shell mkdir -p $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create test directory.' }
    & $adb -s $Serial push @($files.FullName) $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot push fixtures.' }
    & $adb -s $Serial shell chmod 755 "$remote/plt-hook-test" "$remote/plt-elf-test"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot make test executables runnable.' }
    $metadata = @("serial=$Serial", "api=$api", "ndk=$ndk")
    foreach ($prop in @('ro.product.model', 'ro.build.fingerprint', 'ro.build.version.security_patch')) {
        $metadata += "$prop=" + (& $adb -s $Serial shell getprop $prop)
    }
    $metadata += 'identity=' + (& $adb -s $Serial shell id)
    $metadata | Set-Content (Join-Path $output 'device.txt')
    $failed = @()
    foreach ($case in $Cases) {
        if (!$matrix.ContainsKey($case)) { throw "Unknown case $case" }
        $item = $matrix[$case]
        $command = if ($case -eq 'parser') { "$remote/plt-elf-test" }
                   else { "$remote/plt-hook-test $remote $($item[0]) $($item[1])" }
        $result = & $adb -s $Serial shell $command 2>&1
        $exitCode = $LASTEXITCODE
        $result | Tee-Object -FilePath (Join-Path $output "$case.txt")
        $testPid = [regex]::Match(($result -join "`n"), 'PLT_TEST_PID=(\d+)').Groups[1].Value
        if ($testPid) {
            $processLog = @(& $adb -s $Serial logcat -d -v threadtime --pid=$testPid)
            Set-Content (Join-Path $output "$case-logcat.txt") -Value ($processLog -join "`n")
        }
        if ($exitCode -ne 0 -or !($result -match '^PLT_(RUNTIME_PASS |ELF_PASS$)')) { $failed += $case }
    }
    Write-Host "PLT reports: $output"
    if ($failed.Count) { throw "PLT regression failed: $($failed -join ', ')" }
} finally {
    foreach ($file in $files) { & $adb -s $Serial shell rm -f "$remote/$($file.Name)" }
    & $adb -s $Serial shell rmdir $remote
}
