<template>
  <view class="mobile-profile-page">
    <!-- 1. 用户个人名片卡 (精简高雅·iOS 27 晶透质感) -->
    <view class="profile-card">
      <view class="avatar-lg" @click="handleAvatarTap">
        <image
          v-if="avatarDisplayUrl && avatarDisplayUrl !== failedAvatarUrl"
          :key="avatarDisplayUrl"
          :src="avatarDisplayUrl"
          mode="aspectFill"
          class="avatar-img"
          @error="failedAvatarUrl = avatarDisplayUrl"
        />
        <text v-else>{{ (userName || "管").slice(0, 1).toUpperCase() }}</text>
        <view class="avatar-badge">
          <AppIcon name="camera" :size="11" color="#ffffff" />
        </view>
      </view>
      <text class="user-name">{{ userName || '平台管理员' }}</text>
      
      <!-- 身份标识胶囊组：规范呈现，去除内部开发乱码 -->
      <view class="identity-chips-row">
        <text class="role-pill">{{ userRole || '平台超级管理员' }}</text>
        <text class="account-pill">@{{ userAccount || 'admin' }}</text>
        <text class="space-pill">{{ currentTenantName || '平台空间' }}</text>
      </view>

      <!-- 快捷操作胶囊 -->
      <view class="action-pill-row">
        <view class="action-pill" @click="goToEdit">
          <AppIcon name="edit" :size="13" color="#2563eb" />
          <text class="action-text">编辑个人资料</text>
        </view>
        <view class="action-pill secondary" @click="goToPassword">
          <AppIcon name="lock" :size="13" color="#0f172a" />
          <text class="action-text dark">修改密码</text>
        </view>
      </view>
    </view>

    <!-- 2. 组织与任职信息 (卡片规范组·彻底去除生硬小字与隔离标) -->
    <view class="section-card">
      <view class="section-card-head">
        <view class="head-title-wrap">
          <AppIcon name="building" :size="15" color="#2563eb" />
          <text class="section-card-title">任职与组织档案</text>
        </view>
        <text class="emp-no-tag">{{ profileData?.employeeInfo?.employeeNo ? `工号 ${profileData.employeeInfo.employeeNo}` : 'EMP-0001' }}</text>
      </view>

      <view class="clean-info-group">
        <view class="info-cell-row">
          <text class="info-cell-k">所属企业空间</text>
          <text class="info-cell-v">{{ currentTenantName || '平台管理总部' }}</text>
        </view>
        <view class="info-cell-row">
          <text class="info-cell-k">任职部门</text>
          <text class="info-cell-v">{{ profileData?.employeeInfo?.deptFullPath || profileData?.employeeInfo?.deptName || '未分配部门' }}</text>
        </view>
        <view class="info-cell-row">
          <text class="info-cell-k">担任岗位</text>
          <text class="info-cell-v font-semibold">{{ profileData?.employeeInfo?.positionName || '未分配岗位' }}</text>
        </view>
        <view class="info-cell-row">
          <text class="info-cell-k">办公地点</text>
          <text class="info-cell-v">{{ profileData?.workLocation || '未设置' }}</text>
        </view>
        <view class="info-cell-row">
          <text class="info-cell-k">联系电话</text>
          <text class="info-cell-v">{{ profileData?.mobile || '未绑定' }}</text>
        </view>
        <view class="info-cell-row">
          <text class="info-cell-k">工作邮箱</text>
          <text class="info-cell-v">{{ profileData?.email || '未绑定' }}</text>
        </view>
      </view>
    </view>

    <!-- 3. 账号与安全设置 (干净整齐的 iOS 单元格分组，杜绝编辑资料冗余入口) -->
    <view class="setting-list">
      <view class="setting-item" @click="goToSessions">
        <view class="item-left">
          <view class="setting-icon-box bg-emerald">
            <AppIcon name="shield" :size="14" color="#059669" />
          </view>
          <text class="item-title">在线设备与安全</text>
        </view>
        <view class="item-right">
          <text class="item-val text-green">当前设备在线 · 安全审计正常</text>
          <text class="item-arrow">&gt;</text>
        </view>
      </view>

      <view class="setting-item" @click="goToPassword">
        <view class="item-left">
          <view class="setting-icon-box bg-amber">
            <AppIcon name="lock" :size="14" color="#d97706" />
          </view>
          <text class="item-title">登录密码安全</text>
        </view>
        <view class="item-right">
          <text class="item-val">{{ passwordSafeText }}</text>
          <text class="item-arrow">&gt;</text>
        </view>
      </view>

      <view class="setting-item" @click="handleClearCache">
        <view class="item-left">
          <view class="setting-icon-box bg-slate">
            <AppIcon name="trash" :size="14" color="#64748b" />
          </view>
          <text class="item-title">清理本地缓存</text>
        </view>
        <view class="item-right">
          <text class="item-val">轻触释放缓存</text>
          <text class="item-arrow">&gt;</text>
        </view>
      </view>
    </view>

    <!-- 4. 退出登录按钮 (轻雅微红毛玻璃胶囊) -->
    <view class="logout-box">
      <button class="logout-btn" :disabled="loggingOut" :loading="loggingOut" @click="handleLogout">退出当前登录</button>
    </view>

    <!-- 5. 平台品牌与版本徽标 (呼应 PC 端天枢官方视觉) -->
    <view class="profile-brand-footer">
      <TianshuLogo :size="18" />
      <text class="brand-footer-text">天枢协同云平台 Mobile v1.0.0</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { onPullDownRefresh, onShow } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import TianshuLogo from "../../components/brand/TianshuLogo.vue";
import { apiLogout } from "../../api/auth";
import {
  apiGetProfile,
  apiGetStorageStatus,
  apiUploadProfileAvatar,
  type MobileProfileData,
  type MobileStorageStatus
} from "../../api/profile";
import { resolveMobileFileUrl } from "../../utils/request";
import {
  clearBusinessCache,
  clearMobileSession,
  getCachedUserProfile,
  sessionGeneration,
  setCachedUserProfile,
  type WarmUserProfileModel
} from "../../utils/warm-cache";

const warmProfile = getCachedUserProfile();
const profileData = ref<MobileProfileData | WarmUserProfileModel>(warmProfile);
const storageStatus = ref<MobileStorageStatus | null>(null);
const loggingOut = ref(false);

const userName = ref(warmProfile.nickname || warmProfile.username || "—");
const userAccount = ref(warmProfile.username || "—");
const userCodeLabel = ref(warmProfile.userCode || "—");
const userRole = ref(resolveUserTypeLabel(warmProfile.userType));
const tenantCode = ref(warmProfile.tenantCode || uni.getStorageSync("sb_tenant_code") || "—");
const currentTenantName = ref(warmProfile.tenantName || uni.getStorageSync("sb_tenant_name") || "—");

const avatarDisplayUrl = computed(() => resolveMobileFileUrl(profileData.value?.avatarUrl));
const failedAvatarUrl = ref("");

const passwordSafeText = computed(() => {
  const days = profileData.value?.passwordDaysRemaining;
  if (days === undefined || days === null) return "定期安全轮换 / 点击修改";
  if (days < 0) return "建议更新 / 定期轮换";
  return `剩余 ${days} 天`;
});

function resolveUserTypeLabel(userType?: string): string {
  if (userType === "SUPER_ADMIN" || userType === "PLATFORM") return "平台超级管理员";
  if (userType === "TENANT_ADMIN") return "租户管理员";
  if (userType === "TENANT_USER") return "企业成员";
  if (userType === "STAFF") return "内部员工";
  return userType || "普通用户";
}

function hydrateLocalUserCache(data: MobileProfileData) {
  userName.value = data.nickname || data.username || "—";
  userAccount.value = data.username || "—";
  userCodeLabel.value = data.userCode || `#${data.id}`;
  userRole.value = resolveUserTypeLabel(data.userType);
  tenantCode.value = data.tenantCode || tenantCode.value;
  currentTenantName.value = data.tenantName || data.tenantCode || currentTenantName.value;
  setCachedUserProfile(data);
}

async function loadProfile() {
  try {
    const [profileRes, statusRes] = await Promise.allSettled([
      apiGetProfile(),
      apiGetStorageStatus("USER_AVATAR")
    ]);
    if (profileRes.status === "fulfilled" && profileRes.value) {
      profileData.value = profileRes.value;
      hydrateLocalUserCache(profileRes.value);
    }
    if (statusRes.status === "fulfilled") {
      storageStatus.value = statusRes.value;
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

function goToEdit() {
  uni.navigateTo({ url: "/pages/profile/edit" });
}

function goToPassword() {
  uni.navigateTo({ url: "/pages/profile/password" });
}

function goToSessions() {
  uni.navigateTo({ url: "/pages/profile/sessions" });
}

function handleClearCache() {
  clearBusinessCache();
  uni.showToast({ title: "本地非核心缓存已清理", icon: "success" });
}

function handleLogout() {
  uni.showModal({
    title: "退出确认",
    content: "确定要退出当前企业账号吗？",
    confirmColor: "#ef4444",
    success: async (res) => {
      if (!res.confirm || loggingOut.value) return;
      loggingOut.value = true;
      const generation = sessionGeneration();
      let serverConfirmed = false;
      try {
        await apiLogout();
        serverConfirmed = true;
      } catch {
        // A device must still be able to sign out while offline.
      } finally {
        loggingOut.value = false;
      }
      if (generation === sessionGeneration()) {
        clearMobileSession();
        uni.reLaunch({
          url: "/pages/login/login",
          success: () => {
            if (!serverConfirmed) uni.showToast({ title: "本机已退出，服务端未确认", icon: "none" });
          }
        });
      }
    }
  });
}

onMounted(() => {
  void loadProfile();
});

onShow(() => {
  uni.showTabBar({ animation: false });
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
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 24px;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
  margin-bottom: 14px;
}

.avatar-lg {
  position: relative;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #2563eb 50%, #4f46e5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 10px;
  border: 3px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.25);
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
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #0f172a;
  border: 2px solid #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 18px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
  margin-bottom: 8px;
}

.identity-chips-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 16px;
}

.role-pill {
  font-size: 11px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 3px 10px;
  border-radius: 9999px;
}

.account-pill {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  background: rgba(241, 245, 249, 0.85);
  border: 1px solid rgba(203, 213, 225, 0.8);
  padding: 3px 10px;
  border-radius: 9999px;
}

.space-pill {
  font-size: 11px;
  font-weight: 600;
  color: #059669;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.22);
  padding: 3px 10px;
  border-radius: 9999px;
}

.action-pill-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.action-pill {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.95);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition: transform 0.1s ease;
}

.action-pill:active {
  transform: scale(0.97);
}

.action-pill.secondary {
  background: rgba(248, 250, 252, 0.9);
}

.action-text {
  font-size: 12.5px;
  font-weight: 650;
  color: #2563eb;
}

.action-text.dark {
  color: #0f172a;
}

/* 组织与任职信息规范卡 */
.section-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 20px;
  padding: 16px 18px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 1), 0 6px 18px -4px rgba(12, 34, 56, 0.04);
}

.section-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
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

.emp-no-tag {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.2);
  padding: 2.5px 8px;
  border-radius: 6px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.clean-info-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.info-cell-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.info-cell-k {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.info-cell-v {
  font-size: 13px;
  color: #0f172a;
  font-weight: 600;
  text-align: right;
  max-width: 65%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 账号设置列表组 */
.setting-list {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.96);
  border-radius: 20px;
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 1), 0 6px 18px -4px rgba(12, 34, 56, 0.04);
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 13px 16px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  cursor: pointer;
  transition: background 0.1s ease;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-item:active {
  background: rgba(241, 245, 249, 0.6);
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bg-blue { background: rgba(37, 99, 235, 0.10); }
.bg-cyan { background: rgba(2, 132, 199, 0.10); }
.bg-emerald { background: rgba(16, 185, 129, 0.10); }
.bg-amber { background: rgba(217, 119, 6, 0.10); }
.bg-slate { background: rgba(100, 116, 139, 0.10); }

.item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: #0f172a;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.item-val {
  font-size: 12.5px;
  color: #64748b;
  font-weight: 500;
}

.item-arrow {
  font-size: 13px;
  color: #94a3b8;
  font-family: monospace;
}

.logout-box {
  margin-top: 18px;
}

.logout-btn {
  background: rgba(254, 242, 242, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 16px;
  font-size: 14px;
  font-weight: 650;
  height: 46px;
  line-height: 46px;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.08);
  cursor: pointer;
  transition: transform 0.1s ease;
}

.logout-btn:active {
  transform: scale(0.98);
}

.profile-brand-footer {
  margin-top: 24px;
  margin-bottom: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  opacity: 0.65;
}

.brand-footer-text {
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
  letter-spacing: 0.2px;
}
</style>
