"""Signed TianShu industry gateway, backed by the existing project's HTTP API."""
import hashlib
import hmac
import json
import math
import os
import re
import sqlite3
import time
import uuid
from http import HTTPStatus
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from wsgiref.simple_server import make_server

ROOT = Path(__file__).resolve().parent.parent
LIMIT = 2 * 1024 * 1024


class Rejected(Exception):
    def __init__(self, status):
        self.status = status


def at(value, path):
    for key in path.split(".") if path else ():
        if not isinstance(value, dict) or key not in value:
            return None
        value = value[key]
    return value


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


def upstream(origin, path, method, token, data=None, request_id=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if data is not None:
        headers["Content-Type"] = "application/json"
    if request_id:
        headers["Idempotency-Key"] = request_id
    req = Request(origin + path, method=method, headers=headers,
                  data=json.dumps(data).encode() if data is not None else None)
    try:
        with build_opener(NoRedirect).open(req, timeout=2) as response:
            body = response.read(LIMIT + 1)
    except HTTPError as exc:
        raise Rejected(403 if exc.code in (401, 403) else 422 if exc.code in (400, 404, 409, 422) else 503) from exc
    except (OSError, URLError) as exc:
        raise Rejected(503) from exc
    if len(body) > LIMIT:
        raise Rejected(503)
    return json.loads(body) if body else None


def checked(config, *args):
    result = upstream(config["origin"], *args)
    if config.get("codeAt") and at(result, config["codeAt"]) != config["successCode"]:
        raise Rejected(422)
    return result


def parameters(action, supplied):
    path, query, body = action["path"], {}, {}
    if set(supplied) - {f["key"] for f in action["fields"]}:
        raise Rejected(422)
    for field in action["fields"]:
        value = supplied.get(field["key"])
        if value is None or value == "":
            if field["required"]:
                raise Rejected(422)
            continue
        if not isinstance(value, str) or len(value) > 2048:
            raise Rejected(422)
        location, key = field["location"], field["target"]
        if location == "path":
            if not re.fullmatch(r"[a-zA-Z0-9_-]+", value):
                raise Rejected(422)
            path = path.replace("{"+key+"}", quote(value, safe=""))
        elif location == "query":
            query[key] = value
        else:
            if field["type"] == "number":
                try:
                    value = json.loads(value)
                except ValueError as exc:
                    raise Rejected(422) from exc
                if type(value) not in (int, float) or not math.isfinite(value):
                    raise Rejected(422)
            elif field["type"] == "number_list":
                items = [item.strip() for item in value.replace("，",",").split(",")]
                if not items or len(items) > 100 or not all(re.fullmatch(r"[0-9]{1,18}",item) for item in items):
                    raise Rejected(422)
                value = [int(item) for item in items]
            body[key] = value
    return path + ("?" + urlencode(query) if query else ""), body or None


def apply_once(database, key, digest, operation):
    # Reserve before calling the source. A crash or ambiguous timeout requires reconciliation.
    with sqlite3.connect(database) as db:
        db.execute("CREATE TABLE IF NOT EXISTS receipt (id TEXT PRIMARY KEY, digest TEXT NOT NULL, state INTEGER NOT NULL)")
        old = db.execute("SELECT digest,state FROM receipt WHERE id=?", (key,)).fetchone()
        if old:
            if old == (digest, 200):
                return {"accepted": True}
            raise Rejected(409)
        try:
            db.execute("INSERT INTO receipt VALUES (?,?,0)", (key, digest))
        except sqlite3.IntegrityError as exc:
            raise Rejected(409) from exc
    operation()
    with sqlite3.connect(database) as db:
        db.execute("UPDATE receipt SET state=200 WHERE id=? AND digest=?", (key, digest))
    return {"accepted": True}


def invoke(design, config, binding, token, actor, action, supplied, request_id, database):
    """Shared business dispatch for portal and standalone sessions after authentication."""
    app, revision = design["code"], design["revision"]
    for page in design["pages"]:
        if action == page["key"]+"_list":
            if supplied:
                raise Rejected(422)
            rows = at(checked(config, page["path"], "GET", token), page["rowsAt"])
            if not isinstance(rows,list) or not all(isinstance(row,dict) for row in rows):
                raise Rejected(503)
            scope = binding.get("rowScope", {}).get(page["key"], {})
            return [{c["key"]: at(row,c["source"]) for c in page["columns"]} for row in rows
                    if all(at(row,p) is not None and str(at(row,p)) == str(binding[k]) for k,p in scope.items())]
        for operation in page["actions"]:
            if action != operation["key"]:
                continue
            path, body = parameters(operation, supplied)
            digest = hashlib.sha256(json.dumps([revision,action,supplied],sort_keys=True).encode()).hexdigest()
            def send():
                result = checked(config,path,operation["method"],token,body,request_id)
                if operation.get("positiveCountAt"):
                    count = at(result,operation["positiveCountAt"])
                    if type(count) is not int or count < 1:
                        raise Rejected(422)
            return apply_once(database, app+":"+actor+":"+request_id, digest, send)
    raise Rejected(403)


def gateway(root, key, config, database):
    origin = urlsplit(config["origin"])
    if (origin.scheme != "https" and not (origin.scheme == "http" and origin.hostname in ("127.0.0.1", "localhost", "::1"))) or not origin.hostname or origin.username or origin.password or origin.query or origin.fragment or origin.path not in ("", "/"):
        raise ValueError("业务服务需要独立的 HTTPS origin；本机联调可使用回环地址")
    config = dict(config, origin=config["origin"].rstrip("/"))
    identity = config["identity"]
    if not re.fullmatch(r"/[A-Za-z0-9_/-]+", identity["path"]) or not identity["tenantAt"] or not identity["userAt"] or len(key) < 32:
        raise ValueError("请配置身份核对接口和独立签名密钥")
    current = json.loads((root/"project-design.json").read_text())

    def dispatch(env):
        size = int(env.get("CONTENT_LENGTH") or "0")
        if env["REQUEST_METHOD"] != "POST" or not 0 < size <= 100_000:
            raise Rejected(400)
        raw = env["wsgi.input"].read(size)
        signature = hmac.new(key, raw, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, env.get("HTTP_X_TIANSHU_SIGNATURE","")):
            raise Rejected(403)
        msg = json.loads(raw)
        app, action, revision = msg["app"], msg["action"], msg["revision"]
        if (app != current["code"] or type(revision) is not int or not 1 <= revision <= current["revision"]
                or env["PATH_INFO"] != f"/apps/{app}/actions/{action}"
                or abs(time.time()-int(msg["issuedAt"])) > 60):
            raise Rejected(403)
        design = current
        if revision != current["revision"]:
            history = root / "revisions" / f"{revision}.json"
            if not history.is_file():
                raise Rejected(409)
            design = json.loads(history.read_text())
        actor = f'{msg["tenantId"]}:{msg["userId"]}'
        binding = config["members"].get(actor)
        if not binding or action not in binding["actions"]:
            raise Rejected(403)
        token = os.environ.get(binding["tokenEnv"])
        if not token:
            raise Rejected(403)
        who = checked(config, identity["path"], "GET", token)
        if any(at(who,identity[k+"At"]) is None or str(at(who,identity[k+"At"])) != str(binding[k]) for k in ("tenant","user")):
            raise Rejected(403)
        request_id = str(uuid.UUID(msg["requestId"]))
        return invoke(design, config, binding, token, actor, action, msg["input"], request_id, database)

    def application(env, respond):
        try:
            result, status = dispatch(env), 200
        except Rejected as exc:
            result, status = {}, exc.status
        except (KeyError, TypeError, ValueError, OSError, sqlite3.Error):
            result, status = {}, 503
        data = json.dumps(result,ensure_ascii=False,allow_nan=False).encode()
        respond(f"{status} {HTTPStatus(status).phrase}",[("Content-Type","application/json"),("Content-Length",str(len(data)))])
        return [data]
    return application


def configured_app():
    config = json.loads(Path(os.environ["PROJECT_BINDINGS_FILE"]).read_text())
    secret = Path(os.environ["TIANSHU_APP_SECRET_FILE"]).read_bytes().strip()
    return gateway(ROOT,secret,config,os.environ["PROJECT_RECEIPTS_DB"])


if __name__ == "__main__":
    with make_server("127.0.0.1",int(os.getenv("PORT","18087")),configured_app()) as server:
        server.serve_forever()
