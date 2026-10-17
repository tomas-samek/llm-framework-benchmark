"""Build a blinded review bundle of one trial for the code-quality judges (RUBRIC.md, Procedure 1).

Copies only what a reviewer needs (pom.xml, src/, top-level docs and compose files) into
<out>/<case-id>/, adds the spec as SPEC.md and, for Tiko trials, the scaffold's agent docs
under framework-guidance/. Build output, Maven repositories, logs and data files are left
out. Trial IDs, local paths and agent or model names are replaced in every text file, and
the bundle is scanned afterwards for anything identifying.

Stage 3 (--stage3): SPEC.md is the Stage 3 spec, SERVICE-SPEC.md the existing service's
(Stage 1) spec, and CHANGES.diff the contestant's change against the trial's baseline git
commit (new files included), so judges score the change in the context of the fixed
starting code.

Usage: python conformance/quality/make_bundle.py [--stage3] <trial-dir> <out-dir> <case-id>
"""
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SKIP_DIRS = {"target", "build", ".m2", ".maven-repository", ".mvn", ".git", ".idea", ".vscode", "data",
             "dependency-inspection", "build-probe", "node_modules", "logs", ".ai-skills", ".cursor",
             ".junie", ".github"}
SKIP_FILES = re.compile(r"(?i)(\.log|\.db|\.mv\.db|\.trace\.db|\.jar|\.class|\.lock|^mvnw(\.cmd)?$|"
                        r"^CLAUDE\.md$|^AGENTS\.md$|^TASK-SPEC\.md$|^\.mcp\.json$|^\.gitignore$)")
TEXT = re.compile(r"(?i)\.(java|xml|properties|ya?ml|md|txt|json|sql|conf|sh|cmd|ps1|kt|gradle|diff)$|^Dockerfile$")
SCRUB = [
    (re.compile(r"(?i)[a-z]:[\\/]+(?:workspace|bench-contestants)[^\s\"'`<>)]*"), "<project-dir>"),
    (re.compile(r"(?i)/[a-z]/(?:workspace|bench-contestants)[^\s\"'`<>)]*"), "<project-dir>"),
    (re.compile(r"(?i)trial-[a-z0-9]+-\d\d"), "project"),
    (re.compile(r"(?i)\b(claude(?: code)?|anthropic|openai|codex|chatgpt|gpt-[\w.-]+|opus|sonnet|haiku|fable)\b"),
     "[redacted]"),
]
LEFTOVER = re.compile(r"(?i)trial-[a-z0-9]+-\d\d|bench-contestants|llm-framework-benchmark|\bclaude\b|"
                      r"\bopenai\b|\bcodex\b|\bgpt-|\bastra\b")


def copy_tree(src, dst):
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith(".")]
        if rel != "." and rel.split(os.sep)[0] not in ("src",):
            dirs[:] = []
            continue
        for f in files:
            if SKIP_FILES.search(f):
                continue
            if rel == "." and not (f == "pom.xml" or re.match(r"(?i)(readme|docker-compose|compose)", f)):
                continue
            out = os.path.join(dst, rel, f)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            shutil.copyfile(os.path.join(root, f), out)


def scrub(dst):
    for root, _, files in os.walk(dst):
        for f in files:
            if not TEXT.search(f):
                continue
            p = os.path.join(root, f)
            s = open(p, encoding="utf-8", errors="replace").read()
            for rx, rep in SCRUB:
                s = rx.sub(rep, s)
            open(p, "w", encoding="utf-8", newline="").write(s)


def leftovers(dst):
    hits = []
    for root, _, files in os.walk(dst):
        if "framework-guidance" in root:
            continue
        for f in files:
            p = os.path.join(root, f)
            if TEXT.search(f):
                for m in LEFTOVER.finditer(open(p, encoding="utf-8", errors="replace").read()):
                    hits.append(f"{os.path.relpath(p, dst)}: {m.group(0)}")
    return hits


def write_changes(trial, dst):
    """CHANGES.diff: the source change against the trial's first (baseline) commit. New files
    are marked intent-to-add first, so the diff includes them."""
    git = ["git", "-C", trial]
    base = subprocess.run(git + ["rev-list", "--max-parents=0", "HEAD"], capture_output=True, text=True,
                          check=True).stdout.split()[0]
    paths = ["pom.xml", "src"] + [f for f in os.listdir(trial) if re.match(r"(?i)(readme|docker-compose|compose)", f)]
    subprocess.run(git + ["add", "--intent-to-add", "--"] + paths, capture_output=True, check=True)
    diff = subprocess.run(git + ["-c", "core.autocrlf=false", "diff", "--no-color", "--ignore-cr-at-eol", base, "--"] + paths,
                          capture_output=True, text=True, encoding="utf-8", errors="replace", check=True).stdout
    open(os.path.join(dst, "CHANGES.diff"), "w", encoding="utf-8", newline="").write(diff)


def main(trial, out, case, stage3=False):
    dst = os.path.join(out, case)
    if os.path.exists(dst):
        shutil.rmtree(dst)
    os.makedirs(dst)
    copy_tree(trial, dst)
    specs = os.path.join(REPO, "docs", "specs")
    if stage3:
        write_changes(trial, dst)
        shutil.copyfile(os.path.join(specs, "stage-1-spec.md"), os.path.join(dst, "SERVICE-SPEC.md"))
    scrub(dst)
    shutil.copyfile(os.path.join(specs, "stage-3-spec.md" if stage3 else "stage-1-spec.md"), os.path.join(dst, "SPEC.md"))
    pom = open(os.path.join(trial, "pom.xml"), encoding="utf-8", errors="replace").read()
    if "<tiko.version>" in pom:
        g = os.path.join(dst, "framework-guidance")
        os.makedirs(g)
        shutil.copyfile(os.path.join(trial, "CLAUDE.md"), os.path.join(g, "FRAMEWORK-GUIDE.md"))
        shutil.copytree(os.path.join(trial, ".ai-skills"), os.path.join(g, "skills"))
    hits = leftovers(dst)
    n = sum(len(fs) for _, _, fs in os.walk(dst))
    print(f"{case}: {n} files" + (f", LEFTOVER {hits[:5]}" if hits else ", clean"))
    return 1 if hits else 0


if __name__ == "__main__":
    argv = sys.argv[1:]
    stage3 = "--stage3" in argv
    argv = [a for a in argv if a != "--stage3"]
    if len(argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(*argv, stage3=stage3))
