#!/usr/bin/env python3
"""
Extract context around RAW_SECTION divergence to understand what rooms are different.
Looks for the first COMPLETE_ROOM that creates the diverging door boundary.
"""
import sys
import re

def parse_door_bounds(log_line):
    """Extract door bounds as (ll_x, ll_y, ur_x, ur_y) from a bounds string."""
    m = re.search(r'\((-?\d+),(-?\d+)\)\.\.\((-?\d+),(-?\d+)\)', log_line)
    if m:
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)))
    return None

def extract_raw_sections(filename):
    """Extract RAW_SECTION assign entries with door bounds."""
    sections = []
    with open(filename, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            if 'RAW_SECTION assign' not in line:
                continue
            sec_m = re.search(r'selected_section=(\d+)', line)
            from_sec_m = re.search(r'from_section=(\d+)', line)
            ev_m = re.search(r'expansion_value=([0-9.]+)', line)
            
            # Extract door bounds
            door_m = re.search(r'door_bounds=\[(.*?)\]', line)
            from_door_m = re.search(r'from_door_bounds=\[(.*?)\]', line)
            
            if sec_m and from_sec_m and ev_m and door_m:
                # Normalize: extract (ll_x,ll_y)..(ur_x,ur_y)
                door_bounds = door_m.group(1)
                from_door_bounds = from_door_m.group(1) if from_door_m else "null"
                ev = float(ev_m.group(1))
                
                sections.append({
                    'sec': int(sec_m.group(1)),
                    'from_sec': int(from_sec_m.group(1)),
                    'door': door_bounds,
                    'from_door': from_door_bounds,
                    'ev': round(ev, 4),
                    'raw': line.strip()
                })
    return sections

def normalize(entry):
    """Create a normalized key for comparison."""
    return (entry['sec'], entry['from_sec'], entry['door'], entry['from_door'])

def find_divergence(log_curr, log_v19):
    print(f"Loading {log_curr}...")
    curr = extract_raw_sections(log_curr)
    print(f"  {len(curr)} RAW_SECTION entries")
    
    print(f"Loading {log_v19}...")
    v19 = extract_raw_sections(log_v19)
    print(f"  {len(v19)} RAW_SECTION entries")
    
    min_len = min(len(curr), len(v19))
    print(f"\nComparing first {min_len} entries for divergence...")
    
    for i in range(min_len):
        c = curr[i]
        v = v19[i]
        nc = normalize(c)
        nv = normalize(v)
        
        if nc != nv:
            print(f"\n*** FIRST DIVERGENCE AT POSITION {i} ***")
            print(f"Current[{i}]:  sec={c['sec']} door={c['door']} from_door={c['from_door']} ev={c['ev']}")
            print(f"V1.9   [{i}]:  sec={v['sec']} door={v['door']} from_door={v['from_door']} ev={v['ev']}")
            
            # Show context: 5 entries before and after
            print(f"\n--- Context: entries {max(0,i-5)} to {min(min_len-1,i+10)} ---")
            print("Current:")
            for j in range(max(0,i-5), min(len(curr), i+11)):
                marker = ">>>" if j == i else "   "
                e = curr[j]
                print(f"  {marker} [{j:4d}] sec={e['sec']} door={e['door'][:60]} from_door={e['from_door'][:50]} ev={e['ev']}")
            
            print("\nV1.9:")
            for j in range(max(0,i-5), min(len(v19), i+11)):
                marker = ">>>" if j == i else "   "
                e = v19[j]
                print(f"  {marker} [{j:4d}] sec={e['sec']} door={e['door'][:60]} from_door={e['from_door'][:50]} ev={e['ev']}")
            
            return i
    
    print("No divergence found in the overlapping range!")
    return -1

if __name__ == '__main__':
    curr_log = sys.argv[1] if len(sys.argv) > 1 else 'logs/freerouting-current.log'
    v19_log = sys.argv[2] if len(sys.argv) > 2 else 'logs/freerouting-v190.log'
    find_divergence(curr_log, v19_log)
