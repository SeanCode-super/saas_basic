import io
import hashlib
import hmac
import json
import os
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from service import gateway


class ContractTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.key = os.urandom(32)
        page = {"key":"tasks","path":"/tasks","rowsAt":"data.rows","columns":[{"key":"title","source":"name"}],
                "actions":[{"key":"create_task","method":"POST","path":"/tasks","fields":[
                    {"key":"title","target":"name","type":"text","location":"body","required":True}]}]}
        (root/"project-design.json").write_text(json.dumps({"revision":1,"code":"sample","pages":[page]}))
        config = {"origin":"http://127.0.0.1","identity":{"path":"/me","tenantAt":"tenant","userAt":"user"},
                  "members":{"7:9":{"tenant":17,"user":19,"tokenEnv":"PROJECT_TEST_USER","actions":["tasks_list","create_task"],
                                    "rowScope":{"tasks":{"user":"owner"}}}}}
        self.app = gateway(root,self.key,config,root/"receipt.db")
        self.id = str(uuid.uuid4())
        self.environment = patch.dict(os.environ,PROJECT_TEST_USER="test-only")
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def call(self,action,inputs,tenant=7,signed=True):
        data = json.dumps(dict(app="sample",action=action,revision=1,issuedAt=int(time.time()),
                               userId=9,tenantId=tenant,requestId=self.id,input=inputs)).encode()
        env = {"REQUEST_METHOD":"POST","PATH_INFO":"/apps/sample/actions/"+action,"wsgi.input":io.BytesIO(data),
               "CONTENT_LENGTH":str(len(data)),"HTTP_X_TIANSHU_SIGNATURE":hmac.new(self.key,data,hashlib.sha256).hexdigest() if signed else ""}
        statuses=[]
        body=self.app(env,lambda status,_:statuses.append(int(status.split()[0])))
        return statuses[0],json.loads(body[0])

    @patch("service.upstream")
    def test_query_filters_and_requires_bound_identity(self,upstream):
        upstream.side_effect = [{"tenant":17,"user":19},{"data":{"rows":[{"owner":19,"name":"mine"},{"owner":20,"name":"other"}]}}]
        self.assertEqual((200,[{"title":"mine"}]),self.call("tasks_list",{}))
        self.assertEqual(403,self.call("tasks_list",{},tenant=8)[0])
        self.assertEqual(403,self.call("tasks_list",{},signed=False)[0])
        upstream.side_effect = [{"tenant":17,"user":20}]
        self.assertEqual(403,self.call("tasks_list",{})[0])

    @patch("service.upstream")
    def test_write_retry_calls_business_once(self,upstream):
        actor = {"tenant":17,"user":19}
        upstream.side_effect = [actor,{},actor,actor]
        self.assertEqual(200,self.call("create_task",{"title":"first"})[0])
        self.assertEqual(200,self.call("create_task",{"title":"first"})[0])
        self.assertEqual(409,self.call("create_task",{"title":"changed"})[0])
        self.assertEqual(1,sum(call.args[2]=="POST" for call in upstream.call_args_list))
