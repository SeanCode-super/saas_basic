#!/usr/bin/env python3
"""Build a private V83 permission repair from matching pre/post-migration mysqldump snapshots."""

import argparse
import os
from collections import Counter
from pathlib import Path
import re

TOKEN = re.compile(rb"'(?:[^'\\]|\\.|'')*'|NULL|-?\d+|[(),;]", re.DOTALL)
COLUMN = re.compile(rb"  `([a-z_0-9]+)` ")
ESCAPES = {b"0": b"\x00", b"b": b"\b", b"n": b"\n", b"r": b"\r", b"t": b"\t", b"Z": b"\x1a"}
RESTORE_MARKER = "-- Restore archived rows as historical grants."
COLUMNS = ("id tenant_id menu_id menu_code subject_type subject_value button_codes_json status "
           "created_by created_at updated_by updated_at deleted deleted_at version remark").split()


def read_table(dump, table):
    columns, rows = [], {}
    identifier = b"`" + table.encode("ascii") + b"`"
    reading_schema = False
    with dump.open("rb") as stream:
        for line in stream:
            if line.startswith(b"CREATE TABLE " + identifier + b" ("):
                reading_schema = True
            elif reading_schema and line.startswith(b")"):
                reading_schema = False
            if reading_schema and (field := COLUMN.match(line)):
                columns.append(field.group(1).decode("ascii"))
            if not line.startswith(b"INSERT INTO " + identifier + b" VALUES "):
                continue
            literals = line.partition(b" VALUES ")[2].strip()
            tokens = TOKEN.findall(literals)
            if b"".join(tokens) != literals:
                raise ValueError(f"Unsupported dump literal in {table}")
            values = []
            for token in tokens:
                if token == b"(":
                    values = []
                elif token == b")":
                    row = dict(zip(columns, values, strict=True))
                    if row[columns[0]] in rows:
                        raise ValueError(f"Duplicate primary key in {table} dump")
                    rows[row[columns[0]]] = row
                elif token not in (b",", b";"):
                    values.append(token)
    if not columns:
        raise ValueError(f"No schema for {table}")
    return rows


def sql_value(literal, column):
    if literal == b"NULL" or re.fullmatch(rb"-?\d+", literal):
        return literal.decode("ascii")
    value = re.sub(rb"\\(.)|''", lambda m: ESCAPES.get(m[1], m[1]) if m[1] else b"'", literal[1:-1])
    value.decode("utf-8")  # Reject character sets other than the application's utf8mb4 dump format.
    text = f"CONVERT(X'{value.hex()}' USING utf8mb4)"
    return f"CAST({text} AS JSON)" if column == "button_codes_json" else text


def repair(before, after, output):
    def includes_v83(path):
        return any(row["version"] == b"'83'" and row["success"] == b"1"
                   for row in read_table(path, "flyway_schema_history").values())

    if includes_v83(before) or not includes_v83(after):
        raise ValueError("Expected a snapshot before V83 and another after V83")
    menus = read_table(before, "iam_menu")
    old = read_table(before, "iam_menu_permission")
    current = read_table(after, "iam_menu_permission")
    affected = []
    for key, row in old.items():
        menu = menus.get(row["menu_id"])
        if not menu or not (menu["menu_type"] == b"'DIRECTORY'" or menu["menu_code"] == b"'tenant'"):
            continue
        if key in current:
            raise ValueError("A candidate ID is already occupied. Reconcile the source snapshots first.")
        affected.append(row)
    if any(set(row) != set(COLUMNS) for row in affected):
        raise ValueError("Permission schema differs from V83; manual reconciliation required")
    if len(affected) != len(old.keys() - current.keys()):
        raise ValueError("Unrelated permission deletions occurred between snapshots; obtain closer snapshots")
    totals = Counter(row["deleted"] for row in affected)
    print(f"Before {len(old)}, after {len(current)}, removed {len(affected)}, previously revoked "
          f"{totals[b'1']}, previously active {totals[b'0']}, tenants {len({r['tenant_id'] for r in affected})}")
    if output is None:
        return
    migration = (Path(__file__).resolve().parents[2] / "server/saas-basics-app/src/main/resources/db/migration"
                 / "V84__preserve_menu_permission_history.sql").read_text()
    restore = migration[migration.index(RESTORE_MARKER):]
    # The package is applied after V84. A collision is an error rather than permission to overwrite newer records.
    with open(output, "x", encoding="ascii", opener=lambda name, flags: os.open(name, flags, 0o600)) as target:
        target.write("START TRANSACTION;\n")
        for row in affected:
            vals = ", ".join(sql_value(row[c], c) for c in COLUMNS)
            unchanged = " AND ".join(f"a.{c} <=> {sql_value(row[c], c)}" for c in COLUMNS)
            target.write(f"INSERT INTO iam_menu_permission_v83_archive ({', '.join(COLUMNS)})\n"
                         f"SELECT {vals} FROM dual WHERE NOT EXISTS "
                         f"(SELECT 1 FROM iam_menu_permission_v83_archive a WHERE {unchanged});\n")
        target.write(restore)
        target.write("\nCOMMIT;\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path, help="write a new owner-only SQL file to run after V84")
    args = parser.parse_args()
    repair(args.before, args.after, args.output)
