<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  ElMessage
} from "element-plus";
import {
  Cpu,
  VideoPlay
} from "@element-plus/icons-vue";
import {
  evaluateRiskRule,
  fetchRiskRules,
  updateRiskRuleStatus,
  type RiskEvaluateResult,
  type RiskRuleItem
} from "@/api/modules/audit";
import WorkspacePanel from "@/components/platform/WorkspacePanel.vue";

defineProps<{
  riskPolicy: {
    abnormalLoginAlert: boolean;
    privilegeEscalationAlert: boolean;
    exportBurstAlert: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const rules = ref<RiskRuleItem[]>([]);
const loading = ref(false);

// Rule Simulator
const simModalVisible = ref(false);
const simRunning = ref(false);
const simForm = reactive({
  ruleType: "RULE_BRUTE_FORCE",
  sampleAccount: "admin",
  sampleIp: "124.78.201.33",
  frequency: 8
});
const simResult = ref<RiskEvaluateResult | null>(null);

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await fetchRiskRules();
    if (rules.value.length > 0 && !rules.value.some((r) => r.code === simForm.ruleType)) {
      simForm.ruleType = rules.value[0].code;
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "加载风险规则失败");
  } finally {
    loading.value = false;
  }
}

async function handleToggleRule(rule: RiskRuleItem, nextEnabled: boolean | string | number) {
  const enabled = Boolean(nextEnabled);
  try {
    const updated = await updateRiskRuleStatus(rule.id, enabled);
    rule.enabled = updated.enabled;
    ElMessage.success(`规则「${rule.name}」已${updated.enabled ? "启用" : "停用"}`);
  } catch (error: any) {
    rule.enabled = !enabled;
    ElMessage.error(error?.response?.data?.message ?? "更新风险规则状态失败");
  }
}

async function runSimulation() {
  simRunning.value = true;
  try {
    simResult.value = await evaluateRiskRule({
      ruleCode: simForm.ruleType,
      sampleAccount: simForm.sampleAccount.trim() || undefined,
      sampleIp: simForm.sampleIp.trim() || undefined,
      frequency: simForm.frequency
    });
    ElMessage.success("风险规则引擎真实评估完成！");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "执行风险规则评估失败");
  } finally {
    simRunning.value = false;
  }
}

onMounted(() => {
  void loadRules();
});
</script>

<template>
  <div class="risk-policy-stack">
    <el-card shadow="never" v-loading="loading">
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
        <div v-for="r in rules" :key="r.code" class="rule-card">
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
            <el-switch v-model="r.enabled" @change="handleToggleRule(r, $event)" />
          </div>
          <p class="rule-desc">{{ r.description || r.ruleExpr }}</p>
          <div class="rule-foot">
            <span class="action-label">处置动作: <strong>{{ r.action }}</strong></span>
            <span class="mono-code">{{ r.code }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Simulation Panel -->
    <WorkspacePanel v-model="simModalVisible" title="安全风险决策规则实时模拟" width="560px">
      <el-form label-position="top">
        <el-form-item label="选择被测安全规则">
          <el-select v-model="simForm.ruleType" style="width: 100%">
            <el-option v-for="r in rules" :key="r.code" :label="`${r.name} (${r.code})`" :value="r.code" />
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
          <span class="latency">决策耗时: {{ simResult.latencyMs }}ms</span>
        </div>
        <p><strong>命中规则:</strong> {{ simResult.matchedRule }}</p>
        <p><strong>触发阈值:</strong> {{ simResult.matchedThreshold }}</p>
        <p><strong>执行联动:</strong> {{ simResult.enforcedAction }}</p>
        <p>
          <strong>实时审计统计:</strong> 近期登录失败 {{ simResult.recentLoginFailures }} 次 · 近期敏感操作
          {{ simResult.recentOperations }} 次
        </p>
      </div>

      <template #footer>
        <el-button @click="simModalVisible = false">关闭</el-button>
        <el-button type="primary" :loading="simRunning" :icon="VideoPlay" @click="runSimulation">执行模拟评估</el-button>
      </template>
    </WorkspacePanel>
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
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.90);
  backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  -webkit-backdrop-filter: blur(28px) saturate(210%) contrast(104%);
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
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
  color: #475569;
  border-top: 1px dashed var(--sb-border-color);
  padding-top: 8px;

  .mono-code {
    font-family: ui-monospace, SFMono-Regular, monospace;
    color: #475569;
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
      color: #475569;
    }
  }

  p {
    margin: 4px 0;
    font-size: 13px;
    color: #334155;
  }
}
</style>
