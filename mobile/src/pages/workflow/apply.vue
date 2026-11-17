<template>
  <view class="workflow-apply-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">发起业务审批</text>
      <view class="ios-nav-placeholder" />
    </view>

    <view class="page-intro">
      <view class="intro-badge">
        <text>企业智能流程中心</text>
      </view>
      <text class="intro-title">发起业务流程审批</text>
      <text class="intro-desc">选择预设审批单模板，随时随地移动提单与自动流转</text>
    </view>

    <!-- 审批模板选择器 -->
    <view class="template-grid">
      <view
        v-for="tpl in templates"
        :key="tpl.type"
        class="tpl-card"
        :class="{ 'is-active': selectedTpl === tpl.type }"
        @click="selectTemplate(tpl)"
      >
        <text class="tpl-icon">{{ tpl.icon }}</text>
        <view class="tpl-text-box">
          <text class="tpl-name">{{ tpl.name }}</text>
          <text class="tpl-sub">{{ tpl.sub }}</text>
        </view>
      </view>
    </view>

    <!-- 动态表单卡片 -->
    <view class="form-container">
      <view class="form-item">
        <text class="form-label">申请单标题</text>
        <input v-model="form.title" class="form-input" placeholder="输入审批申请单标题..." />
      </view>

      <view class="form-item">
        <text class="form-label">紧急级别</text>
        <view class="radio-group">
          <view
            v-for="p in priorities"
            :key="p.key"
            class="radio-pill"
            :class="{ 'is-selected': form.priority === p.key }"
            @click="form.priority = p.key"
          >
            <text>{{ p.label }}</text>
          </view>
        </view>
      </view>

      <view v-if="selectedTpl === 'LEAVE'" class="form-item">
        <text class="form-label">请假时长 (工作日天数)</text>
        <input v-model="form.days" type="digit" class="form-input" placeholder="输入申请请假天数 (如 2.5)" />
      </view>

      <view v-else-if="selectedTpl === 'EXPENSE'" class="form-item">
        <text class="form-label">报销核算总金额 (人民币 元)</text>
        <input v-model="form.amount" type="digit" class="form-input" placeholder="输入发票报销总金额 (如 1280.00)" />
      </view>

      <view class="form-item">
        <text class="form-label">申请事由与业务凭据说明</text>
        <textarea
          v-model="form.reason"
          class="form-textarea"
          placeholder="请详细说明申请背景、业务必要性与相关凭据说明..."
          maxlength="400"
        />
        <text class="char-count">{{ form.reason.length }}/400</text>
      </view>

      <!-- 快捷事由推荐 -->
      <view class="preset-reasons">
        <text class="preset-label">快捷事由参考：</text>
        <view class="chips-list">
          <view
            v-for="(phrase, idx) in presetPhrases"
            :key="idx"
            class="phrase-chip"
            @click="form.reason = phrase"
          >
            <text>+ {{ phrase }}</text>
          </view>
        </view>
      </view>

      <button class="submit-btn" @click="handlePreSubmit">
        预览并提交至流程引擎
      </button>
    </view>

    <!-- 提单预览确认弹窗 (AppModal Dialog Mode) -->
    <AppModal
      v-model="confirmModalVisible"
      mode="dialog"
      title="审批单提交确认"
      subtitle="确认无误后将立即进入流程引擎审批流"
      confirm-text="确认发起审批"
      :confirm-loading="submitting"
      @confirm="submitWorkflow"
    >
      <view class="preview-content">
        <view class="preview-row">
          <text class="preview-k">申请模板</text>
          <text class="preview-v">{{ getTemplateName(selectedTpl) }}</text>
        </view>
        <view class="preview-row">
          <text class="preview-k">申请标题</text>
          <text class="preview-v">{{ form.title }}</text>
        </view>
        <view class="preview-row">
          <text class="preview-k">紧急程度</text>
          <text class="preview-v text-blue">{{ getPriorityLabel(form.priority) }}</text>
        </view>
        <view v-if="selectedTpl === 'LEAVE'" class="preview-row">
          <text class="preview-k">请假天数</text>
          <text class="preview-v">{{ form.days }} 天</text>
        </view>
        <view v-if="selectedTpl === 'EXPENSE'" class="preview-row">
          <text class="preview-k">报销金额</text>
          <text class="preview-v font-mono text-cyan">¥{{ Number(form.amount || 0).toLocaleString() }} 元</text>
        </view>
        <view class="preview-row align-top">
          <text class="preview-k">申请事由</text>
          <text class="preview-v reason-text">{{ form.reason }}</text>
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import AppModal from "../../components/ui/AppModal.vue";
import AppIcon from "../../components/ui/AppIcon.vue";
import { apiGetWorkflowDefinitions, apiStartWorkflow } from "../../api/workflow";

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: "/pages/workflow/list" });
    }
  });
}

const selectedTpl = ref<"LEAVE" | "EXPENSE" | "PURCHASE" | "GENERAL">("LEAVE");
const submitting = ref(false);
const confirmModalVisible = ref(false);
const remoteDefinitions = ref<any[]>([]);

const templates = [
  { type: "LEAVE" as const, code: "LEAVE_REQUEST", name: "请假出差申请", sub: "年假/事假/病假", icon: "🌴", defaultTitle: "带薪年假申请单" },
  { type: "EXPENSE" as const, code: "EXPENSE_REIMBURSE", name: "费用报销申请", sub: "差旅/业务招待", icon: "💰", defaultTitle: "商务差旅报销单" },
  { type: "PURCHASE" as const, code: "GENERAL_APPROVAL", name: "物资设备申领", sub: "研发硬件/办公耗材", icon: "💻", defaultTitle: "办公硬件采购申请" },
  { type: "GENERAL" as const, code: "GENERAL_APPROVAL", name: "通用事务审批", sub: "合同用印/行政流转", icon: "📑", defaultTitle: "综合行政通用审批单" }
];

onMounted(async () => {
  try {
    const list = await apiGetWorkflowDefinitions();
    if (Array.isArray(list) && list.length > 0) {
      remoteDefinitions.value = list;
    }
  } catch (err) {
    console.warn("Failed to load workflow definitions, will use standard codes", err);
  }
});

const priorities = [
  { key: "NORMAL", label: "普通" },
  { key: "URGENT", label: "紧急" },
  { key: "CRITICAL", label: "特急" }
];

const presetPhrases = [
  "根据本季度研发上线排期安排，申请休假2天，工作已交接妥当。",
  "出差拜访华东重点客户现场PoC支持，报销往返高铁车票及住宿费用。",
  "申请研发团队云原生容器集群测试服务器与高可用存储资源采购。"
];

const form = reactive({
  title: "带薪年假申请单",
  priority: "NORMAL",
  days: "1.0",
  amount: "1500.00",
  reason: "按年度带薪休假计划申请休假，相关工作已交接妥当。"
});

function selectTemplate(tpl: (typeof templates)[number]) {
  selectedTpl.value = tpl.type;
  form.title = tpl.defaultTitle;
}

function getTemplateName(type: string) {
  const match = templates.find((t) => t.type === type);
  return match ? match.name : "业务审批";
}

function getPriorityLabel(priority: string) {
  const match = priorities.find((p) => p.key === priority);
  return match ? match.label : "普通";
}

function handlePreSubmit() {
  if (!form.title.trim()) {
    uni.showToast({ title: "请输入申请单标题", icon: "none" });
    return;
  }
  if (!form.reason.trim()) {
    uni.showToast({ title: "请输入申请事由", icon: "none" });
    return;
  }
  confirmModalVisible.value = true;
}

function resolveDefinitionId(): number {
  const tpl = templates.find((t) => t.type === selectedTpl.value);
  const code = tpl ? tpl.code : "LEAVE_REQUEST";
  if (remoteDefinitions.value.length > 0) {
    const matched = remoteDefinitions.value.find((d) => d.processCode === code);
    if (matched && matched.id) {
      return Number(matched.id);
    }
    // Fallback to first available published definition
    return Number(remoteDefinitions.value[0].id);
  }
  if (code === "EXPENSE_REIMBURSE") return 2;
  if (code === "GENERAL_APPROVAL") return 3;
  return 1;
}

async function submitWorkflow() {
  submitting.value = true;
  try {
    const targetDefId = resolveDefinitionId();
    await apiStartWorkflow({
      definitionId: targetDefId,
      title: form.title,
      formData: {
        type: selectedTpl.value,
        priority: form.priority,
        days: form.days,
        amount: form.amount,
        reason: form.reason
      }
    });
    uni.showToast({ title: "Flowable 流程已成功发起！", icon: "success" });
    confirmModalVisible.value = false;
    setTimeout(() => {
      uni.navigateBack();
    }, 600);
  } catch (e: any) {
    const msg = e?.message || e?.data?.message || "流程发起失败，请重试";
    uni.showToast({ title: msg, icon: "none" });
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 发起业务审批规范
   ============================================================================= */

.workflow-apply-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(80px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.page-intro {
  margin-bottom: 14px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.intro-badge {
  display: inline-block;
  font-size: 10.5px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 2px 8px;
  border-radius: 6px;
  margin-bottom: 6px;
}

.intro-title {
  font-size: 18px;
  font-weight: 750;
  color: #0f172a;
  display: block;
  letter-spacing: -0.3px;
}

.intro-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  display: block;
  font-weight: 500;
}

/* 模板选择 */
.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.tpl-card {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.90);
  border-radius: 16px;
  padding: 14px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(15, 23, 42, 0.03);
}

.tpl-card.is-active {
  border-color: rgba(37, 99, 235, 0.6);
  background: rgba(239, 246, 255, 0.85);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 6px 18px rgba(37, 99, 235, 0.20);
}

.tpl-icon {
  font-size: 24px;
}

.tpl-text-box {
  display: flex;
  flex-direction: column;
}

.tpl-name {
  font-size: 13.5px;
  font-weight: 750;
  color: #0f172a;
}

.tpl-sub {
  font-size: 10.5px;
  color: #64748b;
  margin-top: 2px;
  font-weight: 500;
}

/* 动态表单卡片 */
.form-container {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 18px 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
}

.form-item {
  margin-bottom: 16px;
  position: relative;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 7px;
}

.form-input {
  background: rgba(255, 255, 255, 0.60);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  height: 44px;
  padding: 0 14px;
  font-size: 13.5px;
  color: #0f172a;
}

.radio-group {
  display: flex;
  gap: 8px;
}

.radio-pill {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  background: rgba(255, 255, 255, 0.60);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 10px;
  font-size: 12px;
  color: #475569;
  font-weight: 550;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.radio-pill.is-selected {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.35);
  font-weight: 650;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 12px rgba(37, 99, 235, 0.28);
}

.form-textarea {
  width: 100%;
  height: 85px;
  background: rgba(255, 255, 255, 0.60);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #0f172a;
  box-sizing: border-box;
}

.char-count {
  display: block;
  text-align: right;
  font-size: 10px;
  color: #94a3b8;
  margin-top: 4px;
}

/* 预设事由 */
.preset-reasons {
  margin-bottom: 18px;
}

.preset-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
  display: block;
  font-weight: 500;
}

.chips-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.phrase-chip {
  padding: 6px 12px;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.20);
  border-radius: 8px;
  font-size: 11.5px;
  color: #2563eb;
  font-weight: 500;
}

.phrase-chip:active {
  background: rgba(37, 99, 235, 0.18);
}

.submit-btn {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 9999px;
  font-size: 14.5px;
  font-weight: 650;
  height: 46px;
  line-height: 46px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 6px 18px rgba(37, 99, 235, 0.32);
  transition: transform 0.15s ease;
}

.submit-btn:active {
  transform: scale(0.97);
}

/* 预览弹窗 */
.preview-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.preview-row.align-top {
  flex-direction: column;
  gap: 4px;
}

.preview-k { color: #64748b; }
.preview-v { color: #0f172a; font-weight: 600; }
.reason-text { font-weight: normal; color: #334155; line-height: 1.4; }
.font-mono { font-family: monospace; }
.text-cyan { color: #0284c7; }
.text-blue { color: #2563eb; }
</style>
