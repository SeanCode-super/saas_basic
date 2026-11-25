import io
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from console import Console


class StandaloneSessionTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        (root / "standalone").mkdir()
        for file in ("index.html", "console.css", "console.js"):
            (root / "standalone" / file).touch()
        action = {"key":"edit","method":"POST","path":"/edit","fields":[
            {"key":"ids","required":True,"type":"number_list","location":"body","target":"ids"}]}
        page = {"key":"items","label":"Items","columns":[],"path":"/items","rowsAt":"rows","actions":[action]}
        (root/"project-design.json").write_text(json.dumps({"code":"sample","name":"Sample","revision":1,"pages":[page]}))
        self.origin = "http://127.0.0.1:12345"
        config = {"publicOrigin":self.origin,"origin":self.origin,
                  "auth":{"loginPath":"/login","usernameField":"user","passwordField":"password","tokenAt":"token",
                          "identityPath":"/me","tenantAt":"tenant","userAt":"user","roleAt":"role","nameAt":"name","tenantNameAt":"org"},
                  "roles":{"admin":{"actions":["items_list","edit"]},"member":{"actions":["items_list"]}},
                  "inputScopes":{"edit":[{"field":"ids","page":"items","idAt":"id","tenantAt":"tenant","userAt":"user","labelAt":"name"}]}}
        self.app = Console(root,config,root/"receipts.sqlite")
        self.identity = {"tenant":3,"user":5,"role":"admin","name":"operator","org":"Company"}
        self.writes = 0
        def reply(origin, path, method, token, *data):
            if path == "/login":
                self.assertIsNone(token)
                return {"token":"upstream-session"}
            self.assertEqual("upstream-session",token)
            if path == "/me":
                return self.identity
            if path == "/items":
                return {"rows":[{"tenant":3,"user":5,"id":7,"name":"mine"},{"tenant":4,"user":5,"id":9}]}
            self.writes += 1
            return {}
        mocked = patch("service.upstream",side_effect=reply)
        mocked.start()
        self.addCleanup(mocked.stop)
        self.cookie = ""
        self.request = str(uuid.uuid4())

    def call(self,path,body=None,origin=None):
        raw = json.dumps(body).encode()
        env = {"REQUEST_METHOD":"GET" if body is None else "POST", "PATH_INFO":path,"wsgi.input":io.BytesIO(raw),
               "CONTENT_LENGTH":str(len(raw)),"CONTENT_TYPE":"application/json","HTTP_ORIGIN":origin or self.origin,
               "HTTP_HOST":"127.0.0.1:12345","HTTP_COOKIE":self.cookie}
        result = {}
        def start(status,headers):
            result["status"] = int(status.split()[0])
            for key,value in headers:
                if key == "Set-Cookie":
                    self.assertIn("HttpOnly",value)
                    self.cookie = value.split(";")[0]
        data = self.app(env,start)
        return result["status"],json.loads(data[0])

    def login(self):
        self.assertEqual(200,self.call("/console/login",{"username":"local","password":"test"})[0])

    def test_session_requires_current_business_identity(self):
        self.assertEqual(401,self.call("/console/session")[0])
        self.login()
        self.assertEqual("operator",self.call("/console/session")[1]["username"])
        self.identity["tenant"] = 4
        self.assertEqual(401,self.call("/console/session")[0])
        self.identity["tenant"] = 3
        self.assertEqual(401,self.call("/console/session")[0])

    def test_ownership_permissions_and_replay(self):
        self.login()
        self.assertEqual([{"value":"7","label":"mine"}],self.call("/console/options/edit")[1]["ids"])
        def write(value):
            return self.call("/console/actions/edit",{"requestId":self.request,"input":{"ids":value}})[0]
        self.assertEqual(403,write("9"))
        self.assertEqual(200,write("7"))
        self.assertEqual(200,write("7"))
        self.assertEqual(1,self.writes)
        self.identity["role"] = "member"
        self.assertEqual(403,write("7"))
        self.assertEqual([],self.call("/console/session")[1]["pages"][0]["actions"])

    def test_logout_and_origin(self):
        self.assertEqual(403,self.call("/console/login",{"username":"u","password":"p"},"https://other.example")[0])
        self.login()
        original = self.cookie
        self.assertEqual(200,self.call("/console/logout",{})[0])
        self.cookie = original
        self.assertEqual(401,self.call("/console/session")[0])
