<script setup lang="ts">
import { reactive, ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus, Delete, Download, Connection, Refresh, Check, Search, Promotion, Setting, ArrowUp, ArrowDown } from "@element-plus/icons-vue";
import type { CodegenColumnModel, DbTableItem, CodegenTableModel } from "@/api/modules/codegen";
import { fetchDbTables, fetchDbTableColumns } from "@/api/modules/codegen";

const props = defineProps<{
  templatePolicy?: any;
  styleGuide?: any;
  qualityGate?: any;
  savePolicy?: (message: string) => void;
}>();

const emit = defineEmits<{
  (e: "navigate-section", section: string): void;
}>();

const router = useRouter();
const configExpanded = ref(false);

const tablePresets: CodegenTableModel[] = [
  {
    tableName: "biz_contract",
    tableComment: "企业电子合同台账",
    className: "Contract",
    moduleName: "contract",
    businessName: "企业合同",
    functionName: "电子合同综合治理",
    basePackage: "com.saasbasics.platform.modules",
    columns: [
      { columnName: "id", fieldName: "id", columnType: "BIGINT", javaType: "Long", comment: "主键 ID", isPk: true, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "tenant_id", fieldName: "tenantId", columnType: "BIGINT", javaType: "Long", comment: "租户 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "contract_no", fieldName: "contractNo", columnType: "VARCHAR(64)", javaType: "String", comment: "合同编号", isPk: false, isRequired: true, isQuery: true, queryType: "LIKE", componentType: "input" },
      { columnName: "contract_title", fieldName: "contractTitle", columnType: "VARCHAR(128)", javaType: "String", comment: "合同标题", isPk: false, isRequired: true, isQuery: true, queryType: "LIKE", componentType: "input" },
      { columnName: "customer_id", fieldName: "customerId", columnType: "BIGINT", javaType: "Long", comment: "客户企业 ID", isPk: false, isRequired: true, isQuery: true, queryType: "EQ", componentType: "select" },
      { columnName: "contract_amount", fieldName: "contractAmount", columnType: "DECIMAL(12,2)", javaType: "BigDecimal", comment: "合同标的金额", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "number" },
      { columnName: "signing_date", fieldName: "signingDate", columnType: "DATE", javaType: "LocalDate", comment: "签署日期", isPk: false, isRequired: false, isQuery: true, queryType: "BETWEEN", componentType: "date" },
      { columnName: "contract_status", fieldName: "contractStatus", columnType: "VARCHAR(32)", javaType: "String", comment: "合同状态", isPk: false, isRequired: true, isQuery: true, queryType: "EQ", componentType: "select", dictCode: "contract_status_enum" },
      { columnName: "remark", fieldName: "remark", columnType: "VARCHAR(500)", javaType: "String", comment: "备注说明", isPk: false, isRequired: false, isQuery: false, queryType: "EQ", componentType: "textarea" },
      { columnName: "created_at", fieldName: "createdAt", columnType: "DATETIME", javaType: "LocalDateTime", comment: "创建时间", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "date" },
      { columnName: "created_by", fieldName: "createdBy", columnType: "BIGINT", javaType: "Long", comment: "创建人 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "updated_at", fieldName: "updatedAt", columnType: "DATETIME", javaType: "LocalDateTime", comment: "更新时间", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "date" },
      { columnName: "updated_by", fieldName: "updatedBy", columnType: "BIGINT", javaType: "Long", comment: "更新人 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "deleted", fieldName: "deleted", columnType: "TINYINT(1)", javaType: "Boolean", comment: "逻辑删除标记", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "switch" },
      { columnName: "version", fieldName: "version", columnType: "INT", javaType: "Integer", comment: "乐观锁版本", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" }
    ]
  },
  {
    tableName: "biz_purchase_order",
    tableComment: "供应链采购订单",
    className: "PurchaseOrder",
    moduleName: "purchase",
    businessName: "采购订单",
    functionName: "采购履约中台",
    basePackage: "com.saasbasics.platform.modules",
    columns: [
      { columnName: "id", fieldName: "id", columnType: "BIGINT", javaType: "Long", comment: "主键 ID", isPk: true, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "tenant_id", fieldName: "tenantId", columnType: "BIGINT", javaType: "Long", comment: "租户 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "order_no", fieldName: "orderNo", columnType: "VARCHAR(64)", javaType: "String", comment: "采购单号", isPk: false, isRequired: true, isQuery: true, queryType: "LIKE", componentType: "input" },
      { columnName: "supplier_code", fieldName: "supplierCode", columnType: "VARCHAR(64)", javaType: "String", comment: "供应商编码", isPk: false, isRequired: true, isQuery: true, queryType: "EQ", componentType: "input" },
      { columnName: "total_price", fieldName: "totalPrice", columnType: "DECIMAL(12,2)", javaType: "BigDecimal", comment: "总计金额", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "number" },
      { columnName: "order_status", fieldName: "orderStatus", columnType: "VARCHAR(32)", javaType: "String", comment: "订单状态", isPk: false, isRequired: true, isQuery: true, queryType: "EQ", componentType: "select", dictCode: "purchase_order_status" },
      { columnName: "created_at", fieldName: "createdAt", columnType: "DATETIME", javaType: "LocalDateTime", comment: "创建时间", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "date" },
      { columnName: "created_by", fieldName: "createdBy", columnType: "BIGINT", javaType: "Long", comment: "创建人 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "updated_at", fieldName: "updatedAt", columnType: "DATETIME", javaType: "LocalDateTime", comment: "更新时间", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "date" },
      { columnName: "updated_by", fieldName: "updatedBy", columnType: "BIGINT", javaType: "Long", comment: "更新人 ID", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" },
      { columnName: "deleted", fieldName: "deleted", columnType: "TINYINT(1)", javaType: "Boolean", comment: "逻辑删除标记", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "switch" },
      { columnName: "version", fieldName: "version", columnType: "INT", javaType: "Integer", comment: "乐观锁版本", isPk: false, isRequired: true, isQuery: false, queryType: "EQ", componentType: "number" }
    ]
  }
];

const selectedPresetIndex = ref(0);
const activeTable = reactive<CodegenTableModel>(loadSavedTable());

function loadSavedTable(): CodegenTableModel {
  const saved = localStorage.getItem("sb_codegen_active_table");
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch {}
  }
  return JSON.parse(JSON.stringify(tablePresets[0]));
}

function persistCurrentTable() {
  localStorage.setItem("sb_codegen_active_table", JSON.stringify(activeTable));
}

function switchPreset(idx: number) {
  selectedPresetIndex.value = idx;
  Object.assign(activeTable, JSON.parse(JSON.stringify(tablePresets[idx])));
  persistCurrentTable();
  ElMessage.success(`已切换至预设元数据模型：${activeTable.tableComment}`);
}

// 物理表逆向导入弹窗
const dbImportDialogVisible = ref(false);
const dbTablesLoading = ref(false);
const dbTables = ref<DbTableItem[]>([]);
const tableSearch = ref("");

const filteredDbTables = computed(() => {
  if (!tableSearch.value.trim()) return dbTables.value;
  const kw = tableSearch.value.trim().toLowerCase();
  return dbTables.value.filter(
    (t) => t.tableName.toLowerCase().includes(kw) || (t.tableComment && t.tableComment.toLowerCase().includes(kw))
  );
});

async function openDbImportDialog() {
  dbImportDialogVisible.value = true;
  dbTablesLoading.value = true;
  try {
    const res: any = await fetchDbTables();
    dbTables.value = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
  } catch (err: any) {
    ElMessage.error(err?.message || "读取数据库表失败");
  } finally {
    dbTablesLoading.value = false;
  }
}

async function handleImportTable(row: DbTableItem) {
  try {
    const res: any = await fetchDbTableColumns(row.tableName);
    const columns: CodegenColumnModel[] = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);

    activeTable.tableName = row.tableName;
    activeTable.tableComment = row.tableComment || row.tableName;
    activeTable.businessName = row.tableComment || cleanTableName(row.tableName);
    activeTable.className = toPascalCase(cleanTableName(row.tableName));
    activeTable.moduleName = toCamelCase(cleanTableName(row.tableName));
    activeTable.functionName = (row.tableComment || cleanTableName(row.tableName)) + "管理";
    activeTable.basePackage = "com.saasbasics.platform.modules";
    activeTable.columns = columns;

    persistCurrentTable();
    dbImportDialogVisible.value = false;
    ElMessage.success(`成功从 MySQL 逆向解析表 [${row.tableName}]，已自动完成 ${columns.length} 个字段建模！`);
  } catch (err: any) {
    ElMessage.error(err?.message || "逆向解析数据列失败");
  }
}

function cleanTableName(name: string): string {
  if (name.startsWith("biz_")) return name.substring(4);
  if (name.startsWith("sys_")) return name.substring(4);
  if (name.startsWith("gen_")) return name.substring(4);
  return name;
}

function toPascalCase(str: string): string {
  return str
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join("");
}

function toCamelCase(str: string): string {
  const words = str.split("_");
  return words
    .map((word, index) =>
      index === 0 ? word.toLowerCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    )
    .join("");
}

function handleAddColumn() {
  const newCol: CodegenColumnModel = {
    columnName: `custom_field_${activeTable.columns.length + 1}`,
    fieldName: `customField${activeTable.columns.length + 1}`,
    columnType: "VARCHAR(128)",
    javaType: "String",
    comment: "自定义扩展属性",
    isPk: false,
    isRequired: false,
    isQuery: true,
    queryType: "LIKE",
    componentType: "input"
  };
  activeTable.columns.push(newCol);
  persistCurrentTable();
  ElMessage.success("已追加新字段定义");
}

function handleDeleteColumn(index: number) {
  const col = activeTable.columns[index];
  if (col.isPk) {
    ElMessage.warning("主键字段不允许删除");
    return;
  }
  activeTable.columns.splice(index, 1);
  persistCurrentTable();
  ElMessage.info("字段已移除");
}

function handleExportSchemaJson() {
  const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(activeTable, null, 2));
  const downloadAnchor = document.createElement("a");
  downloadAnchor.setAttribute("href", dataStr);
  downloadAnchor.setAttribute("download", `${activeTable.tableName}_metadata.json`);
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  downloadAnchor.remove();
  ElMessage.success("元数据建模契约已导出");
}

function handleSaveMetadata() {
  persistCurrentTable();
  ElMessage.success(`数据表 [${activeTable.tableName}] 元数据模型已持久化保存`);
}

function handleGoToArtifacts() {
  persistCurrentTable();
  emit("navigate-section", "artifact");
  router.push({ path: "/codegen/artifact" });
}
</script>

<template>
  <div class="codegen-metadata">
    <!-- 模型基本信息控制卡片 (极简高密度顶栏) -->
    <el-card shadow="never" class="metadata-top-card">
      <div class="metadata-toolbar-row">
        <div class="toolbar-left">
          <span class="toolbar-title">元数据建模</span>
          <el-select
            :model-value="selectedPresetIndex"
            class="preset-select"
            placeholder="切换模型模版"
            @change="switchPreset($event as number)"
          >
            <el-option
              v-for="(p, i) in tablePresets"
              :key="p.tableName"
              :label="`${p.tableComment} (${p.tableName})`"
              :value="i"
            />
          </el-select>
        </div>

        <div class="toolbar-actions">
          <el-button type="primary" :icon="Connection" @click="openDbImportDialog">从 MySQL 导入</el-button>
          <el-button :icon="Download" @click="handleExportSchemaJson">导出契约</el-button>
          <el-button :icon="Check" @click="handleSaveMetadata">保存模型</el-button>
          <el-button type="success" :icon="Promotion" @click="handleGoToArtifacts">生成代码产物</el-button>
        </div>
      </div>

      <!-- 紧凑折叠摘要栏 -->
      <div class="compact-config-bar">
        <div class="config-summary" @click="configExpanded = !configExpanded">
          <div class="summary-chips">
            <span class="chip-item"><span class="chip-k">表名:</span> <code class="chip-v">{{ activeTable.tableName }}</code></span>
            <span class="chip-divider">·</span>
            <span class="chip-item"><span class="chip-k">实体:</span> <code class="chip-v">{{ activeTable.className }}</code></span>
            <span class="chip-divider">·</span>
            <span class="chip-item"><span class="chip-k">模块:</span> <code class="chip-v">{{ activeTable.moduleName }}</code></span>
            <span class="chip-divider">·</span>
            <span class="chip-item"><span class="chip-k">模式:</span> <el-tag size="small" :type="activeTable.templateType === 'TREE' ? 'warning' : (activeTable.templateType === 'MASTER_SLAVE' ? 'success' : 'info')">{{ activeTable.templateType === 'TREE' ? '树形表' : (activeTable.templateType === 'MASTER_SLAVE' ? '主子表' : '单表 CRUD') }}</el-tag></span>
            <span class="chip-divider">·</span>
            <span class="chip-item"><span class="chip-k">包:</span> <span class="chip-v-pkg" :title="activeTable.basePackage">{{ activeTable.basePackage }}</span></span>
          </div>

          <div class="summary-toggle-btn">
            <span>{{ configExpanded ? '收起配置' : '展开基础属性配置' }}</span>
            <el-icon><component :is="configExpanded ? ArrowUp : ArrowDown" /></el-icon>
          </div>
        </div>

        <el-collapse-transition>
          <div v-show="configExpanded" class="config-expanded-body">
            <el-form label-position="left" label-width="95px" size="small">
              <el-row :gutter="14">
                <el-col :span="8">
                  <el-form-item label="物理数据表">
                    <el-input v-model="activeTable.tableName" placeholder="如 biz_contract" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="业务表描述">
                    <el-input v-model="activeTable.tableComment" placeholder="如 企业电子合同台账" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="Java 实体类">
                    <el-input v-model="activeTable.className" placeholder="如 Contract" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="14" class="mt-2">
                <el-col :span="8">
                  <el-form-item label="所属业务模块">
                    <el-input v-model="activeTable.moduleName" placeholder="如 contract" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="Java 基础包">
                    <el-input v-model="activeTable.basePackage" placeholder="如 com.saasbasics.platform.modules" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="生成结构模式">
                    <el-select v-model="activeTable.templateType" style="width: 100%" placeholder="请选择结构模式" @change="persistCurrentTable">
                      <el-option label="单表标准 CRUD 模式" value="SINGLE" />
                      <el-option label="主子表 (1:N 级联) 模式" value="MASTER_SLAVE" />
                      <el-option label="树形表 (Tree Table) 模式" value="TREE" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 主子表专有配置 -->
              <el-row v-if="activeTable.templateType === 'MASTER_SLAVE'" :gutter="14" class="mt-2 subtable-config-row">
                <el-col :span="8">
                  <el-form-item label="子表物理名称">
                    <el-input v-model="activeTable.subTableName" placeholder="如 biz_order_item" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="子表实体类名">
                    <el-input v-model="activeTable.subClassName" placeholder="如 OrderItem" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="子表关联外键">
                    <el-input v-model="activeTable.subTableFk" placeholder="如 order_id" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 树表专有配置 -->
              <el-row v-if="activeTable.templateType === 'TREE'" :gutter="14" class="mt-2 subtable-config-row">
                <el-col :span="8">
                  <el-form-item label="树主键字段">
                    <el-input v-model="activeTable.treeId" placeholder="默认 id" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="树父级 ID">
                    <el-input v-model="activeTable.treeParentId" placeholder="默认 parent_id" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="树显示名称">
                    <el-input v-model="activeTable.treeName" placeholder="如 name 或 title" @change="persistCurrentTable" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </el-collapse-transition>
      </div>
    </el-card>

    <!-- 字段矩阵与可视化配置表 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="panel-header-row">
          <div class="header-left">
            <span class="panel-title">字段元数据治理清单</span>
            <el-tag size="small" type="primary" round>{{ activeTable.columns.length }} 列</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" plain size="small" @click="handleAddColumn">新增扩展字段</el-button>
        </div>
      </template>

      <el-table :data="activeTable.columns" stripe border size="default" style="width: 100%;">
        <el-table-column prop="columnName" label="物理列名" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.columnName" size="small" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="fieldName" label="Java/TS 属性名" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.fieldName" size="small" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="comment" label="字段中文描述" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.comment" size="small" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="columnType" label="物理类型" width="130">
          <template #default="{ row }">
            <el-input v-model="row.columnType" size="small" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="javaType" label="Java 类型" width="130">
          <template #default="{ row }">
            <el-select v-model="row.javaType" size="small" @change="persistCurrentTable">
              <el-option label="Long" value="Long" />
              <el-option label="String" value="String" />
              <el-option label="Integer" value="Integer" />
              <el-option label="BigDecimal" value="BigDecimal" />
              <el-option label="Boolean" value="Boolean" />
              <el-option label="LocalDate" value="LocalDate" />
              <el-option label="LocalDateTime" value="LocalDateTime" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column prop="componentType" label="UI 呈现组件" width="120">
          <template #default="{ row }">
            <el-select v-model="row.componentType" size="small" @change="persistCurrentTable">
              <el-option label="单行输入框" value="input" />
              <el-option label="数字步进" value="number" />
              <el-option label="下拉单选" value="select" />
              <el-option label="日期时间" value="date" />
              <el-option label="多行文本" value="textarea" />
              <el-option label="开关Switch" value="switch" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column prop="isPk" label="主键" width="65" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isPk" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="isRequired" label="必填" width="65" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isRequired" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="isQuery" label="查询" width="65" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.isQuery" @change="persistCurrentTable" />
          </template>
        </el-table-column>

        <el-table-column prop="queryType" label="匹配方式" width="100">
          <template #default="{ row }">
            <el-select v-model="row.queryType" size="small" :disabled="!row.isQuery" @change="persistCurrentTable">
              <el-option label="精确(=)" value="EQ" />
              <el-option label="模糊(LIKE)" value="LIKE" />
              <el-option label="范围(BETWEEN)" value="BETWEEN" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index, row }">
            <el-button
              type="danger"
              link
              size="small"
              :icon="Delete"
              :disabled="row.isPk"
              @click="handleDeleteColumn($index)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 数据库表逆向导入弹窗 -->
    <el-dialog v-model="dbImportDialogVisible" title="从当前 MySQL 数据库逆向导入物理表" width="760px" destroy-on-close>
      <div class="db-import-header">
        <el-input
          v-model="tableSearch"
          placeholder="搜索数据库表名或表描述..."
          :prefix-icon="Search"
          clearable
          style="width: 320px;"
        />
        <span class="db-tables-count">共发现 {{ filteredDbTables.length }} 张物理表</span>
      </div>

      <el-table
        v-loading="dbTablesLoading"
        :data="filteredDbTables"
        stripe
        border
        height="400"
        style="width: 100%; margin-top: 12px;"
      >
        <el-table-column prop="tableName" label="数据表名" min-width="180">
          <template #default="{ row }">
            <code class="table-name-code">{{ row.tableName }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" label="表描述 / 业务名称" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleImportTable(row)">
              一键导入建模
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dbImportDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.codegen-metadata {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metadata-top-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.metadata-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .toolbar-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }

    .preset-select {
      width: 250px;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.compact-config-bar {
  margin-top: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;

  .config-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 12px;
    cursor: pointer;
    user-select: none;
    transition: background 150ms ease;

    &:hover {
      background: #f1f5f9;
    }

    .summary-chips {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      font-size: 12.5px;

      .chip-divider {
        color: #cbd5e1;
      }

      .chip-item {
        display: inline-flex;
        align-items: center;
        gap: 4px;

        .chip-k {
          color: #64748b;
        }

        .chip-v {
          color: #0f172a;
          font-weight: 600;
        }

        code.chip-v {
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          color: #2563eb;
          background: #eff6ff;
          padding: 1px 5px;
          border-radius: 4px;
        }

        .chip-v-pkg {
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          color: #475569;
          max-width: 200px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .summary-toggle-btn {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #2563eb;
      font-weight: 500;
      white-space: nowrap;
    }
  }

  .config-expanded-body {
    padding: 12px 14px;
    border-top: 1px solid #e2e8f0;
    background: #ffffff;

    :deep(.el-form-item) {
      margin-bottom: 0;
    }

    .subtable-config-row {
      background: #f8fafc;
      padding: 8px 10px;
      border-radius: 6px;
      border: 1px dashed #cbd5e1;
    }
  }
}

.table-card {
  :deep(.el-card__header) {
    padding: 10px 16px;
  }

  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .panel-title {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
    }
  }
}

.db-import-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .db-tables-count {
    font-size: 13px;
    color: #64748b;
  }
}

.table-name-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.mt-2 {
  margin-top: 8px;
}
</style>
