#!/usr/bin/env python3
"""
Runs the Kruskal vs Prim benchmark and writes the result into ANALYSIS.md
(between <!-- BENCH:START --> and <!-- BENCH:END -->).

    python tests/bench.py

Needs: JDK 17+ (javac, java) on PATH.
"""
import datetime
import os
import platform
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / ".build" / "bench"
START, END = "<!-- BENCH:START -->", "<!-- BENCH:END -->"


def cpu_name():
    try:
        if sys.platform.startswith("linux"):
            for line in open("/proc/cpuinfo", encoding="utf-8"):
                if line.lower().startswith("model name"):
                    return line.split(":", 1)[1].strip()
        if sys.platform == "darwin":
            return subprocess.check_output(["sysctl", "-n", "machdep.cpu.brand_string"], text=True).strip()
    except Exception:
        pass
    return platform.processor() or "unknown"


def ram_gb():
    try:
        if sys.platform.startswith("linux"):
            for line in open("/proc/meminfo", encoding="utf-8"):
                if line.startswith("MemTotal"):
                    return f"{int(line.split()[1]) / 1024 / 1024:.1f} GB"
        if sys.platform == "win32":
            out = subprocess.check_output(["wmic", "computersystem", "get", "TotalPhysicalMemory"], text=True)
            return f"{int(re.findall(r'\d+', out)[0]) / 1024 ** 3:.1f} GB"
        if sys.platform == "darwin":
            return f"{int(subprocess.check_output(['sysctl', '-n', 'hw.memsize'], text=True)) / 1024 ** 3:.1f} GB"
    except Exception:
        pass
    return "unknown"


def java_version():
    res = subprocess.run(["java", "-version"], capture_output=True, text=True)
    return (res.stderr or res.stdout).splitlines()[0].strip()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sources = [str(p) for p in (ROOT / "src" / "Java").glob("*.java")] + [str(ROOT / "tests" / "Bench.java")]
    comp = subprocess.run(["javac", "-d", str(OUT)] + sources, capture_output=True, text=True)
    if comp.returncode:
        sys.exit("compilation failed:\n" + comp.stderr)
    print("running benchmark (a minute or so)...")
    run = subprocess.run(["java", "-cp", str(OUT), "Bench"], capture_output=True, text=True)
    if run.returncode:
        sys.exit("benchmark failed:\n" + run.stdout + run.stderr)
    (ROOT / "tests" / "benchmark_output.txt").write_text(run.stdout, encoding="utf-8")
    print(run.stdout)

    section = f"""{START}
Measured on {datetime.date.today().isoformat()} by `python tests/bench.py`.

**Environment.** {platform.platform()}; CPU: {cpu_name()} ({os.cpu_count()} logical cores); RAM: {ram_gb()};
{java_version()}; default JVM options (no `-Xmx`/`-Xss` flags); Python {platform.python_version()} only launches the build.

**Results** (all times in milliseconds; the seven runs are listed in execution order):

{run.stdout.strip()}
{END}"""
    path = ROOT / "ANALYSIS.md"
    text = path.read_text(encoding="utf-8")
    if START not in text or END not in text:
        sys.exit("ANALYSIS.md has no BENCH markers")
    text = re.sub(re.escape(START) + r".*?" + re.escape(END), lambda _: section, text, flags=re.S)
    path.write_text(text, encoding="utf-8")
    print("ANALYSIS.md updated")


if __name__ == "__main__":
    main()