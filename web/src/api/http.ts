import axios from "axios";
import { useAuthStore } from "@/stores/modules/auth";
import { buildPortalLoginUrl } from "@/utils/portal-context";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
});

let isRefreshing = false;
let retryQueue: Array<(token: string) => void> = [];

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config;
    const responseCode = error?.response?.data?.code;
    const status = error?.response?.status;

    // 若被管理员强制踢出或租户停用，立即清除会话并重定向
    if (responseCode === "AUTH_SESSION_KICKED" || responseCode === "AUTH_USER_DISABLED" || responseCode === "AUTH_TENANT_DISABLED") {
      clearAuthAndRedirect();
      return Promise.reject(error);
    }

    // 401 触发无感刷新
    if ((status === 401 || responseCode === "AUTH_UNAUTHORIZED") && originalRequest && !originalRequest._retry) {
      if (originalRequest.url?.includes("/api/auth/refresh-token") || originalRequest.url?.includes("/api/auth/login")) {
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
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            resolve(http(originalRequest));
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshRes = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL || ""}/api/auth/refresh-token`,
          { refreshToken: storedRefreshToken }
        );
        const newAccessToken = refreshRes.data?.data?.accessToken;
        if (newAccessToken) {
          const authStore = useAuthStore();
          authStore.token = newAccessToken;
          localStorage.setItem("sb-access-token", newAccessToken);

          retryQueue.forEach((cb) => cb(newAccessToken));
          retryQueue = [];

          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
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
