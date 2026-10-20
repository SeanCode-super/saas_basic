<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Document, Download, Filter, Key, Lock, Search } from "@element-plus/icons-vue";

interface CatalogItem {
  code: string;
  name: string;
  category: "IAM" | "TENANT" | "INTEGRATION" | "DATA" | "SCHEDULER";
  riskLevel: "P0_CRITICAL" | "P1_HIGH" | "P2_MEDIUM";
  requireApproval: boolean;
  maskingPolicy: string;
  retentionDays: number;
}

const catalog = ref<CatalogItem[]>([
  {
    code: "iam:role_api:grant",
    name: "超级管理员角色与权限资源直接授予",
    category: "IAM",
    riskLevel: "P0_CRITICAL",
    requireApproval: true,
    maskingPolicy: "明细不可隐藏 · 强制复核",
    retentionDays: 365
  },
  {
    code: "iam:user:reset_pwd",
    name: "管理员重置他人密码与手机号解绑",
    category: "IAM",
    riskLevel: "P1_HIGH",
    requireApproval: false,
    maskingPolicy: "密码哈希与密文全遮蔽",
    retentionDays: 180
  },
  {
    code: "tenant:isolation:upgrade",
    name: "租户隔离级别变更 (共享到独库迁移)",
    category: "TENANT",
    riskLevel: "P0_CRITICAL",
    requireApproval: true,
    maskingPolicy: "数据源连接串密码完全脱敏",
    retentionDays: 730
  },
  {
    code: "tenant:status:freeze",
    name: "租户空间欠费冻结或强行停用",
    category: "TENANT",
    riskLevel: "P1_HIGH",
    requireApproval: true,
    maskingPolicy: "记录操作人与完整依据",
    retentionDays: 365
  },
  {
    code: "integration:datasource:reveal_pwd",
    name: "多源数据库连接明文密码查看",
    category: "INTEGRATION",
    riskLevel: "P0_CRITICAL",
    requireApproval: true,
    maskingPolicy: "动态水印遮盖 · 仅留痕 30 秒",
    retentionDays: 365
  },
  {
    code: "audit:data:batch_export",
    name: "大批量业务数据与人员档案全量导出",
    category: "DATA",
    riskLevel: "P1_HIGH",
    requireApproval: true,
    maskingPolicy: "手机号/身份证脱敏掩码",
    retentionDays: 365
  },
  {
    code: "scheduler:job:force_stop",
    name: "线上正在运行任务强制终止",
    category: "SCHEDULER",
    riskLevel: "P2_MEDIUM",
    requireApproval: false,
    maskingPolicy: "无需脱敏 · 留存执行栈",
    retentionDays: 90
  }
]);

const searchKeyword = ref("");
const categoryFilter = ref("");

const filteredList = computed(() => {
  return catalog.value.filter((item) => {
    const matchesKw =
      !searchKeyword.value ||
      item.name.includes(searchKeyword.value) ||
      item.code.includes(searchKeyword.value);
    const matchesCat = !categoryFilter.value || item.category === categoryFilter.value;
    return matchesKw && matchesCat;
  });
});

function exportCatalog() {
  const content = JSON.stringify(catalog.value, null, 2);
  const blob = new Blob([content], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `high-risk-action-catalog-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("高风险动作目录清单已导出！");
}
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>高风险动作管控目录</h3>
          <p>等保要求明确定义哪些属于高危动作，并落实必须审计、双人审批与敏感字段脱敏。</p>
        </div>
        <div>
          <el-button :icon="Download" plain @click="exportCatalog">导出目录定义</el-button>
        </div>
      </div>
    </template>

    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索动作代码或名称..."
        clearable
        style="width: 240px"
      />
      <el-select v-model="categoryFilter" placeholder="按业务领域筛选" clearable style="width: 160px">
        <el-option label="身份与权限 (IAM)" value="IAM" />
        <el-option label="多租户控制 (TENANT)" value="TENANT" />
        <el-option label="集成与数据源 (INTEGRATION)" value="INTEGRATION" />
        <el-option label="数据与导出 (DATA)" value="DATA" />
        <el-option label="任务调度 (SCHEDULER)" value="SCHEDULER" />
      </el-select>
    </div>

    <el-table :data="filteredList" stripe style="width: 100%">
      <el-table-column prop="riskLevel" label="风险定级" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.riskLevel === 'P0_CRITICAL'" size="small" type="danger" effect="dark">P0 极高危</el-tag>
          <el-tag v-else-if="row.riskLevel === 'P1_HIGH'" size="small" type="warning" effect="dark">P1 高危</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">P2 中危</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="动作权限代码" min-width="190">
        <template #default="{ row }">
          <span class="mono-code">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="动作业务定义" min-width="210" />
      <el-table-column prop="category" label="所属领域" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.category }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="requireApproval" label="审批要求" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.requireApproval" size="small" type="danger" effect="plain">需主管审批</el-tag>
          <span v-else style="color: #64748b; font-size: 12px;">直接执行</span>
        </template>
      </el-table-column>
      <el-table-column prop="maskingPolicy" label="数据脱敏口径" min-width="180" />
      <el-table-column prop="retentionDays" label="留存天数" width="100" align="right">
        <template #default="{ row }">
          <span class="mono-code">{{ row.retentionDays }} 天</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped lang="scss">
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.mono-code {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  color: var(--sb-text-secondary);
}
</style>
