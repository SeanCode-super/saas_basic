<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Check,
  Delete,
  Plus,
  Promotion,
  RefreshRight,
  Service
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import {
  chatWithAgent,
  deleteAgent,
  fetchAgents,
  fetchAgentTools,
  fetchKnowledgeBases,
  fetchPublicAiModels,
  saveAgent,
  type AiAgentRow,
  type AiToolOption,
  type KnowledgeBaseRow,
  type KnowledgeCitation
} from "@/api/modules/ai";

interface ChatBubble {
  role: "user" | "assistant";
  text: string;
  engine?: string;
  latencyMs?: number;
  toolTraces?: string[];
  citations?: KnowledgeCitation[];
}

const loading = ref(false);
const saving = ref(false);
const sending = ref(false);
const agents = ref<AiAgentRow[]>([]);
const tools = ref<AiToolOption[]>([]);
const kbs = ref<KnowledgeBaseRow[]>([]);
const modelOptions = ref<Array<{ id: string; name: string }>>([]);

const selectedAgentId = ref<number | undefined>(undefined);
const form = reactive({
  id: undefined as number | undefined,
  agentCode: "",
  agentName: "",
  avatar: "🤖",
  roleTitle: "",
  systemPrompt: "",
  modelId: "deepseek-chat",
  kbIds: [] as number[],
  toolCodes: [] as string[],
  channelTypes: "CONSOLE,COMMAND_PALETTE",
  temperature: 0.3,
  welcomeMessage: "",
  status: "PUBLISHED",
  remark: ""
});

const sessionId = ref<number | undefined>(undefined);
const chatInput = ref("");
const messages = ref<ChatBubble[]>([]);

const activeAgent = computed(() => agents.value.find((a) => a.id === selectedAgentId.value));

function applyAgentToForm(agent: AiAgentRow) {
  selectedAgentId.value = agent.id;
  form.id = agent.id;
  form.agentCode = agent.agentCode;
  form.agentName = agent.agentName;
  form.avatar = agent.avatar || "🤖";
  form.roleTitle = agent.roleTitle;
  form.systemPrompt = agent.systemPrompt;
  form.modelId = agent.modelId || "deepseek-chat";
  form.kbIds = [...(agent.kbIds || [])];
  form.toolCodes = [...(agent.toolCodes || [])];
  form.channelTypes = agent.channelTypes || "CONSOLE";
  form.temperature = agent.temperature ?? 0.3;
  form.welcomeMessage = agent.welcomeMessage || "";
  form.status = agent.status || "PUBLISHED";
  form.remark = agent.remark || "";
  sessionId.value = undefined;
  messages.value = agent.welcomeMessage
    ? [{ role: "assistant", text: agent.welcomeMessage, engine: "WELCOME" }]
    : [];
}

function resetNewAgent() {
  selectedAgentId.value = undefined;
  form.id = undefined;
  form.agentCode = "";
  form.agentName = "";
  form.avatar = "✨";
  form.roleTitle = "企业业务专属智能体";
  form.systemPrompt = "你是企业数字化专属智能助手。请依据绑定知识库与实时数据工具回答问题并保持严谨专业。";
  form.modelId = "deepseek-chat";
  form.kbIds = [];
  form.toolCodes = ["knowledge_search", "nl2sql_query"];
  form.channelTypes = "CONSOLE,COMMAND_PALETTE";
  form.temperature = 0.25;
  form.welcomeMessage = "您好！请问有什么可以协助您分析或排查？";
  form.status = "PUBLISHED";
  form.remark = "";
  sessionId.value = undefined;
  messages.value = [{ role: "assistant", text: form.welcomeMessage, engine: "WELCOME" }];
}

async function loadAll() {
  loading.value = true;
  try {
    const [agentRows, toolRows, kbRows, models] = await Promise.all([
      fetchAgents(),
      fetchAgentTools(),
      fetchKnowledgeBases(),
      fetchPublicAiModels().catch(() => [])
    ]);
    agents.value = agentRows;
    tools.value = toolRows;
    kbs.value = kbRows;
    modelOptions.value = models.map((m) => ({ id: m.id, name: `${m.name} (${m.vendor})` }));

    if (agentRows.length > 0 && !selectedAgentId.value) {
      applyAgentToForm(agentRows[0]);
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "加载智能体工坊失败");
  } finally {
    loading.value = false;
  }
}

async function handleSaveAgent() {
  if (!form.agentName.trim() || !form.systemPrompt.trim()) {
    ElMessage.warning("智能体名称和人设提示词不能为空");
    return;
  }
  saving.value = true;
  try {
    const saved = await saveAgent({ ...form });
    ElMessage.success(`智能体「${saved.agentName}」编排已保存并发布`);
    await loadAll();
    const matched = agents.value.find((a) => a.id === saved.id);
    if (matched) applyAgentToForm(matched);
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "保存智能体失败");
  } finally {
    saving.value = false;
  }
}

async function handleDeleteAgent(agent: AiAgentRow) {
  try {
    await ElMessageBox.confirm(`确认删除智能体「${agent.agentName}」吗？`, "删除确认", { type: "warning" });
    await deleteAgent(agent.id);
    ElMessage.success("已删除智能体");
    selectedAgentId.value = undefined;
    await loadAll();
  } catch {
    // cancelled
  }
}

async function handleSendChat() {
  if (!selectedAgentId.value) {
    ElMessage.warning("请先保存或在左侧选择一个已发布的智能体进行联调");
    return;
  }
  const text = chatInput.value.trim();
  if (!text) return;
  messages.value.push({ role: "user", text });
  chatInput.value = "";
  sending.value = true;
  try {
    const res = await chatWithAgent({
      agentId: selectedAgentId.value,
      sessionId: sessionId.value,
      message: text
    });
    sessionId.value = res.sessionId;
    messages.value.push({
      role: "assistant",
      text: res.reply,
      engine: res.engine,
      latencyMs: res.latencyMs,
      toolTraces: res.toolTraces,
      citations: res.citations
    });
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : "智能体联调响应失败");
  } finally {
    sending.value = false;
  }
}

onMounted(() => {
  void loadAll();
});
</script>

<template>
  <ControlSurface
    title="AI 智能体编排工坊 (Agent Studio)"
    description="可视化组装企业数字员工 · 绑定系统 Prompt + RAG 知识库 + MCP/查数工具权限 · 多渠道发布与沙盒实时联调"
    badge="Agent Studio"
  >
    <template #actions>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadAll">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="resetNewAgent">新建智能体</el-button>
    </template>

    <div class="agent-studio-grid">
      <!-- Column 1: Agent Roster -->
      <BaseCard class="roster-card">
        <div class="card-head">
          <h3>已发布数字员工 ({{ agents.length }})</h3>
        </div>
        <div class="roster-list">
          <div
            v-for="ag in agents"
            :key="ag.id"
            class="agent-item"
            :class="{ 'is-selected': ag.id === selectedAgentId }"
            @click="applyAgentToForm(ag)"
          >
            <div class="agent-item-top">
              <span class="agent-avatar">{{ ag.avatar }}</span>
              <div class="agent-titles">
                <strong>{{ ag.agentName }}</strong>
                <span>{{ ag.roleTitle }}</span>
              </div>
              <el-button
                link
                type="danger"
                size="small"
                :icon="Delete"
                @click.stop="handleDeleteAgent(ag)"
              />
            </div>
            <div class="agent-tags">
              <el-tag size="small" type="success">{{ ag.status }}</el-tag>
              <span class="runs-text">调用 {{ ag.runCount }} 次 · {{ ag.toolCodes.length }} 工具</span>
            </div>
          </div>
        </div>
      </BaseCard>

      <!-- Column 2: Visual Composer -->
      <BaseCard class="composer-card">
        <div class="card-head">
          <h3>{{ form.id ? `编排配置 · ${form.agentName}` : "创建新智能体" }}</h3>
          <el-button type="primary" :icon="Check" :loading="saving" @click="handleSaveAgent">保存并发布</el-button>
        </div>

        <el-form label-position="top" class="composer-form">
          <div class="two-cols">
            <el-form-item label="智能体名称" required>
              <el-input v-model="form.agentName" placeholder="例如：售后工单排查专家" />
            </el-form-item>
            <el-form-item label="角色头衔">
              <el-input v-model="form.roleTitle" placeholder="例如：客服主管参谋" />
            </el-form-item>
          </div>

          <div class="two-cols">
            <el-form-item label="底层推理模型">
              <el-select v-model="form.modelId" style="width: 100%" filterable allow-create>
                <el-option v-for="m in modelOptions" :key="m.id" :label="m.name" :value="m.id" />
                <el-option label="deepseek-chat" value="deepseek-chat" />
              </el-select>
            </el-form-item>
            <el-form-item label="发布渠道">
              <el-input v-model="form.channelTypes" placeholder="CONSOLE,CRM_DRAWER,MOBILE" />
            </el-form-item>
          </div>

          <el-form-item label="系统人设与行为约束 (System Prompt)" required>
            <el-input
              v-model="form.systemPrompt"
              type="textarea"
              :rows="4"
              placeholder="描述该智能体的专业身份、回答规范与安全红线..."
            />
          </el-form-item>

          <el-form-item label="挂载企业知识库 (Hybrid RAG)">
            <el-select v-model="form.kbIds" multiple style="width: 100%" placeholder="选择需要联动的企业知识库">
              <el-option v-for="k in kbs" :key="k.id" :label="`${k.kbName} (${k.docCount} 文档)`" :value="k.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="授权调用工具清单 (MCP / Platform Tools)">
            <el-checkbox-group v-model="form.toolCodes" class="tool-check-grid">
              <el-checkbox v-for="t in tools" :key="t.toolCode" :value="t.toolCode" class="tool-chk-item">
                <div class="tool-chk-body">
                  <strong>{{ t.toolName }}</strong>
                  <span>{{ t.description }}</span>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </el-form>
      </BaseCard>

      <!-- Column 3: Live Sandbox -->
      <BaseCard class="sandbox-card">
        <div class="card-head">
          <h3><el-icon><Service /></el-icon> 实时联调沙盒</h3>
          <el-tag v-if="activeAgent" size="small" effect="plain">{{ activeAgent.modelId }}</el-tag>
        </div>

        <div class="sandbox-messages">
          <div v-for="(msg, idx) in messages" :key="idx" class="bubble-wrap" :class="msg.role">
            <div class="bubble-box">
              <div class="bubble-text">{{ msg.text }}</div>
              <div v-if="msg.toolTraces && msg.toolTraces.length" class="trace-pills">
                <el-tag v-for="(tr, i) in msg.toolTraces" :key="i" size="small" type="info">{{ tr }}</el-tag>
              </div>
              <div v-if="msg.citations && msg.citations.length" class="mini-cites">
                <span v-for="c in msg.citations" :key="c.chunkId" class="cite-pill">
                  [{{ c.citationNo }}] 《{{ c.docName }}》P{{ c.pageNo }}
                </span>
              </div>
              <div v-if="msg.engine && msg.engine !== 'WELCOME'" class="bubble-foot">
                {{ msg.engine }} · {{ msg.latencyMs }}ms
              </div>
            </div>
          </div>
          <el-empty v-if="messages.length === 0" description="选择右侧提示或输入测试对话" :image-size="56" />
        </div>

        <div class="sandbox-input">
          <el-input
            v-model="chatInput"
            placeholder="测试智能体：例如「合同首付款和违约责任红线是多少？」或「统计CRM合同总额」"
            @keyup.enter="handleSendChat"
          >
            <template #append>
              <el-button type="primary" :icon="Promotion" :loading="sending" @click="handleSendChat">发送</el-button>
            </template>
          </el-input>
        </div>
      </BaseCard>
    </div>
  </ControlSurface>
</template>

<style scoped lang="scss">
.agent-studio-grid {
  display: grid;
  grid-template-columns: 270px minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;

  h3 {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: 15px;
    font-weight: 600;
  }
}

.roster-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.agent-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.16s ease;

  &:hover {
    border-color: #93c5fd;
    background: #f8fafc;
  }

  &.is-selected {
    border-color: #2563eb;
    background: #eff6ff;
    box-shadow: 0 2px 10px rgba(37, 99, 235, 0.10);
  }
}

.agent-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-avatar {
  font-size: 22px;
}

.agent-titles {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;

  strong {
    color: #0f172a;
  }

  span {
    font-size: 12px;
    color: #475569;
  }
}

.agent-tags {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.runs-text {
  font-size: 11.5px;
  color: #475569;
}

.two-cols {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.tool-check-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.tool-chk-item {
  height: auto;
  padding: 10px 12px;
  margin-right: 0;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.tool-chk-body {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  strong {
    font-size: 13px;
    color: #0f172a;
  }

  span {
    font-size: 11.5px;
    color: #475569;
    white-space: normal;
  }
}

.sandbox-card {
  display: flex;
  flex-direction: column;
}

.sandbox-messages {
  flex: 1;
  min-height: 430px;
  max-height: 520px;
  overflow-y: auto;
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bubble-wrap {
  display: flex;

  &.user {
    justify-content: flex-end;
  }

  &.assistant {
    justify-content: flex-start;
  }
}

.bubble-box {
  max-width: 90%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.bubble-wrap.user .bubble-box {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.trace-pills,
.mini-cites {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.cite-pill {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #eff6ff;
  color: #1d4ed8;
}

.bubble-foot {
  margin-top: 6px;
  font-size: 11px;
  color: #475569;
}

@media (max-width: 1240px) {
  .agent-studio-grid {
    grid-template-columns: 1fr;
  }
}
</style>
