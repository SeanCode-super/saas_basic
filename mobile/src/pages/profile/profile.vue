<template>
  <view class="mobile-profile-page">
    <!-- 用户个人名片卡 -->
    <view class="profile-card">
      <view class="avatar-lg"><text>{{ userInitial }}</text></view>
      <text class="user-name">{{ userName }}</text>
      <text class="user-account">员工账号：{{ userAccount }}</text>
      <view class="badge-row">
        <text class="role-badge">{{ userRoleLabel }}</text>
        <text class="tenant-badge">{{ currentTenantName }}</text>
      </view>
      <view class="edit-profile-btn" @click="editModalVisible = true">
        <AppIcon name="user" :size="12" color="#0284c7" />
        <text class="edit-text">编辑个人资料</text>
      </view>
    </view>

    <!-- 所属企业组织与工作空间卡片 -->
    <view class="section-card">
      <view class="section-card-head">
        <view class="head-title-wrap">
          <AppIcon name="building" :size="16" color="#0284c7" />
          <text class="section-card-title">当前所属企业空间</text>
        </view>
        <view class="tenant-isolated-pill">
          <text class="isolated-dot" />
          <text class="isolated-text">数据安全隔离</text>
        </view>
      </view>
      <view class="tenant-display-box is-static">
        <view class="tenant-icon">
          <AppIcon name="tenant" :size="20" color="#0284c7" />
        </view>
        <view class="tenant-info">
          <text class="tenant-title">已认证企业工作空间</text>
          <text class="tenant-val">{{ currentTenantName }}</text>
        </view>
        <view class="tenant-code-badge">
          <text class="font-mono">#{{ currentTenantCode }}</text>
        </view>
      </view>
    </view>

    <!-- 常用功能列表 -->
    <view class="setting-list">
      <view class="setting-item" @click="navTo('/pages/workflow/list')">
        <view class="item-left">
          <view class="setting-icon-box bg-blue">
            <AppIcon name="workflow" :size="16" color="#2563eb" />
          </view>
          <text class="item-title">我的审批代办</text>
        </view>
        <AppIcon name="chevron-right" :size="14" color="#64748b" />
      </view>

      <view class="setting-item" @click="navTo('/pages/crm/customers')">
        <view class="item-left">
          <view class="setting-icon-box bg-emerald">
            <AppIcon name="building" :size="16" color="#059669" />
          </view>
          <text class="item-title">我的私海客户与公海</text>
        </view>
        <AppIcon name="chevron-right" :size="14" color="#64748b" />
      </view>

      <view class="setting-item" @click="navTo('/pages/erp/inventory')">
        <view class="item-left">
          <view class="setting-icon-box bg-amber">
            <AppIcon name="cpu" :size="16" color="#d97706" />
          </view>
          <text class="item-title">云仓物料与库存分布</text>
        </view>
        <AppIcon name="chevron-right" :size="14" color="#64748b" />
      </view>

      <view class="setting-item" @click="navTo('/pages/notify/index')">
        <view class="item-left">
          <view class="setting-icon-box bg-indigo">
            <AppIcon name="bell" :size="16" color="#4f46e5" />
          </view>
          <text class="item-title">消息与协同通知</text>
        </view>
        <AppIcon name="chevron-right" :size="14" color="#64748b" />
      </view>

      <view class="setting-item" @click="clearCache">
        <view class="item-left">
          <view class="setting-icon-box bg-rose">
            <AppIcon name="clean" :size="16" color="#e11d48" />
          </view>
          <text class="item-title">清理本地离线缓存</text>
        </view>
        <text class="item-val">2.4 MB</text>
      </view>

      <view class="setting-item">
        <view class="item-left">
          <view class="setting-icon-box bg-cyan">
            <AppIcon name="cpu" :size="16" color="#0284c7" />
          </view>
          <text class="item-title">运行环境支持</text>
        </view>
        <text class="item-val">企业智能协同引擎</text>
      </view>

      <view class="setting-item">
        <view class="item-left">
          <view class="setting-icon-box bg-purple">
            <AppIcon name="shield" :size="16" color="#9333ea" />
          </view>
          <text class="item-title">系统软件版本</text>
        </view>
        <text class="item-val">天枢协同办公 标准企业版</text>
      </view>
    </view>

    <!-- 安全退出 -->
    <view class="logout-box">
      <button class="logout-btn" @click="handleLogout">安全退出登录</button>
    </view>

    <!-- 个人资料编辑模态框 (AppModal Dialog Mode) -->
    <AppModal
      v-model="editModalVisible"
      mode="dialog"
      title="编辑个人资料"
      subtitle="更新您的对外显示昵称与联系方式"
      confirm-text="保存资料"
      :confirm-loading="savingProfile"
      @confirm="submitEditProfile"
    >
      <view class="edit-modal-form">
        <view class="form-row">
          <text class="form-lbl">员工姓名 / 昵称</text>
          <input v-model="editForm.nickname" class="edit-input" placeholder="输入姓名或昵称" />
        </view>
        <view class="form-row">
          <text class="form-lbl">工作联系邮箱</text>
          <input v-model="editForm.email" class="edit-input" placeholder="输入电子邮箱" />
        </view>
        <view class="form-row">
          <text class="form-lbl">手机号码</text>
          <input v-model="editForm.phone" class="edit-input" placeholder="输入手机号码" />
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppModal from "../../components/ui/AppModal.vue";
import { apiGetUserInfo } from "../../api/auth";

const userName = ref("体验员工 (普通用户)");
const userAccount = ref("user");
const userInitial = ref("普");
const userRoleLabel = ref("企业普通员工 · 业务授权");
const currentTenantName = ref("平台管理总部");
const currentTenantCode = ref("platform");
const currentTenantId = ref("1");

const editModalVisible = ref(false);
const savingProfile = ref(false);

const editForm = reactive({
  nickname: "",
  email: "user@tianshu.cloud",
  phone: "13800138000"
});

function applyUserData(u: any) {
  if (!u) return;
  userName.value = u.nickname || u.username || "用户";
  userAccount.value = u.username || "";
  userInitial.value = (u.nickname || u.username || "用").slice(0, 1);
  editForm.nickname = u.nickname || u.username || "";
  if (u.email) editForm.email = u.email;
  if (u.phone) editForm.phone = u.phone;

  if (u.tenantCode) {
    currentTenantCode.value = u.tenantCode;
    if (u.tenantCode === "platform" || u.username === "admin") {
      currentTenantName.value = "平台管理总部";
    } else {
      currentTenantName.value = u.tenantName || `${u.tenantCode.toUpperCase()} 企业空间`;
    }
  }
  if (u.tenantId) {
    currentTenantId.value = String(u.tenantId);
  }

  if (u.userType === "SUPER_ADMIN" || u.username === "admin" || (u.roleIds && u.roleIds.includes(1))) {
    userRoleLabel.value = "超级管理员 · 平台管控";
  } else if (u.userType === "TENANT_ADMIN") {
    userRoleLabel.value = "租户管理员 · 企业中枢";
  } else {
    userRoleLabel.value = "企业普通员工 · 业务授权";
  }
}

async function loadProfile() {
  try {
    const rawUser = uni.getStorageSync("sb_current_user");
    if (rawUser) {
      applyUserData(typeof rawUser === "string" ? JSON.parse(rawUser) : rawUser);
    }
    const remoteUser = await apiGetUserInfo();
    if (remoteUser) {
      applyUserData(remoteUser);
      uni.setStorageSync("sb_current_user", JSON.stringify(remoteUser));
    }
  } catch {}
}

onPullDownRefresh(async () => {
  await loadProfile();
  uni.stopPullDownRefresh();
});

async function submitEditProfile() {
  savingProfile.value = true;
  try {
    userName.value = editForm.nickname || userAccount.value;
    userInitial.value = (editForm.nickname || userAccount.value).slice(0, 1);
    const rawUser = uni.getStorageSync("sb_current_user");
    if (rawUser) {
      const u = typeof rawUser === "string" ? JSON.parse(rawUser) : rawUser;
      u.nickname = editForm.nickname;
      u.email = editForm.email;
      u.phone = editForm.phone;
      uni.setStorageSync("sb_current_user", JSON.stringify(u));
    }
    uni.showToast({ title: "个人资料更新成功", icon: "success" });
    editModalVisible.value = false;
  } finally {
    savingProfile.value = false;
  }
}

function navTo(url: string) {
  if (url.includes("/pages/workflow/list")) {
    uni.switchTab({ url });
  } else {
    uni.navigateTo({ url });
  }
}

function clearCache() {
  uni.showModal({
    title: "清理提示",
    content: "确定清理本地离线缓存吗？（登录凭据将被安全保留）",
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: "本地缓存已清理完毕", icon: "success" });
      }
    }
  });
}

function handleLogout() {
  uni.showModal({
    title: "退出提示",
    content: "确定安全退出当前企业空间会话吗？",
    success: (res) => {
      if (res.confirm) {
        const lastTenant = currentTenantCode.value;
        uni.removeStorageSync("sb_access_token");
        uni.removeStorageSync("sb_refresh_token");
        uni.removeStorageSync("sb_current_user");
        if (lastTenant && lastTenant !== "platform") {
          uni.reLaunch({ url: `/pages/login/login?tenantCode=${encodeURIComponent(lastTenant)}` });
        } else {
          uni.reLaunch({ url: "/pages/login/login" });
        }
      }
    }
  });
}

onMounted(() => {
  loadProfile();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 个人中心与多租户规范
   ============================================================================= */

.mobile-profile-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 用户个人名片卡 */
.profile-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 22px;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  margin-bottom: 14px;
}

.avatar-lg {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 12px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.35);
}

.user-name {
  font-size: 19px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
  margin-bottom: 4px;
}

.user-account {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 10px;
  font-weight: 500;
}

.badge-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 12px;
}

.role-badge {
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.22);
  color: #2563eb;
  font-size: 11px;
  font-weight: 650;
  padding: 3px 10px;
  border-radius: 9999px;
}

.tenant-badge {
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(203, 213, 225, 0.85);
  color: #475569;
  font-size: 11px;
  font-weight: 550;
  padding: 3px 10px;
  border-radius: 9999px;
}

.edit-profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border-radius: 9999px;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.22);
  transition: transform 0.15s ease;
}

.edit-profile-btn:active {
  transform: scale(0.96);
}

.edit-text {
  font-size: 11.5px;
  font-weight: 650;
  color: #2563eb;
}

/* 租户空间管理卡片 */
.section-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 20px;
  padding: 16px;
  margin-bottom: 14px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.section-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.head-title-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-card-title {
  font-size: 14px;
  font-weight: 750;
  color: #0f172a;
}

.tenant-isolated-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 9999px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.22);
}

.isolated-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.isolated-text {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
}

.tenant-display-box {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 14px;
  padding: 12px;
}

.tenant-display-box.is-static {
  cursor: default;
}

.tenant-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.10);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
}

.tenant-info {
  flex: 1;
}

.tenant-title {
  display: block;
  font-size: 10.5px;
  color: #94a3b8;
}

.tenant-val {
  display: block;
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
}

.tenant-code-badge {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.18);
  padding: 3px 8px;
  border-radius: 8px;
  font-weight: 600;
}

/* 列表区 */
.setting-list {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 20px;
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  transition: background 0.15s ease;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-item:active {
  background: rgba(255, 255, 255, 0.85);
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-icon-box {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-blue { background: rgba(37, 99, 235, 0.12); }
.bg-emerald { background: rgba(5, 150, 105, 0.12); }
.bg-amber { background: rgba(217, 119, 6, 0.12); }
.bg-indigo { background: rgba(79, 70, 229, 0.12); }
.bg-rose { background: rgba(225, 29, 72, 0.12); }
.bg-cyan { background: rgba(2, 132, 199, 0.12); }
.bg-purple { background: rgba(147, 51, 234, 0.12); }

.item-title {
  font-size: 13.5px;
  font-weight: 550;
  color: #1e293b;
}

.item-val {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

/* 退出登录 */
.logout-box {
  margin-top: 20px;
}

.logout-btn {
  background: rgba(254, 242, 242, 0.85);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 9999px;
  font-size: 14px;
  font-weight: 650;
  height: 46px;
  line-height: 46px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.8);
  transition: transform 0.15s ease;
}

.logout-btn:active {
  transform: scale(0.98);
}

.font-mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }

/* 编辑资料表单 */
.edit-modal-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 6px 0;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-lbl {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.edit-input {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #0f172a;
}
</style>
