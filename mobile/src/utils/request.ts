/**
 * Antigravity 生产级跨端 HTTP 引擎
 * 核心特性：
 * 1. 双令牌机制（Access Token + 7天 Refresh Token）无感静默续期
 * 2. 401 并发请求锁与订阅队列重放（防止多个请求同时触发 Token 刷新）
 * 3. 多租户上下文 Header (X-Tenant-Id) 自动注入
 * 4. 可控传输加密（X25519 + HKDF-SHA256 + AES-256-GCM 双向加密与动态策略自动同步）
 */
import {
  normalizeApiPathname,
  prepareEncryptedTransmission,
  shouldEncryptRequest,
  type PreparedTransmission,
  type PublicTransportPolicy
} from "./transport-crypto";

// #ifdef H5
const BASE_URL = "";
// #endif
// #ifndef H5
const BASE_URL = "http://localhost:8080";
// #endif

export interface ApiResponse<T = any> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

// 刷新锁与重放队列
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

let cachedPolicy: PublicTransportPolicy | null = null;
let policyLoadedAt = 0;
let inflightPolicyReq: Promise<PublicTransportPolicy | null> | null = null;
const POLICY_TTL_MS = 60_000;

function readHeaderIgnoreCase(headers: Record<string, any> | undefined, target: string): string | undefined {
  if (!headers) return undefined;
  const lower = target.toLowerCase();
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === lower) {
      return headers[key] != null ? String(headers[key]) : undefined;
    }
  }
  return undefined;
}

export async function getMobileTransportPolicy(forceRefresh = false): Promise<PublicTransportPolicy | null> {
  const now = Date.now();
  if (!forceRefresh && cachedPolicy && now - policyLoadedAt < POLICY_TTL_MS) {
    return cachedPolicy;
  }
  if (inflightPolicyReq && !forceRefresh) {
    return inflightPolicyReq;
  }
  inflightPolicyReq = new Promise<PublicTransportPolicy | null>((resolve) => {
    uni.request({
      url: (BASE_URL || "") + "/api/security/transport/policy",
      method: "GET",
      timeout: 5000,
      success: (res) => {
        const body = res.data as ApiResponse<PublicTransportPolicy>;
        if (res.statusCode === 200 && body?.success && body.data?.publicKey) {
          body.data.clockOffsetMs = body.data.serverTimestampMs ? body.data.serverTimestampMs - Date.now() : 0;
          cachedPolicy = body.data;
          policyLoadedAt = Date.now();
        }
        resolve(cachedPolicy);
      },
      fail: () => resolve(cachedPolicy)
    });
  }).finally(() => {
    inflightPolicyReq = null;
  });
  return inflightPolicyReq;
}

export function invalidateMobileTransportPolicy(): void {
  policyLoadedAt = 0;
}

function syncPolicyVersion(headers: Record<string, any> | undefined): void {
  const ver = readHeaderIgnoreCase(headers, "x-transport-policy-version");
  if (ver && cachedPolicy && Number(ver) !== cachedPolicy.policyVersion) {
    invalidateMobileTransportPolicy();
  }
}

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function clearSessionAndRedirect() {
  uni.removeStorageSync("sb_access_token");
  uni.removeStorageSync("sb_refresh_token");
  uni.removeStorageSync("sb_current_user");
  const pages = getCurrentPages();
  const currentRoute = pages.length ? pages[pages.length - 1].route : "";
  if (!currentRoute?.includes("pages/login/login")) {
    uni.reLaunch({ url: "/pages/login/login" });
  }
}

async function doRefreshToken(): Promise<string> {
  const refreshToken = uni.getStorageSync("sb_refresh_token");
  if (!refreshToken) {
    throw new Error("NO_REFRESH_TOKEN");
  }
  const data = await request<{ accessToken: string; refreshToken?: string }>({
    url: "/api/auth/refresh-token",
    method: "POST",
    data: { refreshToken },
    silent: true,
    _skipAuthRetry: true
  });
  if (!data?.accessToken) {
    throw new Error("REFRESH_FAILED");
  }
  uni.setStorageSync("sb_access_token", data.accessToken);
  if (data.refreshToken) {
    uni.setStorageSync("sb_refresh_token", data.refreshToken);
  }
  return data.accessToken;
}

function buildQueryString(params?: Record<string, any>): string {
  if (!params) return "";
  return Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== "")
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");
}

function splitUrlAndQuery(rawUrl: string, params?: Record<string, any>): { cleanUrl: string; queryStr: string } {
  const qIdx = rawUrl.indexOf("?");
  const cleanUrl = qIdx >= 0 ? rawUrl.slice(0, qIdx) : rawUrl;
  const parts: string[] = [];
  if (qIdx >= 0 && qIdx + 1 < rawUrl.length) {
    parts.push(rawUrl.slice(qIdx + 1));
  }
  const paramStr = buildQueryString(params);
  if (paramStr) {
    parts.push(paramStr);
  }
  return { cleanUrl, queryStr: parts.join("&") };
}

export async function request<T = any>(options: {
  url: string;
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  data?: any;
  params?: Record<string, any>;
  silent?: boolean;
  timeout?: number;
  transportEncrypt?: boolean;
  _cryptoRetry?: boolean;
  _skipAuthRetry?: boolean;
}): Promise<T> {
  const policy = await getMobileTransportPolicy();
  const rawFullUrl = options.url.startsWith("http") ? options.url : BASE_URL + options.url;
  const { cleanUrl, queryStr } = splitUrlAndQuery(rawFullUrl, options.params);
  const fullPath = normalizeApiPathname(BASE_URL, cleanUrl);

  let session: PreparedTransmission | null = null;
  let finalUrl = cleanUrl;
  let finalData = options.data;
  const extraHeaders: Record<string, string> = {};

  if (shouldEncryptRequest(fullPath, policy, options.transportEncrypt) && policy) {
    session = prepareEncryptedTransmission({
      policy,
      method: options.method || "GET",
      path: fullPath,
      body: options.data,
      queryString: queryStr
    });
    Object.assign(extraHeaders, session.headers);
    if (session.envelope) {
      finalData = session.envelope;
    }
    if (queryStr && !policy.encryptQuery) {
      finalUrl += `?${queryStr}`;
    }
  } else if (queryStr) {
    finalUrl += `?${queryStr}`;
  }

  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync("sb_access_token") || "";
    const tenantId = uni.getStorageSync("sb_tenant_id") || "1";
    const tenantCode = uni.getStorageSync("sb_tenant_code") || "";

    uni.request({
      url: finalUrl,
      method: (options.method || "GET") as UniApp.RequestOptions["method"],
      data: finalData,
      timeout: options.timeout || 12000,
      header: {
        "Content-Type": "application/json",
        "X-Tenant-Id": tenantId,
        ...(tenantCode ? { "X-Tenant-Code": tenantCode } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...extraHeaders
      },
      success: async (res) => {
        syncPolicyVersion(res.header as Record<string, any>);
        let rawBody = res.data as any;
        const encHeader = readHeaderIgnoreCase(res.header as Record<string, any>, "x-transport-encrypted");
        if (session && (encHeader === "1" || rawBody?.encrypted === true)) {
          try {
            rawBody = session.decryptResponse(rawBody);
          } catch (decryptErr) {
            if (!options.silent) {
              uni.showToast({ title: "响应密文解密失败", icon: "none" });
            }
            reject(decryptErr);
            return;
          }
        }

        const body = rawBody as ApiResponse<T>;
        const code = body?.code;

        // 密钥过期或策略强制加密 -> 刷新策略与公钥并重试一次
        if (
          !options._cryptoRetry &&
          (res.statusCode === 428 ||
            code === "TRANSPORT_KEY_EXPIRED" ||
            code === "TRANSPORT_ENCRYPTION_REQUIRED" ||
            code === "TRANSPORT_TIMESTAMP_EXPIRED")
        ) {
          invalidateMobileTransportPolicy();
          await getMobileTransportPolicy(true);
          request<T>({ ...options, _cryptoRetry: true }).then(resolve).catch(reject);
          return;
        }

        if (res.statusCode === 200) {
          if (body.success) {
            resolve(body.data);
          } else {
            if (!options.silent) {
              uni.showToast({ title: body.message || "请求失败", icon: "none" });
            }
            reject(new Error(body.message));
          }
        } else if (res.statusCode === 401) {
          if (options._skipAuthRetry || options.url.includes("/login") || options.url.includes("/refresh-token")) {
            clearSessionAndRedirect();
            reject(new Error("UNAUTHORIZED"));
            return;
          }

          const refreshToken = uni.getStorageSync("sb_refresh_token");
          if (!refreshToken) {
            clearSessionAndRedirect();
            if (!options.silent) {
              uni.showToast({ title: "登录凭证已失效，请重新登录", icon: "none" });
            }
            reject(new Error("UNAUTHORIZED"));
            return;
          }

          if (isRefreshing) {
            subscribeTokenRefresh(() => {
              request<T>(options).then(resolve).catch(reject);
            });
            return;
          }

          isRefreshing = true;
          try {
            const newToken = await doRefreshToken();
            isRefreshing = false;
            onRefreshed(newToken);
            request<T>(options).then(resolve).catch(reject);
          } catch (refreshErr) {
            isRefreshing = false;
            refreshSubscribers = [];
            clearSessionAndRedirect();
            if (!options.silent) {
              uni.showToast({ title: "会话已过期，请重新登录", icon: "none" });
            }
            reject(refreshErr);
          }
        } else if (res.statusCode === 403) {
          if (!options.silent) {
            uni.showToast({ title: body?.message || "暂无此功能操作权限", icon: "none" });
          }
          reject(new Error(body?.message || "FORBIDDEN"));
        } else {
          if (!options.silent) {
            uni.showToast({ title: body?.message || `服务异常 (${res.statusCode})`, icon: "none" });
          }
          reject(new Error(body?.message || String(res.statusCode)));
        }
      },
      fail: (err) => {
        if (!options.silent) {
          uni.showToast({ title: "网络异常，请检查网络连接", icon: "none" });
        }
        reject(err);
      }
    });
  });
}

export function resolveMobileFileUrl(url?: string | null): string {
  if (!url) return "";
  const full = url.startsWith("http") ? url : BASE_URL + url;
  if (!full.includes("/api/file/raw-download/") || full.includes("token=")) {
    return full;
  }
  const token = uni.getStorageSync("sb_access_token") || "";
  if (!token) return full;
  const sep = full.includes("?") ? "&" : "?";
  return `${full}${sep}token=${encodeURIComponent(token)}`;
}

export function uploadFile<T = any>(options: {
  url: string;
  filePath: string;
  name?: string;
  formData?: Record<string, string>;
}): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync("sb_access_token") || "";
    const tenantId = uni.getStorageSync("sb_tenant_id") || "1";
    const tenantCode = uni.getStorageSync("sb_tenant_code") || "";
    const fullUrl = options.url.startsWith("http") ? options.url : BASE_URL + options.url;

    uni.uploadFile({
      url: fullUrl,
      filePath: options.filePath,
      name: options.name || "file",
      formData: options.formData,
      header: {
        "X-Tenant-Id": tenantId,
        ...(tenantCode ? { "X-Tenant-Code": tenantCode } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        let parsed: ApiResponse<T> | null = null;
        try {
          parsed = typeof res.data === "string" ? JSON.parse(res.data) : (res.data as ApiResponse<T>);
        } catch {
          parsed = null;
        }
        if (res.statusCode === 200 && parsed?.success) {
          resolve(parsed.data);
          return;
        }
        const msg = parsed?.message || `上传失败 (${res.statusCode})`;
        uni.showToast({ title: msg, icon: "none" });
        reject(new Error(msg));
      },
      fail: (err) => {
        uni.showToast({ title: "上传请求失败，请检查网络", icon: "none" });
        reject(err);
      }
    });
  });
}
