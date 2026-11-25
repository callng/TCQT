"""Check reaction Emoji hooks with DexSQL and the production Android DexKit matchers."""

import argparse
import concurrent.futures
import json
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile


QUERIES = [
    """SELECT 'ES:' || descriptor AS result FROM methods
       WHERE class_descriptor='Lcom/tencent/qqnt/aio/emoReply/AioEsReactionSwitch;'
       AND return_descriptor='Z' AND param_count=0 AND method_kind IS NOT NULL""",
    """SELECT DISTINCT 'FILTER:' || m.descriptor AS result FROM methods m
       JOIN method_strings s ON m.file_id=s.file_id AND m.method_idx=s.method_idx
       JOIN method_strings t ON m.file_id=t.file_id AND m.method_idx=t.method_idx
       WHERE m.class_descriptor LIKE 'Lcom/tencent/qqnt/aio/menu/emoreply/%'
       AND m.param_count=2 AND m.return_descriptor='Ljava/util/List;'
       AND s.value='after filterEmotionData. filteredData size:' AND t.value='AIOEmoReplyMorePanel'""",
    """SELECT DISTINCT 'LIMIT:' || m.descriptor AS result FROM methods m
       JOIN method_strings s ON m.file_id=s.file_id AND m.class_idx=s.class_idx
       WHERE m.param_count=0 AND m.return_descriptor='J' AND (m.access_flags & 25)=25
       AND s.value='AIOEmoReplyUtils'""",
    """SELECT 'FIELD:' || field_descriptor AS result FROM class_fields
       WHERE (class_descriptor='Lcom/tencent/mobileqq/text/EmotcationConstants;'
           AND field_name IN ('EMOJI_CODES','EMOJI_MAP','VALID_EMOJI_COUNT'))
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/SystemAndEmojiEmoticonInfo;'
           AND field_name IN ('title','TITLE_EMOJI'))
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/EmoticonPanelController;' AND field_name='callback')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/EmotionPanelInfo;' AND field_name='type')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/BaseEmotionAdapter;' AND field_name='columnNum')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/EmotionPanelViewPagerAdapter;'
           AND field_name='panelController')""",
    """SELECT 'API:' || descriptor AS result FROM methods WHERE method_kind IS NOT NULL AND (
          (class_descriptor='Lcom/tencent/mobileqq/emoticon/QQEmojiUtil;' AND name='getOrderList')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticon/QQSysAndEmojiResMgr;'
           AND name IN ('getInstance','getResImpl'))
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticon/QQSysAndEmojiResInfo;' AND name='getFullFaceCodeList')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/SystemAndEmojiEmoticonInfo;'
           AND name='getEmojiEmoticonPanelData')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/EmoticonPanelController;' AND name='getPanelDataList')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticonview/EmotionPanelViewPagerAdapter;' AND name='getEmotionPanelData')
       OR (class_descriptor='Lcom/tencent/mobileqq/emoticon/QQEmojiResImpl;'
           AND name IN ('getServerId','getLocalId','getDrawable','getDescription'))
       OR (class_descriptor='Lcom/tencent/mobileqq/guild/emoj/api/impl/QQGuildEmojiApiImpl;'
           AND name IN ('getFilterEmojiData','getFilterSysData')))""",
    """SELECT DISTINCT 'DEX:' || d.entry_name AS result FROM dex_files d JOIN classes c ON d.file_id=c.file_id
       WHERE c.descriptor='Lcom/tencent/qqnt/aio/emoReply/AioEsReactionSwitch;'
       OR c.file_id IN (SELECT s.file_id FROM method_strings s WHERE s.value='AIOEmoReplyUtils'
           OR (s.method_class LIKE 'Lcom/tencent/qqnt/aio/menu/emoreply/%'
               AND s.value='after filterEmotionData. filteredData size:'))""",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apks', required=True, type=Path)
    parser.add_argument('--dexsql', required=True)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', help='Run real DexKit against the identified original DEX files')
    parser.add_argument('--module', type=Path, help='Debug TCQT APK containing the compatibility main')
    args = parser.parse_args()
    if args.serial and not args.module:
        parser.error('--serial requires --module')
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / 'queries.sql').write_text(';\n\n'.join(QUERIES) + ';\n', encoding='utf-8')
    apks = sorted(args.apks.glob('*.apk'))
    assert apks, f'No APKs in {args.apks}'

    def scan(apk):
        command = [args.dexsql, 'query', '--threads', '4', '--input', str(apk)]
        for sql in QUERIES:
            command += ['--sql', sql]
        output = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', check=True, timeout=180)
        (args.out / (apk.stem + '.txt')).write_text(output.stdout, encoding='utf-8')
        report = {'apk': str(apk.resolve()), 'bytes': apk.stat().st_size}
        for line in output.stdout.splitlines():
            tag, sep, value = line.strip().strip('|').strip().partition(':')
            if sep and tag in ('ES', 'FILTER', 'LIMIT', 'FIELD', 'API', 'DEX'):
                report.setdefault(tag, []).append(value)
        assert len(report.get('ES', [])) in (0, 1), report
        for tag in ('FILTER', 'LIMIT'):
            assert len(report.get(tag, [])) == 1, (apk.name, tag, report.get(tag))
        assert len(report.get('FIELD', [])) == 9, (apk.name, 'fields')
        assert len(report.get('API', [])) == 13, (apk.name, 'APIs')
        print(f'DexSQL PASS {apk.name}', flush=True)
        return report

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        reports = list(executor.map(scan, apks))
    for tag in ('FIELD', 'API'):
        assert len({tuple(sorted(report[tag])) for report in reports}) == 1, f'{tag} signatures differ'
    matrix = args.out / 'matrix.json'
    matrix.write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding='utf-8')

    if args.serial:
        adb = [args.adb, '-s', args.serial]
        remote = '/data/local/tmp/tcqt-reaction-emoji-compat'

        def device(*command):
            return subprocess.run(adb + list(command), capture_output=True, text=True,
                                  encoding='utf-8', check=True, timeout=180).stdout

        device('shell', 'mkdir', '-p', remote)
        device('push', str(args.module), remote + '/module.apk')
        with tempfile.TemporaryDirectory(prefix='tcqt-reaction-') as temp:
            temp = Path(temp)
            with ZipFile(args.module) as module:
                library = temp / 'libdexkit.so'
                library.write_bytes(module.read('lib/arm64-v8a/libdexkit.so'))
            device('push', str(library), remote + '/libdexkit.so')
            for report in reports:
                fixture = temp / 'fixture.zip'
                # SQL above scans the complete APK. Reuse its original target DEX
                # files for real DexKit validation without transferring resources.
                with ZipFile(report['apk']) as source, ZipFile(fixture, 'w', ZIP_DEFLATED, compresslevel=1) as target:
                    for index, name in enumerate(report['DEX'], start=1):
                        # DexKit loads classes.dex, classes2.dex, ... in order.
                        entry = 'classes.dex' if index == 1 else f'classes{index}.dex'
                        target.writestr(entry, source.read(name))
                device('push', str(fixture), remote + '/fixture.zip')
                output = device('shell', f'CLASSPATH={remote}/module.apk', 'app_process', '/system/bin',
                                'com.owo233.tcqt.loader.validation.ReactionEmojiCompatibilityMain',
                                remote + '/libdexkit.so', remote + '/fixture.zip')
                result = json.loads(next(line for line in reversed(output.splitlines()) if line.startswith('{')))
                assert result['passed'], result
                for key, tag in (('reaction_es_switch', 'ES'), ('reaction_more_panel_filter', 'FILTER'),
                                 ('reaction_interval', 'LIMIT')):
                    assert result[key] == report.get(tag, []), (report['apk'], key, result[key], report.get(tag))
                report['dexkit'] = result
                matrix.write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding='utf-8')
                print(f'DexKit PASS {Path(report["apk"]).name}', flush=True)
        device('shell', 'rm', '-f', remote + '/module.apk', remote + '/libdexkit.so', remote + '/fixture.zip')
    print(f'PASS {len(reports)} APKs; matrix: {matrix}', flush=True)


if __name__ == '__main__':
    main()
