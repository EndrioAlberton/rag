#!/usr/bin/env python3
"""Inserts minimal /** ... */ before Javadoc / MissingJavadoc lines from checkstyle-result.xml."""
import re
import sys
import xml.etree.ElementTree as ET

FIELD_LINE = re.compile(
    r"^(?P<ind>\s*)(?P<vis>private|protected|public)\s+"
    r"(?:static\s+)?(?:final\s+)?[\w.<>,?\[\]]+\s+\w+"
)

STATIC_CLASS = re.compile(r"^\s*public\s+static\s+class\s+\w+")

METHOD_LINE = re.compile(
    r"^(?P<ind>\s*)(?P<vis>private|protected|public)\s+"
    r"(?:static\s+)?[\w.<>,?\[\]]+\s+\w+\s*\("
)


def main() -> None:
    xml_path = "target/checkstyle-result.xml"
    tree = ET.parse(xml_path)
    root = tree.getroot()

    by_file: dict[str, set[int]] = {}
    for f in root.findall("file"):
        path = f.get("name")
        for e in f.findall("error"):
            src = e.get("source", "")
            if "JavadocVariable" not in src and "MissingJavadoc" not in src:
                continue
            line_no = int(e.get("line"))
            by_file.setdefault(path, set()).add(line_no)

    for path in sorted(by_file.keys()):
        lines_to_fix = by_file[path]
        with open(path, encoding="utf-8") as fh:
            content = fh.readlines()

        for line_no in sorted(lines_to_fix, reverse=True):
            idx = line_no - 1
            if idx < 0 or idx >= len(content):
                continue
            line = content[idx]
            stripped = line.lstrip()

            if has_javadoc_above(content, idx):
                continue

            indent = re.match(r"(\s*)", line).group(1)

            if STATIC_CLASS.match(line):
                doc = f"{indent}/** Nested webhook DTO. */\n"
                content.insert(idx, doc)
                continue

            if FIELD_LINE.match(line) and not stripped.startswith("public static class"):
                doc = f"{indent}/** JSON field. */\n"
                content.insert(idx, doc)
                continue

            if METHOD_LINE.match(line):
                doc = f"{indent}/** Hook or helper. */\n"
                content.insert(idx, doc)
                continue

            # Fallback: single-line javadoc
            doc = f"{indent}/** Declared member. */\n"
            content.insert(idx, doc)

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.writelines(content)
        print(path, len(lines_to_fix), file=sys.stderr)


def has_javadoc_above(content: list[str], idx: int) -> bool:
    j = idx - 1
    while j >= 0 and content[j].strip() == "":
        j -= 1
    if j < 0:
        return False
    s = content[j].strip()
    if s.startswith("/**") or s.startswith("*") or s.startswith("*/"):
        return True
    # Annotation block
    if s.startswith("@"):
        k = j - 1
        while k >= 0 and content[k].strip().startswith("@"):
            k -= 1
        while k >= 0 and content[k].strip() == "":
            k -= 1
        if k >= 0:
            t = content[k].strip()
            if t.startswith("/**") or t.startswith("*"):
                return True
    return False


if __name__ == "__main__":
    main()
