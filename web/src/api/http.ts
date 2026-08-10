import axios from "axios";
import { useAuthStore } from "@/stores/modules/auth";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();

  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }

  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const responseCode = error?.response?.data?.code;
    if (error?.response?.status === 401 || responseCode === "AUTH_UNAUTHORIZED") {
      localStorage.removeItem("sb-access-token");
      localStorage.removeItem("sb-current-user");
      if (window.location.pathname !== "/login") {
        const redirect = `${window.location.pathname}${window.location.search}`;
        window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
      }
    }
    return Promise.reject(error);
  }
);
