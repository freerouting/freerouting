import sys

def extract_context(log1_path, log2_path):
    # Find match #2502
    def get_lines(path):
        with open(path, 'r', encoding='utf-8', errors='ignore') as f:
            return f.readlines()
            
    lines1 = get_lines(log1_path)
    lines2 = get_lines(log2_path)
    
    idx1 = -1
    count = 0
    for i, line in enumerate(lines1):
        if "RAW_SECTION assign" in line:
            count += 1
            if count == 2502:
                idx1 = i
                break
                
    count = 0
    idx2 = -1
    for i, line in enumerate(lines2):
        if "RAW_SECTION assign" in line:
            count += 1
            if count == 2502:
                idx2 = i
                break

    with open('context.txt', 'w', encoding='utf-8') as out:
        out.write("--- CURRENT --- \n")
        for i in range(max(0, idx1 - 50), min(len(lines1), idx1 + 10)):
            out.write(lines1[i])
        
        out.write("\n\n--- V1.9 --- \n")
        for i in range(max(0, idx2 - 50), min(len(lines2), idx2 + 10)):
            out.write(lines2[i])

if __name__ == "__main__":
    extract_context(sys.argv[1], sys.argv[2])
