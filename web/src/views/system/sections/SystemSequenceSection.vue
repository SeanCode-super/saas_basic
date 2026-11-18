<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  ElMessage,
  ElMessageBox
} from "element-plus";
import {
  Check,
  RefreshRight,
  Tickets,
  MagicStick,
  CircleCheck,
  Setting
} from "@element-plus/icons-vue";
import {
  fetchSequenceRules,
  generateNextSequence,
  resetSequenceRule,
  updateSequenceRule,
  type SequenceRuleItem
} from "@/api/modules/system";
import WorkspacePanel from "@/components/platform/WorkspacePanel.vue";

defineProps<{
  sequencePolicy: {
    tenantPrefix: string;
    orderPrefix: string;
    resetCycle: string;
    sequenceLength: number;
  };
  savePolicy: (message: string) => void;
}>();

const policyExpanded = ref(false);
const loading = ref(false);
const savingRule = ref(false);
const editDialogVisible = ref(false);
const editingRule = ref<SequenceRuleItem | null>(null);
const sequenceRules = ref<SequenceRuleItem[]>([]);

async function loadSequenceRules() {
  loading.value = true;
  try {
    sequenceRules.value = await fetchSequenceRules();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "加载分布式序列规则失败");
  } finally {
    loading.value = false;
  }
}

async function handleGenerateNext(row: SequenceRuleItem) {
  try {
    const next = await generateNextSequence(row.code);
    row.currentSeq = next.currentSeq;
    row.sample = next.generatedValue;
    ElMessage.success(`已从数据库原子递增并生成流水号：${next.generatedValue}`);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "生成流水号失败");
  }
}

async function handleResetCounter(row: SequenceRuleItem) {
  try {
    await ElMessageBox.confirm(
      `确定要将规则 [${row.name}] 的分布式流水号重置为 0 吗？此操作会导致编号计数重新开始。`,
      "重置流水号确认",
      { type: "warning" }
    );
    const updated = await resetSequenceRule(row.code);
    Object.assign(row, updated);
    ElMessage.success(`规则 [${row.name}] 流水号已重置`);
  } catch (error: any) {
    if (error !== "cancel" && error !== "close") {
      ElMessage.error(error?.response?.data?.message ?? "重置流水号失败");
    }
  }
}

function handleEditRule(row: SequenceRuleItem) {
  editingRule.value = { ...row };
  editDialogVisible.value = true;
}

async function handleSaveRule() {
  if (!editingRule.value) {
    return;
  }
  if (!editingRule.value.name?.trim()) {
    ElMessage.warning("请输入规则名称");
    return;
  }
  savingRule.value = true;
  try {
    const updated = await updateSequenceRule(editingRule.value.id, {
      name: editingRule.value.name.trim(),
      prefix: editingRule.value.prefix?.trim() || undefined,
      datePattern: editingRule.value.datePattern?.trim() ?? "",
      seqLength: editingRule.value.seqLength,
      step: editingRule.value.step,
      resetCycle: editingRule.value.resetCycle,
      status: editingRule.value.status
    });
    const index = sequenceRules.value.findIndex((r) => r.id === updated.id);
    if (index !== -1) {
      sequenceRules.value[index] = updated;
    }
    editDialogVisible.value = false;
    ElMessage.success("分布式序列规则配置已持久化保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存序列规则失败");
  } finally {
    savingRule.value = false;
  }
}

onMounted(() => {
  void loadSequenceRules();
});
</script>

<template>
  <div class="system-sequence">
    <!-- Sleek 40px Sequence Telemetry Strip -->
    <div class="sequence-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><Tickets /></el-icon></span>
          <span class="telemetry-label">租户前缀</span>
          <strong class="telemetry-val">{{ sequencePolicy.tenantPrefix }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon green"><el-icon><MagicStick /></el-icon></span>
          <span class="telemetry-label">订单前缀</span>
          <strong class="telemetry-val">{{ sequencePolicy.orderPrefix }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><RefreshRight /></el-icon></span>
          <span class="telemetry-label">重置周期</span>
          <strong class="telemetry-val">{{ sequencePolicy.resetCycle }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><CircleCheck /></el-icon></span>
          <span class="telemetry-label">补零长度</span>
          <strong class="telemetry-val">{{ sequencePolicy.sequenceLength }} 位</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button link type="primary" size="small" @click="policyExpanded = !policyExpanded">
          <el-icon><Setting /></el-icon>
          <span>{{ policyExpanded ? '收起基线设置' : '配置全局基线' }}</span>
        </el-button>
        <el-button type="primary" size="small" :icon="Check" @click="savePolicy('分布式编号规则基线已成功保存')">
          保存编号基线
        </el-button>
      </div>
    </div>

    <!-- Collapsible Baseline Configuration Card -->
    <el-collapse-transition>
      <div v-show="policyExpanded" class="policy-collapse-box">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header-row">
              <div>
                <h4 style="margin: 0; font-size: 14px; font-weight: 600; color: #0f172a">分布式序列号全局基线</h4>
                <p style="margin: 2px 0 0; font-size: 12px; color: #64748b">租户编码、订单号、合同台账等分布式唯一单号生成规则与防碰撞机制。</p>
              </div>
            </div>
          </template>

          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="租户编码默认前缀">
                  <el-input v-model="sequencePolicy.tenantPrefix" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="订单业务默认前缀">
                  <el-input v-model="sequencePolicy.orderPrefix" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="自增序列重置周期">
                  <el-select v-model="sequencePolicy.resetCycle" style="width: 100%">
                    <el-option label="按日重置 (DAILY)" value="DAILY" />
                    <el-option label="按月重置 (MONTHLY)" value="MONTHLY" />
                    <el-option label="按年重置 (YEARLY)" value="YEARLY" />
                    <el-option label="永不重置 (NEVER)" value="NEVER" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="自增补零长度 (Length)">
                  <el-input-number v-model="sequencePolicy.sequenceLength" :min="4" :max="18" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>
      </div>
    </el-collapse-transition>

    <!-- 分布式序列号生成器与演练台账 (Primary Viewport) -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">分布式序列规则与实时演练台账</h3>
            <p class="panel-subtitle">支持前缀、时间通配符、补零位数与基于 Redis 分布式步长递增。</p>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="sequenceRules" border stripe style="width: 100%">
        <el-table-column prop="code" label="规则编码" width="140" />
        <el-table-column prop="name" label="业务规则名称" min-width="160">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #0f172a">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="prefix" label="前缀" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.prefix }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pattern" label="模板通配符表达式" min-width="220">
          <template #default="{ row }">
            <code class="seq-code">{{ row.pattern }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="resetCycle" label="重置周期" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.resetCycle }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentSeq" label="当前计数值" width="110" align="center">
          <template #default="{ row }">
            <span style="font-weight: 700; color: #2563eb">{{ row.currentSeq }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sample" label="当前最新生成样例" min-width="210">
          <template #default="{ row }">
            <span class="seq-code" style="color: #16a34a">{{ row.sample }}</span>
          </template>
        </el-table-column>
        <el-table-column label="动作演练与配置" min-width="250">
          <template #default="{ row }">
            <el-button link type="primary" :icon="MagicStick" @click="handleGenerateNext(row)">发号测试</el-button>
            <el-button link type="warning" :icon="RefreshRight" @click="handleResetCounter(row)">归零重置</el-button>
            <el-button link type="primary" @click="handleEditRule(row)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑规则面板 -->
    <WorkspacePanel v-model="editDialogVisible" title="配置分布式序列规则" width="520px">
      <el-form v-if="editingRule" label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="editingRule.name" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前缀标识">
              <el-input v-model="editingRule.prefix" placeholder="如 ORD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期格式 (Date Pattern)">
              <el-input v-model="editingRule.datePattern" placeholder="如 yyyyMMdd 或 yyyy" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="补零长度">
              <el-input-number v-model="editingRule.seqLength" :min="2" :max="18" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="递增步长">
              <el-input-number v-model="editingRule.step" :min="1" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重置周期">
              <el-select v-model="editingRule.resetCycle" style="width: 100%">
                <el-option label="按日重置" value="DAILY" />
                <el-option label="按月重置" value="MONTHLY" />
                <el-option label="按年重置" value="YEARLY" />
                <el-option label="永不重置" value="NEVER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="handleSaveRule">保存规则</el-button>
      </template>
    </WorkspacePanel>
  </div>
</template>

<style scoped lang="scss">
.system-sequence {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Sleek Sequence Telemetry Bar */
.sequence-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
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

      &.blue { background: #eff6ff; color: #2563eb; }
      &.green { background: #f0fdf4; color: #16a34a; }
      &.purple { background: #faf5ff; color: #9333ea; }
      &.amber { background: #fffbeb; color: #d97706; }
    }

    .telemetry-label {
      color: #475569;
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
    gap: 8px;
  }
}

.policy-collapse-box {
  margin-bottom: 4px;
}

.table-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title {
    margin: 0 0 4px;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }

  .panel-subtitle {
    margin: 0;
    font-size: 12px;
    color: #475569;
  }
}

.seq-code {
  font-family: monospace;
  font-weight: 600;
  color: #0f172a;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
