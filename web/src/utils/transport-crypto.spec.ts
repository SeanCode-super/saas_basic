import { describe, expect, it } from "vitest";
import { x25519 } from "@noble/curves/ed25519.js";
import { gcm } from "@noble/ciphers/aes.js";
import { extract, expand } from "@noble/hashes/hkdf.js";
import { sha256 } from "@noble/hashes/sha2.js";
import {
  antMatch,
  base64ToBytes,
  bytesToBase64,
  generateX25519KeyPairHex,
  normalizeApiPathname,
  prepareEncryptedTransmission,
  shouldEncryptRequest,
  utf8Decode,
  utf8Encode,
  type PublicTransportPolicy
} from "./transport-crypto";

function samplePolicy(overrides?: Partial<PublicTransportPolicy>): PublicTransportPolicy {
  const serverPriv = new Uint8Array(32).fill(7);
  const serverPub = x25519.getPublicKey(serverPriv);
  return {
    mode: "OPTIONAL",
    scopeMode: "ALL_API",
    algorithm: "X25519-HKDF-AES-256-GCM",
    encryptRequest: true,
    encryptResponse: true,
    encryptQuery: true,
    replayProtection: true,
    timestampSkewSeconds: 300,
    includePaths: ["/api/**"],
    excludePaths: ["/api/security/transport/policy", "/api/health/**"],
    sensitivePaths: ["/api/auth/login", "/api/system/**"],
    policyVersion: 1,
    activeKid: "k_test_01",
    publicKey: bytesToBase64(serverPub),
    fingerprintSha256: "abc",
    keyIssuedAtEpochMs: Date.now(),
    serverTimestampMs: Date.now(),
    clockOffsetMs: 0,
    ...overrides
  };
}

describe("transport-crypto", () => {
  it("normalizes pathnames and matches Ant path patterns single-pass safely", () => {
    expect(normalizeApiPathname("/api", "/system/configs?page=1")).toBe("/api/system/configs");
    expect(normalizeApiPathname("", "http://localhost:8080/api/auth/login?foo=1")).toBe("/api/auth/login");

    expect(antMatch("/api/**", "/api/system/ai/models")).toBe(true);
    expect(antMatch("/api/system/**", "/api/auth/login")).toBe(false);
    expect(antMatch("/api/*/configs", "/api/system/configs")).toBe(true);
    expect(antMatch("/api/*/configs", "/api/system/a/configs")).toBe(false);
    expect(antMatch("/api/iam/users/{id}/roles", "/api/iam/users/42/roles")).toBe(true);
    expect(antMatch("/api/iam/users/{id}/roles", "/api/iam/users/42/x/roles")).toBe(false);

    const generated = generateX25519KeyPairHex();
    expect(generated.privateKeyHex).toMatch(/^[0-9a-f]{64}$/);
    expect(base64ToBytes(generated.publicKeyBase64)).toHaveLength(32);
  });

  it("evaluates controllable encryption policy across modes, scopes and overrides", () => {
    const offPolicy = samplePolicy({ mode: "OFF" });
    expect(shouldEncryptRequest("/api/auth/login", offPolicy)).toBe(false);
    expect(shouldEncryptRequest("/api/auth/login", offPolicy, true)).toBe(true);

    const sensitivePolicy = samplePolicy({ mode: "REQUIRED", scopeMode: "SENSITIVE_ONLY" });
    expect(shouldEncryptRequest("/api/auth/login", sensitivePolicy)).toBe(true);
    expect(shouldEncryptRequest("/api/portal/entry", sensitivePolicy)).toBe(false);
    expect(shouldEncryptRequest("/api/security/transport/policy", sensitivePolicy)).toBe(false);
    expect(shouldEncryptRequest("/api/system/configs", sensitivePolicy, false)).toBe(false);
  });

  it("derives compatible X25519 + HKDF-SHA256 keys and encrypts/decrypts request, query and response", () => {
    const serverPriv = new Uint8Array(32).fill(9);
    const serverPub = x25519.getPublicKey(serverPriv);
    const policy = samplePolicy({ publicKey: bytesToBase64(serverPub), mode: "REQUIRED" });

    const tx = prepareEncryptedTransmission({
      policy,
      method: "POST",
      path: "/api/auth/login",
      body: { username: "admin", secret: "S3cr3t!" },
      queryString: "tenantCode=platform"
    });

    expect(tx.headers["X-Transport-Encrypted"]).toBe("1");
    expect(tx.headers["X-Enc-Query"]).toContain(".");
    expect(tx.envelope?.cipherText).toBeTruthy();

    // Server side derivation mirror
    const clientEpk = base64ToBytes(tx.envelope!.epk);
    const shared = x25519.getSharedSecret(serverPriv, clientEpk);
    const salt = utf8Encode(`saas-transport-v1|${tx.envelope!.ts}|${tx.envelope!.nonce}`);
    const ikm = new Uint8Array(96);
    ikm.set(shared, 0);
    ikm.set(clientEpk, 32);
    ikm.set(serverPub, 64);
    const prk = extract(sha256, ikm, salt);
    const reqKey = expand(sha256, prk, utf8Encode("saas-basics-req-v1"), 32);
    const respKey = expand(sha256, prk, utf8Encode("saas-basics-resp-v1"), 32);

    const reqAad = utf8Encode(`REQ|POST|/api/auth/login|${policy.activeKid}|${tx.envelope!.ts}|${tx.envelope!.nonce}`);
    const decryptedBody = utf8Decode(gcm(reqKey, base64ToBytes(tx.envelope!.iv), reqAad).decrypt(base64ToBytes(tx.envelope!.cipherText)));
    expect(JSON.parse(decryptedBody)).toEqual({ username: "admin", secret: "S3cr3t!" });

    const [qIvB64, qCtB64] = tx.headers["X-Enc-Query"].split(".");
    const qryAad = utf8Encode(`QRY|POST|/api/auth/login|${policy.activeKid}|${tx.envelope!.ts}|${tx.envelope!.nonce}`);
    const decryptedQuery = utf8Decode(gcm(reqKey, base64ToBytes(qIvB64), qryAad).decrypt(base64ToBytes(qCtB64)));
    expect(decryptedQuery).toBe("tenantCode=platform");

    // Server response encrypted with respKey
    const respTs = Date.now();
    const respIv = new Uint8Array(12).fill(3);
    const respAad = utf8Encode(`RESP|POST|/api/auth/login|${policy.activeKid}|${respTs}|${tx.envelope!.nonce}`);
    const respCt = gcm(respKey, respIv, respAad).encrypt(utf8Encode(JSON.stringify({ success: true, data: { token: "ok" } })));

    const decryptedResp = tx.decryptResponse({
      encrypted: true,
      alg: policy.algorithm,
      kid: policy.activeKid,
      iv: bytesToBase64(respIv),
      ts: respTs,
      cipherText: bytesToBase64(respCt)
    });
    expect(decryptedResp).toEqual({ success: true, data: { token: "ok" } });
  });
});
