"""Tianshu native application. Run behind the internal application gateway."""
import hashlib
import hmac
import json
import os
import re
import sqlite3
import time
import uuid
from contextlib import closing
from pathlib import Path
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIServer, make_server

APP = "hazard_work"
PREFIX = f"/apps/{APP}/actions/"
ACTIONS = {"query", "create", "approve", "reject", "close"}
STATES = {"approve": "已批准", "reject": "已驳回", "close": "已关闭"}


class Denied(Exception):
    pass


class Conflict(Exception):
    pass


class Invalid(Exception):
    pass


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


class PermitStore:
    def __init__(self, path):
        self.path = path
        with closing(self.connect()) as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS permit (
                    id TEXT PRIMARY KEY, tenant INTEGER NOT NULL, applicant INTEGER NOT NULL,
                    title TEXT NOT NULL, area TEXT NOT NULL, status TEXT NOT NULL,
                    extras TEXT NOT NULL, created INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS permit_by_tenant ON permit(tenant,created);
                CREATE TABLE IF NOT EXISTS permit_history (
                    permit_id TEXT NOT NULL REFERENCES permit(id), actor INTEGER NOT NULL,
                    action TEXT NOT NULL, occurred INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS receipt (
                    tenant INTEGER NOT NULL, actor INTEGER NOT NULL, request_id TEXT NOT NULL,
                    digest TEXT NOT NULL, result TEXT NOT NULL,
                    PRIMARY KEY(tenant,actor,request_id));
            """)

    def connect(self):
        db = sqlite3.connect(self.path, timeout=2)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        return db

    def run(self, action, tenant, actor, request_id, fields):
        with closing(self.connect()) as db, db:
            if action == "query":
                rows = db.execute("""
                    SELECT id,title,area,status,extras FROM permit WHERE tenant=?
                    ORDER BY created DESC, rowid DESC LIMIT 100
                """, (tenant,))
                return [dict(json.loads(r["extras"]), id=r["id"], title=r["title"],
                             area=r["area"], status=r["status"]) for r in rows]

            # Serialize the check, transition, history entry and receipt as one transaction.
            db.execute("BEGIN IMMEDIATE")
            key = (tenant, actor, request_id)
            digest = hashlib.sha256(encode([action, fields])).hexdigest()
            receipt = db.execute("SELECT digest,result FROM receipt WHERE tenant=? AND actor=? AND request_id=?", key).fetchone()
            if receipt:
                if receipt["digest"] != digest:
                    raise Conflict()
                return json.loads(receipt["result"])

            if action == "create":
                title = fields.get("title", "").strip()
                area = fields.get("area", "").strip()
                if not title or not area or max(len(title), len(area)) > 160:
                    raise Invalid()
                permit_id = str(uuid.uuid4())
                status = "待审批"
                extras = {k: v for k, v in fields.items() if k not in ("title", "area", "id", "status")}
                db.execute("INSERT INTO permit VALUES(?,?,?,?,?,?,?,?)",
                           (permit_id, tenant, actor, title, area, status, encode(extras).decode(), int(time.time())))
            else:
                permit_id = fields.get("id")
                row = db.execute("SELECT applicant,status FROM permit WHERE tenant=? AND id=?", (tenant, permit_id)).fetchone()
                expected = "已批准" if action == "close" else "待审批"
                if not row or row["status"] != expected or (action != "close" and actor == row["applicant"]):
                    raise Conflict()
                status = STATES[action]
                db.execute("UPDATE permit SET status=? WHERE tenant=? AND id=?", (status, tenant, permit_id))
            db.execute("INSERT INTO permit_history VALUES(?,?,?,?)", (permit_id, actor, action, int(time.time())))
            result = {"id": permit_id, "status": status}
            db.execute("INSERT INTO receipt VALUES(?,?,?,?,?)", (*key, digest, encode(result).decode()))
            return result


def application(store, secret):
    def handle(env):
        path = env["PATH_INFO"]
        if env["REQUEST_METHOD"] == "GET" and path == "/health":
            return "200 OK", {"status": "UP"}
        if env["REQUEST_METHOD"] != "POST" or not path.startswith(PREFIX) or path[len(PREFIX):] not in ACTIONS:
            return "404 Not Found", {}
        size = int(env.get("CONTENT_LENGTH") or 0)
        if not 0 < size <= 256 * 1024:
            raise Invalid()
        body = env["wsgi.input"].read(size)
        sig = hmac.new(secret, body, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, env.get("HTTP_X_TIANSHU_SIGNATURE", "")):
            raise Denied()
        data = json.loads(body)
        action = path[len(PREFIX):]
        if (data["app"] != APP or data["action"] != action or data["revision"] < 1
                or abs(time.time() - data["issuedAt"]) > 60):
            raise Denied()
        tenant, actor = data["tenantId"], data["userId"]
        if any(type(v) is not int or v < 1 for v in (tenant, actor)):
            raise Denied()
        request_id = str(uuid.UUID(data["requestId"]))
        fields = data["input"]
        if (not isinstance(fields, dict) or len(fields) > 32 or any(
                not re.fullmatch(r"[a-z][a-z0-9_]{0,39}", k) or not isinstance(v, str) or len(v) > 2048
                for k, v in fields.items())):
            raise Invalid()
        return "200 OK", store.run(action, tenant, actor, request_id, fields)

    def dispatch(env, start_response):
        try:
            status, result = handle(env)
        except Denied:
            status, result = "403 Forbidden", {}
        except Conflict:
            status, result = "409 Conflict", {}
        except (Invalid, KeyError, TypeError, ValueError):
            status, result = "422 Unprocessable Entity", {}
        except sqlite3.OperationalError:
            status, result = "503 Service Unavailable", {}
        body = encode(result)
        start_response(status, [("Content-Type", "application/json"), ("Content-Length", str(len(body)))])
        return [body]
    return dispatch


def configured_app():
    os.umask(0o077)
    secret = Path(os.environ["HAZARD_SECRET_FILE"]).read_bytes().strip()
    if not 32 <= len(secret) <= 256:
        raise ValueError("Invalid HAZARD_SECRET_FILE length")
    return application(PermitStore(os.environ["HAZARD_DATABASE"]), secret)


if __name__ == "__main__":
    class LocalServer(ThreadingMixIn, WSGIServer):
        daemon_threads = True
    with make_server("127.0.0.1", int(os.getenv("HAZARD_PORT", "18086")), configured_app(),
                     server_class=LocalServer) as server:
        server.serve_forever()
