<template>
  <view class="mobile-edit-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">编辑个人资料</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 顶部头像更换卡片 -->
    <view class="avatar-card" @click="handleAvatarTap">
      <view class="avatar-wrap">
        <image v-if="avatarDisplayUrl" :src="avatarDisplayUrl" mode="aspectFill" class="avatar-img" />
        <text v-else class="avatar-char">{{ (editForm.nickname || userName || "管").slice(0, 1).toUpperCase() }}</text>
        <view class="avatar-camera-pill">
          <AppIcon name="camera" :size="12" color="#ffffff" />
        </view>
      </view>
      <view class="avatar-info">
        <text class="avatar-title">{{ editForm.nickname || userName || '平台管理员' }}</text>
        <text class="avatar-tip">轻触更换企业头像 · 支持智能缩放裁切</text>
      </view>
    </view>

    <!-- 基本联系资料卡片 -->
    <view class="glass-form-card">
      <view class="card-head-row">
        <AppIcon name="user" :size="15" color="#2563eb" />
        <text class="card-title">基本联系资料</text>
      </view>

      <view class="form-group">
        <view class="form-item">
          <text class="form-label">用户昵称 <text class="req">*</text></text>
          <input
            v-model="editForm.nickname"
            class="glass-input"
            placeholder="请输入您的中文姓名或称谓"
            maxlength="20"
          />
        </view>

        <view class="form-item">
          <text class="form-label">手机号码</text>
          <input
            v-model="editForm.mobile"
            type="number"
            class="glass-input"
            placeholder="请输入 11 位有效手机号"
            maxlength="11"
          />
        </view>

        <view class="form-item">
          <text class="form-label">工作邮箱</text>
          <input
            v-model="editForm.email"
            class="glass-input"
            placeholder="请输入用于接收企业通知的邮箱"
          />
        </view>

        <!-- 安全核验密码行 -->
        <view v-if="needsConfirmPassword()" class="form-item warn-box">
          <view class="warn-head">
            <AppIcon name="shield" :size="13" color="#dc2626" />
            <text class="warn-title">安全核验：变更手机或邮箱需核验当前密码</text>
          </view>
          <input
            v-model="editForm.confirmPassword"
            password
            class="glass-input warn-input"
            placeholder="请输入当前登录密码以完成验证"
          />
        </view>
      </view>
    </view>

    <!-- 组织任职与工位卡片 -->
    <view class="glass-form-card">
      <view class="card-head-row">
        <AppIcon name="building" :size="15" color="#2563eb" />
        <text class="card-title">组织任职与工位</text>
      </view>

      <view class="form-group">
        <view class="form-item">
          <text class="form-label">办公地点 / 工位</text>
          <input
            v-model="editForm.workLocation"
            class="glass-input"
            placeholder="如 研发总部 A座 502"
          />
        </view>

        <view class="form-item">
          <text class="form-label">备用联系 / 办公分机</text>
          <input
            v-model="editForm.extContact"
            class="glass-input"
            placeholder="如 企业微信或内线分机号"
          />
        </view>

        <view class="form-item">
          <text class="form-label">个人工作签名</text>
          <textarea
            v-model="editForm.bio"
            class="glass-textarea"
            placeholder="填写您的业务擅长、座右铭或对内外勤工作协作备忘..."
            maxlength="200"
          />
          <text class="word-counter">{{ (editForm.bio || '').length }}/200</text>
        </view>
      </view>
    </view>

    <!-- 底部常驻保存按钮容器 -->
    <view class="bottom-bar">
      <button class="save-btn" :loading="saving" @click="submitEditProfile">
        保存个人资料
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import {
  apiGetProfile,
  apiGetStorageStatus,
  apiUpdateProfile,
  apiUploadProfileAvatar,
  type MobileProfileData,
  type MobileStorageStatus
} from "../../api/profile";
import { resolveMobileFileUrl } from "../../utils/request";

const saving = ref(false);
const profileData = ref<MobileProfileData | null>(null);
const storageStatus = ref<MobileStorageStatus | null>(null);

function getCachedUser() {
  try {
    const raw = uni.getStorageSync("sb_current_user");
    return raw ? (typeof raw === "string" ? JSON.parse(raw) : raw) : {};
  } catch {
    return {};
  }
}

const cachedUser = getCachedUser();

const editForm = reactive({
  nickname: cachedUser.nickname || cachedUser.username || "平台管理员",
  mobile: cachedUser.mobile || "13800000000",
  email: cachedUser.email || "admin@tianshu.cloud",
  confirmPassword: "",
  workLocation: cachedUser.workLocation || "研发总部 A座 502",
  extContact: cachedUser.extContact || "企业微信 (分机 8001)",
  bio: cachedUser.bio || "全栈架构师 · 个人资料完整度测试"
});

const originalMobile = ref(editForm.mobile);
const originalEmail = ref(editForm.email);

const userName = computed(() => profileData.value?.nickname || profileData.value?.username || "");
const avatarDisplayUrl = computed(() => {
  if (profileData.value?.avatarUrl) {
    return resolveMobileFileUrl(profileData.value.avatarUrl);
  }
  return "";
});

function goBack() {
  uni.navigateBack({ delta: 1 });
}

function needsConfirmPassword() {
  const mobileChanged = (editForm.mobile || "").trim() !== originalMobile.value;
  const emailChanged = (editForm.email || "").trim() !== originalEmail.value;
  const hasOriginal = Boolean(originalMobile.value || originalEmail.value);
  return hasOriginal && (mobileChanged || emailChanged);
}

async function loadProfile() {
  try {
    const [res, sRes] = await Promise.allSettled([
      apiGetProfile(),
      apiGetStorageStatus("USER_AVATAR")
    ]);
    if (res.status === "fulfilled" && res.value) {
      const d = res.value;
      profileData.value = d;
      if (d.nickname) editForm.nickname = d.nickname;
      if (d.mobile) editForm.mobile = d.mobile;
      if (d.email) editForm.email = d.email;
      if (d.workLocation) editForm.workLocation = d.workLocation;
      if (d.extContact) editForm.extContact = d.extContact;
      if (d.bio) editForm.bio = d.bio;
      originalMobile.value = (editForm.mobile || "").trim();
      originalEmail.value = (editForm.email || "").trim();
    }
    if (sRes.status === "fulfilled") {
      storageStatus.value = sRes.value;
    }
  } catch (err) {
    console.error("加载个人资料失败", err);
  }
}

function handleAvatarTap() {
  if (storageStatus.value && !storageStatus.value.configured) {
    uni.showModal({
      title: "未配置文件存储源",
      content: storageStatus.value.message || "请在管理后台「文件中心 -> 存储策略」启用存储源后再上传头像。",
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
        uni.showToast({ title: "头像更新成功", icon: "success" });
      } catch (err: any) {
        uni.showToast({ title: err?.message || "上传头像失败", icon: "none" });
      }
    }
  });
}

async function submitEditProfile() {
  if (!editForm.nickname.trim()) {
    uni.showToast({ title: "用户昵称不能为空", icon: "none" });
    return;
  }
  if (editForm.mobile && !/^1\d{10}$/.test(editForm.mobile.trim())) {
    uni.showToast({ title: "手机号格式不正确", icon: "none" });
    return;
  }
  if (editForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editForm.email.trim())) {
    uni.showToast({ title: "电子邮箱格式不正确", icon: "none" });
    return;
  }
  if (needsConfirmPassword() && !editForm.confirmPassword.trim()) {
    uni.showToast({ title: "修改手机/邮箱需输入当前密码核验", icon: "none" });
    return;
  }

  saving.value = true;
  try {
    const updated = await apiUpdateProfile({
      nickname: editForm.nickname.trim(),
      mobile: editForm.mobile ? editForm.mobile.trim() : undefined,
      email: editForm.email ? editForm.email.trim() : undefined,
      confirmPassword: needsConfirmPassword() ? editForm.confirmPassword.trim() : undefined,
      workLocation: editForm.workLocation ? editForm.workLocation.trim() : undefined,
      extContact: editForm.extContact ? editForm.extContact.trim() : undefined,
      bio: editForm.bio ? editForm.bio.trim() : undefined
    });

    profileData.value = updated;
    uni.showToast({ title: "资料保存成功", icon: "success" });
    setTimeout(() => {
      goBack();
    }, 500);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "更新个人资料失败", icon: "none" });
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  uni.hideTabBar({ animation: false });
  void loadProfile();
});

onShow(() => {
  uni.hideTabBar({ animation: false });
});
</script>

<style scoped>
.mobile-edit-page {
  min-height: 100vh;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(100px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部头像更换卡片 */
.avatar-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 18px 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.avatar-card:active {
  transform: scale(0.98);
}

.avatar-wrap {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb 0%, #38bdf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
  border: 2px solid #ffffff;
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.avatar-char {
  font-size: 24px;
  font-weight: 800;
  color: #ffffff;
}

.avatar-camera-pill {
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

.avatar-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.avatar-title {
  font-size: 16px;
  font-weight: 750;
  color: #0f172a;
}

.avatar-tip {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

/* 玻璃表单卡片 */
.glass-form-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 16px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
}

.card-head-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.card-title {
  font-size: 14.5px;
  font-weight: 750;
  color: #0f172a;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
}

.form-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #334155;
}

.req {
  color: #ef4444;
  margin-left: 2px;
}

.glass-input {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  height: 44px;
  line-height: 44px;
  padding: 0 14px;
  font-size: 14px;
  color: #0f172a;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.03);
  box-sizing: border-box;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.glass-input input,
.glass-input :deep(input),
.glass-input :deep(.uni-input-input) {
  height: 44px !important;
  line-height: 44px !important;
  font-size: 14px !important;
  color: #0f172a !important;
  display: block !important;
  opacity: 1 !important;
}

.glass-input :deep(.uni-input-placeholder),
.glass-input .uni-input-placeholder,
.glass-input .input-placeholder {
  color: #94a3b8 !important;
  font-size: 13.5px !important;
  line-height: 44px !important;
}

.glass-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.glass-textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 13.5px;
  color: #0f172a;
  min-height: 80px;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.03);
  box-sizing: border-box;
  width: 100%;
}

.word-counter {
  font-size: 11px;
  color: #94a3b8;
  text-align: right;
  margin-top: 2px;
}

.warn-box {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 12px;
  padding: 12px;
  margin-top: 4px;
}

.warn-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.warn-title {
  font-size: 12px;
  font-weight: 650;
  color: #be123c;
}

.warn-input {
  background: #ffffff !important;
  border-color: #f43f5e !important;
}

/* 底部常驻保存按钮容器 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 18px calc(14px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border-top: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 -4px 16px rgba(12, 34, 56, 0.05);
  z-index: 99;
}

@media screen and (min-width: 640px) {
  .bottom-bar {
    max-width: 430px;
    left: 50% !important;
    transform: translateX(-50%);
  }
}

.save-btn {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  font-size: 14.5px;
  font-weight: 650;
  height: 48px;
  line-height: 48px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 6px 18px rgba(37, 99, 235, 0.35);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.save-btn:active {
  transform: scale(0.98);
}
</style>
