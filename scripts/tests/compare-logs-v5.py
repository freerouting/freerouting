import sys
import re

def extract_raw_section(line):
    idx = line.find("RAW_SECTION assign")
    if idx < 0:
        return None
    return line[idx:]

def extract_field(line, field):
    m = re.search(rf'{field}=([^\s,]+)', line)
    return m.group(1) if m else None

def normalize_line(line):
    line = re.sub(r'expansion_value=[0-9\\.E+-]+', 'EXPVAL', line)
    line = re.sub(r'sorting_value=[0-9\\.E+-]+', 'SORTVAL', line)
    return line.strip()

def extract_door_key(line):
    """Extract a stable key: door bounds + from_door bounds + section"""
    sec = extract_field(line, 'selected_section')
    fsec = extract_field(line, 'from_section')
    
    # Extract door bounds
    door_m = re.search(r'door=\S+/bounds=\[([^\]]+)\]', line)
    door_bounds = door_m.group(1) if door_m else None
    
    # Extract from_door bounds
    from_door_m = re.search(r'from_door=\S+/bounds=\[([^\]]+)\]', line)
    from_door_bounds = from_door_m.group(1) if from_door_m else None
    
    return (sec, door_bounds, from_door_bounds, fsec)

def compare_logs(log1, log2):
    with open(log1, 'r', encoding='utf-8', errors='ignore') as f1, \
         open(log2, 'r', encoding='utf-8', errors='ignore') as f2, \
         open('mismatch_v5.txt', 'w', encoding='utf-8') as out:
         
        raw1 = [extract_raw_section(l.strip()) for l in f1 if "RAW_SECTION assign" in l]
        raw2 = [extract_raw_section(l.strip()) for l in f2 if "RAW_SECTION assign" in l]
        
        lines1 = [l for l in raw1 if l]
        lines2 = [l for l in raw2 if l]
        
        out.write(f"Current: {len(lines1)} entries, V1.9: {len(lines2)} entries\n\n")
        
        # Find first divergence
        mismatch_idx = -1
        for i in range(min(len(lines1), len(lines2))):
            n1 = normalize_line(lines1[i])
            n2 = normalize_line(lines2[i])
            if n1 != n2:
                mismatch_idx = i
                break
        
        if mismatch_idx < 0:
            out.write("No mismatch found!\n")
            return
        
        out.write(f"First mismatch at position #{mismatch_idx+1}\n\n")
        out.write(f"Current: {lines1[mismatch_idx]}\n\n")
        out.write(f"V1.9:    {lines2[mismatch_idx]}\n\n")
        
        # Show next 20 entries from current that DON'T appear in v1.9 at similar position
        # Find if the v1.9 entry appears later in current, and vice versa
        v19_entry_norm = normalize_line(lines2[mismatch_idx])
        out.write(f"V1.9 entry (norm): {v19_entry_norm}\n\n")
        
        # Find where v1.9's mismatch entry appears in current
        for j in range(mismatch_idx, min(mismatch_idx+100, len(lines1))):
            if normalize_line(lines1[j]) == v19_entry_norm:
                out.write(f"V1.9's mismatch entry appears in current at position #{j+1} (offset +{j-mismatch_idx})\n")
                break
        else:
            out.write("V1.9's mismatch entry NOT found in current within next 100 entries\n")
        
        # Show 30 entries of current starting from mismatch, vs 30 of v1.9
        out.write("\n--- Current entries from mismatch (30 entries) ---\n")
        for j in range(mismatch_idx, min(mismatch_idx+30, len(lines1))):
            k = extract_door_key(lines1[j])
            ev = extract_field(lines1[j], 'expansion_value')
            out.write(f"  [{j+1}] sec={k[0]} door={str(k[1])[:40]} from_door={str(k[2])[:40]} fsec={k[3]} ev={ev}\n")
        
        out.write("\n--- V1.9 entries from mismatch (30 entries) ---\n")
        for j in range(mismatch_idx, min(mismatch_idx+30, len(lines2))):
            k = extract_door_key(lines2[j])
            ev = extract_field(lines2[j], 'expansion_value')
            out.write(f"  [{j+1}] sec={k[0]} door={str(k[1])[:40]} from_door={str(k[2])[:40]} fsec={k[3]} ev={ev}\n")
        
if __name__ == '__main__':
    compare_logs(sys.argv[1], sys.argv[2])
