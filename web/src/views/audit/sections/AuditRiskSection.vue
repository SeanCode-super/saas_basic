<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Bell, Cpu, Operation, Search, VideoPlay, Warning } from "@element-plus/icons-vue";

const props = defineProps<{
  riskPolicy: {
    abnormalLoginAlert: boolean;
    privilegeEscalationAlert: boolean;
    exportBurstAlert: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const rules = reactive([
  {
    id: "RULE_BRUTE_FORCE",
    name: "暴力破解防范规则",
    desc: "同一 IP 或账号 5 分钟内连续认证失败达 5 次，自动触发告警并锁定 15 分钟。",
    severity: "HIGH",
    enabled: true,
    action: "LOCK_ACCOUNT"
  },
  {
    id: "RULE_EXPORT_BURST",
    name: "大批量敏捷数据导出",
    desc: "单次导出数据超过 5,000 条，或 1 小时内累计导出 3 次以上，触发二次主管审批与留痕。",
    severity: "CRITICAL",
    enabled: true,
    action: "REQUIRE_APPROVAL"
  },
  {
    id: "RULE_PRIV_ESCALATION",
    name: "未授权提权与越权拦截",
    desc: "非超级管理员账号试图直接修改 iam_role_api 或分配根组织管理权限，立即拦截并阻断会话。",
    severity: "CRITICAL",
    enabled: true,
    action: "REVOKE_SESSION"
  },
  {
    id: "RULE_GEO_ANOMALY",
    name: "异地/多端并发登录异常",
    desc: "同一用户账号在 10 分钟内出现跨省/跨大区 IP 访问，强制触发短信或 MFA 验证码二次校验。",
    severity: "MEDIUM",
    enabled: true,
    action: "TRIGGER_MFA"
  }
]);

// Rule Simulator
const simModalVisible = ref(false);
const simRunning = ref(false);
const simForm = reactive({
  ruleType: "RULE_BRUTE_FORCE",
  sampleAccount: "admin",
  sampleIp: "124.78.201.33",
  frequency: 8
});
const simResult = ref<Record<string, any> | null>(null);

function runSimulation() {
  simRunning.value = true;
  setTimeout(() => {
    simRunning.value = false;
    simResult.value = {
      evaluatedAt: new Date().toISOString(),
      matchedRule: simForm.ruleType,
      decision: simForm.frequency >= 5 ? "ALERT_AND_BLOCK" : "ALLOW_WITH_LOG",
      matchedThreshold: "失败阈值 >= 5次 (实际提交: " + simForm.frequency + "次)",
      enforcedAction: simForm.frequency >= 5 ? "封禁客户端 IP 15分钟 · 发送安全员告警" : "无处置动作",
      latencyMs: "0.82ms"
    };
    ElMessage.success("风险规则引擎决策模拟完成！");
  }, 400);
}
</script>

<template>
  <div class="risk-policy-stack">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <div class="header-left">
            <h3>风险识别基线与规则引擎</h3>
            <p>对高危行为（异常登录、权限提升、批量导出、跨租户探测）实行毫秒级策略决策。</p>
          </div>
          <div class="header-right">
            <el-button type="primary" :icon="Cpu" @click="simModalVisible = true">运行策略模拟器</el-button>
          </div>
        </div>
      </template>

      <!-- Risk Rules Grid -->
      <div class="rules-grid">
        <div v-for="r in rules" :key="r.id" class="rule-card">
          <div class="rule-head">
            <div class="rule-title-group">
              <el-tag
                size="small"
                :type="r.severity === 'CRITICAL' ? 'danger' : r.severity === 'HIGH' ? 'warning' : 'info'"
                effect="dark"
              >
                {{ r.severity }}
              </el-tag>
              <strong>{{ r.name }}</strong>
            </div>
            <el-switch v-model="r.enabled" @change="savePolicy(`规则「${r.name}」状态已更新`)" />
          </div>
          <p class="rule-desc">{{ r.desc }}</p>
          <div class="rule-foot">
            <span class="action-label">处置动作: <strong>{{ r.action }}</strong></span>
            <span class="mono-code">{{ r.id }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Simulation Modal -->
    <el-dialog v-model="simModalVisible" title="安全风险决策规则实时模拟" width="560px">
      <el-form label-position="top">
        <el-form-item label="选择被测安全规则">
          <el-select v-model="simForm.ruleType" style="width: 100%">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.name} (${r.id})`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模拟账号">
              <el-input v-model="simForm.sampleAccount" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户端 IP">
              <el-input v-model="simForm.sampleIp" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发频次 / 导出量级">
          <el-input-number v-model="simForm.frequency" :min="1" :max="100000" style="width: 100%" />
        </el-form-item>
      </el-form>

      <div v-if="simResult" class="sim-result-box">
        <div class="result-title">
          <span>引擎决策判定:</span>
          <el-tag :type="simResult.decision.includes('BLOCK') ? 'danger' : 'success'" size="small" effect="dark">
            {{ simResult.decision }}
          </el-tag>
          <span class="latency">决策耗时: {{ simResult.latencyMs }}</span>
        </div>
        <p><strong>命中规则:</strong> {{ simResult.matchedRule }}</p>
        <p><strong>触发阈值:</strong> {{ simResult.matchedThreshold }}</p>
        <p><strong>执行联动:</strong> {{ simResult.enforcedAction }}</p>
      </div>

      <template #footer>
        <el-button @click="simModalVisible = false">关闭</el-button>
        <el-button type="primary" :loading="simRunning" :icon="VideoPlay" @click="runSimulation">执行模拟评估</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.risk-policy-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rules-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.rule-card {
  padding: 16px 18px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
}

.rule-head {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .rule-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: var(--sb-text-primary);
  }
}

.rule-desc {
  margin: 0;
  font-size: 13px;
  color: var(--sb-text-secondary);
  line-height: 1.6;
}

.rule-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  border-top: 1px dashed var(--sb-border-color);
  padding-top: 8px;

  .mono-code {
    font-family: ui-monospace, SFMono-Regular, monospace;
    color: #94a3b8;
  }
}

.sim-result-box {
  margin-top: 16px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  .result-title {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    font-weight: 600;

    .latency {
      margin-left: auto;
      font-size: 12px;
      color: #64748b;
    }
  }

  p {
    margin: 4px 0;
    font-size: 13px;
    color: #334155;
  }
}
</style>
