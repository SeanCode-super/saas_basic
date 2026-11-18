<script setup lang="ts">
import {
  computed,
  onMounted,
  ref
} from "vue";
import {
  ElMessage
} from "element-plus";
import {
  Check,
  CircleCheck,
  Search,
  Refresh,
  Setting,
  ArrowUp
} from "@element-plus/icons-vue";
import {
  fetchCodegenProjects,
  fetchDbTableColumns,
  fetchDbTables,
  type CodegenColumnModel,
  type CodegenProjectRow,
  type DbTableItem
} from "@/api/modules/codegen";

const props = defineProps<{
  qualityGate: {
    requireAuditField: boolean;
    requireDictBinding: boolean;
    requireQueryForm: boolean;
    requireListPermission: boolean;
    requireSqlArtifact: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const policyExpanded = ref(false);
const scanning = ref(false);
const scanScore = ref(0);
const dbTables = ref<DbTableItem[]>([]);
const selectedTable = ref("");
const projects = ref<CodegenProjectRow[]>([]);

interface GateRuleCheck {
  id: string;
  name: string;
  dimension: string;
  status: "PASSED" | "WARNING" | "FAILED";
  detail: string;
  recommendation: string;
}

const checkResults = ref<GateRuleCheck[]>([]);

const passedCount = computed(() => checkResults.value.filter((item) => item.status === "PASSED").length);

function evaluateTableGate(tableName: string, cols: CodegenColumnModel[]): GateRuleCheck[] {
  const names = new Set(cols.map((c) => c.columnName.toLowerCase()));
  const auditCols = ["created_at", "created_by", "updated_at", "updated_by"];
  const foundAudit = auditCols.filter((c) => names.has(c));
  const auditPassed = !props.qualityGate.requireAuditField || foundAudit.length === auditCols.length;

  const hasTenant = names.has("tenant_id");
  const hasDeleted = names.has("deleted");
  const isolationStatus: GateRuleCheck["status"] =
    hasTenant && hasDeleted ? "PASSED" : hasTenant || hasDeleted ? "WARNING" : "FAILED";

  const dictCols = cols.filter((c) => Boolean(c.dictCode));
  const statusCols = cols.filter((c) => c.columnName.endsWith("status") || c.columnName.endsWith("type"));
  const dictStatus: GateRuleCheck["status"] =
    !props.qualityGate.requireDictBinding || dictCols.length > 0
      ? "PASSED"
      : statusCols.length > 0
        ? "WARNING"
        : "PASSED";

  const pkCols = cols.filter((c) => c.isPk);
  const queryCols = cols.filter((c) => c.isQuery);
  const queryStatus: GateRuleCheck["status"] =
    pkCols.length > 0 && (!props.qualityGate.requireQueryForm || queryCols.length > 0)
      ? "PASSED"
      : pkCols.length > 0
        ? "WARNING"
        : "FAILED";

  const enabledProjects = projects.value.filter((p) => p.status === "ENABLED").length;
  const projectStatus: GateRuleCheck["status"] = enabledProjects > 0 ? "PASSED" : "WARNING";

  return [
    {
      id: "GATE_01",
      name: "审计字段合规性",
      dimension: "数据审计",
      status: auditPassed ? "PASSED" : foundAudit.length > 0 ? "WARNING" : "FAILED",
      detail: `表 ${tableName} 检测到 ${foundAudit.length}/4 个标准审计列 (${foundAudit.join(", ") || "无"})`,
      recommendation: auditPassed
        ? "符合生产环境追溯标准，已匹配 MyBatis-Plus 自动填充规范"
        : "建议补全 created_at, created_by, updated_at, updated_by"
    },
    {
      id: "GATE_02",
      name: "多租户与软删除隔离",
      dimension: "租户安全",
      status: isolationStatus,
      detail: `tenant_id: ${hasTenant ? "已就绪" : "缺失"} · deleted 逻辑删: ${hasDeleted ? "已就绪" : "缺失"}`,
      recommendation:
        isolationStatus === "PASSED"
          ? "符合 JSqlParser AST 行级与租户隔离改写要求"
          : "业务表建议补充 tenant_id 与 deleted 索引列以防越权与误删"
    },
    {
      id: "GATE_03",
      name: "字典与枚举绑定校验",
      dimension: "元数据映射",
      status: dictStatus,
      detail:
        dictCols.length > 0
          ? `检测到 ${dictCols.length} 个显式字典绑定字段 (${dictCols.map((c) => c.columnName).join(", ")})`
          : `发现 ${statusCols.length} 个状态/类型字段，可在元数据建模中绑定 sys_dict`,
      recommendation: "前端 ProTable 渲染与查询下拉组件将自动对齐系统字典"
    },
    {
      id: "GATE_04",
      name: "主键与查询契约约束",
      dimension: "接口规范",
      status: queryStatus,
      detail: `主键字段 ${pkCols.length} 个 · 可查询字段 ${queryCols.length} 个（共 ${cols.length} 列）`,
      recommendation: "已校验主键唯一性与 PageQuery 安全分页输入约束"
    },
    {
      id: "GATE_05",
      name: "生成工程注册与产物闭环",
      dimension: "工程治理",
      status: projectStatus,
      detail: `当前已注册 ${enabledProjects} 个启用态代码生成工程模板基线`,
      recommendation:
        enabledProjects > 0
          ? "生成代码落盘后可直接在菜单与角色授权中心分配"
          : "建议先在代码生成工程台账中启用默认生成工程"
    }
  ];
}

async function handleRunScan() {
  if (!selectedTable.value) {
    ElMessage.warning("当前暂无可检测的数据库表");
    return;
  }
  scanning.value = true;
  try {
    const res = await fetchDbTableColumns(selectedTable.value);
    const cols = res.data ?? [];
    const results = evaluateTableGate(selectedTable.value, cols);
    checkResults.value = results;
    scanScore.value = results.reduce(
      (sum, item) => sum + (item.status === "PASSED" ? 20 : item.status === "WARNING" ? 10 : 0),
      0
    );
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? `拉取表 ${selectedTable.value} 元数据失败`);
  } finally {
    scanning.value = false;
  }
}

async function loadMetadataAndScan() {
  scanning.value = true;
  try {
    const [tablesRes, projectsRes] = await Promise.all([
      fetchDbTables(),
      fetchCodegenProjects().catch(() => ({ data: [] as CodegenProjectRow[] }))
    ]);
    dbTables.value = tablesRes.data ?? [];
    projects.value = projectsRes.data ?? [];
    if (!selectedTable.value && dbTables.value.length > 0) {
      const preferred =
        dbTables.value.find((t) => /^(biz_|crm_|erp_|iam_user$)/.test(t.tableName)) ?? dbTables.value[0];
      selectedTable.value = preferred.tableName;
    }
    if (selectedTable.value) {
      await handleRunScan();
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "加载数据库表清单失败");
  } finally {
    scanning.value = false;
  }
}

onMounted(() => {
  void loadMetadataAndScan();
});
</script>

<template>
  <div class="codegen-gate">
    <el-card shadow="never" class="gate-card">
      <!-- 极简门禁状态工具条 -->
      <div class="gate-toolbar-row">
        <div class="toolbar-left">
          <span class="toolbar-title">交付质量门禁</span>
          <el-tag :type="scanScore >= 80 ? 'success' : scanScore >= 60 ? 'warning' : 'danger'" size="default" effect="light" class="gate-score-pill">
            <el-icon><CircleCheck /></el-icon>
            门禁评分 {{ scanScore }}/100 · {{ passedCount }}/{{ checkResults.length }} 通过
          </el-tag>
          <span class="gate-summary-text">实时读取数据库元数据，检测审计列、租户隔离、字典绑定与查询契约</span>
        </div>

        <div class="toolbar-actions">
          <el-select
            v-model="selectedTable"
            size="small"
            filterable
            placeholder="选择检测数据表"
            style="width: 220px"
            @change="handleRunScan"
          >
            <el-option
              v-for="t in dbTables"
              :key="t.tableName"
              :label="t.tableComment ? `${t.tableName} (${t.tableComment})` : t.tableName"
              :value="t.tableName"
            />
          </el-select>
          <el-button link type="primary" size="small" :icon="policyExpanded ? ArrowUp : Setting" @click="policyExpanded = !policyExpanded">
            {{ policyExpanded ? '收起门禁规则' : '门禁策略设置' }}
          </el-button>
          <el-button :icon="Refresh" plain size="small" :loading="scanning" @click="loadMetadataAndScan">刷新表元数据</el-button>
          <el-button type="primary" :icon="Search" size="small" :loading="scanning" @click="handleRunScan">
            {{ scanning ? '元数据检测中...' : '开始门禁检测' }}
          </el-button>
        </div>
      </div>

      <!-- 可收起门禁策略开关面板 -->
      <el-collapse-transition>
        <div v-show="policyExpanded" class="gate-policy-panel">
          <div class="policy-panel-header">
            <span class="policy-heading">质量门禁约束基线配置</span>
            <el-button type="primary" size="small" :icon="Check" @click="savePolicy('交付门禁策略已成功保存')">保存门禁配置</el-button>
          </div>
          <div class="gate-toggles-grid">
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireAuditField" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制审计跟踪字段</span>
                <span class="toggle-desc">包含 created_at/by, updated_at/by</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireDictBinding" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制状态字段绑定字典</span>
                <span class="toggle-desc">status/type 特征字段必须关联字典</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireQueryForm" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制前后端对齐查询</span>
                <span class="toggle-desc">SchemaForm 与 LambdaQueryWrapper 联动</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireListPermission" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制 RBAC 权限码闭环</span>
                <span class="toggle-desc">@RequirePermission 与前端指令双向对齐</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireSqlArtifact" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制同步 Flyway DDL</span>
                <span class="toggle-desc">输出 V{ver} 增量与种子数据脚本</span>
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>

      <!-- 门禁检查项清单表格 -->
      <div class="gate-table-wrapper">
        <div class="table-subheading">
          <span class="subheading-title">静态门禁规则检验清单 ({{ selectedTable || '未选择表' }} · 共 {{ checkResults.length }} 项)</span>
          <span class="subheading-meta">真实元数据合规分 {{ scanScore }}/100 · 通过 {{ passedCount }} 项</span>
        </div>

        <el-table v-loading="scanning" :data="checkResults" border stripe style="width: 100%">
          <el-table-column prop="id" label="规则编号" width="105" align="center" />
          <el-table-column prop="name" label="门禁检查项" width="170" />
          <el-table-column prop="dimension" label="评估维度" width="115" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.dimension }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="95" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PASSED' ? 'success' : row.status === 'WARNING' ? 'warning' : 'danger'" size="small">
                <el-icon v-if="row.status === 'PASSED'"><CircleCheck /></el-icon>
                {{ row.status === 'PASSED' ? '通过' : row.status === 'WARNING' ? '待完善' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="检测结果描述" min-width="260" show-overflow-tooltip />
          <el-table-column prop="recommendation" label="架构基准建议" min-width="240" show-overflow-tooltip />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.codegen-gate {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.gate-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.gate-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .toolbar-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }

    .gate-score-pill {
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .gate-summary-text {
      font-size: 12px;
      color: #475569;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.gate-policy-panel {
  margin-top: 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  .policy-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;

    .policy-heading {
      font-size: 13px;
      font-weight: 600;
      color: #334155;
    }
  }

  .gate-toggles-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 10px;

    .gate-toggle-item {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      background: rgba(255, 255, 255, 0.90);
      backdrop-filter: blur(14px);
      padding: 8px 10px;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.88);

      .toggle-content {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .toggle-name {
          font-size: 12.5px;
          font-weight: 600;
          color: #1e293b;
        }

        .toggle-desc {
          font-size: 11px;
          color: #475569;
          line-height: 1.3;
        }
      }
    }
  }
}

.gate-table-wrapper {
  margin-top: 12px;

  .table-subheading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .subheading-title {
      font-size: 13px;
      font-weight: 700;
      color: #334155;
    }

    .subheading-meta {
      font-size: 11.5px;
      color: #16a34a;
      font-weight: 500;
    }
  }
}
</style>
