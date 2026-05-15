from __future__ import annotations

import argparse
from pathlib import Path
import subprocess


TEXT_EXTENSIONS = {
    ".md",
    ".adoc",
    ".txt",
    ".yml",
    ".yaml",
    ".json",
    ".properties",
    ".java",
    ".kt",
    ".kts",
    ".js",
    ".ts",
    ".css",
    ".html",
    ".xml",
    ".sql",
    ".env",
    ".sh",
}

TEXT_FILENAMES = {
    ".env.example",
    ".editorconfig",
    ".gitattributes",
    "Dockerfile",
    "gradlew",
}

SKIP_DIRS = {".git", ".gradle", "build", "node_modules", ".idea"}
MOJIBAKE_MARKERS = (
    "\ufffd",   # replacement character
    "Ã",
    "Â",
    "â€",
    "ðŸ",
    "ï¿½",
)


def is_text_target(path: Path) -> bool:
    if path.name in TEXT_FILENAMES:
        return True
    if path.suffix.lower() in TEXT_EXTENSIONS:
        return True
    return path.name.endswith(".example")


def iter_targets(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if is_text_target(path):
            yield path


def iter_staged_targets(root: Path):
    proc = subprocess.run(
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
        check=True,
        capture_output=True,
        text=True,
    )
    for raw in proc.stdout.splitlines():
        candidate = raw.strip()
        if not candidate:
            continue
        path = (root / candidate).resolve()
        if not path.exists() or not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if is_text_target(path):
            yield path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--staged", action="store_true", help="Validate only staged files")
    args = parser.parse_args()

    root = Path(".").resolve()
    failures: list[str] = []
    targets = iter_staged_targets(root) if args.staged else iter_targets(root)
    for path in targets:
        data = path.read_bytes()
        rel = path.relative_to(root)
        if data.startswith(b"\xef\xbb\xbf"):
            failures.append(f"{rel}: UTF-8 BOM is not allowed")
            continue
        try:
            decoded = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            failures.append(f"{rel}: invalid UTF-8 ({exc})")
            continue
        marker = next((m for m in MOJIBAKE_MARKERS if m in decoded), None)
        if marker is not None:
            failures.append(f"{rel}: suspicious mojibake marker detected ({marker!r})")
    if failures:
        print("UTF-8 validation failed:")
        for failure in failures:
            print(f" - {failure}")
        return 1
    scope = "staged files" if args.staged else "repository"
    print(f"UTF-8 validation passed ({scope}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
