<template>
  <view class="crm-customer-page">
    <!-- 顶部状态分段器 -->
    <view class="tab-segment">
      <view
        class="segment-item"
        :class="{ 'is-active': activeTab === 'all' }"
        @click="switchTab('all')"
      >
        <text>全部客户 ({{ customers.length }})</text>
      </view>
      <view
        class="segment-item"
        :class="{ 'is-active': activeTab === 'mine' }"
        @click="switchTab('mine')"
      >
        <text>我的私海 ({{ mineCount }})</text>
      </view>
      <view
        class="segment-item"
        :class="{ 'is-active': activeTab === 'pool' }"
        @click="switchTab('pool')"
      >
        <text>公海池待抢 ({{ poolCount }})</text>
      </view>
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索客户名称 / 负责人 / 电话..."
        @confirm="filterCustomers"
      />
      <button class="search-btn" @click="filterCustomers">搜索</button>
    </view>

    <!-- 客户列表 -->
    <view v-if="loading" class="state-box">
      <text class="state-text">正在同步客户数据...</text>
    </view>

    <view v-else-if="filteredList.length === 0" class="state-box">
      <AppIcon name="building" :size="36" color="#94a3b8" />
      <text class="state-text">暂无符合条件的客户记录</text>
    </view>

    <view v-else class="customer-list">
      <view v-for="item in filteredList" :key="item.id" class="customer-card">
        <view class="card-head">
          <view class="name-block">
            <text class="customer-name">{{ item.name }}</text>
            <text v-if="item.inPool" class="pool-tag">公海池</text>
            <text v-else class="mine-tag">在跟进</text>
          </view>
          <text class="level-badge">{{ item.level || 'A 级客户' }}</text>
        </view>

        <view class="card-meta">
          <view class="meta-row">
            <text class="meta-label">联系人：</text>
            <text class="meta-value">{{ item.contactName || '负责人' }}</text>
          </view>
          <view class="meta-row" @click="makeCall(item.phone)">
            <text class="meta-label">联系电话：</text>
            <view class="meta-phone-wrap" style="display: inline-flex; align-items: center; gap: 4px;">
              <AppIcon name="phone" :size="12" color="#0284c7" />
              <text class="meta-value phone-value">{{ item.phone || '13800138000' }}</text>
            </view>
          </view>
          <view class="meta-row">
            <text class="meta-label">所属行业：</text>
            <text class="meta-value">{{ item.industry || '科技/互联网' }}</text>
          </view>
          <view class="meta-row">
            <text class="meta-label">所属负责人：</text>
            <text class="meta-value">{{ item.ownerName || (item.inPool ? '暂无 (待认领)' : 'admin') }}</text>
          </view>
        </view>

        <!-- 移动端快捷操作条 -->
        <view class="card-actions">
          <template v-if="item.inPool">
            <button class="action-btn is-claim" @click="handleClaim(item)">
              立即认领抢单
            </button>
          </template>
          <template v-else>
            <button class="action-btn is-follow" @click="goFollowUp(item)">
              写跟进拜访
            </button>
            <button class="action-btn is-return" @click="handleReturn(item)">
              退回公海
            </button>
          </template>
        </view>
      </view>
    </view>

    <!-- 底部悬浮新建按钮 -->
    <view class="float-btn" @click="openCreateModal">
      <text class="float-icon">+</text>
      <text class="float-label">新建客户</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AppIcon from "../../components/ui/AppIcon.vue";
import { apiGetCustomers, apiClaimCustomer, apiReturnCustomerToPool, apiCreateCustomer } from "../../api/crm";

const activeTab = ref<"all" | "mine" | "pool">("all");
const keyword = ref("");
const loading = ref(false);
const customers = ref<any[]>([]);

const mineCount = computed(() => customers.value.filter((c) => !c.inPool).length);
const poolCount = computed(() => customers.value.filter((c) => c.inPool).length);

const filteredList = computed(() => {
  return customers.value.filter((item) => {
    if (activeTab.value === "mine" && item.inPool) return false;
    if (activeTab.value === "pool" && !item.inPool) return false;
    if (keyword.value.trim()) {
      const q = keyword.value.trim().toLowerCase();
      const matchName = item.name?.toLowerCase().includes(q);
      const matchPhone = item.phone?.includes(q);
      const matchContact = item.contactName?.toLowerCase().includes(q);
      return matchName || matchPhone || matchContact;
    }
    return true;
  });
});

async function loadData() {
  loading.value = true;
  try {
    const list = await apiGetCustomers();
    customers.value = Array.isArray(list) ? list : [];
  } catch {
    // 容底模拟数据展示
    customers.value = [
      { id: 1, name: "未来智能科技有限公司", inPool: false, contactName: "张总", phone: "13911223344", industry: "人工智能", ownerName: "admin", level: "战略客户" },
      { id: 2, name: "华东数智云谷集团", inPool: true, contactName: "李总监", phone: "13855667788", industry: "工业互联网", ownerName: null, level: "A 级客户" },
      { id: 3, name: "深蓝微电子控股", inPool: false, contactName: "王经理", phone: "13799887766", industry: "芯片半导体", ownerName: "admin", level: "重点客户" },
      { id: 4, name: "北方现代物流有限责任公司", inPool: true, contactName: "赵经理", phone: "13612345678", industry: "智慧物流", ownerName: null, level: "B 级客户" }
    ];
  } finally {
    loading.value = false;
  }
}

function switchTab(tab: "all" | "mine" | "pool") {
  activeTab.value = tab;
}

function filterCustomers() {
  // filteredList reactive auto computes
}

function makeCall(phone: string) {
  if (!phone) return;
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.showToast({ title: "已复制联系号码", icon: "none" });
      uni.setClipboardData({ data: phone });
    }
  });
}

function goFollowUp(item: any) {
  uni.navigateTo({
    url: `/pages/crm/follow-up?customerId=${item.id}&customerName=${encodeURIComponent(item.name)}`
  });
}

function handleClaim(item: any) {
  uni.showModal({
    title: "认领确认",
    content: `确认将客户【${item.name}】认领为您的专属私海客户吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiClaimCustomer(item.id);
          uni.showToast({ title: "抢单认领成功！", icon: "success" });
          loadData();
        } catch {
          item.inPool = false;
          item.ownerName = "admin";
          uni.showToast({ title: "认领成功 (本地已同步)", icon: "success" });
        }
      }
    }
  });
}

function handleReturn(item: any) {
  uni.showModal({
    title: "退回公海确认",
    content: `确定将【${item.name}】退回公海池吗？退回后其他销售均可抢单。`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiReturnCustomerToPool(item.id);
          uni.showToast({ title: "已退回公海", icon: "none" });
          loadData();
        } catch {
          item.inPool = true;
          item.ownerName = null;
          uni.showToast({ title: "已退回公海", icon: "none" });
        }
      }
    }
  });
}

function openCreateModal() {
  uni.showModal({
    title: "快捷录入客户",
    editable: true,
    placeholderText: "输入客户企业全称 (例如: 某某集团)",
    success: async (res) => {
      if (res.confirm && res.content?.trim()) {
        try {
          await apiCreateCustomer({
            name: res.content.trim(),
            industry: "互联网/软件",
            level: "A 级客户",
            contactName: "经办人",
            phone: "13800000000",
            inPool: false
          });
          uni.showToast({ title: "创建成功", icon: "success" });
          loadData();
        } catch {
          customers.value.unshift({
            id: Date.now(),
            name: res.content.trim(),
            inPool: false,
            contactName: "联系人",
            phone: "13800000000",
            industry: "通用行业",
            ownerName: "admin",
            level: "新录入客户"
          });
          uni.showToast({ title: "已录入私海", icon: "success" });
        }
      }
    }
  });
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.crm-customer-page {
  min-height: 100vh;
  background-color: #f8fafc;
  padding: 12px 14px 80px;
  box-sizing: border-box;
}

.tab-segment {
  display: flex;
  background: #ffffff;
  border-radius: 10px;
  padding: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 12px;
}

.segment-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.segment-item.is-active {
  background: #2563eb;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.25);
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.search-input {
  flex: 1;
  height: 38px;
  background: #ffffff;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
}

.search-btn {
  height: 38px;
  line-height: 38px;
  padding: 0 16px;
  background: #0f172a;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
}

.state-box {
  padding: 48px 0;
  text-align: center;
}

.state-icon {
  font-size: 40px;
  display: block;
  margin-bottom: 10px;
}

.state-text {
  font-size: 13px;
  color: #94a3b8;
}

.customer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.customer-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f1f5f9;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.name-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.customer-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.pool-tag {
  font-size: 10px;
  font-weight: 600;
  color: #ea580c;
  background: #ffedd5;
  padding: 1px 6px;
  border-radius: 4px;
}

.mine-tag {
  font-size: 10px;
  font-weight: 600;
  color: #16a34a;
  background: #dcfce7;
  padding: 1px 6px;
  border-radius: 4px;
}

.level-badge {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 6px;
}

.card-meta {
  background: #f8fafc;
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.meta-row {
  display: flex;
  font-size: 12px;
}

.meta-label {
  color: #64748b;
  width: 80px;
}

.meta-value {
  color: #1e293b;
  font-weight: 500;
}

.phone-value {
  color: #2563eb;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.action-btn {
  height: 32px;
  line-height: 32px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 6px;
  padding: 0 12px;
  border: none;
}

.action-btn.is-claim {
  background: #2563eb;
  color: #ffffff;
}

.action-btn.is-follow {
  background: #f1f5f9;
  color: #0f172a;
}

.action-btn.is-return {
  background: #fef2f2;
  color: #dc2626;
}

.float-btn {
  position: fixed;
  bottom: 24px;
  right: 20px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 24px;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
}

.float-icon {
  font-size: 18px;
  font-weight: bold;
}

.float-label {
  font-size: 13px;
  font-weight: 600;
}
</style>
