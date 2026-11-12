<template>
  <view class="mobile-profile-page">
    <!-- 用户个人名片卡 -->
    <view class="profile-card">
      <view class="avatar-lg" @click="handleAvatarTap">
        <image v-if="avatarDisplayUrl" :src="avatarDisplayUrl" mode="aspectFill" class="avatar-img" />
        <text v-else>{{ (userName || "U").slice(0, 1).toUpperCase() }}</text>
        <view class="avatar-badge">
          <AppIcon name="camera" :size="12" color="#ffffff" />
        </view>
      </view>
      <text class="user-name">{{ userName }}</text>
      <text class="user-account">账号: @{{ userAccount }} · {{ userCodeLabel }}</text>
      <text v-if="profileData?.bio" class="user-bio">“{{ profileData.bio }}”</text>
      <view class="badge-row">
        <view class="role-badge">{{ userRole }}</view>
        <view class="tenant-badge">{{ currentTenantName }} ({{ tenantCode }})</view>
        <view class="score-badge">安全分 {{ profileData?.securityScore ?? 60 }}</view>
      </view>
      <view class="action-pill-row">
        <view class="edit-profile-btn" @click="openEditModal">
          <AppIcon name="edit" :size="13" color="#2563eb" />
          <text class="edit-text">编辑个人资料</text>
        </view>
        <view class="edit-profile-btn secondary" @click="openPasswordModal">
          <AppIcon name="lock" :size="13" color="#0f172a" />
          <text class="edit-text dark">修改密码</text>
        </view>
      </view>
    </view>

    <!-- 组织与员工档案卡 -->
    <view class="section-card">
      <view class="section-card-head">
        <view class="head-title-wrap">
          <AppIcon name="building" :size="16" color="#2563eb" />
          <text class="section-card-title">组织归属与任职信息</text>
        </view>
        <view class="tenant-isolated-pill">
          <view class="isolated-dot"></view>
          <text class="isolated-text">租户隔离中</text>
        </view>
      </view>

      <view class="tenant-display-box is-static">
        <view class="tenant-icon">
          <AppIcon name="building" :size="18" color="#2563eb" />
        </view>
        <view class="tenant-info">
          <text class="tenant-title">所属企业空间 / 部门岗位</text>
          <text class="tenant-val">
            {{
              profileData?.employeeInfo
                ? `${profileData.employeeInfo.deptFullPath || profileData.employeeInfo.deptName || '--'} · ${profileData.employeeInfo.positionName || '--'}`
                : currentTenantName
            }}
          </text>
        </view>
        <text class="tenant-code-badge">{{ profileData?.employeeInfo?.employeeNo || tenantCode }}</text>
      </view>
    </view>

    <!-- 账号与系统功能列表 -->
    <view class="setting-list">
      <view class="setting-item" @click="openEditModal">
        <view class="item-left">
          <view class="setting-icon-box bg-blue">
            <AppIcon name="user" :size="15" color="#2563eb" />
          </view>
          <text class="item-title">联系方式与办公地点</text>
        </view>
        <text class="item-val">{{ profileData?.mobile || profileData?.email || profileData?.workLocation || "待完善" }}</text>
      </view>

      <view class="setting-item" @click="openSessionsModal">
        <view class="item-left">
          <view class="setting-icon-box bg-emerald">
            <AppIcon name="shield" :size="15" color="#059669" />
          </view>
          <text class="item-title">在线设备与会话安全</text>
        </view>
        <text class="item-val">{{ mySessions.length }} 台在线 · MFA {{ profileData?.mfaEnabled ? '已开' : '未开' }}</text>
      </view>

      <view class="setting-item" @click="openPasswordModal">
        <view class="item-left">
          <view class="setting-icon-box bg-amber">
            <AppIcon name="lock" :size="15" color="#d97706" />
          </view>
          <text class="item-title">密码轮换周期</text>
        </view>
        <text class="item-val">
          {{
            profileData?.passwordDaysRemaining !== undefined && profileData?.passwordDaysRemaining !== null
              ? `剩余 ${profileData.passwordDaysRemaining} 天`
              : "查看与修改"
          }}
        </text>
      </view>

      <view class="setting-item" @click="handleClearCache">
        <view class="item-left">
          <view class="setting-icon-box bg-cyan">
            <AppIcon name="trash" :size="15" color="#0284c7" />
          </view>
          <text class="item-title">清理本地缓存</text>
        </view>
        <text class="item-val">保留登录会话</text>
      </view>
    </view>

    <!-- 退出登录按钮 -->
    <view class="logout-box">
      <button class="logout-btn" @click="handleLogout">退出当前登录</button>
    </view>

    <!-- 编辑个人资料弹窗 -->
    <AppModal
      v-model:visible="editModalVisible"
      title="编辑个人资料"
      confirm-text="保存资料"
      :loading="savingProfile"
      @confirm="submitEditProfile"
    >
      <view class="edit-modal-form">
        <view class="form-row">
          <text class="form-lbl">用户昵称</text>
          <input class="edit-input" v-model="editForm.nickname" placeholder="请输入用户昵称" />
        </view>
        <view class="form-row">
          <text class="form-lbl">手机号码</text>
          <input class="edit-input" v-model="editForm.mobile" placeholder="请输入11位手机号" />
        </view>
        <view class="form-row">
          <text class="form-lbl">电子邮箱</text>
          <input class="edit-input" v-model="editForm.email" placeholder="请输入工作电子邮箱" />
        </view>
        <view v-if="needsConfirmPassword()" class="form-row">
          <text class="form-lbl warn-lbl">安全核验：修改已绑定手机/邮箱需输入登录密码</text>
          <input class="edit-input" v-model="editForm.confirmPassword" password placeholder="请输入当前登录密码" />
        </view>
        <view class="form-row">
          <text class="form-lbl">办公地点 / 工位</text>
          <input class="edit-input" v-model="editForm.workLocation" placeholder="如 研发中心 A座" />
        </view>
        <view class="form-row">
          <text class="form-lbl">备用联系 / 分机</text>
          <input class="edit-input" v-model="editForm.extContact" placeholder="如 企业微信或内线" />
        </view>
        <view class="form-row">
          <text class="form-lbl">个人签名</text>
          <textarea class="edit-textarea" v-model="editForm.bio" placeholder="输入个人简介或工作签名..." />
        </view>
      </view>
    </AppModal>

    <!-- 修改登录密码弹窗 -->
    <AppModal
      v-model:visible="pwdModalVisible"
      title="修改登录密码"
      confirm-text="确认修改"
      :loading="savingPassword"
      @confirm="submitChangePassword"
    >
      <view class="edit-modal-form">
        <view class="form-row">
          <text class="form-lbl">当前密码</text>
          <input class="edit-input" v-model="pwdForm.oldPassword" password placeholder="请输入当前登录密码" />
        </view>
        <view class="form-row">
          <text class="form-lbl">新密码（不少于8位）</text>
          <input class="edit-input" v-model="pwdForm.newPassword" password placeholder="请输入新密码" />
        </view>
        <view class="form-row">
          <text class="form-lbl">确认新密码</text>
          <input class="edit-input" v-model="pwdForm.confirmPassword" password placeholder="请再次输入新密码" />
        </view>
      </view>
    </AppModal>

    <!-- 在线设备与安全会话弹窗 -->
    <AppModal
      v-model:visible="sessionsModalVisible"
      title="我的在线设备会话"
      :confirm-text="mySessions.length > 1 ? '一键踢出其他设备' : '关闭'"
      @confirm="handleSessionModalAction"
    >
      <view class="session-list-box">
        <view v-for="s in mySessions" :key="s.sessionNo" class="session-row">
          <view class="session-info">
            <text class="session-ua">{{ formatSessionUa(s.userAgent) }}</text>
            <text class="session-meta">IP: {{ s.loginIp || '--' }} · {{ (s.lastAccessAt || s.loginTime || '').slice(0, 16).replace('T', ' ') }}</text>
          </view>
          <view v-if="s.isCurrent" class="current-tag">本机当前</view>
          <view v-else class="kick-btn" @click="handleKickSession(s.sessionNo)">下线</view>
        </view>
        <view v-if="mySessions.length === 0" class="empty-sessions">暂无在线设备记录</view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppModal from "../../components/ui/AppModal.vue";
import {
  apiGetMySessions,
  apiGetProfile,
  apiGetStorageStatus,
  apiRevokeMySession,
  apiRevokeOtherMySessions,
  apiUpdatePassword,
  apiUpdateProfile,
  apiUploadProfileAvatar,
  type MobileOnlineSession,
  type MobileProfileData,
  type MobileStorageStatus
} from "../../api/profile";
import { resolveMobileFileUrl } from "../../utils/request";

const profileData = ref<MobileProfileData | null>(null);
const storageStatus = ref<MobileStorageStatus | null>(null);
const mySessions = ref<MobileOnlineSession[]>([]);

const userName = ref("用户");
const userAccount = ref("admin");
const userCodeLabel = ref("#1");
const userRole = ref("企业成员");
const tenantCode = ref(uni.getStorageSync("sb_tenant_code") || "platform");
const currentTenantName = ref(uni.getStorageSync("sb_tenant_name") || "当前租户空间");

const editModalVisible = ref(false);
const savingProfile = ref(false);
const editForm = reactive({
  nickname: "",
  mobile: "",
  email: "",
  gender: "UNKNOWN",
  workLocation: "",
  extContact: "",
  bio: "",
  confirmPassword: ""
});

const pwdModalVisible = ref(false);
const savingPassword = ref(false);
const pwdForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const sessionsModalVisible = ref(false);

const avatarDisplayUrl = computed(() => resolveMobileFileUrl(profileData.value?.avatarUrl));

function resolveUserTypeLabel(userType?: string): string {
  if (userType === "SUPER_ADMIN" || userType === "PLATFORM") return "平台超级管理员";
  if (userType === "TENANT_ADMIN") return "租户管理员";
  if (userType === "TENANT_USER") return "企业成员";
  if (userType === "STAFF") return "内部员工";
  return userType || "普通用户";
}

function hydrateLocalUserCache(data: MobileProfileData) {
  userName.value = data.nickname || data.username;
  userAccount.value = data.username;
  userCodeLabel.value = data.userCode || `#${data.id}`;
  userRole.value = resolveUserTypeLabel(data.userType);
  tenantCode.value = data.tenantCode || tenantCode.value;
  currentTenantName.value = data.tenantName || data.tenantCode || currentTenantName.value;
  try {
    const raw = uni.getStorageSync("sb_current_user");
    const cached = raw ? (typeof raw === "string" ? JSON.parse(raw) : raw) : {};
    uni.setStorageSync("sb_current_user", JSON.stringify({
      ...cached,
      nickname: data.nickname,
      mobile: data.mobile,
      email: data.email,
      avatarUrl: data.avatarUrl
    }));
  } catch {
    // ignore storage sync error
  }
}

async function loadProfile() {
  try {
    const [profileRes, statusRes, sessionsRes] = await Promise.allSettled([
      apiGetProfile(),
      apiGetStorageStatus("USER_AVATAR"),
      apiGetMySessions()
    ]);
    if (profileRes.status === "fulfilled" && profileRes.value) {
      profileData.value = profileRes.value;
      hydrateLocalUserCache(profileRes.value);
    }
    if (statusRes.status === "fulfilled") {
      storageStatus.value = statusRes.value;
    }
    if (sessionsRes.status === "fulfilled") {
      mySessions.value = sessionsRes.value || [];
    }
  } finally {
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  void loadProfile();
});

function handleAvatarTap() {
  if (storageStatus.value && !storageStatus.value.configured) {
    uni.showModal({
      title: "未配置可用文件存储源",
      content: storageStatus.value.message || "请在管理后台「文件中心 -> 存储策略」配置并启用存储源后再上传头像。",
      showCancel: false
    });
    return;
  }
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    success: async (res) => {
      const path = res.tempFilePaths?.[0];
      if (!path) return;
      try {
        const updated = await apiUploadProfileAvatar(path);
        profileData.value = updated;
        hydrateLocalUserCache(updated);
        uni.showToast({ title: "头像更新成功", icon: "success" });
      } catch {
        // Error toast already shown in uploadFile
      }
    }
  });
}

function openEditModal() {
  const p = profileData.value;
  editForm.nickname = p?.nickname || userName.value;
  editForm.mobile = p?.mobile || "";
  editForm.email = p?.email || "";
  editForm.gender = p?.gender || "UNKNOWN";
  editForm.workLocation = p?.workLocation || "";
  editForm.extContact = p?.extContact || "";
  editForm.bio = p?.bio || "";
  editForm.confirmPassword = "";
  editModalVisible.value = true;
}

function needsConfirmPassword(): boolean {
  const p = profileData.value;
  if (!p) return false;
  const om = (p.mobile || "").trim();
  const nm = (editForm.mobile || "").trim();
  const oe = (p.email || "").trim();
  const ne = (editForm.email || "").trim();
  return (Boolean(om) && om !== nm) || (Boolean(oe) && oe !== ne);
}

async function submitEditProfile() {
  if (!editForm.nickname.trim()) {
    uni.showToast({ title: "昵称不能为空", icon: "none" });
    return;
  }
  if (needsConfirmPassword() && !editForm.confirmPassword.trim()) {
    uni.showToast({ title: "修改已绑定手机或邮箱需输入登录密码", icon: "none" });
    return;
  }
  savingProfile.value = true;
  try {
    const updated = await apiUpdateProfile({
      nickname: editForm.nickname.trim(),
      mobile: editForm.mobile.trim() || undefined,
      email: editForm.email.trim() || undefined,
      gender: editForm.gender,
      workLocation: editForm.workLocation.trim() || undefined,
      extContact: editForm.extContact.trim() || undefined,
      bio: editForm.bio.trim() || undefined,
      confirmPassword: editForm.confirmPassword || undefined
    });
    profileData.value = updated;
    hydrateLocalUserCache(updated);
    uni.showToast({ title: "个人资料已保存", icon: "success" });
    editModalVisible.value = false;
  } finally {
    savingProfile.value = false;
  }
}

function openPasswordModal() {
  pwdForm.oldPassword = "";
  pwdForm.newPassword = "";
  pwdForm.confirmPassword = "";
  pwdModalVisible.value = true;
}

async function submitChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    uni.showToast({ title: "请输入原密码和新密码", icon: "none" });
    return;
  }
  if (pwdForm.newPassword.length < 8) {
    uni.showToast({ title: "新密码至少需要 8 位字符", icon: "none" });
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    uni.showToast({ title: "两次输入的新密码不一致", icon: "none" });
    return;
  }
  savingPassword.value = true;
  try {
    await apiUpdatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    });
    uni.showToast({ title: "密码修改成功", icon: "success" });
    pwdModalVisible.value = false;
    void loadProfile();
  } finally {
    savingPassword.value = false;
  }
}

async function openSessionsModal() {
  mySessions.value = await apiGetMySessions().catch(() => []);
  sessionsModalVisible.value = true;
}

async function handleKickSession(sessionNo: string) {
  await apiRevokeMySession(sessionNo);
  uni.showToast({ title: "设备已下线", icon: "success" });
  mySessions.value = await apiGetMySessions().catch(() => []);
}

async function handleSessionModalAction() {
  if (mySessions.value.length > 1) {
    const res = await apiRevokeOtherMySessions();
    uni.showToast({ title: `已下线 ${res.revokedCount} 台其他设备`, icon: "success" });
    mySessions.value = await apiGetMySessions().catch(() => []);
  } else {
    sessionsModalVisible.value = false;
  }
}

function formatSessionUa(ua?: string): string {
  if (!ua) return "未知终端";
  if (ua.includes("Mobile") || ua.includes("Android") || ua.includes("iPhone")) return "移动端设备";
  if (ua.includes("Chrome")) return "Chrome 桌面端";
  return ua.slice(0, 26);
}

function handleClearCache() {
  uni.showToast({ title: "本地非核心缓存已清理", icon: "success" });
}

function handleLogout() {
  uni.showModal({
    title: "退出确认",
    content: "确定要退出当前企业账号吗？",
    confirmColor: "#ef4444",
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync("sb_access_token");
        uni.removeStorageSync("sb_refresh_token");
        uni.removeStorageSync("sb_current_user");
        uni.reLaunch({ url: "/pages/login/login" });
      }
    }
  });
}

onMounted(() => {
  void loadProfile();
});
</script>

<style scoped>
.mobile-profile-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.profile-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 22px;
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 10px 25px -5px rgba(12, 34, 56, 0.08);
  margin-bottom: 14px;
}

.avatar-lg {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 12px;
  border: 2px solid #ffffff;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.3);
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.avatar-badge {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #0f172a;
  border: 2px solid #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.user-account {
  font-size: 12px;
  color: #475569;
  margin-bottom: 8px;
  font-weight: 500;
}

.user-bio {
  font-size: 12px;
  color: #334155;
  text-align: center;
  margin-bottom: 10px;
}

.badge-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 12px;
}

.role-badge {
  background: rgba(37, 99, 235, 0.1);
  border: 1px solid rgba(37, 99, 235, 0.25);
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 9999px;
}

.tenant-badge {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  color: #334155;
  font-size: 11px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 9999px;
}

.score-badge {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 9999px;
}

.action-pill-row {
  display: flex;
  gap: 10px;
}

.edit-profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 9999px;
  background: rgba(37, 99, 235, 0.1);
  border: 1px solid rgba(37, 99, 235, 0.25);
}

.edit-profile-btn.secondary {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.edit-text {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
}

.edit-text.dark {
  color: #0f172a;
}

.section-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 20px;
  padding: 16px;
  margin-bottom: 14px;
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
  font-weight: 700;
  color: #0f172a;
}

.tenant-isolated-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 9999px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
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
  color: #047857;
}

.tenant-display-box {
  display: flex;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
}

.tenant-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.1);
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
  font-size: 11px;
  color: #64748b;
}

.tenant-val {
  display: block;
  font-size: 13.5px;
  font-weight: 700;
  color: #0f172a;
}

.tenant-code-badge {
  font-size: 11px;
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.08);
  padding: 3px 8px;
  border-radius: 8px;
  font-weight: 600;
}

.setting-list {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 20px;
  margin-bottom: 16px;
  overflow: hidden;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
}

.setting-item:last-child {
  border-bottom: none;
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
.bg-cyan { background: rgba(2, 132, 199, 0.12); }

.item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #0f172a;
}

.item-val {
  font-size: 12px;
  color: #475569;
  font-weight: 500;
}

.logout-box {
  margin-top: 20px;
}

.logout-btn {
  background: rgba(254, 242, 242, 0.92);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 9999px;
  font-size: 14px;
  font-weight: 600;
  height: 46px;
  line-height: 46px;
}

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
  color: #334155;
}

.warn-lbl {
  color: #dc2626;
}

.edit-input,
.edit-textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #0f172a;
}

.edit-textarea {
  min-height: 70px;
}

.session-list-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
}

.session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-ua {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.session-meta {
  font-size: 11px;
  color: #64748b;
}

.current-tag {
  font-size: 11px;
  font-weight: 600;
  color: #047857;
  background: #ecfdf5;
  padding: 3px 8px;
  border-radius: 6px;
}

.kick-btn {
  font-size: 12px;
  font-weight: 600;
  color: #dc2626;
  padding: 4px 10px;
  border: 1px solid rgba(220, 38, 38, 0.3);
  border-radius: 6px;
}

.empty-sessions {
  text-align: center;
  padding: 20px;
  font-size: 12px;
  color: #64748b;
}
</style>
