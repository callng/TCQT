"""Compare the production C++ resolver against independent APK analysis evidence.

Run with a host-compiled kernel_scan_probe and the analyzer's inventory.json.
The probe always scans; the built-in Build ID table is never consulted.
"""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('probe', type=Path)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    reports = []
    for entry in json.loads(args.inventory.read_text(encoding='utf-8')):
        kernel = args.inventory.parent / Path(entry['apk']).stem / 'libkernel.so'
        run = subprocess.run([str(args.probe), str(kernel)], text=True, capture_output=True)
        assert run.returncode == 0, (entry['apk'], run.stdout, run.stderr)
        values = run.stdout.split()
        p = entry['profile']
        expected = ([int(s['offset'], 16) for s in p['sites']] +
                    [int(s['instruction'], 16) for s in p['sites']] +
                    [int(p['session_query'], 16), int(p['query_instruction'], 16)])
        assert [int(v, 16) for v in values[:8]] == expected, (entry['apk'], values, expected)
        reports.append(dict(apk=entry['apk'], build_id=entry['build_id'], scan_us=int(values[8])))
        print('PASS', entry['apk'], f'{int(values[8]) / 1000:.1f} ms', flush=True)
    if args.output:
        args.output.write_text(json.dumps(reports, indent=2) + '\n', encoding='utf-8')
    print(f'{len(reports)} APKs: all native scanner targets match static evidence')


if __name__ == '__main__':
    main()
