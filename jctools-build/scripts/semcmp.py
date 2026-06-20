#!/usr/bin/env python3
"""
Semantic-equivalence comparator for two compiled Java class files.

Two classes compare equal if their javap output matches after these
canonicalisations:

  - Field declarations sorted alphabetically (declaration order is non-semantic
    for independent fields).
  - Method declarations sorted alphabetically (definition order is
    non-semantic; bytecode within each method is preserved).
  - {@code <clinit>} body's independent static-init blocks sorted by their
    target putstatic field. The patcher rewrites that emit field updaters in a
    different source order produce different bytecode but the same behaviour;
    this canonicalisation absorbs that.
  - Bytecode offsets stripped, constant-pool indices replaced by the symbolic
    `// ...` comment javap prints alongside.

Exit 0 on equivalence, 1 with a unified diff otherwise. Exits 2 on usage error.
"""
import re
import subprocess
import sys
import difflib


def javap(class_file):
    return subprocess.check_output(
        ["javap", "-p", "-c", "-constants", class_file],
        stderr=subprocess.STDOUT,
    ).decode()


def strip_indices(text):
    """Drop bytecode offsets ('  N: ') and pool indices ('#N')."""
    text = re.sub(r"^\s+\d+:\s*", "  ", text, flags=re.MULTILINE)
    text = re.sub(r"#\d+", "#?", text)
    return text


def split_members(body):
    """
    Split the class body into top-level members (each field decl or method
    block). Top-level members are recognised by exactly two leading spaces in
    javap output; method bodies are indented further.
    """
    lines = body.splitlines()
    members = []
    cur = []
    for line in lines:
        if not line.strip():
            if cur:
                cur.append(line)
            continue
        if line.startswith("  ") and not line.startswith("   "):
            if cur:
                members.append(cur)
            cur = [line]
        else:
            cur.append(line)
    if cur:
        members.append(cur)
    return members


def canon_clinit(body_lines):
    """
    Reorder independent static-init blocks inside <clinit> by target field. A
    block is the run of bytecode lines ending in a putstatic instruction.
    """
    sig = []
    code = body_lines[:]
    while code and not code[0].lstrip().startswith("Code:"):
        sig.append(code.pop(0))
    if code:
        sig.append(code.pop(0))  # "Code:" header itself

    blocks = []
    cur = []
    for line in code:
        cur.append(line)
        m = re.search(r"putstatic\s+\S+\s+//\s*Field\s+(\S+):", line)
        if m:
            blocks.append((m.group(1), cur))
            cur = []
    if cur:
        blocks.append(("￿" + str(len(blocks)), cur))

    blocks.sort(key=lambda b: b[0])
    out = list(sig)
    for _, lines in blocks:
        out.extend(lines)
    return out


def canonicalize(class_file):
    text = strip_indices(javap(class_file))
    header_end = text.index("{\n") + 2
    header = text[:header_end]
    body = re.sub(r"\n\}\s*$", "", text[header_end:])

    members = split_members(body)
    canon_members = []
    for m in members:
        if m[0].lstrip().startswith("static {}"):
            canon_members.append(canon_clinit(m))
        else:
            canon_members.append(m)
    canon_members.sort(key=lambda m: m[0])

    return header + "\n".join("\n".join(m) for m in canon_members) + "\n}\n"


def main():
    if len(sys.argv) != 3:
        print("usage: semcmp.py <class-a> <class-b>", file=sys.stderr)
        sys.exit(2)
    a = canonicalize(sys.argv[1])
    b = canonicalize(sys.argv[2])
    if a == b:
        sys.exit(0)
    diff = difflib.unified_diff(
        a.splitlines(keepends=True), b.splitlines(keepends=True),
        fromfile=sys.argv[1], tofile=sys.argv[2],
    )
    sys.stdout.writelines(diff)
    sys.exit(1)


if __name__ == "__main__":
    main()
