<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { probeAllDatasources, probeDatasource, type DatasourceRow, type DatasourceProbeResult } from "@/api/modules/integration";

const props = defineProps<{ rows: DatasourceRow[] }>();
const busy = ref(false);
const error = ref("");
const results = ref<DatasourceProbeResult[]>([]);
const entries = computed(() => props.rows.map(source => ({
  source, result: results.value.find(item => item.id === source.id)
})));

async function probe(id?: number) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (id === undefined) results.value = await probeAllDatasources();
    else {
      const result = await probeDatasource(id);
      results.value = [...results.value.filter(item => item.id !== id), result];
    }
  } catch {
    error.value = "本次探测未完成，请重试。";
  } finally {
    busy.value = false;
  }
}

function exportResults() {
  if (!results.value.length) return;
  const url = URL.createObjectURL(new Blob([JSON.stringify(results.value, null, 2)], { type: "application/json" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = "datasource-probes.json";
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  ElMessage.success("已导出最近一次实测记录");
}
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <strong>数据源 TCP 连通性</strong>
      <el-button :loading="busy" @click="probe()">检测全部</el-button>
      <el-button :disabled="!results.length || busy" @click="exportResults">导出检测结果</el-button>
    </template>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <el-table :data="entries" empty-text="暂无已登记的数据源">
      <el-table-column prop="source.name" label="数据源" />
      <el-table-column label="最近检测"><template #default="{ row }">{{ row.result?.testedAt ?? row.source.lastTestedAt ?? '未检测' }}</template></el-table-column>
      <el-table-column label="结果"><template #default="{ row }">{{ row.result?.testStatus ?? row.source.testStatus ?? '未检测' }}</template></el-table-column>
      <el-table-column label="本次耗时"><template #default="{ row }">{{ row.result ? row.result.latencyMs + ' ms' : '—' }}</template></el-table-column>
      <el-table-column label="说明"><template #default="{ row }">{{ row.result?.message }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button link :disabled="busy" @click="probe(row.source.id)">检测</el-button></template></el-table-column>
    </el-table>
  </el-card>
</template>
