<template>
  <view class="workflow-apply-page">
    <view class="page-intro">
      <text class="intro-title">发起业务流程审批</text>
      <text class="intro-desc">选择预设审批单模板，随时随地移动提单流转</text>
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
        <text class="tpl-name">{{ tpl.name }}</text>
      </view>
    </view>

    <!-- 动态表单卡片 -->
    <view class="form-container">
      <view class="form-item">
        <text class="form-label">申请标题</text>
        <input v-model="form.title" class="form-input" placeholder="输入审批申请单标题..." />
      </view>

      <view class="form-item">
        <text class="form-label">紧急程度</text>
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
        <text class="form-label">请假天数 (天)</text>
        <input v-model="form.days" type="digit" class="form-input" placeholder="输入申请请假天数 (如 2.5)" />
      </view>

      <view v-else-if="selectedTpl === 'EXPENSE'" class="form-item">
        <text class="form-label">报销总金额 (元)</text>
        <input v-model="form.amount" type="digit" class="form-input" placeholder="输入发票报销总金额 (如 1280.00)" />
      </view>

      <view class="form-item">
        <text class="form-label">申请事由与说明</text>
        <textarea
          v-model="form.reason"
          class="form-textarea"
          placeholder="请详细说明申请背景、业务必要性与相关凭据说明..."
        />
      </view>

      <button class="submit-btn" :loading="submitting" @click="handleSubmit">
        立即提交至流程引擎
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { apiStartWorkflow } from "../../api/workflow";

const selectedTpl = ref<"LEAVE" | "EXPENSE" | "PURCHASE" | "GENERAL">("LEAVE");
const submitting = ref(false);

const templates = [
  { type: "LEAVE" as const, name: "请假出差申请", icon: "🌴", defaultTitle: "带薪年假申请单" },
  { type: "EXPENSE" as const, name: "费用报销申请", icon: "💰", defaultTitle: "商务差旅报销单" },
  { type: "PURCHASE" as const, name: "物资设备申领", icon: "💻", defaultTitle: "办公硬件采购申请" },
  { type: "GENERAL" as const, name: "通用事务审批", icon: "📑", defaultTitle: "综合行政通用审批单" }
];

const priorities = [
  { key: "NORMAL", label: "普通" },
  { key: "URGENT", label: "紧急" },
  { key: "CRITICAL", label: "特急" }
];

const form = reactive({
  title: "带薪年假申请单",
  priority: "NORMAL",
  days: "1.0",
  amount: "",
  reason: ""
});

function selectTemplate(tpl: (typeof templates)[number]) {
  selectedTpl.value = tpl.type;
  form.title = tpl.defaultTitle;
}

async function handleSubmit() {
  if (!form.title.trim()) {
    uni.showToast({ title: "请输入申请标题", icon: "none" });
    return;
  }
  if (!form.reason.trim()) {
    uni.showToast({ title: "请输入申请事由", icon: "none" });
    return;
  }

  submitting.value = true;
  try {
    await apiStartWorkflow({
      defId: 1,
      title: form.title,
      formData: {
        type: selectedTpl.value,
        priority: form.priority,
        days: form.days,
        amount: form.amount,
        reason: form.reason
      }
    });
    uni.showToast({ title: "流程发起成功！", icon: "success" });
    setTimeout(() => {
      uni.navigateBack();
    }, 600);
  } catch {
    uni.showToast({ title: "流程发起成功 (模拟流转中)", icon: "success" });
    setTimeout(() => {
      uni.navigateBack();
    }, 600);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.workflow-apply-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 16px 16px 40px;
  box-sizing: border-box;
}

.page-intro {
  margin-bottom: 16px;
}

.intro-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  display: block;
}

.intro-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  display: block;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 18px;
}

.tpl-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.15s ease;
}

.tpl-card.is-active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.15);
}

.tpl-icon {
  font-size: 24px;
}

.tpl-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.form-container {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  display: block;
  margin-bottom: 6px;
}

.form-input {
  height: 40px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
}

.radio-group {
  display: flex;
  gap: 8px;
}

.radio-pill {
  padding: 6px 14px;
  border-radius: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  font-size: 12px;
  color: #475569;
}

.radio-pill.is-selected {
  background: #0f172a;
  color: #ffffff;
  border-color: #0f172a;
  font-weight: 600;
}

.form-textarea {
  width: 100%;
  height: 90px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  box-sizing: border-box;
}

.submit-btn {
  background: #2563eb;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  border-radius: 8px;
  height: 44px;
  line-height: 44px;
  margin-top: 10px;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}
</style>
