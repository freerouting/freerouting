import os, glob, re, subprocess

def read_and_norm(path):
    lines = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            if 'compare_trace_' in line and 'compare_trace_split' not in line and 'compare_trace_route_item' not in line and 'compare_trace_ripped_item' not in line and 'remove_tails' not in line:
                idx = line.rfind(']')
                s = line[idx+1:].strip() if idx != -1 else line.strip()
                s = re.sub(r'expansion_value=[\d\.]+', 'ev', s)
                s = re.sub(r'sorting_value=[\d\.]+', 'sv', s)
                s = re.sub(r',\s*test_level=[^:]+:', ':', s)
                s = re.sub(r',\s*trace_enabled=[^:]+:', ':', s)
                s = re.sub(r'test_level=[^:]+:?', '', s)
                s = re.sub(r'trace_enabled=[^:]+:?', '', s)
                lines.append(s)
    return lines

for i in range(12, 100):
    print(f'Running iteration {i}...', flush=True)
    subprocess.run(['powershell', '-Command', f'.\\scripts\\tests\\compare-versions.ps1 -max_items {i} -max_passes 1 -NoBuild'], stdout=subprocess.DEVNULL)

    cur_file = 'logs/freerouting-current.log'
    v19_file = 'logs/freerouting-v190.log'

    c_lines = read_and_norm(cur_file)
    v_lines = read_and_norm(v19_file)

    # compare
    min_len = min(len(c_lines), len(v_lines))
    mismatch = False
    for j in range(min_len):
        if c_lines[j] != v_lines[j]:
            print(f'Mismatch found at length match index {j} for iteration {i}!')
            print('Context:')
            for k in range(max(0, j-3), j):
                print(f'Match: {c_lines[k]}')
            print(f'Curr: {c_lines[j]}')
            print(f'V1.9: {v_lines[j]}')
            mismatch = True
            break
    if mismatch:
        break
    if len(c_lines) != len(v_lines):
        print(f'Length mismatch at {i}: Curr={len(c_lines)} V19={len(v_lines)}. Continuing...')

