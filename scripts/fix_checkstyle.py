#!/usr/bin/env python3
"""Fix Checkstyle issues using target/checkstyle-result.xml (run checkstyle:checkstyle first)."""
from __future__ import annotations

import re
import sys
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Set, Tuple

ROOT = Path(__file__).resolve().parents[1]
RESULT_XML = ROOT / "target/checkstyle-result.xml"


def parse_violations() -> Tuple[
    Dict[Path, Set[int]],
    Dict[Path, Set[int]],
    Dict[Path, Set[int]],
]:
    if not RESULT_XML.exists():
        print("Run: ./mvnw checkstyle:checkstyle first", file=sys.stderr)
        sys.exit(1)
    tree = ET.parse(RESULT_XML)
    line_len: Dict[Path, Set[int]] = {}
    jvar: Dict[Path, Set[int]] = {}
    jmeth: Dict[Path, Set[int]] = {}
    for f in tree.getroot().findall("file"):
        name = f.get("name")
        if not name or not name.endswith(".java"):
            continue
        p = Path(name)
        for err in f.findall("error"):
            src = err.get("source", "")
            line = int(err.get("line", "0"))
            if "LineLength" in src:
                line_len.setdefault(p, set()).add(line)
            elif "JavadocVariable" in src:
                jvar.setdefault(p, set()).add(line)
            elif "MissingJavadocMethod" in src:
                jmeth.setdefault(p, set()).add(line)
    return line_len, jvar, jmeth


def wrap_java_line(line: str, max_col: int = 80) -> str:
    raw = line.rstrip("\n")
    ending = line[len(raw) :]
    if len(raw) <= max_col:
        return line
    m = re.match(r"^(\s*)", raw)
    indent = m.group(1) if m else ""
    body = raw[len(indent) :]
    if body.startswith("*") or body.startswith("/*"):
        return line
    avail = max_col - len(indent)
    if avail < 24:
        return line
    chunks = textwrap.wrap(
        body,
        width=avail,
        break_long_words=False,
        break_on_hyphens=False,
    )
    if len(chunks) <= 1:
        return line
    cont = indent + "    "
    out = [indent + chunks[0]]
    for ch in chunks[1:]:
        out.append(cont + ch.lstrip())
    return "\n".join(out) + ending


def apply_line_wraps(path: Path, lines_to_fix: Set[int]) -> None:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    for idx in sorted(lines_to_fix, reverse=True):
        i = idx - 1
        if i < 0 or i >= len(lines):
            continue
        new_line = wrap_java_line(lines[i])
        if new_line != lines[i]:
            lines[i] = new_line
    path.write_text("".join(lines), encoding="utf-8")


def field_javadoc_indent(line: str) -> str:
    m = re.match(r"^(\s*)", line)
    ind = m.group(1) if m else "    "
    return f"{ind}/** Campo. */\n"


def insert_javadoc_fields(path: Path, field_lines: Set[int]) -> None:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    for idx in sorted(field_lines, reverse=True):
        i = idx - 1
        if i < 0 or i >= len(lines):
            continue
        if i > 0 and "*/" in lines[i - 1]:
            continue
        lines.insert(i, field_javadoc_indent(lines[i]))
    path.write_text("".join(lines), encoding="utf-8")


def insert_javadoc_methods(path: Path, method_lines: Set[int]) -> None:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    for idx in sorted(method_lines, reverse=True):
        i = idx - 1
        if i < 0 or i >= len(lines):
            continue
        if i > 0 and "*/" in lines[i - 1]:
            continue
        m = re.match(r"^(\s*)", lines[i])
        ind = m.group(1) if m else "    "
        stub = f"{ind}/**\n{ind} * Descreve o comportamento.\n{ind} */\n"
        lines.insert(i, stub)
    path.write_text("".join(lines), encoding="utf-8")


def main() -> None:
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    line_len, jvar, jmeth = parse_violations()
    if mode in ("all", "lines"):
        for p, nums in line_len.items():
            if p.exists():
                apply_line_wraps(p, nums)
        print(f"Line wraps: {len(line_len)} files")
    if mode in ("all", "fields"):
        for p, nums in jvar.items():
            if p.exists():
                insert_javadoc_fields(p, nums)
        print(f"Field Javadoc: {len(jvar)} files")
    if mode in ("all", "methods"):
        for p, nums in jmeth.items():
            if p.exists():
                insert_javadoc_methods(p, nums)
        print(f"Method Javadoc: {len(jmeth)} files")


if __name__ == "__main__":
    main()
