<script setup lang="ts">
import { ref, reactive, nextTick } from "vue";
import { ElMessage } from "element-plus";
import {
  ChatLineRound,
  Delete,
  DocumentCopy,
  Loading,
  Position,
  Cpu,
  Star,
  RefreshRight
} from "@element-plus/icons-vue";

interface ChatMessageItem {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: string;
}

const visible = ref(false);
const loading = ref(false);
const selectedModel = ref("deepseek-chat");
const inputPrompt = ref("");
const chatScrollRef = ref<HTMLElement | null>(null);

const models = [
  { id: "deepseek-chat", name: "DeepSeek-V3 (通用旗舰)", vendor: "DeepSeek" },
  { id: "deepseek-reasoner", name: "DeepSeek-R1 (深度思考)", vendor: "DeepSeek" },
  { id: "gpt-4o-mini", name: "OpenAI GPT-4o Mini (极速)", vendor: "OpenAI" },
  { id: "qwen-max", name: "通义千问 Qwen-Max", vendor: "Alibaba" }
];

const quickPrompts = [
  "对比芋道 ruoyi-vue-pro 的核心优势是什么？",
  "为我设计一套企业级合同审批业务表 SQL",
  "如何使用 FastExcel 实现百万级数据防爆导出？",
  "解释 Antigravity SaaS 的双 Token 无感静默续期原理"
];

const messages = ref<ChatMessageItem[]>([
  {
    id: "welcome",
    role: "assistant",
    content: "您好！我是 **Antigravity AI 平台智能专家**。\n\n我已深度接入 DeepSeek-V3 / R1 与 OpenAI 模型中台，可协助您进行 **业务架构设计**、**CRUD 代码自动生成**、**慢 SQL 与性能调优** 以及 **第三方支付/通信集成**。\n\n请在下方输入您的问题或点击快捷示例开始交流！",
    timestamp: new Date().toLocaleTimeString()
  }
]);

function openDrawer() {
  visible.value = true;
  scrollToBottom();
}

function clearChat() {
  messages.value = [
    {
      id: "welcome",
      role: "assistant",
      content: "对话历史已清空。请随时提出新的技术或业务问题！",
      timestamp: new Date().toLocaleTimeString()
    }
  ];
}

function scrollToBottom() {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight;
    }
  });
}

function usePrompt(p: string) {
  inputPrompt.value = p;
  handleSend();
}

function copyContent(text: string) {
  navigator.clipboard.writeText(text);
  ElMessage.success("已复制到剪贴板");
}

async function handleSend() {
  const content = inputPrompt.value.trim();
  if (!content || loading.value) return;

  const userMsgId = `user_${Date.now()}`;
  messages.value.push({
    id: userMsgId,
    role: "user",
    content,
    timestamp: new Date().toLocaleTimeString()
  });

  inputPrompt.value = "";
  loading.value = true;
  scrollToBottom();

  const assistantMsgId = `ai_${Date.now()}`;
  const assistantMsg = reactive<ChatMessageItem>({
    id: assistantMsgId,
    role: "assistant",
    content: "",
    timestamp: new Date().toLocaleTimeString()
  });
  messages.value.push(assistantMsg);

  try {
    const token = localStorage.getItem("sb_access_token") || "";
    const response = await fetch("/api/ai/chat/stream", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : ""
      },
      body: JSON.stringify({
        model: selectedModel.value,
        messages: messages.value.filter((m) => m.role !== "system").map((m) => ({ role: m.role, content: m.content })),
        stream: true
      })
    });

    if (!response.ok || !response.body) {
      // Fallback to non-streaming chat
      const fallbackRes = await fetch("/api/ai/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? `Bearer ${token}` : ""
        },
        body: JSON.stringify({
          model: selectedModel.value,
          messages: [{ role: "user", content }]
        })
      });
      const data = await fallbackRes.json();
      assistantMsg.content = data?.data?.reply || data?.message || "请求失败，请稍后重试。";
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let done = false;

    while (!done) {
      const { value, done: readerDone } = await reader.read();
      done = readerDone;
      if (value) {
        const text = decoder.decode(value, { stream: true });
        const lines = text.split("\n");
        for (const line of lines) {
          if (line.startsWith("data:")) {
            const rawData = line.substring(5).trim();
            if (!rawData) continue;
            try {
              const parsed = JSON.parse(rawData);
              if (parsed.content) {
                assistantMsg.content += parsed.content;
                scrollToBottom();
              }
            } catch {
              // ignore partial lines
            }
          }
        }
      }
    }
  } catch (err: any) {
    assistantMsg.content += `\n\n[请求异常: ${err?.message || "网络中断"}]`;
  } finally {
    loading.value = false;
    scrollToBottom();
  }
}

defineExpose({
  openDrawer
});
</script>

<template>
  <el-drawer
    v-model="visible"
    title="Antigravity AI 智能中台助手"
    direction="rtl"
    size="560px"
    class="ai-assistant-drawer"
    :with-header="false"
  >
    <div class="ai-container">
      <!-- Header -->
      <div class="ai-header">
        <div class="ai-title-wrap">
          <div class="ai-badge-icon">
            <el-icon><Cpu /></el-icon>
          </div>
          <div>
            <div class="ai-title">AI 大模型智能中心</div>
            <div class="ai-subtitle">聚合 DeepSeek-V3 / R1 / OpenAI / 通义千问</div>
          </div>
        </div>
        <div class="ai-header-actions">
          <el-select v-model="selectedModel" size="small" style="width: 170px">
            <el-option
              v-for="m in models"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
          <el-button link :icon="Delete" @click="clearChat" title="清空对话" />
        </div>
      </div>

      <!-- Quick prompts -->
      <div class="quick-prompts-bar">
        <span class="prompt-hint"><el-icon><Star /></el-icon> 快捷提问:</span>
        <div class="prompt-chips">
          <span
            v-for="(p, idx) in quickPrompts"
            :key="idx"
            class="prompt-chip"
            @click="usePrompt(p)"
          >
            {{ p }}
          </span>
        </div>
      </div>

      <!-- Message History -->
      <div ref="chatScrollRef" class="ai-chat-body">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="chat-bubble-wrap"
          :class="msg.role"
        >
          <div class="avatar-col">
            <div class="bubble-avatar" :class="msg.role">
              {{ msg.role === 'user' ? 'ME' : 'AI' }}
            </div>
          </div>
          <div class="content-col">
            <div class="bubble-info">
              <span class="sender-name">{{ msg.role === 'user' ? '当前管理员' : selectedModel }}</span>
              <span class="bubble-time">{{ msg.timestamp }}</span>
              <el-button
                v-if="msg.role === 'assistant'"
                link
                size="small"
                :icon="DocumentCopy"
                class="copy-btn"
                @click="copyContent(msg.content)"
              />
            </div>
            <div class="bubble-text markdown-render" v-html="msg.content.replace(/\n/g, '<br/>')" />
          </div>
        </div>

        <div v-if="loading" class="ai-typing-indicator">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 思考与流式推理中...</span>
        </div>
      </div>

      <!-- Input Footer -->
      <div class="ai-footer">
        <div class="input-wrap">
          <el-input
            v-model="inputPrompt"
            type="textarea"
            :rows="3"
            placeholder="请输入技术或业务咨询问题 (按 Enter 发送)..."
            resize="none"
            @keydown.enter.prevent="handleSend"
          />
          <div class="input-actions">
            <span class="token-tip">Shift+Enter 换行</span>
            <el-button
              type="primary"
              :icon="Position"
              :loading="loading"
              @click="handleSend"
            >
              发送提问
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.ai-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color-page, #f8fafc);
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light, #e2e8f0);
}

.ai-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-badge-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.ai-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary, #0f172a);
}

.ai-subtitle {
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
}

.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quick-prompts-bar {
  padding: 10px 16px;
  background: var(--el-fill-color-light, #f1f5f9);
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 1px solid var(--el-border-color-lighter, #e2e8f0);
}

.prompt-hint {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #64748b);
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 4px;
}

.prompt-chips {
  display: flex;
  gap: 8px;
  flex-wrap: nowrap;
}

.prompt-chip {
  font-size: 11px;
  padding: 3px 10px;
  background: #fff;
  border: 1px solid var(--el-border-color, #cbd5e1);
  border-radius: 12px;
  cursor: pointer;
  white-space: nowrap;
  color: var(--el-text-color-regular, #334155);
  transition: all 0.2s;
}

.prompt-chip:hover {
  background: #4f46e5;
  color: #fff;
  border-color: #4f46e5;
}

.ai-chat-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-bubble-wrap {
  display: flex;
  gap: 10px;
}

.chat-bubble-wrap.user {
  flex-direction: row-reverse;
}

.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}

.bubble-avatar.assistant {
  background: linear-gradient(135deg, #4f46e5, #06b6d4);
}

.bubble-avatar.user {
  background: #3b82f6;
}

.content-col {
  max-width: 82%;
  display: flex;
  flex-direction: column;
}

.bubble-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary, #94a3b8);
}

.chat-bubble-wrap.user .bubble-info {
  justify-content: flex-end;
}

.bubble-text {
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-bubble-wrap.assistant .bubble-text {
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  color: var(--el-text-color-primary, #1e293b);
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);
}

.chat-bubble-wrap.user .bubble-text {
  background: #4f46e5;
  color: #fff;
}

.ai-typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6366f1;
  padding-left: 42px;
}

.ai-footer {
  padding: 14px 16px;
  background: var(--el-bg-color, #fff);
  border-top: 1px solid var(--el-border-color-light, #e2e8f0);
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.token-tip {
  font-size: 11px;
  color: var(--el-text-color-secondary, #94a3b8);
}
</style>
