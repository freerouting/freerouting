import re
from pathlib import Path

dsn_files = list(Path("scripts/benchmark/fixtures/PCBench").glob("*/unrouted.dsn")) + list(Path("fixtures").glob("*.dsn"))

results = []

for p in dsn_files:
    try:
        content = p.read_text(encoding="latin-1", errors="ignore")
        places = re.findall(r'\(place\s+([^\s\)]+)', content)
        if not places:
            continue
        c_places = [pl.strip('"') for pl in places if re.match(r'^\"?C\d+', pl, re.IGNORECASE)]
        u_places = [pl.strip('"') for pl in places if re.match(r'^\"?U\d+', pl, re.IGNORECASE)]
        nets = re.findall(r'\(net\s+', content)
        pins = re.findall(r'\(pin\s+', content)
        results.append((len(c_places), len(u_places), len(places), len(nets), len(pins), p))
    except Exception:
        pass

results.sort(key=lambda x: x[0], reverse=True)

print(f"{'Path':<65} | {'Caps (C*)':<10} | {'ICs (U*)':<8} | {'Total Comps':<11} | {'Pins':<6}")
print("-" * 110)
for caps, ics, comps, nets, pins, p in results[:30]:
    rel_p = str(p).replace("\\", "/")
    print(f"{rel_p:<65} | {caps:<10} | {ics:<8} | {comps:<11} | {pins:<6}")
