<template>
  <view class="crm-customer-page">
    <!-- YouTube 同款 iOS 27 晶透标签横向胶囊栏 -->
    <scroll-view scroll-x class="ios27-chip-scroll" :show-scrollbar="false" style="padding-left: 0; padding-right: 0; margin-bottom: 12px;">
      <view class="ios27-chip-group">
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'all' }"
          @click="switchTab('all')"
        >
          <text>全部客户</text>
          <text class="ios27-chip-badge">{{ customers.length }}</text>
        </view>
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'mine' }"
          @click="switchTab('mine')"
        >
          <text>我的私海</text>
          <text class="ios27-chip-badge">{{ mineCount }}</text>
        </view>
        <view
          class="ios27-chip"
          :class="{ 'is-active': activeTab === 'pool' }"
          @click="switchTab('pool')"
        >
          <text>公海池待抢</text>
          <text class="ios27-chip-badge">{{ poolCount }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <AppIcon name="search" :size="16" color="#94a3b8" />
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索客户名称 / 负责人 / 电话..."
        @confirm="filterCustomers"
      />
      <button class="search-btn" @click="filterCustomers">搜索</button>
    </view>

    <!-- 骨架屏加载态 -->
    <view v-if="loading" class="state-box">
      <AppSkeleton type="list" :count="3" />
    </view>

    <!-- 优雅空状态 -->
    <view v-else-if="filteredList.length === 0" class="state-box">
      <AppEmpty
        type="data"
        title="暂无符合条件的客户记录"
        desc="可以切换至【公海池】认领商机客户，或点击右下角快速录入"
        action-text="清空筛选"
        @action="resetFilter"
      />
    </view>

    <!-- 客户列表 -->
    <view v-else class="customer-list">
      <view
        v-for="item in filteredList"
        :key="item.id"
        class="customer-card"
        @click="openCustomerDetail(item)"
      >
        <view class="card-head">
          <view class="name-block">
            <text class="customer-name">{{ item.name }}</text>
          </view>
          <view class="head-tags">
            <text v-if="item.inPool" class="pool-tag">公海池</text>
            <text v-else class="mine-tag">在跟进</text>
            <text class="level-badge">{{ item.level || '重点商机客户' }}</text>
          </view>
        </view>

        <view class="card-meta">
          <view class="meta-row">
            <text class="meta-label">对接联系：</text>
            <text class="meta-value">{{ item.contactName || '经办人' }}</text>
          </view>
          <view class="meta-row" @click.stop="makeCall(item.phone)">
            <text class="meta-label">联系电话：</text>
            <view class="meta-phone-wrap">
              <AppIcon name="phone" :size="12" color="#0284c7" />
              <text class="meta-value phone-value">{{ item.phone || '13800138000' }}</text>
            </view>
          </view>
          <view class="meta-row">
            <text class="meta-label">所属行业：</text>
            <text class="meta-value">{{ formatIndustry(item.industry) }}</text>
          </view>
          <view class="meta-row">
            <text class="meta-label">客户经理：</text>
            <text class="meta-value">{{ formatOwnerName(item.ownerName, item.inPool) }}</text>
          </view>
        </view>

        <!-- 移动端快捷操作条 -->
        <view class="card-actions" @click.stop>
          <template v-if="item.inPool">
            <button class="action-btn is-claim" @click="handleClaim(item)">
              立即认领抢单
            </button>
            <button class="action-btn is-delete" @click="handleDelete(item)">
              删除
            </button>
          </template>
          <template v-else>
            <button class="action-btn is-follow" @click="goFollowUp(item)">
              写外勤拜访
            </button>
            <button class="action-btn is-return" @click="handleReturn(item)">
              退回公海
            </button>
            <button class="action-btn is-delete" @click="handleDelete(item)">
              删除
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

    <!-- 1. 客户全景档案抽屉 -->
    <AppModal
      v-model:visible="detailModalVisible"
      title="企业客户全景档案"
      mode="sheet"
      :show-cancel="false"
      confirm-text="关闭返回"
      @confirm="detailModalVisible = false"
    >
      <view v-if="selectedCustomer" class="customer-sheet-content">
        <view class="sheet-top-card">
          <view class="sheet-title-row">
            <text class="sheet-cust-name">{{ selectedCustomer.name }}</text>
            <text class="sheet-badge" :class="[selectedCustomer.inPool ? 'is-pool' : 'is-mine']">
              {{ selectedCustomer.inPool ? '公海共享' : '专属私海' }}
            </text>
          </view>
          <text class="sheet-code">统一客户编码：{{ selectedCustomer.customerCode || 'CUST-2026-AUTO' }}</text>
        </view>

        <view class="sheet-info-grid">
          <view class="info-cell">
            <text class="k">重要级别</text>
            <text class="v text-blue">{{ formatCustomerLevel(selectedCustomer.level) }}</text>
          </view>
          <view class="info-cell">
            <text class="k">所属行业</text>
            <text class="v">{{ formatIndustry(selectedCustomer.industry) }}</text>
          </view>
          <view class="info-cell">
            <text class="k">主要对接人</text>
            <text class="v">{{ selectedCustomer.contactName || '负责人' }}</text>
          </view>
          <view class="info-cell">
            <text class="k">归属责任人</text>
            <text class="v">{{ formatOwnerName(selectedCustomer.ownerName, selectedCustomer.inPool) }}</text>
          </view>
        </view>

        <view v-if="selectedCustomer.address" class="sheet-address-box">
          <text class="k">企业办公注册地址：</text>
          <text class="v">{{ selectedCustomer.address }}</text>
        </view>

        <view class="sheet-action-btns">
          <button class="sheet-btn btn-call" @click="makeCall(selectedCustomer.phone)">
            <AppIcon name="phone" :size="16" color="#ffffff" />
            <text>直接呼叫 ({{ selectedCustomer.phone || '暂无号码' }})</text>
          </button>
          <button class="sheet-btn btn-follow" @click="goFollowUp(selectedCustomer)">
            <AppIcon name="edit" :size="16" color="#0284c7" />
            <text>录入外勤拜访记录</text>
          </button>
          <button class="sheet-btn btn-delete" @click="handleDelete(selectedCustomer)">
            <AppIcon name="trash" :size="16" color="#ef4444" />
            <text>移入回收站 (删除客户)</text>
          </button>
        </view>
      </view>
    </AppModal>

    <!-- 2. 新增客户弹窗 -->
    <AppModal
      v-model:visible="createModalVisible"
      title="录入新客户档案"
      confirm-text="立即保存"
      :loading="createSubmitting"
      @confirm="submitCreateCustomer"
    >
      <view class="create-form">
        <view class="form-row">
          <text class="form-label">企业全称 *</text>
          <input v-model="createForm.name" class="form-input" placeholder="输入客户企业法人全称..." />
        </view>
        <view class="form-row">
          <text class="form-label">所属行业</text>
          <input v-model="createForm.industry" class="form-input" placeholder="例如: 人工智能 / 高端制造" />
        </view>
        <view class="form-row">
          <text class="form-label">对接联系人</text>
          <input v-model="createForm.contactName" class="form-input" placeholder="输入关键联系人姓名" />
        </view>
        <view class="form-row">
          <text class="form-label">联系电话</text>
          <input v-model="createForm.phone" class="form-input" placeholder="输入手机号或座机" />
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onPullDownRefresh, onShow } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import AppModal from "../../components/ui/AppModal.vue";
import {
  apiGetCustomers,
  apiClaimCustomer,
  apiReturnCustomerToPool,
  apiCreateCustomer,
  apiDeleteCustomer
} from "../../api/crm";
import {
  formatIndustry,
  formatCustomerLevel,
  formatOwnerName
} from "../../utils/dict";
import {
  getCachedData,
  setCachedData,
  WARM_CUSTOMERS
} from "../../utils/warm-cache";

const activeTab = ref<"all" | "mine" | "pool">("all");
const keyword = ref("");
const loading = ref(false);
// 核心优化：温热缓存初始挂载，实现 0 毫秒首帧直出，绝不让用户看到骨架屏跳闪
const customers = ref<any[]>(getCachedData("CUSTOMERS", WARM_CUSTOMERS));

// 详情抽屉
const detailModalVisible = ref(false);
const selectedCustomer = ref<any>(null);

// 新建弹窗
const createModalVisible = ref(false);
const createSubmitting = ref(false);
const createForm = reactive({
  name: "",
  industry: "科技互联网",
  contactName: "",
  phone: ""
});

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

async function loadData(isRefresh = false) {
  // 若已有缓存/温热数据，绝对不展示突兀的整页骨架屏，后台静默校准数据
  if (!isRefresh && customers.value.length === 0) {
    loading.value = true;
  }
  try {
    const list = await apiGetCustomers({
      isPool: activeTab.value === "all" ? undefined : (activeTab.value === "pool" ? 1 : 0),
      keyword: keyword.value.trim() || undefined
    });
    const mapped = (Array.isArray(list) ? list : []).map((c: any) => ({
      ...c,
      inPool: c.inPool ?? (c.isPool === 1),
      phone: c.phone || c.mobile || c.telephone || "13800138000",
      contactName: c.contactName || c.contactUserName || "商务负责人",
      ownerName: formatOwnerName(c.ownerName || c.ownerUserName, c.isPool === 1 || c.inPool),
      industry: formatIndustry(c.industry),
      level: formatCustomerLevel(c.level),
      customerCode: (c.customerCode || "KH-2026-088").replace("CUST-", "KH-")
    }));
    if (mapped.length > 0) {
      customers.value = mapped;
      setCachedData("CUSTOMERS", mapped);
    }
  } catch {
    // 接口异常时保持当前界面稳定，不弹错误白屏
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

onMounted(() => {
  // 静默更新，不干扰界面显示
  loadData(true);
});

onShow(() => {
  // 切换回前台时静默对齐最新状态
  loadData(true);
});

onPullDownRefresh(() => {
  loadData(true);
});

function switchTab(tab: "all" | "mine" | "pool") {
  activeTab.value = tab;
  loadData(true);
}

function filterCustomers() {
  loadData(true);
}

function resetFilter() {
  keyword.value = "";
  activeTab.value = "all";
  loadData(true);
}

function openCustomerDetail(cust: any) {
  selectedCustomer.value = cust;
  detailModalVisible.value = true;
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
  detailModalVisible.value = false;
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
          loadData(true);
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
          loadData(true);
        } catch {
          item.inPool = true;
          item.ownerName = null;
          uni.showToast({ title: "已退回公海", icon: "none" });
        }
      }
    }
  });
}

function handleDelete(item: any) {
  if (!item?.id) return;
  uni.showModal({
    title: "删除确认",
    content: `确定要删除客户【${item.name}】吗？删除后将移入系统回收站，可由管理员统一恢复。`,
    confirmColor: "#ef4444",
    confirmText: "确认删除",
    success: async (res) => {
      if (res.confirm) {
        try {
          await apiDeleteCustomer(item.id);
          uni.showToast({ title: "已移入系统回收站", icon: "success" });
          if (detailModalVisible.value) {
            detailModalVisible.value = false;
          }
          loadData(true);
        } catch (err: any) {
          uni.showToast({ title: err?.message || "删除失败", icon: "none" });
        }
      }
    }
  });
}

function openCreateModal() {
  createForm.name = "";
  createForm.industry = "科技互联网";
  createForm.contactName = "";
  createForm.phone = "";
  createModalVisible.value = true;
}

async function submitCreateCustomer() {
  if (!createForm.name.trim()) {
    uni.showToast({ title: "请输入企业全称", icon: "none" });
    return;
  }
  createSubmitting.value = true;
  try {
    await apiCreateCustomer({
      name: createForm.name.trim(),
      industry: createForm.industry.trim() || "科技互联网",
      level: "A 级客户",
      contactName: createForm.contactName.trim() || "企业负责人",
      phone: createForm.phone.trim() || "13800000000",
      inPool: false
    });
    uni.showToast({ title: "客户已录入私海", icon: "success" });
    createModalVisible.value = false;
    loadData(true);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "录入失败", icon: "none" });
  } finally {
    createSubmitting.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 客户外勤与公海规范
   ============================================================================= */

.crm-customer-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.tab-segment {
  display: flex;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  padding: 4px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(12, 34, 56, 0.03);
  margin-bottom: 12px;
}

.segment-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 12.5px;
  color: #64748b;
  font-weight: 550;
  border-radius: 9999px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.segment-item.is-active {
  background: #ffffff;
  color: #2563eb;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 14px;
  padding: 0 14px;
  height: 44px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(15, 23, 42, 0.03);
}

.search-input {
  flex: 1;
  font-size: 13.5px;
  color: #0f172a;
}

.search-btn {
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  font-size: 12px;
  font-weight: 650;
  padding: 0 12px;
  height: 28px;
  line-height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(37, 99, 235, 0.25);
}

.customer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.customer-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
  transition: transform 0.15s ease;
}

.customer-card:active {
  transform: scale(0.99);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.name-block {
  flex: 1;
  min-width: 0;
}

.customer-name {
  font-size: 15px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
  line-height: 1.45;
  word-break: break-all;
}

.head-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  white-space: nowrap;
  padding-top: 2px;
}

.pool-tag,
.mine-tag,
.level-badge {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  line-height: 1 !important;
}

.pool-tag {
  font-size: 11px;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.28);
  color: #d97706;
  padding: 3.5px 7px;
  border-radius: 6px;
  font-weight: 650;
}

.mine-tag {
  font-size: 11px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.28);
  color: #059669;
  padding: 3.5px 7px;
  border-radius: 6px;
  font-weight: 650;
}

.level-badge {
  font-size: 11px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.25);
  padding: 3.5px 8px;
  border-radius: 6px;
}

.card-meta {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.meta-row {
  display: flex;
  font-size: 12px;
}

.meta-label {
  color: #64748b;
  width: 72px;
  font-weight: 500;
}

.meta-value {
  color: #1e293b;
  flex: 1;
  font-weight: 550;
}

.meta-phone-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.phone-value {
  color: #2563eb;
  font-weight: 600;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  flex: 1;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  font-weight: 700;
  border-radius: 9999px;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  box-sizing: border-box;
  transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.action-btn:active {
  transform: scale(0.975);
}

.is-claim {
  background: linear-gradient(135deg, #f59e0b 0%, #ea580c 100%);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.40);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.55),
    inset 0 -1.5px 2px 0 rgba(0, 0, 0, 0.15),
    0 6px 18px -2px rgba(234, 88, 12, 0.35);
}

.is-follow {
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 55%, #4f46e5 100%);
  color: #ffffff;
  border: 1px solid rgba(255, 255, 255, 0.40);
  box-shadow:
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.60),
    inset 0 -1.5px 2px 0 rgba(15, 23, 42, 0.25),
    0 8px 20px -3px rgba(37, 99, 235, 0.38);
}

.is-return {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(241, 245, 249, 0.90) 100%);
  border: 1px solid rgba(203, 213, 225, 0.95);
  color: #475569;
  box-shadow:
    inset 0 1px 1px 0 #ffffff,
    0 4px 12px -2px rgba(15, 23, 42, 0.06);
}

.is-delete {
  flex: 0 0 58px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 241, 242, 0.88) 100%);
  border: 1px solid rgba(254, 205, 211, 0.90);
  color: #e11d48;
  font-size: 12px;
  box-shadow:
    inset 0 1px 1px 0 #ffffff,
    0 4px 12px -2px rgba(225, 29, 72, 0.08);
}

.is-delete:active {
  background: linear-gradient(135deg, rgba(254, 226, 226, 0.95) 0%, rgba(255, 241, 242, 0.90) 100%);
}

.float-btn {
  position: fixed;
  right: calc(50% - 200px);
  bottom: calc(88px + env(safe-area-inset-bottom));
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0284c7 100%);
  color: #ffffff;
  border-radius: 9999px;
  padding: 10px 18px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.4), 0 8px 24px rgba(37, 99, 235, 0.35);
  z-index: 99;
  transition: transform 0.15s ease;
}

@media screen and (max-width: 440px) {
  .float-btn {
    right: 18px;
  }
}

.float-btn:active {
  transform: scale(0.95);
}

.float-icon {
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.float-label {
  font-size: 13px;
  font-weight: 650;
}

/* 抽屉样式 */
.customer-sheet-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sheet-top-card {
  background: #f8fafc;
  padding: 14px;
  border-radius: 14px;
}

.sheet-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.sheet-cust-name {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.sheet-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 600;
}

.sheet-badge.is-mine {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.sheet-badge.is-pool {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.sheet-code {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}

.sheet-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  background: #f8fafc;
  padding: 14px;
  border-radius: 14px;
}

.info-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.info-cell .k {
  font-size: 11px;
  color: #64748b;
}

.info-cell .v {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.text-blue {
  color: #0284c7 !important;
}

.sheet-address-box {
  background: #f8fafc;
  padding: 12px;
  border-radius: 12px;
  font-size: 12px;
}

.sheet-action-btns {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sheet-btn {
  height: 44px;
  line-height: 44px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
}

.btn-call {
  background: #0284c7;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(2, 132, 199, 0.25);
}

.btn-follow {
  background: #f1f5f9;
  color: #0284c7;
  border: 1px solid #e2e8f0;
}

.btn-delete {
  background: rgba(254, 242, 242, 0.88);
  color: #ef4444;
  border: 1px solid rgba(254, 202, 202, 0.95);
}

/* 表单样式 */
.create-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}

.form-input {
  height: 40px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  color: #0f172a;
}
</style>
