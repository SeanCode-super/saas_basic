<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Check,
  CircleCheck,
  Key,
  Lock,
  Message,
  Phone,
  User,
  WarningFilled
} from "@element-plus/icons-vue";

import {
  fetchUserProfile,
  updateUserProfile,
  updateUserPassword
} from "@/api/modules/profile";
import type { UserProfileData } from "@/api/modules/profile";
import DictTag from "@/components/common/DictTag.vue";

const loading = ref(false);
const activeTab = ref<"basic" | "password" | "logs">("basic");

const profile = ref<UserProfileData>({
  id: 0,
  tenantId: 0,
  tenantCode: "",
  username: "",
  nickname: "",
  mobile: "",
  email: "",
  userType: "",
  status: "ENABLED",
  roles: [],
  recentLogs: []
});

const basicForm = reactive({
  nickname: "",
  mobile: "",
  email: ""
});

const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const passwordStrength = computed(() => {
  const p = passwordForm.newPassword;
  if (!p) return 0;
  let score = 0;
  if (p.length >= 8) score += 30;
  if (/[A-Z]/.test(p)) score += 25;
  if (/[0-9]/.test(p)) score += 25;
  if (/[^A-Za-z0-9]/.test(p)) score += 20;
  return Math.min(score, 100);
});

const passwordStrengthColor = computed(() => {
  if (passwordStrength.value < 40) return "#f56c6c";
  if (passwordStrength.value < 75) return "#e6a23c";
  return "#67c23a";
});

async function loadProfile() {
  loading.value = true;
  try {
    const res = await fetchUserProfile();
    if (res.data) {
      profile.value = res.data;
      basicForm.nickname = res.data.nickname || "";
      basicForm.mobile = res.data.mobile || "";
      basicForm.email = res.data.email || "";
    }
  } catch (err: any) {
    ElMessage.error(err.message || "获取个人信息失败");
  } finally {
    loading.value = false;
  }
}

async function handleSaveBasic() {
  if (!basicForm.nickname.trim()) {
    ElMessage.warning("昵称不能为空");
    return;
  }
  loading.value = true;
  try {
    const res = await updateUserProfile(basicForm);
    if (res.data) {
      profile.value = res.data;
      ElMessage.success("个人基本资料修改成功！");
    }
  } catch (err: any) {
    ElMessage.error(err.message || "更新失败");
  } finally {
    loading.value = false;
  }
}

async function handleUpdatePassword() {
  if (!passwordForm.oldPassword) {
    ElMessage.warning("请输入原密码");
    return;
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    ElMessage.warning("新密码长度不能少于 6 位");
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }

  loading.value = true;
  try {
    await updateUserPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    });
    ElMessage.success("密码修改成功！请牢记您的新密码。");
    passwordForm.oldPassword = "";
    passwordForm.newPassword = "";
    passwordForm.confirmPassword = "";
  } catch (err: any) {
    ElMessage.error(err.message || "密码修改失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadProfile();
});
</script>

<template>
  <div class="profile-container" v-loading="loading">
    <div class="profile-layout">
      <!-- 左侧个人卡片 -->
      <div class="profile-card">
        <div class="profile-card__avatar-wrap">
          <div class="profile-card__avatar">
            {{ (profile.nickname || profile.username || "U").slice(0, 1).toUpperCase() }}
          </div>
          <h2 class="profile-card__name">{{ profile.nickname || profile.username }}</h2>
          <span class="profile-card__username">@{{ profile.username }}</span>
        </div>

        <div class="profile-card__badges">
          <el-tag
            v-for="r in profile.roles"
            :key="r"
            type="primary"
            size="small"
            effect="plain"
          >
            {{ r }}
          </el-tag>
          <el-tag type="info" size="small">{{ profile.userType === 'SUPER_ADMIN' ? '平台超级管理员' : '租户管理员' }}</el-tag>
        </div>

        <el-divider />

        <div class="profile-card__meta-list">
          <div class="meta-item">
            <span class="meta-label">所属租户</span>
            <span class="meta-val font-mono">{{ profile.tenantCode }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">绑定手机</span>
            <span class="meta-val">{{ profile.mobile || "未绑定" }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">电子邮箱</span>
            <span class="meta-val">{{ profile.email || "未绑定" }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">账号状态</span>
            <DictTag :value="profile.status" />
          </div>
          <div class="meta-item">
            <span class="meta-label">最近登录IP</span>
            <span class="meta-val font-mono">{{ profile.lastLoginIp || "127.0.0.1" }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">最近登录时间</span>
            <span class="meta-val text-xs">{{ profile.lastLoginAt ? profile.lastLoginAt.replace('T', ' ').substring(0, 19) : '-' }}</span>
          </div>
        </div>

        <div class="profile-card__security-status">
          <div class="security-header">
            <span class="security-title">
              <el-icon><CircleCheck /></el-icon>

              安全防护评估
            </span>
            <span class="security-score">高防护</span>
          </div>
          <el-progress :percentage="85" :stroke-width="6" status="success" :show-text="false" />
          <p class="security-desc">已启用多因素防刷与防重提交保护</p>
        </div>
      </div>

      <!-- 右侧设置与日志 Tab -->
      <div class="profile-content">
        <el-tabs v-model="activeTab" class="profile-tabs">
          <!-- 基本资料 -->
          <el-tab-pane label="基本资料设置" name="basic">
            <div class="pane-wrap">
              <h3 class="pane-title">修改个人基本资料</h3>
              <p class="pane-subtitle">管理您的公开显示昵称、联系电话及通知接收邮箱</p>

              <el-form label-position="top" class="profile-form">
                <el-form-item label="登录账号 (不可变更)">
                  <el-input :model-value="profile.username" disabled />
                </el-form-item>

                <el-form-item label="用户昵称" required>
                  <el-input v-model="basicForm.nickname" placeholder="请输入您的昵称" />
                </el-form-item>

                <el-form-item label="手机号码">
                  <el-input v-model="basicForm.mobile" placeholder="请输入您的手机号" :prefix-icon="Phone" />
                </el-form-item>

                <el-form-item label="电子邮箱">
                  <el-input v-model="basicForm.email" placeholder="请输入通知接收邮箱" :prefix-icon="Message" />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" :icon="Check" @click="handleSaveBasic">保存资料修改</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- 安全密码 -->
          <el-tab-pane label="修改安全密码" name="password">
            <div class="pane-wrap">
              <h3 class="pane-title">修改登录密码</h3>
              <p class="pane-subtitle">定期更换强密码有助于保障您的 SaaS 企业管理权限与数据安全</p>

              <el-form label-position="top" class="profile-form">
                <el-form-item label="原密码" required>
                  <el-input
                    v-model="passwordForm.oldPassword"
                    type="password"
                    show-password
                    placeholder="请输入当前生效的旧密码"
                    :prefix-icon="Key"
                  />
                </el-form-item>

                <el-form-item label="新密码" required>
                  <el-input
                    v-model="passwordForm.newPassword"
                    type="password"
                    show-password
                    placeholder="请输入新密码 (不少于6位)"
                    :prefix-icon="Lock"
                  />
                  <!-- 密码强度条 -->
                  <div v-if="passwordForm.newPassword" class="password-meter">
                    <div class="meter-bar">
                      <div
                        class="meter-fill"
                        :style="{ width: passwordStrength + '%', backgroundColor: passwordStrengthColor }"
                      />
                    </div>
                    <span class="meter-text" :style="{ color: passwordStrengthColor }">
                      强度: {{ passwordStrength < 40 ? '弱' : (passwordStrength < 75 ? '中' : '强') }}
                    </span>
                  </div>
                </el-form-item>

                <el-form-item label="确认新密码" required>
                  <el-input
                    v-model="passwordForm.confirmPassword"
                    type="password"
                    show-password
                    placeholder="请再次输入新密码"
                    :prefix-icon="Lock"
                  />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" :icon="Key" @click="handleUpdatePassword">立即更新密码</el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- 登录日志 -->
          <el-tab-pane label="个人登录审计" name="logs">
            <div class="pane-wrap">
              <div class="pane-header-row">
                <div>
                  <h3 class="pane-title">最近登录审计日志</h3>
                  <p class="pane-subtitle">展示当前账号最近 10 次的系统登录记录及来源 IP</p>
                </div>
                <el-button size="small" @click="loadProfile">刷新日志</el-button>
              </div>

              <el-table :data="profile.recentLogs" border stripe style="width: 100%">
                <el-table-column label="登录时间" width="200">
                  <template #default="{ row }">
                    <span class="font-mono text-xs">{{ row.occurredAt ? row.occurredAt.replace('T', ' ').substring(0, 19) : '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="loginIp" label="登录 IP" width="160">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain" class="font-mono">{{ row.loginIp }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="认证结果" width="120">
                  <template #default="{ row }">
                    <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                      {{ row.success ? '登录成功' : '登录失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="failReason" label="备注 / 失败原因">
                  <template #default="{ row }">
                    <span :class="{ 'text-danger': !row.success }">{{ row.failReason || (row.success ? '账号密码凭据匹配通过' : '未知原因') }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-container {
  padding: 20px;
  background-color: var(--sb-bg-color, #f8fafc);
  min-height: calc(100vh - 100px);
}

.profile-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
  max-width: 1300px;
  margin: 0 auto;
}

.profile-card {
  background: #ffffff;
  border-radius: 10px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--sb-border-color, #e2e8f0);
}

.profile-card__avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16px;
}

.profile-card__avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
  margin-bottom: 12px;
}

.profile-card__name {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 4px 0;
}

.profile-card__username {
  font-size: 13px;
  color: #64748b;
  font-family: monospace;
}

.profile-card__badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin-bottom: 16px;
}

.profile-card__meta-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.meta-label {
  color: #64748b;
}

.meta-val {
  color: #1e293b;
  font-weight: 500;
}

.profile-card__security-status {
  margin-top: 24px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #cbd5e1;
}

.security-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
}

.security-title {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #334155;
}

.security-score {
  color: #16a34a;
}

.security-desc {
  font-size: 11px;
  color: #94a3b8;
  margin: 6px 0 0 0;
}

.profile-content {
  background: #ffffff;
  border-radius: 10px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--sb-border-color, #e2e8f0);
}

.pane-wrap {
  padding: 8px 0;
}

.pane-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.pane-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.pane-subtitle {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 20px 0;
}

.profile-form {
  max-width: 520px;
}

.password-meter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.meter-bar {
  flex: 1;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}

.meter-fill {
  height: 100%;
  transition: width 0.3s, background-color 0.3s;
}

.meter-text {
  font-size: 12px;
  font-weight: 600;
  min-width: 50px;
}

.text-danger {
  color: #dc2626;
  font-size: 12px;
}
</style>
