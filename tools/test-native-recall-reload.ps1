param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$NativeLibrary,
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [Parameter(Mandatory = $true)][string]$AndroidJar
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskOutput = Join-Path $taskRoot 'app/build/analysis/native-recall-reload'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskD8 = Join-Path $SdkPath 'build-tools/37.0.0/d8.bat'
& javac '-cp' $AndroidJar '-d' $taskOutput (Join-Path $taskRoot 'tests/native/NativeRecallReloadProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Reload probe Java compilation failed' }
$taskClasses = @(Get-ChildItem -LiteralPath $taskOutput -Filter '*.class' | ForEach-Object { $_.FullName })
& $taskD8 '--min-api' '27' '--lib' $AndroidJar '--output' $taskOutput @taskClasses
if ($LASTEXITCODE -ne 0) { throw 'Reload probe DEX compilation failed' }
$taskRemote = '/data/local/tmp/tcqt-native-recall-reload'
& adb -s $Serial shell mkdir -p "$taskRemote/libs" "$taskRemote/cache"
& adb -s $Serial push (Join-Path $taskOutput 'classes.dex') "$taskRemote/probe.dex"
& adb -s $Serial push $Apk "$taskRemote/module.apk"
& adb -s $Serial push $NativeLibrary "$taskRemote/libs/libtcqtrecall.so"
if ($LASTEXITCODE -ne 0) { throw 'Reload probe deployment failed' }
$taskResult = & adb -s $Serial shell "CLASSPATH=$taskRemote/probe.dex app_process -Xcheck:jni /system/bin NativeRecallReloadProbe $taskRemote/module.apk $taskRemote/libs $taskRemote/cache" 2>&1
$taskExit = $LASTEXITCODE
$taskResult | Tee-Object -FilePath (Join-Path $taskOutput 'result.txt')
if ($taskExit -ne 0 -or !($taskResult -match 'NATIVE_RECALL_RELOAD_PASS')) { throw 'Reload probe failed' }
