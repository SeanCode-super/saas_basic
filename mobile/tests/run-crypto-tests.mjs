import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createRequire } from "node:module";
import esbuild from "esbuild";
import { x25519 } from "@noble/curves/ed25519.js";
import { gcm } from "@noble/ciphers/aes.js";
import { extract, expand } from "@noble/hashes/hkdf.js";
import { sha256 } from "@noble/hashes/sha2.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, ".compiled");
fs.mkdirSync(outDir, { recursive: true });

await esbuild.build({
  entryPoints: [path.resolve(__dirname, "../src/utils/transport-crypto.ts")],
  outfile: path.join(outDir, "transport-crypto.mjs"),
  bundle: true,
  format: "esm",
  platform: "node",
  target: "node20"
});

const mod = await import(pathToFileURL(path.join(outDir, "transport-crypto.mjs")).href);

test("shouldEncryptRequest respects scope modes and always-bypass paths", () => {
  const serverPriv = new Uint8Array(32).fill(7);
  const serverPub = x25519.getPublicKey(serverPriv);
  const policy = {
    mode: "OPTIONAL",
    scopeMode: "SENSITIVE_ONLY",
    algorithm: "X25519-HKDF-AES-256-GCM",
    encryptRequest: true,
    encryptResponse: true,
    encryptQuery: true,
    replayProtection: true,
    timestampSkewSeconds: 300,
    includePaths: ["/api/**"],
    excludePaths: ["/api/health/**"],
    sensitivePaths: ["/api/auth/login", "/api/iam/**"],
    policyVersion: 1,
    activeKid: "k_test_1",
    publicKey: mod.bytesToBase64(serverPub),
    fingerprintSha256: "test",
    keyIssuedAtEpochMs: Date.now(),
    serverTimestampMs: Date.now()
  };

  assert.equal(mod.shouldEncryptRequest("/api/auth/login", policy), true);
  assert.equal(mod.shouldEncryptRequest("/api/iam/users", policy), true);
  assert.equal(mod.shouldEncryptRequest("/api/crm/customers", policy), false);
  assert.equal(mod.shouldEncryptRequest("/api/security/transport/policy", policy), false);
});

test("prepareEncryptedTransmission completes X25519 + HKDF-SHA256 + AES-256-GCM roundtrip", () => {
  const serverPriv = new Uint8Array(32).fill(11);
  const serverPub = x25519.getPublicKey(serverPriv);
  const policy = {
    mode: "REQUIRED",
    scopeMode: "ALL_API",
    algorithm: "X25519-HKDF-AES-256-GCM",
    encryptRequest: true,
    encryptResponse: true,
    encryptQuery: true,
    replayProtection: true,
    timestampSkewSeconds: 300,
    includePaths: ["/api/**"],
    excludePaths: [],
    sensitivePaths: [],
    policyVersion: 2,
    activeKid: "k_mob_1",
    publicKey: mod.bytesToBase64(serverPub),
    fingerprintSha256: "test",
    keyIssuedAtEpochMs: Date.now(),
    serverTimestampMs: Date.now()
  };

  const payload = { username: "admin", mobile: "13900001111" };
  const tx = mod.prepareEncryptedTransmission({
    policy,
    method: "POST",
    path: "/api/iam/users",
    body: payload,
    queryString: "page=1&size=20"
  });

  assert.ok(tx.envelope);
  assert.equal(tx.headers["X-Transport-Encrypted"], "1");
  assert.equal(tx.headers["X-Enc-Kid"], "k_mob_1");

  const epk = mod.base64ToBytes(tx.envelope.epk);
  const shared = x25519.getSharedSecret(serverPriv, epk);
  const salt = mod.utf8Encode(`saas-transport-v1|${tx.envelope.ts}|${tx.envelope.nonce}`);
  const ikm = new Uint8Array(shared.length + epk.length + serverPub.length);
  ikm.set(shared, 0);
  ikm.set(epk, shared.length);
  ikm.set(serverPub, shared.length + epk.length);
  const prk = extract(sha256, ikm, salt);
  const reqKey = expand(sha256, prk, mod.utf8Encode("saas-basics-req-v1"), 32);
  const respKey = expand(sha256, prk, mod.utf8Encode("saas-basics-resp-v1"), 32);

  const reqAad = mod.utf8Encode(`REQ|POST|/api/iam/users|${tx.envelope.kid}|${tx.envelope.ts}|${tx.envelope.nonce}`);
  const decryptedReq = JSON.parse(
    mod.utf8Decode(gcm(reqKey, mod.base64ToBytes(tx.envelope.iv), reqAad).decrypt(mod.base64ToBytes(tx.envelope.cipherText)))
  );
  assert.deepEqual(decryptedReq, payload);

  const respTs = Date.now();
  const respIv = new Uint8Array(12).fill(5);
  const respAad = mod.utf8Encode(`RESP|POST|/api/iam/users|${tx.envelope.kid}|${respTs}|${tx.envelope.nonce}`);
  const respCt = gcm(respKey, respIv, respAad).encrypt(mod.utf8Encode(JSON.stringify({ ok: true, code: 200 })));
  const decodedResp = tx.decryptResponse({
    encrypted: true,
    alg: "X25519-HKDF-AES-256-GCM",
    kid: tx.envelope.kid,
    iv: mod.bytesToBase64(respIv),
    ts: respTs,
    cipherText: mod.bytesToBase64(respCt)
  });
  assert.deepEqual(decodedResp, { ok: true, code: 200 });
});
