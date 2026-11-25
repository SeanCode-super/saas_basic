#!/usr/bin/env python3
"""Build a complete local database from an offline dump and official Flowable 8 DDL."""
import argparse
from collections import defaultdict
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import zipfile

SCRIPTS = Path(__file__).resolve().parent
OLD_LOGS = {"FLW_EV_DATABASECHANGELOG", "FLW_EV_DATABASECHANGELOGLOCK"}
PROPERTIES = {"ACT_GE_PROPERTY", "ACT_ID_PROPERTY"}
VERSION_ROW = "(NAME_ IN ('schema.version','schema.history') OR RIGHT(NAME_,15)='.schema.version')"
EXPECTED_V7 = {"schema.version", "common.schema.version", "eventregistry.schema.version",
               "batch.schema.version", "entitylink.schema.version", "eventsubscription.schema.version",
               "identitylink.schema.version", "job.schema.version", "task.schema.version", "variable.schema.version"}


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def q(name):
    return "`" + name.replace("`", "``") + "`"


def lit(name):
    require(re.fullmatch(r"[A-Za-z0-9_]+", name), "Unexpected identifier: " + name)
    return "'" + name + "'"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def engine_table(name):
    return name.upper().startswith(("ACT_", "FLW_"))


class PrivateServer:
    def __init__(self, work, port):
        self.work = work
        self.port = port
        self.password = secrets.token_hex(32)
        self.env = {**os.environ, "MYSQL_PWD": self.password,
                    "FLOWABLE_REHEARSAL_PASSWORD": self.password}
        self.connection = ["--no-defaults", "--protocol=SOCKET", "--user=root",
                           "--socket=" + str(work / "db.sock")]
        self.mysql = ["mysql", *self.connection, "--batch", "--skip-column-names",
                      "--binary-mode", "--default-character-set=utf8mb4"]
        self.process = None

    def __enter__(self):
        data = self.work / "mysql"
        data.mkdir()
        init_file = self.work / "init.sql"
        init_file.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + self.password + "';\n"
                             "CREATE DATABASE captured;\nCREATE DATABASE baseline8;\nCREATE DATABASE recovered;\n")
        base = ["mysqld", "--no-defaults", "--datadir=" + str(data), "--log-error=" + str(self.work / "mysql.log")]
        subprocess.run([*base, "--initialize-insecure"], check=True)
        self.process = subprocess.Popen([*base, "--bind-address=127.0.0.1", "--port=" + str(self.port),
                                        self.connection[-1], "--mysqlx=OFF", "--event-scheduler=OFF",
                                        "--pid-file=" + str(self.work / "db.pid"), "--init-file=" + str(init_file)])
        try:
            for _ in range(300):
                if self.process.poll() is not None:
                    raise RuntimeError((self.work / "mysql.log").read_text()[-1500:])
                if (self.work / "db.sock").exists():
                    ping = subprocess.run([*self.mysql, "recovered", "-e", "SELECT 1"],
                                          env=self.env, capture_output=True)
                    if not ping.returncode:
                        break
                time.sleep(0.1)
            else:
                raise RuntimeError("Private MySQL startup timed out")
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, *unused):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(30)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()

    def read(self, sql):
        result = subprocess.run(self.mysql, input=sql.encode(), stdout=subprocess.PIPE,
                                env=self.env, check=True)
        return [line.split("\t") for line in result.stdout.decode().splitlines()]

    def load(self, schema, infile):
        with infile.open("rb") as src:
            subprocess.run([*self.mysql, schema], stdin=src, env=self.env, check=True)

    def dump(self, schema, output, extra=()):
        cmd = ["mysqldump", *self.connection, "--single-transaction", "--hex-blob", "--routines",
               "--events", "--triggers", "--set-gtid-purged=OFF", "--no-tablespaces", *extra, schema]
        with output.open("xb") as target:
            subprocess.run(cmd, stdout=target, env=self.env, check=True)

    def engine(self, mode, java, output):
        with output.open("wb") as log:
            proc = subprocess.run([java, "-cp", str(self.work / "lib/*"),
                                   str(SCRIPTS / "FlowableSchemaCheck.java"), mode, str(self.port)],
                                  stdout=log, stderr=subprocess.STDOUT, env=self.env)
        logtext = output.read_text()
        match = re.search(r"^FLOWABLE_COUNTS (\d+) (\d+) (\d+) (\d+)$", logtext, re.M)
        require(not proc.returncode and match, "Flowable " + mode + " failed. Inspect " + str(output))
        return dict(zip(("definitions", "active_tasks", "running_instances", "historic_instances"),
                        map(int, match.groups())))

    def tables(self, schema):
        return {name: kind for name, kind in self.read(
            f"SELECT TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA={lit(schema)}")}

    def columns(self, schema):
        out = defaultdict(list)
        for table, name in self.read("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS "
                                     f"WHERE TABLE_SCHEMA={lit(schema)} ORDER BY TABLE_NAME,COLUMN_NAME"):
            out[table].append(name)
        return out

    def shape(self, schema, tables):
        specs = {
            "tables": ("TABLES", "TABLE_NAME,ENGINE,TABLE_COLLATION,ROW_FORMAT"),
            "columns": ("COLUMNS", "TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,"
                                   "COLLATION_NAME,GENERATION_EXPRESSION"),
            "indexes": ("STATISTICS", "TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX,COLUMN_NAME,NON_UNIQUE,"
                                      "SUB_PART,INDEX_TYPE,COLLATION,IS_VISIBLE,EXPRESSION"),
            "constraints": ("TABLE_CONSTRAINTS", "TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE,ENFORCED"),
            "references": ("REFERENTIAL_CONSTRAINTS", "TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_NAME,"
                                                      "UPDATE_RULE,DELETE_RULE,MATCH_OPTION"),
            "keys": ("KEY_COLUMN_USAGE", "TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION,COLUMN_NAME,"
                                         "REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME"),
        }
        result = {}
        for category, (meta, fields) in specs.items():
            schema_field = "CONSTRAINT_SCHEMA" if meta == "REFERENTIAL_CONSTRAINTS" else "TABLE_SCHEMA"
            rows = self.read(f"SELECT {fields} FROM information_schema.{meta} WHERE {schema_field}={lit(schema)}")
            result[category] = {tuple(row) for row in rows if row[0] in tables}
        checks = self.read("SELECT t.TABLE_NAME,t.CONSTRAINT_NAME,c.CHECK_CLAUSE "
                           "FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c "
                           "ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME "
                           f"WHERE t.TABLE_SCHEMA={lit(schema)}")
        result["checks"] = {tuple(row) for row in checks if row[0] in tables}
        return result

    def database_objects(self, schema):
        specs = {
            "TRIGGERS": ("TRIGGER_SCHEMA", "TRIGGER_NAME,DEFINER,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_ORDER,"
                                           "ACTION_TIMING,ACTION_STATEMENT,SQL_MODE,CHARACTER_SET_CLIENT,"
                                           "COLLATION_CONNECTION,DATABASE_COLLATION"),
            "ROUTINES": ("ROUTINE_SCHEMA", "ROUTINE_NAME,ROUTINE_TYPE,DTD_IDENTIFIER,DEFINER,ROUTINE_DEFINITION,"
                                            "IS_DETERMINISTIC,SQL_DATA_ACCESS,SECURITY_TYPE,SQL_MODE"),
            "EVENTS": ("EVENT_SCHEMA", "EVENT_NAME,DEFINER,EVENT_DEFINITION,EVENT_TYPE,EXECUTE_AT,INTERVAL_VALUE,"
                                       "INTERVAL_FIELD,STARTS,ENDS,STATUS,ON_COMPLETION,SQL_MODE"),
            "VIEWS": ("TABLE_SCHEMA", "TABLE_NAME,VIEW_DEFINITION,CHECK_OPTION,IS_UPDATABLE,DEFINER,SECURITY_TYPE"),
        }
        return {kind: self.read(f"SELECT {fields} FROM information_schema.{kind} WHERE {field}={lit(schema)} ORDER BY 1")
                for kind, (field, fields) in specs.items()}

    def allocators(self, schema, tables):
        rows = self.read("SET SESSION information_schema_stats_expiry=0; SELECT TABLE_NAME,AUTO_INCREMENT "
                         f"FROM information_schema.TABLES WHERE TABLE_SCHEMA={lit(schema)} AND AUTO_INCREMENT IS NOT NULL")
        return {table: int(n) for table, n in rows if table in tables}

    def workflow_links(self, schema):
        links = (("wf_process_definition", "flowable_deployment_id", "ACT_RE_DEPLOYMENT"),
                 ("wf_process_instance", "flowable_proc_inst_id", "ACT_HI_PROCINST"),
                 ("wf_task", "flowable_task_id", "ACT_HI_TASKINST"))
        result = {}
        for table, column, target in links:
            known, matched = self.read(f"SELECT COUNT(*),COUNT(f.ID_) FROM {q(schema)}.{q(table)} b "
                                       f"LEFT JOIN {q(schema)}.{q(target)} f ON f.ID_=b.{q(column)} "
                                       f"WHERE b.{q(column)} IS NOT NULL AND b.{q(column)}<>''")[0]
            result[f"{table}.{column}"] = {"populated": int(known), "resolved": int(matched)}
        return result

    def content(self, schema, tables):
        columns = self.columns(schema)
        # Per-column SHA-256 and an explicit NULL marker preserve boundaries, binary data and nullability.
        # Sorting the final row hashes also retains multiplicities for tables without a primary key.
        queries = ["SET time_zone='+00:00'"]
        all_rows = {t: [] for t in tables}
        for table in sorted(tables):
            exprs = [f"IF({q(c)} IS NULL,'N',CONCAT('V',SHA2(CAST({q(c)} AS BINARY),256)))" for c in columns[table]]
            where = " WHERE NOT " + VERSION_ROW if table in PROPERTIES else ""
            queries.append(f"SELECT {lit(table)},SHA2(CONCAT({','.join(exprs)}),256) FROM "
                           f"{q(schema)}.{q(table)}{where}")
        for table, row_hash in self.read(";\n".join(queries)):
            all_rows[table].append(row_hash)
        return {table: {"rows": len(hashes),
                        "sha256": hashlib.sha256("".join(sorted(hashes)).encode()).hexdigest()}
                for table, hashes in sorted(all_rows.items())}

    def fk_health(self, schema):
        groups = defaultdict(list)
        rows = self.read("SELECT TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,"
                         "COLUMN_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                         f"WHERE TABLE_SCHEMA={lit(schema)} AND REFERENCED_TABLE_NAME IS NOT NULL "
                         "ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION")
        for table, key, ref_db, ref_table, col, ref_col in rows:
            require(ref_db == schema, f"External schema FK {table}.{key} -> {ref_db}.{ref_table}")
            groups[(table, key, ref_table)].append((col, ref_col))
        queries = []
        for (table, key, ref), columns in groups.items():
            nonnull = " AND ".join(f"c.{q(c)} IS NOT NULL" for c, _ in columns)
            match = " AND ".join(f"c.{q(c)}=p.{q(r)}" for c, r in columns)
            queries.append(f"SELECT {lit(table)},{lit(key)},COUNT(*) FROM {q(schema)}.{q(table)} c WHERE "
                           f"{nonnull} AND NOT EXISTS (SELECT 1 FROM {q(schema)}.{q(ref)} p WHERE {match})")
        violations = [row for row in self.read(";\n".join(queries)) if int(row[2])]
        require(not violations, "Unresolved foreign key references: " + str(violations))
        return {"foreign_keys_checked": len(groups), "orphan_rows": 0,
                "engine_to_business_foreign_keys": [list(key) for key in groups if engine_table(key[0]) != engine_table(key[2])]}


def extract_jars(app, work):
    lib = work / "lib"
    lib.mkdir()
    with zipfile.ZipFile(app) as jar:
        names = [n for n in jar.namelist() if n.startswith("BOOT-INF/lib/") and n.endswith(".jar")]
        engines = [Path(n).name for n in names if re.fullmatch(r"flowable-engine-[0-9][^.]*\..*\.jar", Path(n).name)]
        require(engines == ["flowable-engine-8.0.0.jar"], f"Expected Flowable 8.0.0, found {engines}")
        for n in names:
            with jar.open(n) as inp, (lib / Path(n).name).open("xb") as out:
                shutil.copyfileobj(inp, out)


def compare_schema(db, engine):
    target = db.shape("recovered", engine)
    require(target == db.shape("baseline8", engine), "Official Flowable 8 differs from known-good upgrade DDL")
    current = db.shape("captured", engine)
    changes = []
    for category, wanted in target.items():
        extra = current[category] - wanted
        missing = wanted - current[category]
        require(not extra, f"Unexpected source {category}: {extra}")
        for row in sorted(missing):
            require(row[0] == "FLW_EVENT_RESOURCE" and row[1] in ("FLW_IDX_EVENT_RSRC_DPL", "FLW_FK_EVENT_RSRC_DPL"),
                    f"Unrecognized missing {category}: {row}")
            changes.append([category, *row])
    return changes


def verify_metadata(db, dbname):
    fresh = {}
    for t in PROPERTIES:
        query = f"SELECT NAME_,VALUE_ FROM {q(dbname)}.{q(t)} WHERE {VERSION_ROW}"
        fresh[t] = dict(db.read(query))
    return fresh


def validate_versions(db):
    fresh = verify_metadata(db, "recovered")
    baseline = verify_metadata(db, "baseline8")
    current = verify_metadata(db, "captured")
    for table in PROPERTIES:
        official = fresh[table]
        require(official and all(v == "8.0.0.0" for k, v in official.items() if k != "schema.history"),
                "Unexpected official Flowable version")
        require(official.keys() == baseline[table].keys() and
                all(baseline[table][k] == v for k, v in official.items() if k != "schema.history"),
                "Unknown baseline metadata")
        stamps = {k: v for k, v in current[table].items() if k != "schema.history"}
        expected7 = EXPECTED_V7 if table == "ACT_GE_PROPERTY" else {"schema.version"}
        require(stamps == {name: "7.0.1.1" for name in expected7}
                or stamps == {k: v for k, v in official.items() if k != "schema.history"},
                "Unexpected source Flowable version metadata")
    return {"official": fresh, "trusted_prior": baseline, "captured": current}


def migrate(db, tables, cols):
    actions = ["SET FOREIGN_KEY_CHECKS=0", "START TRANSACTION"]
    for t in sorted(tables):
        where = " WHERE NOT " + VERSION_ROW if t in PROPERTIES else ""
        fields = ",".join(map(q, cols[t]))
        actions.append("DELETE FROM recovered." + q(t) + where)
        actions.append(f"INSERT INTO recovered.{q(t)} ({fields}) SELECT {fields} FROM captured.{q(t)}{where}")
    actions.extend(["COMMIT", "SET FOREIGN_KEY_CHECKS=1"])
    db.read(";\n".join(actions))
    # Preserve allocator state even when the largest allocated row was later deleted.
    for t, number in db.allocators("captured", tables).items():
        db.read(f"ALTER TABLE recovered.{q(t)} AUTO_INCREMENT={number}")


def restore(db, args, work, out):
    inputs = (args.current_dump, args.verified_v8_dump, args.app_jar)
    source_hashes = {str(p.resolve()): digest(p) for p in inputs}
    extract_jars(args.app_jar, work)
    db.load("captured", args.current_dump)
    db.load("baseline8", args.verified_v8_dump)
    db.engine("init", args.java, out / "engine-init.log")
    canonical = set(db.tables("recovered"))
    require(canonical and all(map(engine_table, canonical)), "Fresh DB has unexpected tables")
    baseline = {name for name in db.tables("baseline8") if engine_table(name)}
    require(canonical == baseline, "Known-good backup and official engine have different table sets")
    captured = db.tables("captured")
    source_engine = {name for name in captured if engine_table(name)}
    legacy = source_engine - canonical
    require(canonical <= source_engine and {n.upper() for n in legacy} in (set(), OLD_LOGS),
            "Unknown source Flowable table set")
    changes = compare_schema(db, canonical)
    metadata = validate_versions(db)
    old_logs = db.content("captured", legacy)
    business = {name: kind for name, kind in captured.items() if name not in source_engine}
    business_dump = work / "business.sql"
    db.dump("captured", business_dump, ["--ignore-table=captured." + n for n in sorted(source_engine)])
    db.load("recovered", business_dump)
    migrate(db, canonical, db.columns("recovered"))
    require(db.tables("recovered") == {**business, **dict.fromkeys(canonical, "BASE TABLE")},
            "Recovered table/view list differs")
    require(db.shape("captured", business) == db.shape("recovered", business), "Business schema mismatch")
    require(db.shape("baseline8", canonical) == db.shape("recovered", canonical), "Final Flowable DDL mismatch")
    require(verify_metadata(db, "recovered") == metadata["official"], "Official metadata changed during import")
    tables = canonical | {n for n, k in business.items() if k == "BASE TABLE"}
    wanted = db.content("captured", tables)
    actual = db.content("recovered", tables)
    require(wanted == actual, "Row contents changed in " + str([t for t in wanted if actual[t] != wanted[t]]))
    objects = db.database_objects("captured")
    allocators = db.allocators("captured", tables)
    links = db.workflow_links("captured")
    require(db.database_objects("recovered") == objects, "Database object definitions changed")
    require(db.allocators("recovered", tables) == allocators, "Auto-increment counters changed")
    require(db.workflow_links("recovered") == links, "Workflow business links changed")
    # Row IDs can overlap in different tables. This comparison is specifically against definition IDs.
    diff = db.read("SELECT c.ID_ FROM captured.ACT_RE_PROCDEF c LEFT JOIN baseline8.ACT_RE_PROCDEF b "
                   "ON c.ID_=b.ID_ WHERE b.ID_ IS NULL ORDER BY c.ID_")
    fks = db.fk_health("recovered")
    image = out / "recovered.sql.partial"
    db.dump("recovered", image)
    db.read("CREATE DATABASE exportcheck")
    db.load("exportcheck", image)
    require(db.tables("exportcheck") == db.tables("recovered"), "Exported object list mismatch")
    require(db.shape("exportcheck", tables) == db.shape("recovered", tables), "Exported schema differs")
    require(db.content("exportcheck", tables) == actual, "Exported rows differ")
    require(db.database_objects("exportcheck") == objects, "Exported database objects differ")
    require(db.allocators("exportcheck", tables) == allocators, "Exported auto-increment counters differ")
    require(db.workflow_links("exportcheck") == links, "Exported workflow links differ")
    require(db.fk_health("exportcheck") == fks, "Exported foreign keys differ")
    require(verify_metadata(db, "exportcheck") == metadata["official"], "Exported version metadata differs")
    counts = db.engine("verify", args.java, out / "engine-verify.log")
    require(counts["definitions"] == actual["ACT_RE_PROCDEF"]["rows"] and
            counts["historic_instances"] == actual["ACT_HI_PROCINST"]["rows"], "Engine query count mismatch")
    require(source_hashes == {str(p.resolve()): digest(p) for p in inputs},
            "An input changed while the rehearsal was running")
    report = {
        "engine": "8.0.0",
        "mysql": db.read("SELECT VERSION()")[0][0],
        "sources_sha256": source_hashes,
        "recovered_dump_sha256": digest(image),
        "official_tables": sorted(canonical),
        "business_tables_and_views": len(business),
        "schema_differences_repaired": changes,
        "historical_migration_tables_archived_in_source_only": old_logs,
        "schema_metadata": metadata,
        "foreign_key_verification": fks,
        "verified_business_links": links,
        "verified_auto_increment_counters": allocators,
        "verified_database_objects": {kind: [row[0] for row in rows] for kind, rows in objects.items()},
        "export_reimport_verified": True,
        "engine_queries_schema_updates_disabled": counts,
        "new_definition_ids_since_trusted_backup": [row[0] for row in diff],
        "verified_source_and_target_rows": actual,
    }
    (out / "validation.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    image.rename(out / "recovered.sql")
    print(f"Verified {len(canonical)} engine tables and {len(business)} business tables/views. {counts}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("current-dump", "verified-v8-dump", "app-jar", "output-dir"):
        parser.add_argument("--" + arg, type=Path, required=True)
    parser.add_argument("--java", default="java", help="Project JDK java executable")
    parser.add_argument("--port", type=int, help="Loopback port for the newly initialized private mysqld")
    args = parser.parse_args()
    os.umask(0o077)
    require(all(p.is_file() for p in (args.current_dump, args.verified_v8_dump, args.app_jar)), "Missing input")
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=False)
    with tempfile.TemporaryDirectory(prefix="ts-f8-", dir="/tmp") as folder:
        if not args.port:
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", 0))
                args.port = sock.getsockname()[1]
        work = Path(folder).resolve()
        with PrivateServer(work, args.port) as db:
            restore(db, args, work, out)


if __name__ == "__main__":
    main()
