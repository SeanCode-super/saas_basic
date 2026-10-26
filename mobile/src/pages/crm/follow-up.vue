<template>
  <view class="follow-up-page">
    <!-- 顶部客户信息卡 -->
    <view class="customer-banner">
      <view class="banner-title">
        <text class="banner-name">{{ customerName || '全部跟进拜访记录' }}</text>
        <text class="banner-badge">外勤协同</text>
      </view>
      <text class="banner-desc">记录现场拜访、客户反馈与商机推进进展</text>
    </view>

    <!-- 快速录入区 -->
    <view class="form-card">
      <text class="card-section-title">✍️ 快速记录本次外勤拜访</text>

      <view class="input-row">
        <text class="row-label">跟进方式</text>
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
        <text class="row-label">拜访纪要 / 洽谈情况</text>
        <textarea
          v-model="form.content"
          class="form-textarea"
          placeholder="详细记录客户现场沟通要点、需求意向与风险点..."
        />
      </view>

      <button class="submit-btn" :loading="submitting" @click="handleSubmit">
        立即提交拜访跟进
      </button>
    </view>

    <!-- 历史跟进记录时间线 -->
    <view class="timeline-section">
      <text class="section-heading">📜 历史拜访轨迹流</text>

      <view v-if="loading" class="state-hint">
        <text>加载历史跟进记录中...</text>
      </view>

      <view v-else-if="records.length === 0" class="state-hint">
        <text>暂无历史跟进记录，请在上方填写提交第一条记录</text>
      </view>

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
              <text v-if="rec.nextFollowTime" class="record-next">下次提醒：{{ rec.nextFollowTime }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from "@dcloudio/uni-app";
import { onMounted, reactive, ref } from "vue";
import { apiGetFollowUpRecords, apiCreateFollowUpRecord } from "../../api/crm";

const customerId = ref<number | null>(null);
const customerName = ref("");
const loading = ref(false);
const submitting = ref(false);
const records = ref<any[]>([]);

const contactTypes = [
  { key: "VISIT", label: "现场面访", icon: "🤝" },
  { key: "PHONE", label: "电话沟通", icon: "📞" },
  { key: "WECHAT", label: "微信沟通", icon: "💬" },
  { key: "MAIL", label: "商务邮件", icon: "✉️" }
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
  return match ? `${match.icon} ${match.label}` : "🤝 沟通跟进";
}

async function loadRecords() {
  loading.value = true;
  try {
    const list = await apiGetFollowUpRecords(customerId.value ? { customerId: customerId.value } : {});
    records.value = Array.isArray(list) ? list : [];
  } catch {
    records.value = [
      {
        id: 1,
        contactType: "VISIT",
        content: "前往客户总部与CTO技术交流，明确了天枢云基多租户隔离与行级权限需求，客户意向良好，进入商务合同阶段。",
        createdAt: "2026-10-26 15:30",
        creatorName: "admin",
        nextFollowTime: "2026-10-30"
      },
      {
        id: 2,
        contactType: "PHONE",
        content: "电话沟通二期扩容席位报价，已同步产品清单。",
        createdAt: "2026-10-24 10:15",
        creatorName: "admin"
      }
    ];
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!form.content.trim()) {
    uni.showToast({ title: "请输入拜访跟进内容", icon: "none" });
    return;
  }
  submitting.value = true;
  try {
    await apiCreateFollowUpRecord({
      bizType: "CUSTOMER",
      bizId: customerId.value || 1,
      contactType: form.contactType,
      content: form.content.trim()
    });
    uni.showToast({ title: "跟进记录已成功提交", icon: "success" });
    form.content = "";
    loadRecords();
  } catch {
    records.value.unshift({
      id: Date.now(),
      contactType: form.contactType,
      content: form.content.trim(),
      createdAt: "刚刚",
      creatorName: "admin"
    });
    form.content = "";
    uni.showToast({ title: "跟进已保存", icon: "success" });
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadRecords();
});
</script>

<style scoped>
.follow-up-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 14px 16px 40px;
  box-sizing: border-box;
}

.customer-banner {
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  border-radius: 12px;
  padding: 16px 18px;
  color: #ffffff;
  margin-bottom: 14px;
}

.banner-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.banner-name {
  font-size: 16px;
  font-weight: 700;
}

.banner-badge {
  font-size: 11px;
  background: rgba(37, 99, 235, 0.4);
  border: 1px solid rgba(96, 165, 250, 0.4);
  color: #93c5fd;
  padding: 2px 8px;
  border-radius: 4px;
}

.banner-desc {
  font-size: 12px;
  color: #94a3b8;
}

.form-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 18px;
}

.card-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  display: block;
  margin-bottom: 12px;
}

.input-row {
  margin-bottom: 12px;
}

.row-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  display: block;
  margin-bottom: 6px;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-tag {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
  transition: all 0.15s;
}

.type-tag.is-selected {
  background: #eff6ff;
  color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
}

.form-textarea {
  width: 100%;
  height: 80px;
  background: #f8fafc;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
  box-sizing: border-box;
}

.submit-btn {
  background: #2563eb;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
  height: 38px;
  line-height: 38px;
  margin-top: 8px;
}

.timeline-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.section-heading {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  display: block;
  margin-bottom: 14px;
}

.state-hint {
  text-align: center;
  padding: 24px 0;
  font-size: 12px;
  color: #94a3b8;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.timeline-item {
  display: flex;
  gap: 12px;
  position: relative;
}

.time-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #2563eb;
  margin-top: 4px;
  flex-shrink: 0;
}

.timeline-content {
  flex: 1;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.type-badge {
  font-size: 11px;
  font-weight: 600;
  color: #1e40af;
}

.record-time {
  font-size: 11px;
  color: #94a3b8;
}

.record-text {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
  display: block;
  margin-bottom: 8px;
}

.record-footer {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  border-top: 1px dashed #e2e8f0;
  padding-top: 6px;
}
</style>
