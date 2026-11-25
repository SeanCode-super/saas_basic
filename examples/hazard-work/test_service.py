import hashlib
import hmac
import io
import json
import os
import sqlite3
import tempfile
import time
import unittest
import uuid
from concurrent.futures import ThreadPoolExecutor

from service import APP, PREFIX, PermitStore, application, encode


class PermitTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = os.path.join(directory.name, "work.sqlite")
        self.key = os.urandom(32)
        self.app = application(PermitStore(self.path), self.key)

    def call(self, action, fields=None, actor=1, tenant=1, rid=None, signing_key=None, **override):
        message = dict(app=APP, revision=1, action=action, input=fields or {}, userId=actor,
                       tenantId=tenant, issuedAt=int(time.time()), requestId=rid or str(uuid.uuid4()))
        message.update(override)
        body = encode(message)
        signature = hmac.new(signing_key or self.key, body, hashlib.sha256).hexdigest()
        env = {"REQUEST_METHOD": "POST", "PATH_INFO": PREFIX + action, "CONTENT_LENGTH": str(len(body)),
               "wsgi.input": io.BytesIO(body), "HTTP_X_TIANSHU_SIGNATURE": signature}
        status = []
        data = self.app(env, lambda value, headers: status.append(int(value.split()[0])))
        return status[0], json.loads(data[0])

    def test_concurrent_retry_writes_once_even_after_restart(self):
        rid = str(uuid.uuid4())
        fields = {"title": "年度检修", "area": "测试区", "team": "一班"}
        with ThreadPoolExecutor(4) as pool:
            replies = list(pool.map(lambda i: self.call("create", fields, rid=rid), range(4)))
        self.assertEqual([replies[0]] * 4, replies)
        self.assertEqual(200, replies[0][0])
        self.app = application(PermitStore(self.path), self.key)
        self.assertEqual(replies[0], self.call("create", fields, rid=rid))
        self.assertEqual(409, self.call("create", dict(fields, title="另一单"), rid=rid)[0])
        self.assertEqual(["一班"], [row["team"] for row in self.call("query")[1]])
        with sqlite3.connect(self.path) as db:
            self.assertEqual(1, db.execute("SELECT COUNT(*) FROM permit_history").fetchone()[0])

    def test_tenant_and_actor_boundaries_and_state_transitions(self):
        _, permit = self.call("create", {"title": "测试申请", "area": "测试区"})
        ref = {"id": permit["id"]}
        self.assertEqual((200, []), self.call("query", tenant=2))
        self.assertEqual(409, self.call("approve", ref, actor=2, tenant=2)[0])
        self.assertEqual(409, self.call("approve", ref)[0])
        self.assertEqual(409, self.call("close", ref, actor=2)[0])
        self.assertEqual("已批准", self.call("approve", ref, actor=2)[1]["status"])
        self.assertEqual("已关闭", self.call("close", ref, actor=2)[1]["status"])
        self.assertEqual(409, self.call("reject", ref, actor=2)[0])

    def test_signature_freshness_and_application_identity(self):
        self.assertEqual(403, self.call("query", signing_key=b"wrong")[0])
        self.assertEqual(403, self.call("query", issuedAt=int(time.time()) - 120)[0])
        self.assertEqual(403, self.call("query", app="other")[0])
        self.assertEqual(422, self.call("create", {"title": "测试"})[0])


if __name__ == "__main__":
    unittest.main()
