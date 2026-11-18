<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  ElMessage
} from "element-plus";
import {
  Download
} from "@element-plus/icons-vue";
import { fetchHighRiskCatalog, type HighRiskCatalogItem } from "@/api/modules/audit";

const catalog = ref<HighRiskCatalogItem[]>([]);
const loading = ref(false);
const searchKeyword = ref("");
const categoryFilter = ref("");

const filteredList = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  return catalog.value.filter((item) => {
    const matchesKw =
      !kw ||
      item.name.toLowerCase().includes(kw) ||
      item.code.toLowerCase().includes(kw) ||
      item.urlPattern.toLowerCase().includes(kw);
    const matchesCat = !categoryFilter.value || item.category === categoryFilter.value;
    return matchesKw && matchesCat;
  });
});

async function loadCatalog() {
  loading.value = true;
  try {
    catalog.value = await fetchHighRiskCatalog();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "加载高风险管控目录失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadCatalog();
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
      <el-select v-model="categoryFilter" placeholder="按业务领域筛选" clearable style="width: 180px">
        <el-option label="身份与权限 (IAM)" value="IAM" />
        <el-option label="多租户控制 (TENANT)" value="TENANT" />
        <el-option label="系统治理 (SYSTEM)" value="SYSTEM" />
        <el-option label="集成与数据源 (INTEGRATION)" value="INTEGRATION" />
        <el-option label="数据与导出 (DATA)" value="DATA" />
        <el-option label="任务调度 (SCHEDULER)" value="SCHEDULER" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="filteredList" stripe style="width: 100%">
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
