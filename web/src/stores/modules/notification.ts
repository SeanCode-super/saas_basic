import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { ElNotification } from "element-plus";
import {
  fetchMyNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead
} from "@/api/modules/notify";
import type { NotifyCategory, NotifyMessage } from "@/api/modules/notify";
import { useAuthStore } from "./auth";

export const useNotificationStore = defineStore("notification", () => {
  const authStore = useAuthStore();
  const unreadCount = ref<number>(0);
  const messages = ref<NotifyMessage[]>([]);
  const loading = ref<boolean>(false);
  const wsConnected = ref<boolean>(false);

  let ws: WebSocket | null = null;
  let heartbeatTimer: any = null;
  let reconnectTimer: any = null;

  async function loadUnreadCount() {
    if (!authStore.isAuthenticated) return;
    try {
      const count = await fetchUnreadCount();
      unreadCount.value = count;
    } catch {
      // ignore
    }
  }

  async function loadMessages(category?: string, unreadOnly?: boolean) {
    if (!authStore.isAuthenticated) return;
    loading.value = true;
    try {
      const list = await fetchMyNotifications({ category, unreadOnly });
      messages.value = list;
    } catch {
      // ignore
    } finally {
      loading.value = false;
    }
  }

  function connectWebSocket() {
    if (!authStore.isAuthenticated || !authStore.token) return;
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    try {
      const isHttps = window.location.protocol === "https:";
      const protocol = isHttps ? "wss:" : "ws:";
      const host = window.location.host;
      const url = `${protocol}//${host}/api/ws?token=${encodeURIComponent(authStore.token)}`;

      ws = new WebSocket(url);

      ws.onopen = () => {
        wsConnected.value = true;
        startHeartbeat();
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === "NOTIFICATION_ARRIVED") {
            if (typeof data.unreadCount === "number") {
              unreadCount.value = data.unreadCount;
            } else {
              unreadCount.value += 1;
            }

            if (data.message) {
              messages.value = [data.message, ...messages.value];
              notifyUser(data.message);
            }
          }
        } catch {
          // ignore non-json
        }
      };

      ws.onclose = () => {
        wsConnected.value = false;
        stopHeartbeat();
        scheduleReconnect();
      };

      ws.onerror = () => {
        wsConnected.value = false;
      };
    } catch (err) {
      console.warn("WebSocket initialization error:", err);
      scheduleReconnect();
    }
  }

  function notifyUser(msg: NotifyMessage) {
    let notifyType: "info" | "success" | "warning" | "error" = "info";
    if (msg.category === "WORKFLOW") notifyType = "warning";
    else if (msg.category === "AUDIT") notifyType = "error";
    else if (msg.category === "SYSTEM") notifyType = "success";

    ElNotification({
      title: msg.title,
      message: msg.content,
      type: notifyType,
      duration: 6000,
      position: "top-right"
    });
  }

  function startHeartbeat() {
    stopHeartbeat();
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "PING" }));
      }
    }, 25000);
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function scheduleReconnect() {
    if (!authStore.isAuthenticated) return;
    if (reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connectWebSocket();
    }, 5000);
  }

  function disconnectWebSocket() {
    stopHeartbeat();
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (ws) {
      ws.close();
      ws = null;
    }
    wsConnected.value = false;
  }

  async function markAsRead(id: number) {
    try {
      await markNotificationRead(id);
      const target = messages.value.find((m) => m.id === id);
      if (target && target.readStatus === 0) {
        target.readStatus = 1;
        unreadCount.value = Math.max(0, unreadCount.value - 1);
      }
    } catch {
      // ignore
    }
  }

  async function markAllRead() {
    try {
      await markAllNotificationsRead();
      messages.value.forEach((m) => (m.readStatus = 1));
      unreadCount.value = 0;
    } catch {
      // ignore
    }
  }

  return {
    unreadCount,
    messages,
    loading,
    wsConnected,
    loadUnreadCount,
    loadMessages,
    connectWebSocket,
    disconnectWebSocket,
    markAsRead,
    markAllRead
  };
});
