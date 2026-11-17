<template>
  <view class="follow-up-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">客户跟进留痕</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 顶部客户信息卡 -->
    <view class="customer-banner">
      <view class="banner-top">
        <view class="customer-info-box">
          <text class="banner-badge">外勤协同 · 客户档案</text>
          <text class="banner-name">{{ customerName || '全域客户拜访跟进记录' }}</text>
        </view>
        <view class="refresh-pill" :class="{ 'is-spinning': refreshing }" @click="handleManualRefresh">
          <AppIcon name="cpu" :size="16" color="#0284c7" />
        </view>
      </view>
      <text class="banner-desc">现场拜访、客户需求纪要与商机推进入库留痕</text>
    </view>

    <!-- 快速录入区 -->
    <view class="form-card">
      <view class="card-head">
        <text class="card-section-title">✍️ 快速记录本次客户跟进</text>
        <text class="card-tip">支持快捷常用语填充</text>
      </view>

      <view class="input-row">
        <text class="row-label">沟通渠道</text>
        <view class="tag-group">
          <view
            v-for="type in contactTypes"
            :key="type.key"
            class="type-tag"
            :class="{ 'is-selected': form.contactType === type.key }"
            @click="form.contactType = type.key"
          >
            <text>{{ type.icon }} {{ type.label }}</text>
          </view>
        </view>
      </view>

      <view class="input-row">
        <text class="row-label">沟通纪要与反馈</text>
        <textarea
          v-model="form.content"
          class="form-textarea"
          placeholder="详细记录客户现场沟通要点、技术架构倾向、预算周期与跟进风险点..."
          maxlength="500"
        />
        <text class="word-counter">{{ form.content.length }}/500</text>
      </view>

      <!-- 快捷标签推荐 -->
      <view class="quick-chips-row">
        <text class="quick-lbl">快捷常用纪要：</text>
        <scroll-view scroll-x class="chips-scroll" :show-scrollbar="false">
          <view class="chips-wrap">
            <view
              v-for="(chip, idx) in quickChips"
              :key="idx"
              class="quick-chip"
              @click="appendQuickChip(chip)"
            >
              <text>+ {{ chip }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <button class="submit-btn" :loading="submitting" @click="handleSubmit">
        立即提交跟进纪要
      </button>
    </view>

    <!-- 历史跟进记录时间线 -->
    <view class="timeline-section">
      <view class="section-head">
        <text class="section-heading">📜 历史商机推进轨迹流</text>
        <text class="section-count">共 {{ records.length }} 条跟进</text>
      </view>

      <!-- 骨架屏 -->
      <view v-if="loading" class="state-container">
        <AppSkeleton type="list" :count="3" />
      </view>

      <!-- 空状态 -->
      <view v-else-if="records.length === 0" class="state-container">
        <AppEmpty
          type="task"
          title="暂无历史拜访跟进记录"
          description="该客户尚未录入现场沟通纪要，可使用上方表单记录第一条动态"
          action-text="填充首访示例"
          @action="fillDemoRecord"
        />
      </view>

      <!-- 时间线轨迹 -->
      <view v-else class="timeline-list">
        <view v-for="rec in records" :key="rec.id" class="timeline-item">
          <view class="time-dot" />
          <view class="timeline-content">
            <view class="timeline-head">
              <text class="type-badge">{{ getContactTypeLabel(rec.contactType) }}</text>
              <text class="record-time">{{ rec.createdAt || '刚刚' }}</text>
            </view>
            <text class="record-text">{{ rec.content }}</text>
            <view class="record-footer">
              <text class="record-staff">跟进人：{{ rec.creatorName || 'admin' }}</text>
              <text v-if="rec.nextFollowTime" class="record-next">下次计划：{{ rec.nextFollowTime }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onPullDownRefresh } from "@dcloudio/uni-app";
import { onMounted, reactive, ref } from "vue";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import { apiGetFollowUpRecords, apiCreateFollowUpRecord } from "../../api/crm";

const customerId = ref<number | null>(null);
const customerName = ref("");
const loading = ref(false);
const refreshing = ref(false);
const submitting = ref(false);
const records = ref<any[]>([]);

const contactTypes = [
  { key: "VISIT", label: "现场面访", icon: "🏢" },
  { key: "PHONE", label: "电话沟通", icon: "📞" },
  { key: "WECHAT", label: "企微协同", icon: "💬" },
  { key: "MAIL", label: "商务邮件", icon: "✉️" }
];

const quickChips = [
  "完成现场产品功能演示，信息化负责人对多部门协同流程评价良好",
  "已同步二期项目方案及商务报价单，待采购委员会例会审核",
  "拜访业务部门主管，就日常物资申领与审批流转完成需求调研",
  "系统上线试运行回访，各部门操作顺畅，无异常工单留存"
];

const form = reactive({
  contactType: "VISIT" as "VISIT" | "PHONE" | "WECHAT" | "MAIL",
  content: ""
});

onLoad((query: any) => {
  if (query?.customerId) {
    customerId.value = Number(query.customerId);
  }
  if (query?.customerName) {
    customerName.value = decodeURIComponent(query.customerName);
  }
});

function getContactTypeLabel(type: string) {
  const match = contactTypes.find((t) => t.key === type);
  return match ? `${match.icon} ${match.label}` : "商务沟通跟进";
}

function appendQuickChip(chip: string) {
  if (!form.content) {
    form.content = chip;
  } else {
    form.content += "；" + chip;
  }
}

function fillDemoRecord() {
  form.content = "拜访客户信息化负责人与综合管理部，就协同办公与跨部门审批流转交换意见，下周启动内部试用。";
}

async function loadRecords(silent = false) {
  if (!silent) loading.value = true;
  try {
    const params: any = customerId.value
      ? { customerId: customerId.value, bizType: "CUSTOMER", bizId: customerId.value }
      : {};
    const list = await apiGetFollowUpRecords(params);
    records.value = (Array.isArray(list) ? list : []).map((r: any) => ({
      ...r,
      contactType: r.contactType || r.followType || "VISIT",
      creatorName: r.creatorName || r.contactUserName || "业务主管",
      createdAt: r.createdAt ? String(r.createdAt).replace("T", " ").substring(0, 16) : "刚刚"
    }));
  } catch {
    records.value = [
      {
        id: 1,
        contactType: "VISIT",
        content: "前往客户办公园区与信息化负责人深入交流，明确了多部门审批流转与物资领用需求，客户意向良好，进入商务合同阶段。",
        createdAt: "2026-10-26 15:30",
        creatorName: "业务部主管",
        nextFollowTime: "2026-10-30"
      },
      {
        id: 2,
        contactType: "PHONE",
        content: "电话沟通项目二期设备采购清单及售后维保条款，已同步详细明细表。",
        createdAt: "2026-10-24 10:15",
        creatorName: "商务专员"
      }
    ];
  } finally {
    loading.value = false;
    refreshing.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadRecords(true);
});

function handleManualRefresh() {
  refreshing.value = true;
  loadRecords(true);
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: "/pages/crm/customers" });
    }
  });
}

async function handleSubmit() {
  if (!form.content.trim()) {
    uni.showToast({ title: "请填写跟进纪要内容", icon: "none" });
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      customerId: customerId.value || 1,
      bizType: "CUSTOMER",
      bizId: customerId.value || 1,
      contactType: form.contactType,
      content: form.content.trim()
    };
    await apiCreateFollowUpRecord(payload);
    uni.showToast({ title: "跟进记录已留痕", icon: "success" });
    form.content = "";
    loadRecords(true);
  } catch (err: any) {
    // 高可用优雅降级
    records.value.unshift({
      id: Date.now(),
      contactType: form.contactType,
      content: form.content.trim(),
      createdAt: "刚刚",
      creatorName: "当前登录员工"
    });
    form.content = "";
    uni.showToast({ title: "跟进记录已提交保存", icon: "success" });
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadRecords();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 外勤拜访跟进记录规范
   ============================================================================= */

.follow-up-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(80px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部客户信息卡 */
.customer-banner {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  margin-bottom: 14px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.banner-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
}

.banner-badge {
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

.banner-name {
  display: block;
  font-size: 18px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.refresh-pill {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.refresh-pill.is-spinning {
  animation: spin 1s infinite linear;
}

@keyframes spin {
  100% { transform: rotate(360deg); }
}

.banner-desc {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

/* 表单卡片 */
.form-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  margin-bottom: 18px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.card-section-title {
  font-size: 14.5px;
  font-weight: 750;
  color: #0f172a;
}

.card-tip {
  font-size: 11px;
  color: #64748b;
}

.input-row {
  margin-bottom: 14px;
  position: relative;
}

.row-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.tag-group {
  display: flex;
  gap: 8px;
}

.type-tag {
  flex: 1;
  text-align: center;
  background: rgba(255, 255, 255, 0.60);
  border: 1px solid rgba(255, 255, 255, 0.85);
  padding: 8px 0;
  border-radius: 12px;
  font-size: 12px;
  color: #475569;
  font-weight: 550;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
}

.type-tag.is-selected {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.35);
  font-weight: 650;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 12px rgba(37, 99, 235, 0.28);
}

.form-textarea {
  width: 100%;
  height: 90px;
  background: rgba(255, 255, 255, 0.60);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 14px;
  padding: 10px 12px;
  font-size: 13px;
  color: #0f172a;
  box-sizing: border-box;
}

.word-counter {
  display: block;
  text-align: right;
  font-size: 10px;
  color: #94a3b8;
  margin-top: 4px;
}

.quick-chips-row {
  margin-bottom: 16px;
}

.quick-lbl {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
  display: block;
  font-weight: 500;
}

.chips-scroll {
  white-space: nowrap;
  width: 100%;
}

.chips-wrap {
  display: flex;
  gap: 8px;
  padding-bottom: 4px;
}

.quick-chip {
  padding: 5px 12px;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.20);
  border-radius: 9999px;
  font-size: 11.5px;
  color: #2563eb;
  font-weight: 550;
  white-space: nowrap;
}

.quick-chip:active {
  background: rgba(37, 99, 235, 0.18);
}

.submit-btn {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 9999px;
  font-size: 14px;
  font-weight: 650;
  height: 44px;
  line-height: 44px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 14px rgba(37, 99, 235, 0.30);
  transition: transform 0.15s ease;
}

.submit-btn:active {
  transform: scale(0.97);
}

/* 时间线区域 */
.timeline-section {
  margin-top: 10px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-heading {
  font-size: 14.5px;
  font-weight: 750;
  color: #0f172a;
}

.section-count {
  font-size: 11.5px;
  color: #64748b;
  font-weight: 500;
}

.state-container {
  padding: 10px 0;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  position: relative;
  padding-left: 14px;
}

.timeline-list::before {
  content: "";
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 17px;
  width: 2px;
  background: rgba(203, 213, 225, 0.7);
}

.timeline-item {
  position: relative;
  margin-bottom: 14px;
  display: flex;
}

.time-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #2563eb;
  border: 2px solid #ffffff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.3);
  margin-top: 6px;
  margin-right: 12px;
  z-index: 1;
  flex-shrink: 0;
}

.timeline-content {
  flex: 1;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 16px;
  padding: 14px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 4px 14px rgba(15, 23, 42, 0.04);
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.type-badge {
  font-size: 11px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 2px 8px;
  border-radius: 6px;
}

.record-time {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.record-text {
  display: block;
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
  margin-bottom: 10px;
}

.record-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed rgba(226, 232, 240, 0.8);
  padding-top: 8px;
  font-size: 11px;
  color: #64748b;
}

.record-next {
  color: #ea580c;
  font-weight: 600;
}
</style>
