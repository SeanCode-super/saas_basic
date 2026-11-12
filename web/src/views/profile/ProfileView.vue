<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { useRouter } from "vue-router";
import {
  Camera,
  Check,
  Checked,
  Clock,
  Key,
  Lock,
  Monitor,
  OfficeBuilding,
  Refresh,
  Setting,
  User as UserIcon
} from "@element-plus/icons-vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import {
  disableMfa,
  enableMfa,
  fetchMfaStatus,
  setupMfa,
  selectAuthContext,
  clearAuthContext,
  type MfaSetupResponse,
  type MfaStatusResponse,
  type OnlineSession,
  type UserWorkspacePreferences
} from "@/api/modules/auth";
import { fetchFileStorageStatus, resolveAuthorizedFileUrl, type StorageAvailability } from "@/api/modules/file";
import {
  fetchMyProfileSessions,
  fetchUserProfile,
  revokeMyProfileSession,
  revokeOtherMyProfileSessions,
  updateUserPassword,
  updateUserPreferences,
  updateUserProfile,
  uploadUserAvatar,
  type UserProfileData
} from "@/api/modules/profile";
import { useAuthStore } from "@/stores/modules/auth";

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const savingProfile = ref(false);
const savingPrefs = ref(false);
const savingPassword = ref(false);
const uploadingAvatar = ref(false);
const activeTab = ref<"basic" | "org" | "preferences" | "security" | "logs">("basic");

const profile = ref<UserProfileData | null>(null);
const avatarStorageStatus = ref<StorageAvailability | null>(null);
const avatarInputRef = ref<HTMLInputElement | null>(null);
const mySessions = ref<OnlineSession[]>([]);
const loadingSessions = ref(false);

const basicFormRef = ref<FormInstance>();
const passwordFormRef = ref<FormInstance>();

const basicForm = reactive({
  nickname: "",
  mobile: "",
  email: "",
  gender: "UNKNOWN",
  bio: "",
  workLocation: "",
  extContact: "",
  locale: "zh-CN",
  timezone: "Asia/Shanghai",
  confirmPassword: ""
});

const prefForm = reactive<UserWorkspacePreferences>({
  locale: "zh-CN",
  timezone: "Asia/Shanghai",
  layoutMode: "top",
  railTheme: "light",
  showBreadcrumbs: true,
  sidebarAccordion: false,
  enableWatermark: false,
  emailNotify: true,
  siteNotify: true,
  securityAlertNotify: true,
  defaultAiModel: ""
});

const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const mfaStatus = ref<MfaStatusResponse | null>(null);
const mfaSetupData = ref<MfaSetupResponse | null>(null);
const mfaTotpCode = ref("");
const mfaDisableForm = reactive({ password: "", totpCode: "" });
const mfaBusy = ref(false);

const avatarDisplayUrl = computed(() =>
  resolveAuthorizedFileUrl(profile.value?.avatarUrl || authStore.currentUser?.avatarUrl, authStore.token)
);
const avatarLoadError = ref(false);
watch(avatarDisplayUrl, () => {
  avatarLoadError.value = false;
});

const userTypeLabel = computed(() => {
  const t = profile.value?.userType;
  if (t === "SUPER_ADMIN" || t === "PLATFORM") return "平台超级管理员";
  if (t === "TENANT_ADMIN") return "租户管理员";
  if (t === "TENANT_USER") return "企业成员";
  if (t === "STAFF") return "内部员工";
  return t || "普通用户";
});

const sensitiveContactChanged = computed(() => {
  if (!profile.value) return false;
  const origMobile = (profile.value.mobile || "").trim();
  const nextMobile = (basicForm.mobile || "").trim();
  const origEmail = (profile.value.email || "").trim();
  const nextEmail = (basicForm.email || "").trim();
  return (Boolean(origMobile) && origMobile !== nextMobile) || (Boolean(origEmail) && origEmail !== nextEmail);
});

const securityLevelText = computed(() => {
  const s = profile.value?.securityScore ?? 60;
  if (s >= 85) return { text: "防护等级：极佳", type: "success" as const, color: "#059669" };
  if (s >= 65) return { text: "防护等级：良好", type: "warning" as const, color: "#d97706" };
  return { text: "防护等级：待加固", type: "danger" as const, color: "#dc2626" };
});

const basicRules: FormRules = {
  nickname: [{ required: true, message: "请输入显示昵称", trigger: "blur" }],
  mobile: [{ pattern: /^$|^1[3-9]\d{9}$/, message: "请输入正确的11位手机号", trigger: "blur" }],
  email: [{ type: "email", message: "请输入合法的电子邮箱格式", trigger: "blur" }]
};

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: "请输入当前登录密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 8, message: "新密码至少需要 8 位字符", trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, message: "请再次确认新密码", trigger: "blur" },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error("两次输入的新密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur"
    }
  ]
};

function hydrateForms(data: UserProfileData) {
  basicForm.nickname = data.nickname || "";
  basicForm.mobile = data.mobile || "";
  basicForm.email = data.email || "";
  basicForm.gender = data.gender || "UNKNOWN";
  basicForm.bio = data.bio || "";
  basicForm.workLocation = data.workLocation || "";
  basicForm.extContact = data.extContact || "";
  basicForm.locale = data.locale || "zh-CN";
  basicForm.timezone = data.timezone || "Asia/Shanghai";
  basicForm.confirmPassword = "";

  if (data.preferences) {
    Object.assign(prefForm, data.preferences);
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [profileRes, storageRes] = await Promise.allSettled([
      fetchUserProfile(),
      fetchFileStorageStatus("USER_AVATAR")
    ]);
    if (profileRes.status === "fulfilled" && profileRes.value.success && profileRes.value.data) {
      profile.value = profileRes.value.data;
      hydrateForms(profileRes.value.data);
      authStore.updateUserSnapshot({
        nickname: profileRes.value.data.nickname,
        avatarUrl: profileRes.value.data.avatarUrl,
        locale: profileRes.value.data.locale,
        timezone: profileRes.value.data.timezone,
        preferences: profileRes.value.data.preferences
      });
    }
    if (storageRes.status === "fulfilled") {
      avatarStorageStatus.value = storageRes.value;
    }
  } finally {
    loading.value = false;
  }
  await Promise.allSettled([loadMfaStatus(), loadMySessions()]);
}

async function loadMfaStatus() {
  try {
    mfaStatus.value = await fetchMfaStatus();
  } catch {
    mfaStatus.value = null;
  }
}

async function loadMySessions() {
  loadingSessions.value = true;
  try {
    const res = await fetchMyProfileSessions();
    mySessions.value = res.data || [];
  } catch {
    mySessions.value = [];
  } finally {
    loadingSessions.value = false;
  }
}

function triggerAvatarSelect() {
  if (avatarStorageStatus.value && !avatarStorageStatus.value.configured) {
    ElMessage.warning(avatarStorageStatus.value.message || "未配置可用文件存储源，请先前往「文件中心 -> 存储策略」配置");
    return;
  }
  avatarInputRef.value?.click();
}

async function handleAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  uploadingAvatar.value = true;
  try {
    const res = await uploadUserAvatar(file);
    if (res.success && res.data) {
      profile.value = res.data;
      authStore.updateUserSnapshot({ avatarUrl: res.data.avatarUrl });
      ElMessage.success("个人头像已更新并同步至工作台");
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "头像上传失败");
  } finally {
    uploadingAvatar.value = false;
    input.value = "";
  }
}

async function handleSaveBasic() {
  if (!basicFormRef.value) return;
  const valid = await basicFormRef.value.validate().catch(() => false);
  if (!valid) return;
  if (sensitiveContactChanged.value && !basicForm.confirmPassword.trim()) {
    ElMessage.warning("修改已绑定的手机或邮箱时，请输入当前登录密码进行安全核验");
    return;
  }
  savingProfile.value = true;
  try {
    const res = await updateUserProfile({
      nickname: basicForm.nickname,
      mobile: basicForm.mobile,
      email: basicForm.email,
      gender: basicForm.gender,
      bio: basicForm.bio,
      workLocation: basicForm.workLocation,
      extContact: basicForm.extContact,
      locale: basicForm.locale,
      timezone: basicForm.timezone,
      confirmPassword: basicForm.confirmPassword || undefined
    });
    if (res.success && res.data) {
      profile.value = res.data;
      hydrateForms(res.data);
      authStore.updateUserSnapshot({
        nickname: res.data.nickname,
        locale: res.data.locale,
        timezone: res.data.timezone,
        preferences: res.data.preferences
      });
      ElMessage.success("个人基本与扩展资料已保存");
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "保存个人资料失败");
  } finally {
    savingProfile.value = false;
  }
}

async function handleSavePreferences() {
  savingPrefs.value = true;
  try {
    const res = await updateUserPreferences({ ...prefForm });
    if (res.success && res.data) {
      Object.assign(prefForm, res.data);
      if (profile.value) {
        profile.value.preferences = res.data;
      }
      authStore.updateUserSnapshot({
        locale: res.data.locale,
        timezone: res.data.timezone,
        preferences: res.data
      });
      ElMessage.success("个人工作台云端偏好已保存并即时生效");
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "保存工作台偏好失败");
  } finally {
    savingPrefs.value = false;
  }
}

async function handleUpdatePassword() {
  if (!passwordFormRef.value) return;
  const valid = await passwordFormRef.value.validate().catch(() => false);
  if (!valid) return;
  savingPassword.value = true;
  try {
    const res = await updateUserPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    });
    if (res.success) {
      ElMessage.success("密码修改成功，请妥善保管新凭证");
      passwordForm.oldPassword = "";
      passwordForm.newPassword = "";
      passwordForm.confirmPassword = "";
      await loadData();
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "密码修改失败");
  } finally {
    savingPassword.value = false;
  }
}

async function handleSwitchAssignment(assignmentPublicId: string) {
  try {
    const updatedUser = await selectAuthContext(assignmentPublicId);
    authStore.updateUserSnapshot(updatedUser);
    await loadData();
    ElMessage.success("当前组织任职上下文已切换");
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "切换任职上下文失败");
  }
}

async function handleClearAssignment() {
  try {
    const updatedUser = await clearAuthContext();
    authStore.updateUserSnapshot(updatedUser);
    await loadData();
    ElMessage.success("已恢复默认任职视角");
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "清除任职上下文失败");
  }
}

async function handleSetupMfa() {
  mfaBusy.value = true;
  try {
    mfaSetupData.value = await setupMfa();
    mfaTotpCode.value = "";
  } finally {
    mfaBusy.value = false;
  }
}

async function handleEnableMfa() {
  if (!mfaTotpCode.value.trim()) {
    ElMessage.warning("请输入认证器显示的 6 位动态验证码");
    return;
  }
  mfaBusy.value = true;
  try {
    mfaStatus.value = await enableMfa(mfaTotpCode.value.trim());
    mfaSetupData.value = null;
    mfaTotpCode.value = "";
    ElMessage.success("双因素认证 (TOTP) 已开启");
    await loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "验证码校验失败");
  } finally {
    mfaBusy.value = false;
  }
}

async function handleDisableMfa() {
  if (!mfaDisableForm.password) {
    ElMessage.warning("请输入当前登录密码");
    return;
  }
  mfaBusy.value = true;
  try {
    mfaStatus.value = await disableMfa(mfaDisableForm.password, mfaDisableForm.totpCode);
    mfaDisableForm.password = "";
    mfaDisableForm.totpCode = "";
    ElMessage.success("双因素认证已停用");
    await loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "停用 MFA 失败");
  } finally {
    mfaBusy.value = false;
  }
}

async function handleRevokeSession(session: OnlineSession) {
  try {
    await ElMessageBox.confirm(`确认下线登录 IP 为 ${session.loginIp || "未知"} 的会话设备？`, "安全会话下线", {
      type: "warning"
    });
    await revokeMyProfileSession(session.sessionNo);
    ElMessage.success("目标设备已强制下线");
    await loadMySessions();
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err?.response?.data?.message || "下线会话失败");
    }
  }
}

async function handleRevokeOthers() {
  try {
    await ElMessageBox.confirm("确认下线除当前浏览器之外的所有其他在线设备？", "一键踢出其他设备", {
      type: "warning"
    });
    const res = await revokeOtherMyProfileSessions();
    ElMessage.success(`已下线 ${res.data?.revokedCount ?? 0} 台其他在线设备`);
    await loadMySessions();
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err?.response?.data?.message || "批量下线失败");
    }
  }
}

function summarizeUserAgent(ua?: string): string {
  if (!ua) return "未知终端";
  if (ua.includes("Mobile") || ua.includes("Android") || ua.includes("iPhone")) return "移动端 H5 / App";
  if (ua.includes("Chrome")) return "Chrome 桌面端";
  if (ua.includes("Safari")) return "Safari 浏览器";
  if (ua.includes("Firefox")) return "Firefox 浏览器";
  return ua.slice(0, 36);
}

function formatDateTime(val?: string | null) {
  if (!val) return "--";
  return val.replace("T", " ").slice(0, 19);
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <ControlSurface
    eyebrow="个人数字身份中心"
    title="个人中心与安全工作台"
    description="管理数字身份名片、头像、组织任职上下文、云端工作台偏好、MFA 与本人活跃设备会话"
    v-loading="loading"
  >
    <template #actions>
      <el-button :icon="Refresh" @click="loadData">刷新资料</el-button>
    </template>

    <input
      ref="avatarInputRef"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      style="display: none"
      @change="handleAvatarFileChange"
    />

    <div class="profile-grid">
      <!-- 左侧：身份名片与安全评分卡片 -->
      <aside class="profile-sidebar">
        <div class="glass-panel hero-card">
          <div class="avatar-upload-box">
            <div class="avatar-circle" v-loading="uploadingAvatar" @click="triggerAvatarSelect">
              <img
                v-if="avatarDisplayUrl && !avatarLoadError"
                :src="avatarDisplayUrl"
                alt="avatar"
                class="avatar-img"
                @error="avatarLoadError = true"
              />
              <span v-else class="avatar-initial">
                {{ (profile?.nickname || profile?.username || "U").slice(0, 1).toUpperCase() }}
              </span>
              <span class="avatar-camera-badge" title="点击上传个人头像">
                <el-icon><Camera /></el-icon>
              </span>
            </div>
            <div class="hero-identity">
              <h3 class="nickname">{{ profile?.nickname || profile?.username || "--" }}</h3>
              <p class="account-meta">账号：@{{ profile?.username }} · 编码 {{ profile?.userCode || `#${profile?.id}` }}</p>
              <div class="badge-row">
                <el-tag size="small" type="primary" effect="light">{{ userTypeLabel }}</el-tag>
                <el-tag size="small" :type="profile?.status === 'ENABLED' ? 'success' : 'danger'" effect="plain">
                  {{ profile?.status === 'ENABLED' ? '账号正常' : '状态受限' }}
                </el-tag>
              </div>
            </div>
          </div>

          <el-alert
            v-if="avatarStorageStatus && !avatarStorageStatus.configured"
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: 12px"
          >
            <template #title>
              <span style="font-size: 12px">未配置可用存储源，暂无法上传头像</span>
            </template>
            <el-button link type="primary" size="small" @click="router.push('/file/storage')">
              前往配置存储源
            </el-button>
          </el-alert>

          <p v-if="profile?.bio" class="bio-quote">“{{ profile.bio }}”</p>

          <!-- 安全评分条 -->
          <div class="security-score-card">
            <div class="score-head">
              <span class="score-label">账户综合安全评分</span>
              <strong :style="{ color: securityLevelText.color }">{{ profile?.securityScore ?? 60 }} / 100</strong>
            </div>
            <el-progress
              :percentage="profile?.securityScore ?? 60"
              :stroke-width="8"
              :show-text="false"
              :status="securityLevelText.type === 'danger' ? 'exception' : securityLevelText.type === 'success' ? 'success' : 'warning'"
            />
            <div class="score-checks">
              <span class="check-item" :class="{ ok: profile?.mfaEnabled }">MFA {{ profile?.mfaEnabled ? '已开启' : '未开启' }}</span>
              <span class="check-item" :class="{ ok: Boolean(profile?.mobile) }">手机{{ profile?.mobile ? '已绑' : '未绑' }}</span>
              <span class="check-item" :class="{ ok: Boolean(profile?.email) }">邮箱{{ profile?.email ? '已绑' : '未绑' }}</span>
            </div>
          </div>

          <!-- 详细元数据 -->
          <div class="meta-kv-list">
            <div class="kv-row">
              <span class="kv-k">所属租户</span>
              <span class="kv-v">{{ profile?.tenantName || profile?.tenantCode }}</span>
            </div>
            <div v-if="profile?.employeeInfo" class="kv-row">
              <span class="kv-k">组织岗位</span>
              <span class="kv-v">
                {{ profile.employeeInfo.deptName || '--' }} / {{ profile.employeeInfo.positionName || '--' }}
              </span>
            </div>
            <div v-if="profile?.workLocation" class="kv-row">
              <span class="kv-k">办公地点</span>
              <span class="kv-v">{{ profile.workLocation }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-k">最近登录</span>
              <span class="kv-v">{{ formatDateTime(profile?.lastLoginAt) }} ({{ profile?.lastLoginIp || '--' }})</span>
            </div>
            <div class="kv-row">
              <span class="kv-k">拥有角色</span>
              <div class="kv-tags">
                <el-tag v-for="r in (profile?.roles || [])" :key="r" size="small" effect="plain">{{ r }}</el-tag>
                <span v-if="!profile?.roles?.length" style="color: #64748b">未分配</span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧：多标签工作台 -->
      <main class="profile-main">
        <div class="glass-panel workbench-panel">
          <el-tabs v-model="activeTab" class="profile-tabs">
            <!-- Tab 1: 基本与扩展资料 -->
            <el-tab-pane name="basic">
              <template #label>
                <span class="tab-label"><el-icon><UserIcon /></el-icon> 基本与扩展资料</span>
              </template>
              <el-form
                ref="basicFormRef"
                :model="basicForm"
                :rules="basicRules"
                label-position="top"
                style="max-width: 680px; margin-top: 8px"
              >
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="登录账号（唯一标识不可修改）">
                      <el-input :model-value="profile?.username" disabled />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="显示昵称" prop="nickname">
                      <el-input v-model="basicForm.nickname" maxlength="64" placeholder="输入显示姓名/昵称" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="8">
                    <el-form-item label="性别">
                      <el-select v-model="basicForm.gender" style="width: 100%">
                        <el-option label="保密 / 未知" value="UNKNOWN" />
                        <el-option label="男" value="MALE" />
                        <el-option label="女" value="FEMALE" />
                        <el-option label="其他" value="OTHER" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="办公地点 / 工位">
                      <el-input v-model="basicForm.workLocation" placeholder="如 杭州研发中心 A座" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="分机 / 备用联系">
                      <el-input v-model="basicForm.extContact" placeholder="如 企业微信/内线分机" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="手机号码" prop="mobile">
                      <el-input v-model="basicForm.mobile" placeholder="输入11位手机号码" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="电子邮箱" prop="email">
                      <el-input v-model="basicForm.email" placeholder="输入工作电子邮箱" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item
                  v-if="sensitiveContactChanged"
                  label="安全验证：变更已绑定手机或邮箱需输入当前登录密码"
                  required
                >
                  <el-input
                    v-model="basicForm.confirmPassword"
                    type="password"
                    show-password
                    placeholder="请输入当前登录密码确认身份"
                  />
                </el-form-item>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="默认界面语言">
                      <el-select v-model="basicForm.locale" style="width: 100%">
                        <el-option label="简体中文 (zh-CN)" value="zh-CN" />
                        <el-option label="English (en-US)" value="en-US" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="个人时区">
                      <el-select v-model="basicForm.timezone" style="width: 100%">
                        <el-option label="Asia/Shanghai (UTC+8)" value="Asia/Shanghai" />
                        <el-option label="UTC (标准协调时间)" value="UTC" />
                        <el-option label="Asia/Tokyo (UTC+9)" value="Asia/Tokyo" />
                        <el-option label="America/New_York (UTC-5)" value="America/New_York" />
                        <el-option label="Europe/London (UTC+0)" value="Europe/London" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item label="个人简介 / 签名">
                  <el-input
                    v-model="basicForm.bio"
                    type="textarea"
                    :rows="3"
                    maxlength="500"
                    show-word-limit
                    placeholder="介绍一下您的岗位职责或工作签名..."
                  />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" :loading="savingProfile" :icon="Check" @click="handleSaveBasic">
                    保存基本资料
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- Tab 2: 组织归属与任职上下文 -->
            <el-tab-pane name="org">
              <template #label>
                <span class="tab-label"><el-icon><OfficeBuilding /></el-icon> 组织任职与权限范围</span>
              </template>

              <div class="tab-section-stack">
                <div v-if="profile?.employeeInfo" class="sub-card">
                  <h4 class="sub-card-title">关联员工档案</h4>
                  <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="工号">{{ profile.employeeInfo.employeeNo || '--' }}</el-descriptions-item>
                    <el-descriptions-item label="员工姓名">{{ profile.employeeInfo.employeeName || '--' }}</el-descriptions-item>
                    <el-descriptions-item label="所属部门">{{ profile.employeeInfo.deptFullPath || profile.employeeInfo.deptName || '--' }}</el-descriptions-item>
                    <el-descriptions-item label="主岗位">{{ profile.employeeInfo.positionName || '--' }}</el-descriptions-item>
                    <el-descriptions-item label="入职日期">{{ profile.employeeInfo.hireDate || '--' }}</el-descriptions-item>
                    <el-descriptions-item label="在职状态">{{ profile.employeeInfo.employeeStatus || '--' }}</el-descriptions-item>
                  </el-descriptions>
                </div>
                <el-empty v-else description="当前账号尚未关联内部员工档案" :image-size="60" />

                <div v-if="profile?.employeeInfo?.assignments?.length" class="sub-card">
                  <div class="sub-card-head">
                    <h4 class="sub-card-title">多组织兼职与任职上下文切换</h4>
                    <el-button
                      v-if="profile.employeeInfo.activeAssignmentPublicId"
                      size="small"
                      @click="handleClearAssignment"
                    >
                      恢复默认视角
                    </el-button>
                  </div>
                  <el-table :data="profile.employeeInfo.assignments" border stripe size="small">
                    <el-table-column prop="organizationName" label="所属组织" min-width="140" />
                    <el-table-column prop="orgUnitName" label="任职部门/单元" min-width="140" />
                    <el-table-column prop="positionName" label="岗位" min-width="120" />
                    <el-table-column label="标识" width="120">
                      <template #default="{ row }">
                        <el-tag v-if="row.primary" size="small" type="success">主职</el-tag>
                        <el-tag v-else size="small" type="info">兼职</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="130">
                      <template #default="{ row }">
                        <el-tag v-if="row.active" size="small" type="primary" effect="dark">当前使用中</el-tag>
                        <el-button v-else link type="primary" @click="handleSwitchAssignment(row.assignmentPublicId)">
                          切换至此身份
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <div class="sub-card">
                  <h4 class="sub-card-title">角色与数据权限范围明细</h4>
                  <el-table :data="profile?.dataScopes || []" border stripe size="small">
                    <el-table-column prop="roleName" label="角色名称" min-width="150" />
                    <el-table-column prop="scopeName" label="数据范围策略" min-width="160" />
                    <el-table-column prop="scopeType" label="范围类型" width="160">
                      <template #default="{ row }">
                        <el-tag size="small" effect="plain">{{ row.scopeType }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 3: 云端工作台偏好 -->
            <el-tab-pane name="preferences">
              <template #label>
                <span class="tab-label"><el-icon><Setting /></el-icon> 工作台云端偏好</span>
              </template>

              <el-form :model="prefForm" label-position="top" style="max-width: 680px; margin-top: 8px">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="控制台导航布局">
                      <el-select v-model="prefForm.layoutMode" style="width: 100%">
                        <el-option label="全顶部横向模式 (Full Top)" value="top" />
                        <el-option label="顶层主域置顶模式 (Mix)" value="mix" />
                        <el-option label="左侧多级折叠树模式 (Side)" value="side" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="侧边栏视觉风格">
                      <el-select v-model="prefForm.railTheme" style="width: 100%">
                        <el-option label="极简雅白通透 (Light)" value="light" />
                        <el-option label="深钛金灰暗色 (Dark)" value="dark" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="偏好语言">
                      <el-select v-model="prefForm.locale" style="width: 100%">
                        <el-option label="简体中文 (zh-CN)" value="zh-CN" />
                        <el-option label="English (en-US)" value="en-US" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="默认 AI 模型标识（可选）">
                      <el-input v-model="prefForm.defaultAiModel" placeholder="留空表示跟随平台路由" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <div class="pref-switch-grid">
                  <el-form-item label="界面辅助显示">
                    <div class="switch-column">
                      <el-switch v-model="prefForm.showBreadcrumbs" active-text="显示顶部面包屑导航" />
                      <el-switch v-model="prefForm.sidebarAccordion" active-text="侧栏手风琴互斥展开" />
                      <el-switch v-model="prefForm.enableWatermark" active-text="开启界面防泄密个人水印" />
                    </div>
                  </el-form-item>
                  <el-form-item label="消息与告警通知通道">
                    <div class="switch-column">
                      <el-switch v-model="prefForm.siteNotify" active-text="站内工作台消息中心通知" />
                      <el-switch v-model="prefForm.emailNotify" active-text="重要审批与报表邮件提醒" />
                      <el-switch v-model="prefForm.securityAlertNotify" active-text="异常登录与安全告警实时提醒" />
                    </div>
                  </el-form-item>
                </div>

                <el-form-item>
                  <el-button type="primary" :loading="savingPrefs" :icon="Check" @click="handleSavePreferences">
                    保存并云端同步偏好
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- Tab 4: 密码策略、MFA 与本人在线设备 -->
            <el-tab-pane name="security">
              <template #label>
                <span class="tab-label"><el-icon><Checked /></el-icon> 安全凭证与设备</span>
              </template>

              <div class="tab-section-stack">
                <!-- 密码策略与修改 -->
                <div class="sub-card">
                  <div class="sub-card-head">
                    <h4 class="sub-card-title"><el-icon><Lock /></el-icon> 登录密码与有效期</h4>
                    <el-tag
                      v-if="profile?.passwordDaysRemaining !== undefined && profile?.passwordDaysRemaining !== null"
                      :type="profile.passwordDaysRemaining <= 7 ? 'danger' : 'success'"
                      size="small"
                    >
                      {{ profile.passwordDaysRemaining > 0 ? `距离轮换到期还剩 ${profile.passwordDaysRemaining} 天` : '密码已过期，请立即更新' }}
                    </el-tag>
                  </div>
                  <p class="sub-hint">
                    上次修改时间：{{ formatDateTime(profile?.passwordChangedAt) }} · 租户策略有效期 {{ profile?.passwordMaxAgeDays || 90 }} 天
                  </p>
                  <el-form
                    ref="passwordFormRef"
                    :model="passwordForm"
                    :rules="passwordRules"
                    label-position="top"
                    style="max-width: 480px"
                  >
                    <el-form-item label="当前密码" prop="oldPassword">
                      <el-input v-model="passwordForm.oldPassword" type="password" show-password />
                    </el-form-item>
                    <el-row :gutter="12">
                      <el-col :span="12">
                        <el-form-item label="新密码" prop="newPassword">
                          <el-input v-model="passwordForm.newPassword" type="password" show-password />
                        </el-form-item>
                      </el-col>
                      <el-col :span="12">
                        <el-form-item label="确认新密码" prop="confirmPassword">
                          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
                        </el-form-item>
                      </el-col>
                    </el-row>
                    <el-button type="primary" :loading="savingPassword" @click="handleUpdatePassword">更新登录密码</el-button>
                  </el-form>
                </div>

                <!-- MFA 与外部身份绑定 -->
                <div class="sub-card">
                  <div class="sub-card-head">
                    <h4 class="sub-card-title"><el-icon><Key /></el-icon> 双因素认证 (TOTP) 与关联身份</h4>
                    <el-tag :type="mfaStatus?.enabled ? 'success' : 'warning'" size="small">
                      {{ mfaStatus?.enabled ? 'MFA 已激活保护' : 'MFA 未启用' }}
                    </el-tag>
                  </div>

                  <div v-if="!mfaStatus?.enabled" style="margin-top: 8px">
                    <el-button v-if="!mfaSetupData" type="primary" plain :loading="mfaBusy" @click="handleSetupMfa">
                      生成 MFA 绑定密钥
                    </el-button>
                    <div v-else class="mfa-setup-box">
                      <p>请在认证器 App 中输入密钥：<code>{{ mfaSetupData.secret }}</code></p>
                      <div class="mfa-verify-row">
                        <el-input v-model="mfaTotpCode" placeholder="输入 6 位动态码" maxlength="6" style="width: 180px" />
                        <el-button type="success" :loading="mfaBusy" @click="handleEnableMfa">激活绑定</el-button>
                      </div>
                    </div>
                  </div>
                  <div v-else class="mfa-verify-row" style="margin-top: 8px">
                    <el-input v-model="mfaDisableForm.password" type="password" show-password placeholder="输入密码以停用" style="width: 180px" />
                    <el-input v-model="mfaDisableForm.totpCode" placeholder="6位动态码" maxlength="6" style="width: 130px" />
                    <el-button type="danger" plain :loading="mfaBusy" @click="handleDisableMfa">停用 MFA</el-button>
                  </div>

                  <div v-if="profile?.identities?.length" style="margin-top: 14px">
                    <el-table :data="profile.identities" border size="small">
                      <el-table-column prop="identityType" label="身份渠道" width="130" />
                      <el-table-column prop="identifier" label="绑定账号标识" min-width="160" />
                      <el-table-column label="核验状态" width="110">
                        <template #default="{ row }">
                          <el-tag size="small" :type="row.verified ? 'success' : 'info'">
                            {{ row.verified ? '已核验' : '未核验' }}
                          </el-tag>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </div>

                <!-- 本人在线会话管理 -->
                <div class="sub-card">
                  <div class="sub-card-head">
                    <h4 class="sub-card-title"><el-icon><Monitor /></el-icon> 我的在线设备与会话 ({{ mySessions.length }})</h4>
                    <div style="display: flex; gap: 8px">
                      <el-button size="small" :loading="loadingSessions" @click="loadMySessions">刷新设备</el-button>
                      <el-button
                        v-if="mySessions.length > 1"
                        size="small"
                        type="danger"
                        plain
                        @click="handleRevokeOthers"
                      >
                        一键下线其他设备
                      </el-button>
                    </div>
                  </div>

                  <el-table :data="mySessions" border stripe size="small" v-loading="loadingSessions">
                    <el-table-column label="终端设备" min-width="150">
                      <template #default="{ row }">
                        <div style="display: flex; align-items: center; gap: 6px">
                          <span>{{ summarizeUserAgent(row.userAgent) }}</span>
                          <el-tag v-if="row.isCurrent" size="small" type="success" effect="dark">本机当前</el-tag>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="loginIp" label="登录 IP" width="135" />
                    <el-table-column label="最近活跃" width="160">
                      <template #default="{ row }">{{ formatDateTime(row.lastAccessAt || row.loginTime) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="100">
                      <template #default="{ row }">
                        <el-button
                          v-if="!row.isCurrent"
                          link
                          type="danger"
                          @click="handleRevokeSession(row)"
                        >
                          下线设备
                        </el-button>
                        <span v-else style="color: #64748b; font-size: 12px">当前会话</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 5: 登录审计 -->
            <el-tab-pane name="logs">
              <template #label>
                <span class="tab-label"><el-icon><Clock /></el-icon> 个人登录轨迹</span>
              </template>
              <el-table :data="profile?.recentLogs || []" border stripe size="small" style="margin-top: 8px">
                <el-table-column label="登录时间" min-width="165">
                  <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
                </el-table-column>
                <el-table-column prop="loginIp" label="来源 IP" min-width="135" />
                <el-table-column label="结果" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                      {{ row.success ? '认证成功' : '认证失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="failReason" label="说明" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.failReason || (row.success ? '正常签发访问令牌' : '--') }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </main>
    </div>
  </ControlSurface>
</template>

<style scoped lang="scss">
.profile-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.glass-panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
}

.avatar-upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 12px;
}

.avatar-circle {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  display: grid;
  place-items: center;
  color: #ffffff;
  font-size: 32px;
  font-weight: 700;
  cursor: pointer;
  overflow: visible;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.25);

  .avatar-img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
  }

  .avatar-camera-badge {
    position: absolute;
    right: -2px;
    bottom: -2px;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: #0f172a;
    color: #ffffff;
    border: 2px solid #ffffff;
    display: grid;
    place-items: center;
    font-size: 13px;
  }
}

.hero-identity {
  .nickname {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }

  .account-meta {
    margin: 4px 0 8px;
    font-size: 12.5px;
    color: #475569;
  }

  .badge-row {
    display: flex;
    justify-content: center;
    gap: 6px;
    flex-wrap: wrap;
  }
}

.bio-quote {
  margin: 12px 0 0;
  padding: 8px 12px;
  background: #f8fafc;
  border-left: 3px solid #3b82f6;
  border-radius: 6px;
  font-size: 12.5px;
  color: #334155;
  line-height: 1.5;
}

.security-score-card {
  margin-top: 16px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;

  .score-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    font-size: 12.5px;
    color: #334155;
  }

  .score-checks {
    display: flex;
    justify-content: space-between;
    margin-top: 8px;
    font-size: 11.5px;
    color: #64748b;

    .check-item.ok {
      color: #059669;
      font-weight: 600;
    }
  }
}

.meta-kv-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 14px;

  .kv-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 10px;
    font-size: 12.5px;

    .kv-k {
      color: #64748b;
      flex-shrink: 0;
    }

    .kv-v {
      color: #0f172a;
      font-weight: 500;
      text-align: right;
    }

    .kv-tags {
      display: flex;
      gap: 4px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
  }
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-section-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sub-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
}

.sub-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.sub-card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.sub-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #475569;
}

.pref-switch-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
}

.switch-column {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mfa-setup-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mfa-verify-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
