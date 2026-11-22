"""Extract QQ kernels and record ARM64 recall call-site evidence (no APK edits).

Requires pyelftools, capstone and numpy. Analysis output belongs under app/build.
"""
import argparse
import hashlib
import io
import json
import re
import struct
import zipfile
from pathlib import Path

import numpy as np
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from elftools.elf.elffile import ELFFile


def analyze(data, destination):
    elf = ELFFile(io.BytesIO(data))
    text = elf.get_section_by_name('.text')
    code, base = text.data(), text['sh_addr']
    words = np.frombuffer(code, dtype='<u4')
    notes = [note for segment in elf.iter_segments() if segment['p_type'] == 'PT_NOTE'
             for note in segment.iter_notes() if note['n_type'] == 'NT_GNU_BUILD_ID']
    build_id = notes[0]['n_desc']
    hdr = elf.get_section_by_name('.eh_frame_hdr')
    raw = hdr.data()
    assert raw[:4] == bytes([1, 0x1b, 3, 0x3b]), raw[:4].hex()
    count = struct.unpack_from('<I', raw, 8)[0]
    starts = [hdr['sh_addr'] + struct.unpack_from('<i', raw, 12 + 8 * i)[0]
              for i in range(count)]
    decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    adrp_indices = np.flatnonzero((words & 0x9f000000) == 0x90000000)
    adrp = words[adrp_indices].astype(np.int64)
    imm = ((adrp >> 5) & 0x7ffff) * 4 + ((adrp >> 29) & 3)
    imm = (imm ^ 0x100000) - 0x100000
    pages = ((base + adrp_indices * 4) & ~0xfff) + imm * 4096
    result = dict(build_id=build_id, sha256=hashlib.sha256(data).hexdigest(), anchors={})
    anchors = ['ReplaceToRecallMsg', 'ReplaceToTracelessRecallMsgByNotify',
               'HandleRecallMsgNotify', 'HandleRecallNotifyInAndrSilence',
               'OnRecallMsgNotifyWithLocalRecord',
               '[Msg] on grp recall nfy ! frm:{} to:{}, pr:{} seq:{} rd:{} t:{} op_uid:{} op_t:{} is_off:{} trl_flag:{} is_andr_silence:{}']
    for anchor in anchors:
        targets = []
        for section in elf.iter_sections():
            if section['sh_type'] != 'SHT_PROGBITS' or not section['sh_flags'] & 2:
                continue
            section_data = section.data()
            needle = anchor.encode() + b'\0'
            position = section_data.find(needle)
            while position >= 0:
                # A log format containing the name is not the function-name string.
                if position == 0 or section_data[position - 1] == 0:
                    targets.append(section['sh_addr'] + position)
                position = section_data.find(needle, position + 1)
        references = []
        for target in targets:
            for index in adrp_indices[pages == (target & ~0xfff)]:
                reg = int(words[index] & 31)
                for next_index in range(int(index) + 1, min(int(index) + 17, len(words))):
                    w = int(words[next_index])
                    if (w & 0xffc00000) == 0x91000000 and ((w >> 5) & 31) == reg:
                        if ((w >> 10) & 4095) == (target & 4095):
                            address = base + int(index) * 4
                            start_index = int(np.searchsorted(starts, address, side='right')) - 1
                            start = starts[start_index]
                            end = starts[start_index + 1] if start_index + 1 < len(starts) else start + 4096
                            references.append(dict(xref=hex(address), function=hex(start), end=hex(end)))
        result['anchors'][anchor] = references
        for ref in references:
            start, end = int(ref['function'], 16), int(ref['end'], 16)
            listing = '\n'.join(f'{i.address:x}: {i.bytes.hex()} {i.mnemonic} {i.op_str}'
                                for i in decoder.disasm(code[start-base:end-base], start))
            label = 'GroupRecallWorker' if anchor.startswith('[Msg]') else anchor
            (destination / f'{label}-{start:x}.asm').write_text(listing, encoding='utf-8')
    def anchor_function(anchor):
        candidates = {ref['function'] for ref in result['anchors'][anchor]}
        assert len(candidates) == 1, (anchor, candidates)
        return int(candidates.pop(), 16)

    notify = anchor_function('HandleRecallNotifyInAndrSilence')
    replace = anchor_function('ReplaceToRecallMsg')
    traceless = anchor_function('ReplaceToTracelessRecallMsgByNotify')
    sites = []
    for start, load in [(notify, 0xf8420ea8), (replace, 0xf9400028), (traceless, 0xf9400028)]:
        sequence = list(decoder.disasm(code[start-base:start-base+100], start))
        hits = [i + 1 for i in range(len(sequence) - 1)
                if int.from_bytes(sequence[i].bytes, 'little') == load
                and sequence[i + 1].mnemonic == 'cbz' and sequence[i + 1].op_str.startswith('x8,')]
        assert len(hits) == 1, (hex(start), hits)
        branch = sequence[hits[0]]
        target = int(branch.op_str.split('#')[1], 16)
        next_start = starts[int(np.searchsorted(starts, start, side='right'))]
        assert start < target < next_start
        sites.append(dict(offset=hex(branch.address), instruction=hex(int.from_bytes(branch.bytes, 'little'))))
    # Confirm the same native ABI at the instruction boundary, including W5.
    replace_head = list(decoder.disasm(code[replace-base:replace-base+80], replace))
    assert any(i.mnemonic == 'mov' and i.op_str == 'w21, w5' for i in replace_head)
    assert struct.unpack_from('<I', code, int(sites[1]['offset'], 16) - base + 8)[0] == 0x2a0503f5
    trace_head = list(decoder.disasm(code[traceless-base:traceless-base+80], traceless))
    assert any(i.mnemonic == 'ldrb' and i.op_str == 'w9, [x2, #0xc1]' for i in trace_head)
    assert struct.unpack_from('<I', code, int(sites[2]['offset'], 16) - base + 4)[0] == 0x39430449
    worker = anchor_function('HandleRecallMsgNotify')
    worker_end = starts[int(np.searchsorted(starts, worker, side='right'))]
    worker_code = list(decoder.disasm(code[worker-base:worker_end-base], worker))
    query_candidates = []
    for index, instruction in enumerate(worker_code):
        if instruction.mnemonic == 'mov' and instruction.op_str == 'w8, #0x138b':
            next_call = next(i for i in worker_code[index+1:] if i.mnemonic == 'bl')
            query_candidates.append(int(next_call.op_str.lstrip('#'), 16))
    assert len(set(query_candidates)) == 1, query_candidates
    query = query_candidates[0]
    query_size = starts[int(np.searchsorted(starts, query, side='right'))] - query
    query_code = list(decoder.disasm(code[query-base:query-base+query_size], query))
    # Older builds outline argument-save instructions into tiny local helpers.
    query_helpers = []
    for instruction in query_code:
        if instruction.mnemonic == 'bl':
            target = int(instruction.op_str.lstrip('#'), 16)
            helper = []
            for i in decoder.disasm(code[target-base:target-base+128], target):
                helper.append(i)
                if i.mnemonic in ('ret', 'b'):
                    break
            query_helpers += helper
    query_abi = query_code + query_helpers
    for transfer in ('x19, x5', 'x20, x4', 'x21, x3', 'x22, x2'):
        assert any(i.mnemonic == 'mov' and i.op_str == transfer for i in query_abi), (hex(query), transfer)
    # Worker scalar destination offsets establish normalized notify semantics.
    # seq/client_seq/random/server_id/time remain +20/+28/+30/+38/+40.
    query_index = next(index for index, i in enumerate(worker_code)
                       if i.mnemonic == 'mov' and i.op_str == 'w8, #0x138b')
    def scalar_destination(field):
        field_setters = [index for index, i in enumerate(worker_code)
                         if index > query_index and i.mnemonic == 'mov' and i.op_str == f'w1, #{field}']
        hits = []
        for index in field_setters:
            for instruction in worker_code[index+1:index+9]:
                match = re.fullmatch(r'x(?:0|8), \[sp, #(0x[0-9a-f]+)\]', instruction.op_str)
                if instruction.mnemonic == 'str' and match:
                    hits.append(int(match[1], 16))
                    break
        assert hits, field
        return hits[0]
    seq_stack = scalar_destination('0x14')
    offsets = {name: scalar_destination(field) - seq_stack + 0x20
               for name, field in [('seq', '0x14'), ('client_seq', '3'), ('random', '6'),
                                    ('time', '5'), ('server_id', '4')]}
    assert offsets == dict(seq=0x20, client_seq=0x28, random=0x30, time=0x40, server_id=0x38), offsets
    # The worker's string-to-view helper proves default 24-byte libc++ string ABI.
    string_call = next(i for i in reversed(worker_code[:query_index]) if i.mnemonic == 'bl')
    string_view = int(string_call.op_str.lstrip('#'), 16)
    string_code = list(decoder.disasm(code[string_view-base:string_view-base+28], string_view))
    assert [(i.mnemonic, i.op_str) for i in string_code] == [
        ('ldrb', 'w8, [x0]'), ('ldp', 'x10, x9, [x0, #8]'), ('tst', 'w8, #1'),
        ('lsr', 'x8, x8, #1'), ('csinc', 'x0, x9, x0, ne'),
        ('csel', 'x1, x8, x10, eq'), ('ret', '')], hex(string_view)
    getter_code = []
    for entry in (replace, traceless):
        end = starts[int(np.searchsorted(starts, entry, side='right'))]
        instructions = list(decoder.disasm(code[entry-base:end-base], entry))
        getter_code += instructions
        for instruction in instructions:
            if instruction.mnemonic == 'bl':
                target = int(instruction.op_str.lstrip('#'), 16)
                for helper in decoder.disasm(code[target-base:target-base+128], target):
                    getter_code.append(helper)
                    if helper.mnemonic in ('ret', 'b', 'br'):
                        break
    getter_slots = {}
    for slot in (0, 8, 0x18, 0x40, 0xa0):
        memory = '[x8]' if slot == 0 else f'[x8, #{"8" if slot == 8 else hex(slot)}]'
        matches = [hex(i.address) for i in getter_code if i.mnemonic == 'ldr' and i.op_str.endswith(memory)]
        assert matches, (hex(replace), hex(slot))
        getter_slots[hex(slot)] = sorted(set(matches))
    result['layout'] = dict(notify_scalars={key: hex(value) for key, value in offsets.items()},
                            libcxx_string_view=hex(string_view), record_getter_loads=getter_slots,
                            group_worker=hex(anchor_function(anchors[-1])))
    result['profile'] = dict(sites=sites, session_query=hex(query),
                             query_instruction=hex(int.from_bytes(code[query-base:query-base+4], 'little')))
    (destination / 'session-query.asm').write_text('\n'.join(
        f'{i.address:x}: {i.bytes.hex()} {i.mnemonic} {i.op_str}' for i in query_abi), encoding='utf-8')
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk_directory', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results = []
    apk_files = list(args.apk_directory.glob('9.*.apk')) + list(args.apk_directory.glob('Android_9.*.apk'))
    def apk_version(path):
        name = path.name.removeprefix('Android_')
        return tuple(int(x) for x in name.split('_')[0].split('.')[:3])
    for apk in sorted(apk_files, key=apk_version):
        version = apk_version(apk)
        if not (9, 2, 30) <= version <= (9, 3, 90):
            continue
        destination = args.output / apk.stem
        destination.mkdir(exist_ok=True)
        kernel = destination / 'libkernel.so'
        evidence = destination / 'evidence.json'
        if not kernel.exists():
            with zipfile.ZipFile(apk) as archive:
                kernel.write_bytes(archive.read('lib/arm64-v8a/libkernel.so'))
        if evidence.exists() and 'record_getter_loads' in json.loads(evidence.read_text(encoding='utf-8')).get('layout', {}):
            result = json.loads(evidence.read_text(encoding='utf-8'))
        else:
            result = analyze(kernel.read_bytes(), destination)
            result['apk'] = apk.name
            evidence.write_text(json.dumps(result, indent=2), encoding='utf-8')
        results.append(result)
        print(apk.name, result['build_id'], {key: len(value) for key, value in result['anchors'].items()}, flush=True)
    (args.output / 'inventory.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    profiles = {}
    for result in results:
        version = '.'.join(str(part) for part in apk_version(Path(result['apk'])))
        if result['build_id'] in profiles:
            assert profiles[result['build_id']]['sha256'] == result['sha256']
            profiles[result['build_id']]['versions'].append(version)
        else:
            profiles[result['build_id']] = dict(result, versions=[version])
    header = ['// Generated by tools/analyze-native-recall.py from the analyzed QQ APKs.',
              '// ARM64 ELF RVAs; notify/replace/traceless CBZ X8 sites, then native session query.',
              '#pragma once', '#include <cstddef>', '#include <cstdint>', '', 'namespace tcqt::recall {',
              'constexpr size_t kSites = 3;',
              'struct KernelProfile {',
              '    const char *version, *build_id;',
              '    uintptr_t offsets[kSites];', '    uint32_t instructions[kSites];',
              '    uintptr_t session_query;', '    uint32_t query_instruction;', '};',
              'constexpr KernelProfile KERNEL_PROFILES[] = {']
    for result in profiles.values():
        p = result['profile']
        header.append('    {"%s", "%s", {%s}, {%s}, %s, %s},' % (
            '/'.join(result['versions']), result['build_id'],
            ', '.join(s['offset'] for s in p['sites']),
            ', '.join(s['instruction'] for s in p['sites']),
            p['session_query'], p['query_instruction']))
    header += ['};', '} // namespace tcqt::recall', '']
    (args.output / 'kernel_profiles.h').write_text('\n'.join(header), encoding='utf-8')


if __name__ == '__main__':
    main()
