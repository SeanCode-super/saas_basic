import { x25519 } from "@noble/curves/ed25519.js";
import { gcm } from "@noble/ciphers/aes.js";
import { extract, expand } from "@noble/hashes/hkdf.js";
import { sha256 } from "@noble/hashes/sha2.js";

export type TransportCryptoMode = "OFF" | "OPTIONAL" | "REQUIRED";
export type TransportScopeMode = "ALL_API" | "SENSITIVE_ONLY" | "CUSTOM";

export interface PublicTransportPolicy {
  mode: TransportCryptoMode;
  scopeMode: TransportScopeMode;
  algorithm: string;
  encryptRequest: boolean;
  encryptResponse: boolean;
  encryptQuery: boolean;
  replayProtection: boolean;
  timestampSkewSeconds: number;
  includePaths: string[];
  excludePaths: string[];
  sensitivePaths: string[];
  policyVersion: number;
  activeKid: string;
  publicKey: string;
  fingerprintSha256: string;
  keyIssuedAtEpochMs: number;
  serverTimestampMs: number;
  clockOffsetMs?: number;
}

export interface TransportRequestEnvelope {
  alg: string;
  kid: string;
  epk: string;
  iv: string;
  ts: number;
  nonce: string;
  cipherText: string;
}

export interface TransportResponseEnvelope {
  encrypted: boolean;
  alg: string;
  kid: string;
  iv: string;
  ts: number;
  cipherText: string;
}

export interface TransportCryptoTrace {
  kid: string;
  epk: string;
  ts: number;
  nonce: string;
  path: string;
  method: string;
  requestCipherText?: string;
  requestIv?: string;
  queryCipherHeader?: string;
  responseCipherText?: string;
  responseIv?: string;
  decryptedResponse?: unknown;
}

export interface PreparedTransmission {
  headers: Record<string, string>;
  envelope?: TransportRequestEnvelope;
  trace: TransportCryptoTrace;
  decryptResponse: (rawBody: unknown) => unknown;
}

const INFO_REQ = utf8Encode("saas-basics-req-v1");
const INFO_RESP = utf8Encode("saas-basics-resp-v1");
const B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
const antRegexCache = new Map<string, RegExp>();

export function utf8Encode(text: string): Uint8Array {
  if (typeof TextEncoder !== "undefined") {
    return new TextEncoder().encode(text);
  }
  const out: number[] = [];
  for (let i = 0; i < text.length; i++) {
    let code = text.charCodeAt(i);
    if (code < 0x80) {
      out.push(code);
    } else if (code < 0x800) {
      out.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(++i);
      code = 0x10000 + (((code & 0x3ff) << 10) | (next & 0x3ff));
      out.push(0xf0 | (code >> 18), 0x80 | ((code >> 12) & 0x3f), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    }
  }
  return new Uint8Array(out);
}

export function utf8Decode(bytes: Uint8Array): string {
  if (typeof TextDecoder !== "undefined") {
    return new TextDecoder("utf-8").decode(bytes);
  }
  let res = "";
  let i = 0;
  while (i < bytes.length) {
    const c = bytes[i++];
    if (c < 0x80) {
      res += String.fromCharCode(c);
    } else if (c < 0xe0) {
      const c2 = bytes[i++];
      res += String.fromCharCode(((c & 0x1f) << 6) | (c2 & 0x3f));
    } else if (c < 0xf0) {
      const c2 = bytes[i++];
      const c3 = bytes[i++];
      res += String.fromCharCode(((c & 0x0f) << 12) | ((c2 & 0x3f) << 6) | (c3 & 0x3f));
    } else {
      const c2 = bytes[i++];
      const c3 = bytes[i++];
      const c4 = bytes[i++];
      const cp = (((c & 0x07) << 18) | ((c2 & 0x3f) << 12) | ((c3 & 0x3f) << 6) | (c4 & 0x3f)) - 0x10000;
      res += String.fromCharCode(0xd800 | (cp >> 10), 0xdc00 | (cp & 0x3ff));
    }
  }
  return res;
}

export function bytesToBase64(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i];
    const b = i + 1 < bytes.length ? bytes[i + 1] : 0;
    const c = i + 2 < bytes.length ? bytes[i + 2] : 0;
    const triple = (a << 16) | (b << 8) | c;
    out += B64_CHARS[(triple >> 18) & 0x3f];
    out += B64_CHARS[(triple >> 12) & 0x3f];
    out += i + 1 < bytes.length ? B64_CHARS[(triple >> 6) & 0x3f] : "=";
    out += i + 2 < bytes.length ? B64_CHARS[triple & 0x3f] : "=";
  }
  return out;
}

export function base64ToBytes(b64: string): Uint8Array {
  const clean = b64.replace(/[\r\n\s]/g, "");
  const len = clean.length;
  let padding = 0;
  if (len > 0 && clean[len - 1] === "=") padding++;
  if (len > 1 && clean[len - 2] === "=") padding++;
  const out = new Uint8Array((len * 3) / 4 - padding);
  let p = 0;
  for (let i = 0; i < len; i += 4) {
    const e1 = B64_CHARS.indexOf(clean[i]);
    const e2 = B64_CHARS.indexOf(clean[i + 1]);
    const e3 = clean[i + 2] === "=" ? 0 : B64_CHARS.indexOf(clean[i + 2]);
    const e4 = clean[i + 3] === "=" ? 0 : B64_CHARS.indexOf(clean[i + 3]);
    const triple = (e1 << 18) | (e2 << 12) | (e3 << 6) | e4;
    out[p++] = (triple >> 16) & 0xff;
    if (clean[i + 2] !== "=") out[p++] = (triple >> 8) & 0xff;
    if (clean[i + 3] !== "=") out[p++] = triple & 0xff;
  }
  return out;
}

export function getRandomBytes(length: number): Uint8Array {
  const buf = new Uint8Array(length);
  const gCrypto = typeof globalThis !== "undefined" ? (globalThis as { crypto?: Crypto }).crypto : undefined;
  if (gCrypto && typeof gCrypto.getRandomValues === "function") {
    gCrypto.getRandomValues(buf);
    return buf;
  }
  const wxObj = typeof globalThis !== "undefined"
    ? (globalThis as { wx?: { getRandomValues?: (opts: { length: number }) => { randomValues: ArrayBuffer } } }).wx
    : undefined;
  if (wxObj && typeof wxObj.getRandomValues === "function") {
    const res = wxObj.getRandomValues({ length });
    if (res?.randomValues) {
      return new Uint8Array(res.randomValues);
    }
  }
  for (let i = 0; i < length; i++) {
    buf[i] = Math.floor(Math.random() * 256);
  }
  return buf;
}

function concatBytes(...chunks: Uint8Array[]): Uint8Array {
  const total = chunks.reduce((sum, c) => sum + c.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

function wipeBytes(...arrays: Uint8Array[]): void {
  for (const arr of arrays) {
    arr.fill(0);
  }
}

export function generateX25519KeyPairHex(): { privateKeyHex: string; publicKeyBase64: string } {
  const priv = getRandomBytes(32);
  priv[0] &= 248;
  priv[31] = (priv[31] & 127) | 64;
  const pub = x25519.getPublicKey(priv);
  const privateKeyHex = Array.from(priv, (b) => b.toString(16).padStart(2, "0")).join("");
  priv.fill(0);
  return { privateKeyHex, publicKeyBase64: bytesToBase64(pub) };
}

function randomNonce(): string {
  return bytesToBase64(getRandomBytes(16)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function normalizeApiPathname(baseUrl: string | undefined, requestUrl: string | undefined): string {
  const rawBase = (baseUrl || "").trim();
  const rawUrl = (requestUrl || "").trim();
  let combined = rawUrl;
  if (!/^https?:\/\//i.test(rawUrl)) {
    const cleanBase = rawBase.replace(/\/+$/, "");
    const cleanPath = rawUrl.startsWith("/") ? rawUrl : `/${rawUrl}`;
    combined = `${cleanBase}${cleanPath}`;
  }
  // Strip scheme/host if present and drop ?query or #hash
  const withoutOrigin = combined.replace(/^https?:\/\/[^/]+/i, "");
  const pathOnly = withoutOrigin.split(/[?#]/)[0] || "/";
  return pathOnly.startsWith("/") ? pathOnly : `/${pathOnly}`;
}

export function antMatch(pattern: string, path: string): boolean {
  const trimmed = pattern.trim();
  if (!trimmed) return false;
  let regex = antRegexCache.get(trimmed);
  if (!regex) {
    const normalized = trimmed.replace(/\{[^/}]+\}/g, "*");
    // Convert Ant /**, *, ? patterns safely in a single pass
    const expr = normalized
      .replace(/[.+^${}()|[\]\\]/g, "\\$&")
      .replace(/\/\*\*|\*\*|\*|\?/g, (token) => {
        if (token === "/**") return "(?:/.*)?";
        if (token === "**") return ".*";
        if (token === "*") return "[^/]*";
        return "[^/]";
      });
    regex = new RegExp(`^${expr}$`);
    antRegexCache.set(trimmed, regex);
  }
  return regex.test(path);
}

function matchesAny(path: string, patterns?: string[]): boolean {
  if (!patterns || patterns.length === 0) return false;
  return patterns.some((p) => antMatch(p, path));
}

export function isAlwaysBypassPath(path: string): boolean {
  return (
    path === "/api/security/transport/policy" ||
    path.startsWith("/api/health/") ||
    path.startsWith("/api/ws") ||
    path.startsWith("/api/file/download") ||
    path.startsWith("/api/file/preview") ||
    path.startsWith("/api/file/upload")
  );
}

export function shouldEncryptRequest(
  path: string,
  policy: PublicTransportPolicy | null,
  overrideEncrypt?: boolean
): boolean {
  if (!policy || !policy.publicKey || !policy.activeKid) {
    return false;
  }
  if (overrideEncrypt === false) {
    return false;
  }
  if (isAlwaysBypassPath(path)) {
    return false;
  }
  if (overrideEncrypt === true) {
    return true;
  }
  if (policy.mode === "OFF") {
    return false;
  }
  if (matchesAny(path, policy.excludePaths)) {
    return false;
  }
  if (policy.scopeMode === "SENSITIVE_ONLY") {
    return matchesAny(path, policy.sensitivePaths);
  }
  return matchesAny(path, policy.includePaths);
}

function buildAad(
  direction: "REQ" | "QRY" | "RESP",
  method: string,
  path: string,
  kid: string,
  ts: number,
  nonce: string
): Uint8Array {
  return utf8Encode(`${direction}|${method.toUpperCase()}|${path}|${kid}|${ts}|${nonce}`);
}

export function prepareEncryptedTransmission(params: {
  policy: PublicTransportPolicy;
  method: string;
  path: string;
  body?: unknown;
  queryString?: string;
}): PreparedTransmission {
  const { policy, path } = params;
  const method = (params.method || "GET").toUpperCase();
  const serverPub = base64ToBytes(policy.publicKey);
  const esk = getRandomBytes(32);
  const epk = x25519.getPublicKey(esk);
  const sharedSecret = x25519.getSharedSecret(esk, serverPub);

  const ts = Date.now() + (policy.clockOffsetMs || 0);
  const nonce = randomNonce();
  const salt = utf8Encode(`saas-transport-v1|${ts}|${nonce}`);
  const ikm = concatBytes(sharedSecret, epk, serverPub);
  const prk = extract(sha256, ikm, salt);
  const reqKey = expand(sha256, prk, INFO_REQ, 32);
  const respKey = expand(sha256, prk, INFO_RESP, 32);

  wipeBytes(esk, sharedSecret, ikm, prk);

  const epkB64 = bytesToBase64(epk);
  const headers: Record<string, string> = {
    "X-Transport-Encrypted": "1",
    "X-Enc-Kid": policy.activeKid,
    "X-Enc-Epk": epkB64,
    "X-Enc-Ts": String(ts),
    "X-Enc-Nonce": nonce
  };

  const trace: TransportCryptoTrace = {
    kid: policy.activeKid,
    epk: epkB64,
    ts,
    nonce,
    path,
    method
  };

  if (params.queryString && policy.encryptQuery) {
    const qIv = getRandomBytes(12);
    const qAad = buildAad("QRY", method, path, policy.activeKid, ts, nonce);
    const qCt = gcm(reqKey, qIv, qAad).encrypt(utf8Encode(params.queryString));
    const encQuery = `${bytesToBase64(qIv)}.${bytesToBase64(qCt)}`;
    headers["X-Enc-Query"] = encQuery;
    trace.queryCipherHeader = encQuery;
  }

  let envelope: TransportRequestEnvelope | undefined;
  const hasBody = params.body !== undefined && params.body !== null && params.body !== "";
  if (hasBody && policy.encryptRequest) {
    const jsonText = typeof params.body === "string" ? params.body : JSON.stringify(params.body);
    const reqIv = getRandomBytes(12);
    const reqAad = buildAad("REQ", method, path, policy.activeKid, ts, nonce);
    const reqCt = gcm(reqKey, reqIv, reqAad).encrypt(utf8Encode(jsonText));
    const ivB64 = bytesToBase64(reqIv);
    const ctB64 = bytesToBase64(reqCt);
    envelope = {
      alg: policy.algorithm || "X25519-HKDF-AES-256-GCM",
      kid: policy.activeKid,
      epk: epkB64,
      iv: ivB64,
      ts,
      nonce,
      cipherText: ctB64
    };
    trace.requestIv = ivB64;
    trace.requestCipherText = ctB64;
  }

  wipeBytes(reqKey);

  const decryptResponse = (rawBody: unknown): unknown => {
    if (!rawBody || typeof rawBody !== "object") {
      return rawBody;
    }
    const env = rawBody as Partial<TransportResponseEnvelope>;
    if (!env.encrypted || !env.cipherText || !env.iv || !env.ts) {
      return rawBody;
    }
    trace.responseIv = env.iv;
    trace.responseCipherText = env.cipherText;
    const respAad = buildAad("RESP", method, path, env.kid || policy.activeKid, env.ts, nonce);
    const plainBytes = gcm(respKey, base64ToBytes(env.iv), respAad).decrypt(base64ToBytes(env.cipherText));
    const decodedText = utf8Decode(plainBytes);
    try {
      const parsed = JSON.parse(decodedText);
      trace.decryptedResponse = parsed;
      return parsed;
    } catch {
      trace.decryptedResponse = decodedText;
      return decodedText;
    }
  };

  return { headers, envelope, trace, decryptResponse };
}
