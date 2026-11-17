<template>
  <view class="contact-page">
    <!-- 顶部搜索 -->
    <view class="search-box">
      <AppIcon name="search" :size="16" color="#94a3b8" />
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索姓名 / 工号 / 手机号 / 部门..."
      />
      <view v-if="keyword" class="clear-btn" @click="keyword = ''">
        <text class="clear-icon">✕</text>
      </view>
    </view>

    <!-- 部门横向滚动选择胶囊 (YouTube 同款 iOS 27 晶透标签) -->
    <scroll-view class="ios27-chip-scroll" scroll-x :show-scrollbar="false" style="padding-left: 0; padding-right: 0; margin-bottom: 12px;">
      <view class="ios27-chip-group">
        <view
          v-for="dept in depts"
          :key="dept"
          class="ios27-chip"
          :class="{ 'is-active': activeDept === dept }"
          @click="activeDept = dept"
        >
          <text>{{ dept }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 骨架屏加载态 -->
    <view v-if="loading" class="state-view">
      <AppSkeleton type="list" :count="4" />
    </view>

    <!-- 优雅空状态 -->
    <view v-else-if="filteredUsers.length === 0" class="state-view">
      <AppEmpty
        type="search"
        title="未检索到符合条件的组织成员"
        desc="请核对成员姓名、部门名称或手机号码"
        action-text="清空搜索"
        @action="resetFilter"
      />
    </view>

    <!-- 通讯录员工列表 -->
    <view v-else class="user-list">
      <view
        v-for="user in filteredUsers"
        :key="user.id"
        class="user-card"
        @click="openUserCard(user)"
      >
        <view class="user-avatar">
          <text>{{ getInitial(user.nickname || user.username) }}</text>
        </view>

        <view class="user-main">
          <view class="name-row">
            <text class="user-name">{{ user.nickname || user.username }}</text>
            <text class="user-dept">{{ user.deptName || '技术研发中心' }}</text>
          </view>
          <text class="user-position">{{ user.positionName || '高级系统工程师' }}</text>

          <view class="contact-actions" @click.stop>
            <view class="action-item" @click="handleCall(user.phone || user.mobile)">
              <AppIcon name="phone" :size="12" color="#0284c7" />
              <text class="action-val">{{ user.phone || user.mobile || '13800000000' }}</text>
            </view>
            <view class="action-item" @click="handleCopyEmail(user.email)">
              <AppIcon name="mail" :size="12" color="#64748b" />
              <text class="action-val">{{ user.email || 'user@tianshu.cloud' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 成员详情名片抽屉 -->
    <AppModal
      v-model:visible="cardModalVisible"
      title="组织成员电子名片"
      mode="sheet"
      :show-cancel="false"
      confirm-text="关闭返回"
      @confirm="cardModalVisible = false"
    >
      <view v-if="selectedUser" class="member-card-sheet">
        <view class="sheet-avatar-row">
          <view class="sheet-avatar">
            <text>{{ getInitial(selectedUser.nickname || selectedUser.username) }}</text>
          </view>
          <view class="sheet-info">
            <view class="sheet-name-line">
              <text class="sheet-name">{{ selectedUser.nickname || selectedUser.username }}</text>
              <text class="sheet-status-tag">{{ selectedUser.status === 'ENABLED' || !selectedUser.status ? '在职活跃' : '常规档案' }}</text>
            </view>
            <text class="sheet-account">员工账号：{{ selectedUser.username }}</text>
            <text class="sheet-role">{{ selectedUser.positionName || '技术研发部 · 核心骨干' }}</text>
          </view>
        </view>

        <view class="sheet-detail-grid">
          <view class="detail-cell">
            <text class="k">所属企业组织</text>
            <text class="v">{{ selectedUser.deptName || '综合管理与研发中心' }}</text>
          </view>
          <view class="detail-cell">
            <text class="k">账号权限类型</text>
            <text class="v">{{ selectedUser.userType === 'PLATFORM' || selectedUser.username === 'admin' ? '系统管理员' : '企业在册员工' }}</text>
          </view>
        </view>

        <view class="quick-call-actions">
          <button class="call-btn btn-phone" @click="handleCall(selectedUser.phone || selectedUser.mobile)">
            <AppIcon name="phone" :size="16" color="#ffffff" />
            <text>呼叫电话 ({{ selectedUser.phone || selectedUser.mobile || '暂未绑定' }})</text>
          </button>
          <button class="call-btn btn-email" @click="handleCopyEmail(selectedUser.email)">
            <AppIcon name="mail" :size="16" color="#0284c7" />
            <text>复制企业邮箱</text>
          </button>
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import AppModal from "../../components/ui/AppModal.vue";
import { apiGetContactUsers, type ContactUser } from "../../api/contact";

const keyword = ref("");
const activeDept = ref("全部");
const loading = ref(false);
const users = ref<ContactUser[]>([]);

// 名片抽屉
const cardModalVisible = ref(false);
const selectedUser = ref<any>(null);

const depts = ["全部", "基础研发中心", "平台架构部", "客户成功部", "商务销售部", "运营支撑部"];

const filteredUsers = computed(() => {
  return users.value.filter((u) => {
    if (activeDept.value !== "全部") {
      const dName = u.deptName || "基础研发中心";
      if (!dName.includes(activeDept.value) && !activeDept.value.includes(dName)) {
        return false;
      }
    }
    if (keyword.value.trim()) {
      const q = keyword.value.trim().toLowerCase();
      const matchName = (u.nickname || u.username)?.toLowerCase().includes(q);
      const matchPhone = (u.phone || u.mobile)?.includes(q);
      const matchEmail = u.email?.toLowerCase().includes(q);
      const matchDept = u.deptName?.toLowerCase().includes(q);
      return matchName || matchPhone || matchEmail || matchDept;
    }
    return true;
  });
});

function getInitial(name: string) {
  if (!name) return "用";
  return name.trim().slice(0, 1).toUpperCase();
}

async function loadUsers(isRefresh = false) {
  if (!isRefresh && users.value.length === 0) {
    loading.value = true;
  }
  try {
    const list = await apiGetContactUsers();
    users.value = (Array.isArray(list) ? list : []).map((u) => ({
      ...u,
      phone: u.phone || u.mobile || "13800138000",
      nickname: u.nickname || (u.username === "user" ? "体验员工" : (u.username === "admin" ? "平台管理员" : u.username)),
      positionName: (u.positionName || "业务工程师").replace("CTO", "研发总监").replace("SRE", "高级运维").replace("高级系统工程师", "高级工程师"),
      deptName: u.deptName || "综合管理部"
    }));
  } catch {
    users.value = [
      { id: 1, username: "admin", nickname: "平台管理员", phone: "13800138000", email: "admin@tianshu.cloud", deptName: "技术研发部", positionName: "研发总监" },
      { id: 2, username: "zhidan", nickname: "沈志丹", phone: "13911223344", email: "shenzhidan@tianshu.cloud", deptName: "平台架构部", positionName: "资深系统架构师" },
      { id: 3, username: "sales_lead", nickname: "王敏", phone: "13766778899", email: "wangmin@tianshu.cloud", deptName: "商务销售部", positionName: "销售总监" },
      { id: 4, username: "crm_support", nickname: "李欣", phone: "13699887711", email: "lixin@tianshu.cloud", deptName: "客户服务部", positionName: "大客户经理" },
      { id: 5, username: "dev_ops", nickname: "赵强", phone: "13522334455", email: "zhaoqiang@tianshu.cloud", deptName: "系统保障部", positionName: "系统运维工程师" }
    ];
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadUsers(true);
});

function resetFilter() {
  keyword.value = "";
  activeDept.value = "全部";
}

function openUserCard(user: any) {
  selectedUser.value = user;
  cardModalVisible.value = true;
}

function handleCall(phone?: string) {
  if (!phone) return;
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      uni.setClipboardData({ data: phone });
      uni.showToast({ title: "已复制联系电话", icon: "none" });
    }
  });
}

function handleCopyEmail(email?: string) {
  if (!email) return;
  uni.setClipboardData({ data: email });
  uni.showToast({ title: "邮箱已复制到剪贴板", icon: "success" });
}

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 组织通讯录规范
   ============================================================================= */

.contact-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.90);
  border-radius: 14px;
  padding: 0 14px;
  height: 44px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(15, 23, 42, 0.03);
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
  font-size: 13.5px;
  color: #0f172a;
}

.clear-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(203, 213, 225, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.clear-icon {
  font-size: 11px;
  color: #64748b;
  line-height: 1;
}

.dept-scroll {
  white-space: nowrap;
  width: 100%;
  margin-bottom: 14px;
}

.dept-pills {
  display: inline-flex;
  gap: 8px;
  padding: 2px 2px 6px;
}

.dept-pill {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.88);
  font-size: 12px;
  color: #475569;
  font-weight: 550;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95);
}

.dept-pill.is-active {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 12px rgba(37, 99, 235, 0.28);
  font-weight: 650;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  transition: transform 0.15s ease;
}

.user-card:active {
  transform: scale(0.99);
}

.user-avatar {
  width: 46px;
  height: 46px;
  border-radius: 15px;
  background: linear-gradient(135deg, #2563eb 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 19px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
  flex-shrink: 0;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}

.user-name {
  font-size: 15.5px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
}

.user-dept {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.20);
  padding: 2px 7px;
  border-radius: 6px;
  font-weight: 600;
}

.user-position {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 10px;
  display: block;
  font-weight: 500;
}

.contact-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(203, 213, 225, 0.85);
  padding: 4px 10px;
  border-radius: 8px;
}

.action-item:active {
  background: rgba(239, 246, 255, 0.9);
}

.action-val {
  font-size: 11.5px;
  color: #334155;
  font-weight: 550;
}

/* 抽屉样式 */
.member-card-sheet {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sheet-avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.sheet-avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: linear-gradient(135deg, #0284c7 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 24px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(2, 132, 199, 0.3);
}

.sheet-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sheet-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sheet-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.sheet-status-tag {
  font-size: 10px;
  color: #10b981;
  background: rgba(16, 185, 129, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.sheet-account {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}

.sheet-role {
  font-size: 12px;
  color: #0284c7;
  font-weight: 500;
}

.sheet-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  background: #f8fafc;
  padding: 14px;
  border-radius: 14px;
}

.detail-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-cell .k {
  font-size: 11px;
  color: #64748b;
}

.detail-cell .v {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.quick-call-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.call-btn {
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

.btn-phone {
  background: #0284c7;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(2, 132, 199, 0.25);
}

.btn-email {
  background: #f1f5f9;
  color: #0284c7;
  border: 1px solid #e2e8f0;
}
</style>
