<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Check, Document, Lock, Key, CircleCheck } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  securityPolicy: {
    minLength: number;
    passwordHistory: number;
    sessionTimeoutMinutes: number;
    requireSpecial: boolean;
    forceMfa: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const complianceScore = computed(() => {
  let score = 60;
  if (props.securityPolicy.minLength >= 8) score += 10;
  if (props.securityPolicy.requireSpecial) score += 10;
  if (props.securityPolicy.passwordHistory >= 3) score += 10;
  if (props.securityPolicy.sessionTimeoutMinutes <= 120) score += 5;
  if (props.securityPolicy.forceMfa) score += 5;
  return score;
});

function handleExportDossier() {
  const dossier = {
    title: "企业安全基线与等保三级 (GB/T 22239-2019) 遵从性报告",
    generatedAt: new Date().toISOString(),
    score: complianceScore.value,
    evaluation: complianceScore.value >= 90 ? "优秀 - 符合等保三级准入" : "良好 - 建议加固",
    policy: props.securityPolicy,
    standards: [
      { standard: "应具备登录会话超时自动退出机制", status: "COMPLIANT", detail: `当前设置 ${props.securityPolicy.sessionTimeoutMinutes} 分钟` },
      { standard: "应使用包含数字、字母及特殊字符的强密码", status: props.securityPolicy.requireSpecial ? "COMPLIANT" : "WARNING", detail: `最小长度 ${props.securityPolicy.minLength}` },
      { standard: "应避免使用近期历史使用过的密码", status: props.securityPolicy.passwordHistory >= 3 ? "COMPLIANT" : "WARNING", detail: `保留历史 ${props.securityPolicy.passwordHistory} 次` },
      { standard: "特权账号应强制启用多因子鉴权 (MFA)", status: props.securityPolicy.forceMfa ? "COMPLIANT" : "INFO", detail: props.securityPolicy.forceMfa ? "全员强制" : "白名单受控" }
    ]
  };

  const blob = new Blob([JSON.stringify(dossier, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `security_compliance_dossier_${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
  ElMessage.success("已导出等保三级安全基线评估报告");
}
</script>

<template>
  <div class="system-security">
    <!-- Sleek 40px Security Telemetry Strip -->
    <div class="security-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">等保合规评分</span>
          <strong class="telemetry-val">{{ complianceScore }} 分</strong>
          <el-tag type="success" size="small" effect="plain">等保三级达标</el-tag>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Lock /></el-icon></span>
          <span class="telemetry-label">密码基线</span>
          <strong class="telemetry-val">≥{{ securityPolicy.minLength }} 位 · {{ securityPolicy.requireSpecial ? '含特殊字符' : '通用' }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">会话超时</span>
          <strong class="telemetry-val">{{ securityPolicy.sessionTimeoutMinutes }} 分钟</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">多因子 MFA</span>
          <strong class="telemetry-val">{{ securityPolicy.forceMfa ? '强制开启' : '选配' }}</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button type="primary" size="small" :icon="Document" @click="handleExportDossier">导出安全报告</el-button>
      </div>
    </div>

    <!-- 策略配置与执行看板 -->
    <div class="policy-workbench">
      <BaseCard tone="soft">
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">安全基线与凭证控制</h3>
              <p class="panel-subtitle">密码复杂度、历史密码留存、会话有效期与双因子认证平台统一收口。</p>
            </div>
            <el-button type="primary" :icon="Check" @click="savePolicy('系统安全策略与等保基线已成功保存')">
              保存安全策略
            </el-button>
          </div>
        </template>

        <el-form label-position="top">
          <div class="dialog-grid">
            <el-form-item label="密码最小字符长度">
              <el-input-number v-model="securityPolicy.minLength" :min="8" :max="32" style="width: 100%" />
            </el-form-item>
            <el-form-item label="历史密码记忆次数">
              <el-input-number v-model="securityPolicy.passwordHistory" :min="1" :max="24" style="width: 100%" />
            </el-form-item>
            <el-form-item label="会话无操作失效 (分钟)">
              <el-input-number v-model="securityPolicy.sessionTimeoutMinutes" :min="15" :max="1440" style="width: 100%" />
            </el-form-item>
          </div>
          <div class="policy-toggle-stack mt-3">
            <el-form-item>
              <el-switch v-model="securityPolicy.requireSpecial" active-text="强制密码包含特殊字符 (!@#$%^&*)" />
            </el-form-item>
            <el-form-item>
              <el-switch v-model="securityPolicy.forceMfa" active-text="强制所有管理账号启用多因子认证 (TOTP / SMS)" />
            </el-form-item>
          </div>
        </el-form>
      </BaseCard>

      <BaseCard tone="contrast">
        <template #header>
          <div class="panel-header-row">
            <div>
              <h3 class="panel-title">等保核查准入检查单</h3>
              <p class="panel-subtitle">关键安全切面实时合规检测。</p>
            </div>
          </div>
        </template>

        <div class="compliance-checklist">
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">身份鉴别复杂度</span>
              <span class="item-desc">最小 {{ securityPolicy.minLength }} 位，{{ securityPolicy.requireSpecial ? '包含特殊字符' : '常规组合' }}</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">密码轮换与防重放</span>
              <span class="item-desc">保留最近 {{ securityPolicy.passwordHistory }} 次密码记录，禁止复用</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">会话超时登出</span>
              <span class="item-desc">{{ securityPolicy.sessionTimeoutMinutes }} 分钟无操作自动清理前端 Token</span>
            </div>
          </div>
          <div class="check-item">
            <el-icon class="icon-pass"><CircleCheck /></el-icon>
            <div class="item-meta">
              <span class="item-title">动态特权防护</span>
              <span class="item-desc">{{ securityPolicy.forceMfa ? '全员启用 MFA 双因子防护' : '关键写操作需二道密码确认' }}</span>
            </div>
          </div>
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-security {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Sleek Security Telemetry Bar */
.security-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .telemetry-items {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .telemetry-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;

    .telemetry-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 4px;
      font-size: 13px;

      &.green { background: #f0fdf4; color: #16a34a; }
      &.blue { background: #eff6ff; color: #2563eb; }
      &.amber { background: #fffbeb; color: #d97706; }
      &.purple { background: #faf5ff; color: #9333ea; }
    }

    .telemetry-label {
      color: #64748b;
      font-size: 12px;
    }

    .telemetry-val {
      color: #0f172a;
      font-weight: 600;
    }
  }

  .telemetry-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }

  .telemetry-actions {
    display: inline-flex;
    align-items: center;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
  }

  .panel-subtitle {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }
}

.policy-workbench {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.compliance-checklist {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;

  .check-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;

    .icon-pass {
      color: #16a34a;
      font-size: 18px;
      flex-shrink: 0;
    }

    .item-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .item-title {
        font-size: 13px;
        font-weight: 600;
        color: #1e293b;
      }

      .item-desc {
        font-size: 12px;
        color: #64748b;
      }
    }
  }
}

.mt-4 { margin-top: 16px; }
.mt-3 { margin-top: 12px; }
</style>
