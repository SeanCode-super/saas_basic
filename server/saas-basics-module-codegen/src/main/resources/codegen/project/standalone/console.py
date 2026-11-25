"""Independent management UI and API. Start with python3 server/console.py --config FILE."""
import argparse
import json
import os
import secrets
import sqlite3
import threading
import time
import uuid
from http import HTTPStatus
from http.cookies import SimpleCookie, CookieError
from pathlib import Path
from socketserver import ThreadingMixIn
from urllib.parse import urlsplit
from wsgiref.simple_server import WSGIServer, make_server
from service import ROOT, Rejected, at, checked, invoke


class SessionStore:
    def __init__(self):
        self.sessions = {}
        self.lock = threading.Lock()

    def create(self, token, tenant, user):
        with self.lock:
            now = time.monotonic()
            self.sessions = {k:v for k,v in self.sessions.items() if v[0] > now}
            if len(self.sessions) >= 4096:
                raise Rejected(503)
            sid = secrets.token_urlsafe(32)
            self.sessions[sid] = (now + 8*3600, token, tenant, user)
            return sid

    def get(self, sid):
        with self.lock:
            session = self.sessions.get(sid)
            if not session or session[0] <= time.monotonic():
                self.sessions.pop(sid, None)
                raise Rejected(401)
            return session[1:]

    def remove(self, sid):
        with self.lock:
            self.sessions.pop(sid, None)


def origin(value):
    parsed = urlsplit(value)
    if (parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in ("localhost","127.0.0.1","::1"))) or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("","/"):
        raise ValueError("需要 HTTPS 地址或本机回环 HTTP 地址")
    return value.rstrip("/")


class Console:
    def __init__(self, root, config, database):
        self.design = json.loads((root/"project-design.json").read_text())
        self.config = dict(config, origin=origin(config["origin"]))
        self.public = origin(config["publicOrigin"])
        self.database = database
        self.sessions = SessionStore()
        self.auth = config["auth"]
        self.cookie = "ts_"+self.design["code"]+"_console"
        self.assets = {path: (mime,(root/"standalone"/name).read_bytes()) for path,name,mime in (
            ("/","index.html","text/html"),("/console.js","console.js","text/javascript"),("/console.css","console.css","text/css"))}
        self.asset_headers = [("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"),
                              ("X-Content-Type-Options","nosniff"),("Cache-Control","no-store"),("Referrer-Policy","no-referrer")]
        self.status_text = {401:"请重新登录",403:"当前账号没有此项权限",409:"此操作已提交，请刷新确认",422:"请核对输入或业务状态",503:"业务服务暂时不可用"}

    def identity(self, token):
        rule = self.auth
        who = checked(self.config,rule["identityPath"],"GET",token)
        tenant,user,role = (at(who,rule[k]) for k in ("tenantAt","userAt","roleAt"))
        scope = self.config["roles"].get(str(role))
        if tenant is None or user is None or not scope:
            raise Rejected(403)
        if rule.get("statusAt") and at(who,rule["statusAt"]) not in rule["allowedStatuses"]:
            raise Rejected(403)
        return str(tenant),str(user),scope,at(who,rule["nameAt"]),at(who,rule["tenantNameAt"])

    def cookie_header(self, value, lifetime):
        flags = "; Secure" if self.public.startswith("https:") else ""
        return ("Set-Cookie",f"{self.cookie}={value}; Path=/; Max-Age={lifetime}; HttpOnly; SameSite=Strict{flags}")

    def pages(self, scope):
        values = self.config.get("displayValues",{})
        return [dict(key=p["key"],label=p["label"],columns=p["columns"],values=values.get(p["key"],{}),
                     actions=[a for a in p["actions"] if a["key"] in scope["actions"]])
                for p in self.design["pages"] if p["key"]+"_list" in scope["actions"]]

    def owned_options(self, rule, token, tenant, user):
        page = next(p for p in self.design["pages"] if p["key"] == rule["page"])
        rows = at(checked(self.config, page["path"], "GET", token), page["rowsAt"])
        if not isinstance(rows, list) or not all(isinstance(row, dict) for row in rows):
            raise Rejected(503)
        options = []
        for row in rows:
            if str(at(row, rule["tenantAt"])) != tenant or str(at(row, rule["userAt"])) != user:
                continue
            value = at(row, rule["idAt"])
            label = at(row, rule.get("labelAt", rule["idAt"]))
            if value is not None:
                options.append({"value": str(value), "label": str(label or value)})
        return options

    def enforce_input_scope(self, action, inputs, token, tenant, user):
        for rule in self.config.get("inputScopes", {}).get(action, []):
            allowed = {o["value"] for o in self.owned_options(rule, token, tenant, user)}
            value = inputs.get(rule["field"])
            if not isinstance(value, str) or not all(i.strip() in allowed for i in value.replace("，", ",").split(",")):
                raise Rejected(403)

    def dispatch(self, env):
        path, method = env["PATH_INFO"], env["REQUEST_METHOD"]
        # Pin browser requests to the configured origin, including local development.
        if env.get("HTTP_HOST","") != urlsplit(self.public).netloc:
            raise Rejected(403)
        if method == "GET" and path in self.assets:
            mime,data=self.assets[path]
            return 200,mime,data,[]
        if method == "GET" and path == "/healthz":
            return 200,"application/json",{"app":self.design["code"],"status":"UP"},[]
        cookies = SimpleCookie()
        cookies.load(env.get("HTTP_COOKIE", ""))
        sid = cookies[self.cookie].value if self.cookie in cookies else ""
        if method == "GET" and path == "/console/about":
            try:
                self.sessions.get(sid)
                signed_in = True
            except Rejected:
                signed_in = False
            return 200, "application/json", {"name": self.design["name"], "signedIn": signed_in}, []
        body={}
        if method=="POST":
            if env.get("HTTP_ORIGIN") != self.public or env.get("CONTENT_TYPE","").split(";")[0] != "application/json":
                raise Rejected(403)
            size=int(env.get("CONTENT_LENGTH") or 0)
            if not 0<size<=65536:
                raise Rejected(422)
            body=json.loads(env["wsgi.input"].read(size))
            if not isinstance(body,dict):
                raise Rejected(422)
        if method=="POST" and path=="/console/login":
            username,password=body.get("username"),body.get("password")
            if not all(isinstance(v,str) and 0<len(v)<=256 for v in (username,password)):
                raise Rejected(422)
            try:
                reply=checked(self.config,self.auth["loginPath"],"POST",None,
                              {self.auth["usernameField"]:username,self.auth["passwordField"]:password})
                token=at(reply,self.auth["tokenAt"])
                if not isinstance(token,str) or not token:
                    raise Rejected(401)
                tenant,user,_,_,_=self.identity(token)
            except Rejected as exc:
                if exc.status in (401,403,422):
                    raise Rejected(401) from exc
                raise
            new=self.sessions.create(token,tenant,user)
            self.sessions.remove(sid)
            return 200,"application/json",{"success":True},[self.cookie_header(new,28800)]
        if method=="POST" and path=="/console/logout":
            self.sessions.remove(sid)
            return 200,"application/json",{},[self.cookie_header("",0)]
        token,tenant,user=self.sessions.get(sid)
        try:
            now_tenant,now_user,scope,username,tenant_name=self.identity(token)
        except Rejected as exc:
            if exc.status in (403,422,401):
                self.sessions.remove(sid)
                raise Rejected(401) from exc
            raise
        if (now_tenant,now_user)!=(tenant,user):
            self.sessions.remove(sid)
            raise Rejected(401)
        if method=="GET" and path=="/console/session":
            return 200,"application/json",dict(name=self.design["name"],username=username,tenant=tenant_name,pages=self.pages(scope)),[]
        if method == "GET" and path.startswith("/console/options/"):
            action = path.removeprefix("/console/options/")
            page = next((p for p in self.design["pages"] if any(a["key"] == action for a in p["actions"])), None)
            if not page or page["key"] + "_list" not in scope["actions"] or action not in scope["actions"]:
                raise Rejected(403)
            choices = {rule["field"]: self.owned_options(rule, token, tenant, user)
                       for rule in self.config.get("inputScopes", {}).get(action, [])}
            return 200, "application/json", choices, []
        if method=="POST" and path.startswith("/console/actions/"):
            action=path.removeprefix("/console/actions/")
            page=next((p for p in self.design["pages"] if action==p["key"]+"_list" or any(a["key"]==action for a in p["actions"])),None)
            if not page or page["key"]+"_list" not in scope["actions"] or action not in scope["actions"]:
                raise Rejected(403)
            inputs=body.get("input")
            if not isinstance(inputs,dict):
                raise Rejected(422)
            self.enforce_input_scope(action,inputs,token,tenant,user)
            result=invoke(self.design,self.config,dict(scope,tenant=tenant,user=user),token,"local:"+tenant+":"+user,
                          action,inputs,str(uuid.UUID(body["requestId"])),self.database)
            return 200,"application/json",result,[]
        raise Rejected(404)

    def __call__(self, env, respond):
        try:
            status,mime,value,extra=self.dispatch(env)
        except (Rejected,ValueError,KeyError,TypeError,CookieError,OSError,sqlite3.Error) as exc:
            status=exc.status if isinstance(exc,Rejected) else 422 if isinstance(exc,(ValueError,KeyError,TypeError,CookieError)) else 503
            mime,value,extra="application/json",{"message":self.status_text.get(status,"请求无效")},[]
        data=value if isinstance(value,bytes) else json.dumps(value,ensure_ascii=False).encode()
        respond(f"{status} {HTTPStatus(status).phrase}",self.asset_headers+extra+[("Content-Type",mime+"; charset=utf-8"),("Content-Length",str(len(data)))])
        return [data]


class LocalServer(ThreadingMixIn, WSGIServer):
    daemon_threads=True


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--config",type=Path,required=True)
    args=parser.parse_args()
    os.umask(0o077)
    config=json.loads(args.config.read_text())
    database=args.config.resolve().parent / "console-receipts.sqlite"
    app=Console(ROOT,config,database)
    with make_server("127.0.0.1",config["listenPort"],app,server_class=LocalServer) as server:
        print(f'Local admin: {app.public}',flush=True)
        server.serve_forever()
