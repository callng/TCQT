"""Capture QQ settings first entry after force-stop, keeping repeated entry separate."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--label', required=True)
parser.add_argument('--ready', action='store_true', help='Already cold-started and on host settings')
parser.add_argument('--profile', action='store_true')
parser.add_argument('--repeat', action='store_true')
parser.add_argument('--entry-point', help='Previously verified TCQT button center, x,y; skips the idle XML dump')
parser.add_argument('--entry-delay', type=float, default=0, help='Seconds to wait before an entry-point tap')
args = parser.parse_args()
out = Path(__file__).resolve().parents[1] / 'app/build/refactor-validation/settings-cold-start' / args.label
out.mkdir(parents=True, exist_ok=True)
adb = ['adb', '-s', args.serial]

def run(*cmd, binary=False, check=True):
    result = subprocess.run(adb + list(cmd), capture_output=True, check=check)
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace').replace('\r\n', '\n')

def tap_node(predicate):
    for _ in range(8):
        path = '/data/local/tmp/tcqt-capture-window.xml'
        run('shell', 'rm', '-f', path)
        result = run('shell', 'uiautomator', 'dump', path, check=False)
        if 'dumped to' not in result:
            time.sleep(.3)
            continue
        root = ET.fromstring(run('shell', 'cat', path))
        for node in root.iter('node'):
            if predicate(node):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
        time.sleep(.3)
    raise RuntimeError('Requested navigation control not found')

def start_trace():
    run('shell', 'atrace', '--async_start', '-b', '32768', '-a', 'com.tencent.mobileqq', 'sched', 'freq', 'idle', 'am', 'wm', 'gfx', 'view', 'dalvik', 'input')

if not args.ready:
    run('shell', 'am', 'force-stop', 'com.tencent.mobileqq')
    processes = run('shell', 'ps', '-A', '-o', 'PID,NAME')
    assert not any(line.split()[-1].startswith('com.tencent.mobileqq') for line in processes.splitlines() if line.split()), 'Host processes survived force-stop'
    run('shell', 'monkey', '-p', 'com.tencent.mobileqq', '-c', 'android.intent.category.LAUNCHER', '1')
    time.sleep(3)
    boot_pid = run('shell', 'pidof', 'com.tencent.mobileqq').strip()
    boot_log = run('shell', 'su', '-c', 'tail -60 /data/user/0/com.tencent.mobileqq/files/.tcqt/logs/com_tencent_mobileqq.events.log')
    boot_log = '\n'.join(line for line in boot_log.splitlines() if f'pid={boot_pid} ' in line)
    if '模块=false' in boot_log or '宿主=false' in boot_log or '代码=false' in boot_log:
        raise RuntimeError('DexKit cache miss: finish the scan, close its dialog, then run a fresh cold start')
    tap_node(lambda n: n.get('content-desc') == '账户及设置')
    start_trace()
    tap_node(lambda n: n.get('content-desc') == '设置')
else:
    start_trace()

pid = int(run('shell', 'pidof', 'com.tencent.mobileqq').strip())
run('shell', 'dumpsys', 'gfxinfo', 'com.tencent.mobileqq', 'reset')
remote = '/data/local/tmp/tcqt-settings-' + args.label + '.perf.data'
recorder = None
if args.profile:
    recorder = subprocess.Popen(adb + ['shell', 'su', '-c', f'simpleperf record -t {pid} -f 1000 -g --duration 7 -o {remote}'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    time.sleep(.5)
clicked_at = time.monotonic()
if args.entry_point:
    x, y = map(int, args.entry_point.split(','))
    time.sleep(args.entry_delay)
    run('shell', 'input', 'tap', str(x), str(y))
else:
    tap_node(lambda n: n.get('text') == 'TCQT')
time.sleep(3)
if recorder:
    (out / 'simpleperf.txt').write_bytes(recorder.communicate(timeout=15)[0])
    run('pull', remote, str(out / 'perf.data'))
(out / 'trace.atrace').write_bytes(run('exec-out', 'atrace', '--async_stop', '-z', binary=True))
(out / 'frames.txt').write_text(run('shell', 'dumpsys', 'gfxinfo', 'com.tencent.mobileqq', 'framestats'), encoding='utf-8')
logs = run('shell', 'su', '-c', 'tail -160 /data/user/0/com.tencent.mobileqq/files/.tcqt/logs/com_tencent_mobileqq.events.log')
session = '\n'.join(line for line in logs.splitlines() if f'pid={pid} ' in line)
(out / 'session.log').write_text(session, encoding='utf-8')
summary = {'serial': args.serial, 'pid': pid, 'coldStart': True, 'settingsStartup': re.findall(r'\[SettingsStartup\].*', session), 'dexKitScan': any(flag in session for flag in ['模块=false', '宿主=false', '代码=false']), 'captureSeconds': round(time.monotonic()-clicked_at, 3)}
summary['settingsWarmup'] = re.findall(r'\[SettingsWarmup\].*', session)
assert summary['settingsStartup'], 'Settings activity did not reach its first pre-draw'
if args.repeat:
    run('shell', 'input', 'keyevent', '4')
    time.sleep(.4)
    tap_node(lambda n: n.get('text') == 'TCQT')
    time.sleep(1)
    logs = run('shell', 'su', '-c', 'tail -30 /data/user/0/com.tencent.mobileqq/files/.tcqt/logs/com_tencent_mobileqq.events.log')
    summary['repeatedStartup'] = re.findall(r'\[SettingsStartup\].*', logs)[-1:]
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False))
