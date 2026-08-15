import re
import sys
from pathlib import Path

# Mapping of specific curr* names to even more idiomatic names if desired
CUSTOM_MAP = {
    "currPos": "currentPosition",
    "currDist": "currentDistance",
    "currVal": "currentValue",
    "currCol": "currentColumn",
    "currConn": "currentConnection",
    "currDir": "currentDirection",
    "currNum": "currentNumber",
}

def rename_curr_in_code(content: str) -> str:
    # 1. Custom mappings first
    for old_name, new_name in CUSTOM_MAP.items():
        content = re.sub(rf"\b{old_name}\b", new_name, content)
    
    # 2. General curr([A-Z]\w*) -> current\1
    content = re.sub(r"\bcurr([A-Z]\w*)\b", r"current\1", content)
    
    # 3. Standalone curr -> current (word boundary)
    content = re.sub(r"\bcurr\b", "current", content)
    
    return content

def process_directory(dir_path: Path):
    for java_file in dir_path.rglob("*.java"):
        original = java_file.read_text(encoding="utf-8")
        updated = rename_curr_in_code(original)
        if updated != original:
            java_file.write_text(updated, encoding="utf-8")
            print(f"Updated: {java_file}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python refactor_curr.py <path_to_package_dir>")
        sys.exit(1)
    target = Path(sys.argv[1])
    process_directory(target)
