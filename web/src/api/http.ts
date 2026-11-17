import axios from "axios";
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/stores/modules/auth";
import { buildPortalLoginUrl, extractSubdomainTenant } from "@/utils/portal-context";
import {
  normalizeApiPathname,
  prepareEncryptedTransmission,
  shouldEncryptRequest,
  type PreparedTransmission,
  type PublicTransportPolicy,
  type TransportCryptoTrace
} from "@/utils/transport-crypto";

declare module "axios" {
  export interface AxiosRequestConfig {
    transportEncrypt?: boolean;
    onCryptoTrace?: (trace: TransportCryptoTrace) => void;
    _retry?: boolean;
    _cryptoRetry?: boolean;
    _cryptoSession?: PreparedTransmission;
    _plainUrl?: string;
    _plainParams?: unknown;
    _plainData?: unknown;
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
});

let cachedPolicy: PublicTransportPolicy | null = null;
let policyLoadedAt = 0;
let inflightPolicyRequest: Promise<PublicTransportPolicy | null> | null = null;
const POLICY_TTL_MS = 60_000;

export async function getTransportPolicy(forceRefresh = false): Promise<PublicTransportPolicy | null> {
  const now = Date.now();
  if (!forceRefresh && cachedPolicy && now - policyLoadedAt < POLICY_TTL_MS) {
    return cachedPolicy;
  }
  if (inflightPolicyRequest && !forceRefresh) {
    return inflightPolicyRequest;
  }
  const base = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/+$/, "");
  inflightPolicyRequest = axios
    .get<{ success: boolean; data: PublicTransportPolicy }>(`${base}/security/transport/policy`, {
      timeout: 5000
    })
    .then((res) => {
      const payload = res.data?.data;
      if (payload && payload.publicKey) {
        payload.clockOffsetMs = payload.serverTimestampMs ? payload.serverTimestampMs - Date.now() : 0;
        cachedPolicy = payload;
        policyLoadedAt = Date.now();
      }
      return cachedPolicy;
    })
    .catch(() => cachedPolicy)
    .finally(() => {
      inflightPolicyRequest = null;
    });
  return inflightPolicyRequest;
}

export function invalidateTransportPolicyCache(): void {
  policyLoadedAt = 0;
}

function syncPolicyFromResponseHeaders(headers: Record<string, unknown> | undefined): void {
  if (!headers) return;
  const rawVer = headers["x-transport-policy-version"] ?? headers["X-Transport-Policy-Version"];
  const rawMode = headers["x-transport-mode"] ?? headers["X-Transport-Mode"];
  if (rawVer !== undefined && cachedPolicy) {
    const serverVer = Number(rawVer);
    if (Number.isFinite(serverVer) && serverVer !== cachedPolicy.policyVersion) {
      invalidateTransportPolicyCache();
      if (rawMode === "OFF" || rawMode === "OPTIONAL" || rawMode === "REQUIRED") {
        cachedPolicy.mode = rawMode;
      }
    }
  }
}

function buildSerializedQuery(url: string | undefined, params: unknown): { cleanUrl: string; queryString: string } {
  const rawUrl = url || "";
  const qIdx = rawUrl.indexOf("?");
  let cleanUrl = qIdx >= 0 ? rawUrl.slice(0, qIdx) : rawUrl;
  const parts: string[] = [];
  if (qIdx >= 0 && qIdx + 1 < rawUrl.length) {
    parts.push(rawUrl.slice(qIdx + 1));
  }
  if (params && typeof params === "object") {
    for (const [k, v] of Object.entries(params as Record<string, unknown>)) {
      if (v === undefined || v === null || v === "") continue;
      if (Array.isArray(v)) {
        for (const item of v) {
          if (item !== undefined && item !== null) {
            parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(item))}`);
          }
        }
      } else {
        parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
      }
    }
  }
  if (!cleanUrl && rawUrl) {
    cleanUrl = rawUrl;
  }
  return { cleanUrl, queryString: parts.join("&") };
}

function isBinaryOrUploadRequest(config: AxiosRequestConfig): boolean {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) return true;
  if (typeof Blob !== "undefined" && config.data instanceof Blob) return true;
  if (typeof ArrayBuffer !== "undefined" && config.data instanceof ArrayBuffer) return true;
  if (config.responseType === "blob" || config.responseType === "arraybuffer") return true;
  const contentType = String((config.headers as Record<string, unknown> | undefined)?.["Content-Type"] ?? "");
  return contentType.toLowerCase().startsWith("multipart/");
}

let isRefreshing = false;
let retryQueue: Array<(token: string) => void> = [];

http.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const authStore = useAuthStore();
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }
  const subdomainTenant = extractSubdomainTenant();
  if (subdomainTenant) {
    config.headers["X-Tenant-Code"] = subdomainTenant;
  }

  if (isBinaryOrUploadRequest(config)) {
    return config;
  }

  const fullPath = normalizeApiPathname(config.baseURL, config._plainUrl ?? config.url);
  if (fullPath === "/api/security/transport/policy") {
    return config;
  }

  const policy = await getTransportPolicy();
  if (shouldEncryptRequest(fullPath, policy, config.transportEncrypt) && policy) {
    // Preserve originals to support transparent retry after server key rotation
    if (config._plainUrl === undefined) {
      config._plainUrl = config.url;
      config._plainParams = config.params;
      config._plainData = config.data;
    }

    const { cleanUrl, queryString } = buildSerializedQuery(config._plainUrl ?? config.url, config._plainParams ?? config.params);
    const transmission = prepareEncryptedTransmission({
      policy,
      method: config.method || "GET",
      path: fullPath,
      body: config._plainData ?? config.data,
      queryString
    });

    for (const [hk, hv] of Object.entries(transmission.headers)) {
      config.headers.set(hk, hv);
    }
    if (queryString && policy.encryptQuery) {
      config.url = cleanUrl;
      config.params = undefined;
    }
    if (transmission.envelope) {
      config.data = transmission.envelope;
      config.headers.set("Content-Type", "application/json");
    }
    config._cryptoSession = transmission;
  }

  return config;
});

function tryDecryptAxiosPayload(config: AxiosRequestConfig | undefined, data: unknown, headers: Record<string, unknown> | undefined): unknown {
  syncPolicyFromResponseHeaders(headers);
  const encryptedHeader = headers?.["x-transport-encrypted"] ?? headers?.["X-Transport-Encrypted"];
  const looksEncrypted =
    encryptedHeader === "1" ||
    (Boolean(data) && typeof data === "object" && (data as { encrypted?: boolean }).encrypted === true);
  if (looksEncrypted && config?._cryptoSession) {
    const plain = config._cryptoSession.decryptResponse(data);
    config.onCryptoTrace?.(config._cryptoSession.trace);
    return plain;
  }
  if (config?._cryptoSession && config.onCryptoTrace) {
    config.onCryptoTrace(config._cryptoSession.trace);
  }
  return data;
}

http.interceptors.response.use(
  (response) => {
    response.data = tryDecryptAxiosPayload(response.config, response.data, response.headers as Record<string, unknown>);
    return response;
  },
  async (error) => {
    const originalRequest = error?.config as AxiosRequestConfig | undefined;
    if (error?.response) {
      error.response.data = tryDecryptAxiosPayload(
        originalRequest,
        error.response.data,
        error.response.headers as Record<string, unknown>
      );
    }

    const responseCode = error?.response?.data?.code;
    const status = error?.response?.status;

    // 密钥轮换/策略强制升级：强制拉取最新策略与公钥并无感重试一次
    if (
      originalRequest &&
      !originalRequest._cryptoRetry &&
      (status === 428 ||
        responseCode === "TRANSPORT_KEY_EXPIRED" ||
        responseCode === "TRANSPORT_ENCRYPTION_REQUIRED" ||
        responseCode === "TRANSPORT_TIMESTAMP_EXPIRED")
    ) {
      originalRequest._cryptoRetry = true;
      originalRequest._cryptoSession = undefined;
      if (originalRequest._plainUrl !== undefined) {
        originalRequest.url = originalRequest._plainUrl;
        originalRequest.params = originalRequest._plainParams;
        originalRequest.data = originalRequest._plainData;
      }
      invalidateTransportPolicyCache();
      await getTransportPolicy(true);
      return http(originalRequest);
    }

    // 若被管理员强制踢出或租户停用，立即清除会话并重定向
    if (responseCode === "AUTH_SESSION_KICKED" || responseCode === "AUTH_USER_DISABLED" || responseCode === "AUTH_TENANT_DISABLED") {
      clearAuthAndRedirect();
      return Promise.reject(error);
    }

    // 401 触发无感刷新
    if ((status === 401 || responseCode === "AUTH_UNAUTHORIZED") && originalRequest && !originalRequest._retry) {
      if (originalRequest.url?.includes("/auth/refresh-token") || originalRequest.url?.includes("/auth/login")) {
        clearAuthAndRedirect();
        return Promise.reject(error);
      }

      const storedRefreshToken = localStorage.getItem("sb-refresh-token");
      if (!storedRefreshToken) {
        clearAuthAndRedirect();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          retryQueue.push((newToken: string) => {
            if (!originalRequest.headers) originalRequest.headers = {};
            (originalRequest.headers as Record<string, string>).Authorization = `Bearer ${newToken}`;
            resolve(http(originalRequest));
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // 使用受控 http 实例发刷新请求，自动遵循当前传输加密策略
        const refreshRes = await http.post("/auth/refresh-token", { refreshToken: storedRefreshToken }, { _retry: true });
        const newAccessToken = refreshRes.data?.data?.accessToken;
        if (newAccessToken) {
          const authStore = useAuthStore();
          authStore.token = newAccessToken;
          localStorage.setItem("sb-access-token", newAccessToken);

          retryQueue.forEach((cb) => cb(newAccessToken));
          retryQueue = [];

          if (!originalRequest.headers) originalRequest.headers = {};
          (originalRequest.headers as Record<string, string>).Authorization = `Bearer ${newAccessToken}`;
          return http(originalRequest);
        } else {
          clearAuthAndRedirect();
          return Promise.reject(error);
        }
      } catch (refreshErr) {
        retryQueue = [];
        clearAuthAndRedirect();
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

function clearAuthAndRedirect() {
  localStorage.removeItem("sb-access-token");
  localStorage.removeItem("sb-refresh-token");
  localStorage.removeItem("sb-current-user");
  if (window.location.pathname !== "/login") {
    const redirect = `${window.location.pathname}${window.location.search}`;
    window.location.href = buildPortalLoginUrl(redirect);
  }
}
