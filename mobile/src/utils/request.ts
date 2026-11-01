/**
 * Antigravity 生产级跨端 HTTP 引擎
 * 核心特性：
 * 1. 双令牌机制（Access Token + 7天 Refresh Token）无感静默续期
 * 2. 401 并发请求锁与订阅队列重放（防止多个请求同时触发 Token 刷新）
 * 3. 多租户上下文 Header (X-Tenant-Id) 自动注入
 * 4. 超时安全控制与网络断网容灾感知
 */

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
  // 避免在登录页死循环跳转
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

  return new Promise((resolve, reject) => {
    uni.request({
      url: (BASE_URL || "") + "/api/auth/refresh-token",
      method: "POST",
      data: { refreshToken },
      header: {
        "Content-Type": "application/json",
        "X-Tenant-Id": uni.getStorageSync("sb_tenant_id") || "1"
      },
      success: (res) => {
        const body = res.data as ApiResponse<any>;
        if (res.statusCode === 200 && body.success && body.data?.accessToken) {
          const newAccessToken = body.data.accessToken;
          uni.setStorageSync("sb_access_token", newAccessToken);
          if (body.data.refreshToken) {
            uni.setStorageSync("sb_refresh_token", body.data.refreshToken);
          }
          resolve(newAccessToken);
        } else {
          reject(new Error(body?.message || "REFRESH_FAILED"));
        }
      },
      fail: (err) => reject(err)
    });
  });
}

export function request<T = any>(options: {
  url: string;
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  data?: any;
  params?: Record<string, any>;
  silent?: boolean;
  timeout?: number;
}): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync("sb_access_token") || "";
    const tenantId = uni.getStorageSync("sb_tenant_id") || "1";

    let finalUrl = options.url.startsWith("http") ? options.url : BASE_URL + options.url;
    if (options.params) {
      const query = Object.entries(options.params)
        .filter(([, v]) => v !== undefined && v !== null && v !== "")
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&");
      if (query) {
        finalUrl += (finalUrl.includes("?") ? "&" : "?") + query;
      }
    }

    uni.request({
      url: finalUrl,
      method: options.method || "GET",
      data: options.data,
      timeout: options.timeout || 12000,
      header: {
        "Content-Type": "application/json",
        "X-Tenant-Id": tenantId,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: async (res) => {
        const body = res.data as ApiResponse<T>;

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
          // 如果本身是登录或刷新接口触发401，直接终止
          if (options.url.includes("/login") || options.url.includes("/refresh-token")) {
            clearSessionAndRedirect();
            reject(new Error("UNAUTHORIZED"));
            return;
          }

          // 启用双令牌静默刷新重放机制
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
            // 已在刷新中，将当前请求排队加入订阅池
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
            // 重放本次请求
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
