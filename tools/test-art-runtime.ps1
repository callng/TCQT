param([Parameter(Mandatory = $true)][string]$Serial, [string]$SdkPath = $env:ANDROID_HOME, [switch]$CheckJni)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$SdkPath) {
    $line = Get-Content (Join-Path $repo 'local.properties') | Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
    if (!$line) { throw 'Pass -SdkPath or configure sdk.dir.' }
    $SdkPath = $line.Substring(8).Replace('\:', ':').Replace('\\', '\')
}
$build = Get-Content (Join-Path $repo 'build.gradle.kts') -Raw
$ndk = [regex]::Match($build, 'val androidNdkVersion = "([^"]+)"').Groups[1].Value
$compiler = Join-Path $SdkPath "ndk/$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android33-clang++.cmd"
$adb = Join-Path $SdkPath 'platform-tools/adb.exe'
$buildTools = Get-ChildItem (Join-Path $SdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$d8 = Join-Path $buildTools.FullName 'd8.bat'
if ((& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim() -ne 'arm64-v8a') { throw 'ART runtime test requires arm64.' }
$api = [int](& $adb -s $Serial shell getprop ro.build.version.sdk)
if ($api -lt 33 -or $api -gt 37) { throw "Unsupported Android API $api (33..37 required)." }
$output = Join-Path $repo 'app/build/native-tests/art-runtime'
New-Item -ItemType Directory -Force $output | Out-Null
$lib = Join-Path $output 'libart-runtime-test.so'
$sources = @('art_hook', 'art_layout', 'art_symbols', 'art_memory', 'art_method_patch', 'art_invoke', 'elf_util', 'trampoline') |
    ForEach-Object { Join-Path $repo "app/src/main/cpp/zygisk/art/$_.cpp" }
& $compiler '-std=c++20' '-O2' '-Wall' '-Wextra' '-Werror' '-Wno-unused-parameter' '-shared' '-fPIC' '-static-libstdc++' '-pthread' `
    '-Wl,-z,max-page-size=16384' "-I$(Join-Path $repo 'app/src/main/cpp')" `
    (Join-Path $repo 'app/src/test/cpp/art_runtime_test.cpp') @sources `
    (Join-Path $repo 'app/src/main/cpp/zygisk/logging/log_file.cpp') '-llog' '-o' $lib
if ($LASTEXITCODE -ne 0) { throw 'ART runtime native build failed.' }
& javac '-d' $output (Join-Path $repo 'app/src/test/art/ArtRuntimeRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'ART runtime Java build failed.' }
$classes = @(Get-ChildItem $output -Filter '*.class' | ForEach-Object { $_.FullName })
& $d8 '--min-api' '33' '--output' $output @classes
if ($LASTEXITCODE -ne 0) { throw 'ART runtime DEX build failed.' }
$remote = '/data/local/tmp/tcqt-art-runtime-' + [guid]::NewGuid().ToString('N')
try {
    & $adb -s $Serial shell mkdir -p $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create test directory.' }
    & $adb -s $Serial push $lib (Join-Path $output 'classes.dex') $remote
    if ($LASTEXITCODE -ne 0) { throw 'Cannot deploy test.' }
    & $adb -s $Serial shell chmod 444 "$remote/libart-runtime-test.so" "$remote/classes.dex"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot protect test files.' }
    foreach ($mode in @('pointer', 'indices')) {
        # Disposable shell process: does not install a module, stop hosts or reboot.
        $checkOption = if ($CheckJni) { '-Xcheck:jni' } else { '' }
        $label = if ($CheckJni) { "$mode-checkjni" } else { $mode }
        $result = & $adb -s $Serial shell "CLASSPATH=$remote/classes.dex app_process -Xopaque-jni-ids:$mode $checkOption /system/bin ArtRuntimeRegression $remote/libart-runtime-test.so $mode" 2>&1
        $exitCode = $LASTEXITCODE
        $result | Tee-Object -FilePath (Join-Path $output "api-$api-$label.txt")
        if ($exitCode -ne 0 -or !($result -match "ART_RUNTIME_PASS $mode")) { throw "ART runtime test failed: API $api, $mode" }
    }
} finally {
    & $adb -s $Serial shell rm -f "$remote/libart-runtime-test.so" "$remote/classes.dex"
    & $adb -s $Serial shell rmdir $remote
}
