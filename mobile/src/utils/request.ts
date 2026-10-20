/**
 * Antigravity 跨端移动端统一 HTTP 请求适配器
 * 支持多租户 Header 注入、Bearer Token 拦截与 401 自动重连
 */

const BASE_URL = "http://localhost:8080";

export interface ApiResponse<T = any> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export function request<T = any>(options: {
  url: string;
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  data?: any;
  params?: Record<string, any>;
}): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync("sb_access_token") || "";
    const tenantId = uni.getStorageSync("sb_tenant_id") || "1";

    let finalUrl = options.url.startsWith("http") ? options.url : BASE_URL + options.url;
    if (options.params) {
      const query = Object.entries(options.params)
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
      header: {
        "Content-Type": "application/json",
        "X-Tenant-Id": tenantId,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        const body = res.data as ApiResponse<T>;
        if (res.statusCode === 200) {
          if (body.success) {
            resolve(body.data);
          } else {
            uni.showToast({ title: body.message || "请求失败", icon: "none" });
            reject(new Error(body.message));
          }
        } else if (res.statusCode === 401) {
          uni.removeStorageSync("sb_access_token");
          uni.showToast({ title: "登录已过期，请重新登录", icon: "none" });
          uni.navigateTo({ url: "/pages/login/login" });
          reject(new Error("UNAUTHORIZED"));
        } else {
          uni.showToast({ title: body.message || `网络异常: ${res.statusCode}`, icon: "none" });
          reject(new Error(body.message || String(res.statusCode)));
        }
      },
      fail: (err) => {
        uni.showToast({ title: "网络连接失败，请检查服务状态", icon: "none" });
        reject(err);
      }
    });
  });
}
