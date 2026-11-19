<template>
  <view class="mobile-password-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">修改登录密码</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 安全防护提示卡片 -->
    <view class="security-banner">
      <view class="banner-icon-box">
        <AppIcon name="shield" :size="20" color="#2563eb" />
      </view>
      <view class="banner-texts">
        <text class="banner-title">账号密码安全加固</text>
        <text class="banner-desc">建议设置混合字符高强度密码，修改成功后需牢记并在下次登录时使用。</text>
      </view>
    </view>

    <!-- 密码输入表单卡片 -->
    <view class="glass-form-card">
      <view class="form-group">
        <view class="form-item">
          <text class="form-label">当前登录密码 <text class="req">*</text></text>
          <input
            v-model="pwdForm.oldPassword"
            password
            class="glass-input"
            placeholder="请输入当前正在使用的旧密码"
          />
        </view>

        <view class="form-item">
          <text class="form-label">新登录密码 <text class="req">*</text></text>
          <input
            v-model="pwdForm.newPassword"
            password
            class="glass-input"
            placeholder="请输入不少于 8 位的新密码"
          />
        </view>

        <view class="form-item">
          <text class="form-label">再次确认新密码 <text class="req">*</text></text>
          <input
            v-model="pwdForm.confirmPassword"
            password
            class="glass-input"
            placeholder="请再次输入新密码核对"
          />
        </view>
      </view>

      <!-- 密码合规指南 -->
      <view class="rules-box">
        <text class="rules-title">安全合规建议：</text>
        <view class="rule-line">
          <text class="rule-dot">·</text>
          <text class="rule-text">密码长度需达到 8 ~ 32 位字符</text>
        </view>
        <view class="rule-line">
          <text class="rule-dot">·</text>
          <text class="rule-text">建议组合使用大小写字母、数字及下划线符号</text>
        </view>
        <view class="rule-line">
          <text class="rule-dot">·</text>
          <text class="rule-text">避免使用个人姓名简拼、生日或常用弱口令</text>
        </view>
      </view>
    </view>

    <!-- 底部常驻确认修改按钮容器 -->
    <view class="bottom-bar">
      <button class="save-btn" :loading="saving" @click="submitPassword">
        确认修改密码
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import { apiUpdatePassword } from "../../api/profile";

const saving = ref(false);

onMounted(() => {
  uni.hideTabBar({ animation: false });
});

onShow(() => {
  uni.hideTabBar({ animation: false });
});

const pwdForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

function goBack() {
  uni.navigateBack({ delta: 1 });
}

async function submitPassword() {
  if (!pwdForm.oldPassword.trim()) {
    uni.showToast({ title: "请输入当前旧密码", icon: "none" });
    return;
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 8) {
    uni.showToast({ title: "新密码长度不得少于8位", icon: "none" });
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    uni.showToast({ title: "两次输入的新密码不一致", icon: "none" });
    return;
  }
  if (pwdForm.oldPassword === pwdForm.newPassword) {
    uni.showToast({ title: "新密码不能与旧密码相同", icon: "none" });
    return;
  }

  saving.value = true;
  try {
    await apiUpdatePassword({
      oldPassword: pwdForm.oldPassword.trim(),
      newPassword: pwdForm.newPassword.trim()
    });

    uni.showToast({ title: "密码修改成功", icon: "success" });
    setTimeout(() => {
      goBack();
    }, 500);
  } catch (err: any) {
    uni.showToast({ title: err?.message || "密码修改失败", icon: "none" });
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.mobile-password-page {
  min-height: 100vh;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(100px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部安全横幅卡片 */
.security-banner {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
}

.banner-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: rgba(37, 99, 235, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.banner-texts {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.banner-title {
  font-size: 15px;
  font-weight: 750;
  color: #0f172a;
}

.banner-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
}

/* 玻璃表单卡片 */
.glass-form-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 18px 16px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 1), 0 8px 24px -4px rgba(12, 34, 56, 0.04);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
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

/* 规范提示 */
.rules-box {
  margin-top: 18px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rules-title {
  font-size: 12px;
  font-weight: 650;
  color: #475569;
}

.rule-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.rule-dot {
  font-size: 14px;
  color: #2563eb;
  line-height: 1.2;
}

.rule-text {
  font-size: 11.5px;
  color: #64748b;
  line-height: 1.4;
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
