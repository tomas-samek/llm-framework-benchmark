"""Measured code signals for Stage 1 trials (see results/reviews/RUBRIC.md, "Measured signals").

These are facts about a trial's source tree that inform the code-quality judges. They are
not scores. The script reads files only; it never builds or runs anything.

Usage:
  python conformance/quality/code_metrics.py <trial-dir> [<trial-dir> ...]
  python conformance/quality/code_metrics.py --cell runs/stage-1/<cell>     # every trial-* in a cell
Writes CSV to stdout (add `> file.csv` to keep it).
"""
import csv
import glob
import os
import re
import sys

BROAD_CATCH = re.compile(r"catch\s*\(\s*(?:final\s+)?(?:Exception|Throwable|RuntimeException)\s+\w+\s*\)")
TYPE_DECL = re.compile(r"^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|record|interface|enum)\s+\w+", re.M)
MAIN_METHOD = re.compile(r"static\s+void\s+main\s*\(")
TEST_ANNOTATION = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest)\b")
BOOTSTRAP_LITERAL = re.compile(r"localhost:9092")
SLEEP = re.compile(r"Thread\.sleep\s*\(|TimeUnit\.\w+\.sleep\s*\(")
FIELDS = ["trial", "framework", "main_loc", "test_loc", "main_types", "test_types", "test_methods",
          "main_entry_points", "broad_catches", "sleeps_in_main", "bootstrap_literals_in_java",
          "config_files", "docker_compose", "pom_dependencies"]


def code_lines(path):
    """Non-blank lines that aren't only a comment."""
    n, in_block = 0, False
    for line in open(path, encoding="utf-8", errors="replace"):
        s = line.strip()
        if in_block:
            if "*/" in s:
                in_block = False
            continue
        if not s or s.startswith("//"):
            continue
        if s.startswith("/*"):
            in_block = "*/" not in s
            continue
        n += 1
    return n


def java_files(root, part):
    return glob.glob(os.path.join(root, "src", part, "**", "*.java"), recursive=True)


def read(paths):
    return [open(p, encoding="utf-8", errors="replace").read() for p in paths]


def metrics(trial):
    main, test = java_files(trial, "main"), java_files(trial, "test")
    main_src, test_src = read(main), read(test)
    pom_path = os.path.join(trial, "pom.xml")
    pom = open(pom_path, encoding="utf-8", errors="replace").read() if os.path.exists(pom_path) else ""
    # Structural markers only: the Spring scaffold's pom mentions Tiko in a comment.
    if "<tiko.version>" in pom or "<groupId>io.github.tomas-samek</groupId>" in pom:
        framework = "tiko"
    elif "<artifactId>spring-boot-starter-parent</artifactId>" in pom:
        framework = "spring"
    else:
        framework = "unknown"
    resources = os.path.join(trial, "src", "main", "resources")
    configs = sorted(os.path.basename(p) for p in glob.glob(os.path.join(resources, "*"))
                     if re.search(r"\.(properties|ya?ml|conf|json)$", p) and "META-INF" not in p)
    compose = any(os.path.exists(os.path.join(trial, n))
                  for n in ("docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"))
    return {
        "trial": os.path.basename(os.path.normpath(trial)),
        "framework": framework,
        "main_loc": sum(code_lines(p) for p in main),
        "test_loc": sum(code_lines(p) for p in test),
        "main_types": sum(len(TYPE_DECL.findall(s)) for s in main_src),
        "test_types": sum(len(TYPE_DECL.findall(s)) for s in test_src),
        "test_methods": sum(len(TEST_ANNOTATION.findall(s)) for s in test_src),
        "main_entry_points": sum(len(MAIN_METHOD.findall(s)) for s in main_src),
        "broad_catches": sum(len(BROAD_CATCH.findall(s)) for s in main_src),
        "sleeps_in_main": sum(len(SLEEP.findall(s)) for s in main_src),
        "bootstrap_literals_in_java": sum(len(BOOTSTRAP_LITERAL.findall(s)) for s in main_src),
        "config_files": " ".join(configs),
        "docker_compose": compose,
        "pom_dependencies": pom.count("<dependency>"),
    }


def main(argv):
    if not argv:
        sys.exit(__doc__)
    if argv[0] == "--cell":
        trials = sorted(glob.glob(os.path.join(argv[1], "trial-*")))
    else:
        trials = argv
    w = csv.DictWriter(sys.stdout, fieldnames=FIELDS, lineterminator="\n")
    w.writeheader()
    for t in trials:
        w.writerow(metrics(t))


if __name__ == "__main__":
    main(sys.argv[1:])
