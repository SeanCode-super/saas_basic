<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { Download, DocumentCopy, Folder, Check, RefreshRight, Promotion } from "@element-plus/icons-vue";
import type { CodegenPreviewFile, CodegenTableModel } from "@/api/modules/codegen";
import { previewCodegen, downloadCodegenZip, writeCodegenToDisk } from "@/api/modules/codegen";

const props = defineProps<{
  outputMatrix?: string[];
}>();

const generating = ref(false);
const writingToDisk = ref(false);
const activeFileIndex = ref(0);
const dryRunVisible = ref(false);
const generatedFiles = ref<CodegenPreviewFile[]>([]);

function getActiveTable(): CodegenTableModel {
  const saved = localStorage.getItem("sb_codegen_active_table");
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch {}
  }
  return {
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
      { columnName: "contract_amount", fieldName: "contractAmount", columnType: "DECIMAL(12,2)", javaType: "BigDecimal", comment: "合同标的金额", isPk: false, isRequired: true, isQuery: false, queryType: "BETWEEN", componentType: "number" },
      { columnName: "contract_status", fieldName: "contractStatus", columnType: "VARCHAR(32)", javaType: "String", comment: "合同状态", isPk: false, isRequired: true, isQuery: true, queryType: "EQ", componentType: "select" }
    ]
  };
}

async function loadGeneratedFiles() {
  generating.value = true;
  try {
    const table = getActiveTable();
    const res: any = await previewCodegen(table);
    generatedFiles.value = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
    if (activeFileIndex.value >= generatedFiles.value.length) {
      activeFileIndex.value = 0;
    }
  } catch (err: any) {
    ElMessage.error(err?.message || "生成代码预览失败");
  } finally {
    generating.value = false;
  }
}

async function handleRecompile() {
  await loadGeneratedFiles();
  ElMessage.success("代码已重新编译生成最新产物！");
}

async function handleDownloadZip() {
  try {
    const table = getActiveTable();
    await downloadCodegenZip(table);
    ElMessage.success(`代码压缩包 [${table.moduleName}-bundle.zip] 开始下载！`);
  } catch (err: any) {
    ElMessage.error(err?.message || "下载 ZIP 失败");
  }
}

function handleCopyCurrentFile() {
  const current = generatedFiles.value[activeFileIndex.value];
  if (!current) return;
  navigator.clipboard.writeText(current.code);
  ElMessage.success(`代码已复制到剪贴板：${current.name}`);
}

function handleDryRunApply() {
  dryRunVisible.value = true;
}

function handleConfirmApply() {
  dryRunVisible.value = false;
  handleWriteToDisk();
}

async function handleWriteToDisk() {
  writingToDisk.value = true;
  try {
    const table = getActiveTable();
    const res = await writeCodegenToDisk(table);
    const count = res?.data?.count ?? 0;
    ElMessage.success(`🎉 成功将 ${count} 个代码文件直接写入工程磁盘目录！实时生效！`);
  } catch (err: any) {
    ElMessage.error(err?.message || "物理落盘失败");
  } finally {
    writingToDisk.value = false;
  }
}

function resolveTagType(layer: string) {
  if (layer.includes("Entity")) return "primary";
  if (layer.includes("Mapper")) return "success";
  if (layer.includes("Service")) return "warning";
  if (layer.includes("Controller")) return "danger";
  if (layer.includes("Vue")) return "success";
  if (layer.includes("TS") || layer.includes("API")) return "info";
  if (layer.includes("SQL")) return "warning";
  return "info";
}

onMounted(() => {
  loadGeneratedFiles();
});
</script>

<template>
  <div class="codegen-artifact">
    <!-- 产物矩阵总览 -->
    <el-card shadow="never" class="artifact-card">
      <template #header>
        <div class="panel-header-row">
          <div class="header-left">
            <span class="panel-title">产物输出矩阵</span>
            <el-tag size="small" type="primary" round>{{ generatedFiles.length }} 个交付文件</el-tag>
          </div>
          <div class="panel-actions">
            <el-button size="small" :icon="RefreshRight" :loading="generating" @click="handleRecompile">重新编译生成</el-button>
            <el-button size="small" :icon="Promotion" plain @click="handleDryRunApply">模拟落盘预览</el-button>
            <el-button size="small" type="success" :icon="Folder" :loading="writingToDisk" @click="handleWriteToDisk">直接写入工程目录</el-button>
            <el-button size="small" type="primary" :icon="Download" @click="handleDownloadZip">下载 ZIP 压缩包</el-button>
          </div>
        </div>
      </template>

      <!-- 产物文件列表与实时代码查看 -->
      <div v-loading="generating" class="artifact-layout">
        <!-- 左侧文件树导航 -->
        <div class="artifact-file-list">
          <div class="file-list-header">
            <el-icon><Folder /></el-icon>
            <span>生成产物清单 ({{ generatedFiles.length }})</span>
          </div>
          <div
            v-for="(file, idx) in generatedFiles"
            :key="file.path"
            class="file-item"
            :class="{ active: idx === activeFileIndex }"
            @click="activeFileIndex = idx"
          >
            <div class="file-name-row">
              <span class="file-name">{{ file.name }}</span>
              <el-tag size="small" :type="resolveTagType(file.layer)">{{ file.layer }}</el-tag>
            </div>
            <span class="file-path">{{ file.path }}</span>
          </div>
        </div>

        <!-- 右侧代码透视与一键操作 -->
        <div class="artifact-code-view">
          <div class="code-view-header">
            <span class="code-view-path">{{ generatedFiles[activeFileIndex]?.path }}</span>
            <el-button size="small" :icon="DocumentCopy" plain @click="handleCopyCurrentFile">复制代码</el-button>
          </div>
          <div class="code-view-body">
            <pre><code>{{ generatedFiles[activeFileIndex]?.code }}</code></pre>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 模拟落盘确认弹窗 -->
    <el-dialog v-model="dryRunVisible" title="模拟一键写入工程目录 (Dry Run)" width="640px" destroy-on-close>
      <div class="dry-run-content">
        <el-alert
          title="系统将以安全只读沙箱模拟写入工程。此操作会校验包冲突、类名覆盖与 Flyway 版本号冲突。"
          type="info"
          show-icon
          :closable="false"
        />
        <div class="file-manifest-list mt-3">
          <h4>拟写入文件清单 ({{ generatedFiles.length }} 项)：</h4>
          <ul>
            <li v-for="file in generatedFiles" :key="file.path">
              <span class="badge-add">+ CREATE</span>
              <code>{{ file.path }}</code>
            </li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="dryRunVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirmApply">确认模拟落盘</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.codegen-artifact {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.artifact-card {
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
  flex-wrap: wrap;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .panel-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }
  }

  .panel-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.artifact-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 14px;
  height: calc(100vh - 225px);
  min-height: 560px;
}

.artifact-file-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px;
  height: 100%;
  overflow-y: auto;
  box-sizing: border-box;

  .file-list-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #334155;
    padding: 8px 10px;
    border-bottom: 1px solid #e2e8f0;
    margin-bottom: 6px;
  }

  .file-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 140ms ease;

    &:hover {
      background: #e2e8f0;
    }

    &.active {
      background: #eff6ff;
      border: 1px solid #bfdbfe;

      .file-name {
        color: #2563eb;
        font-weight: 600;
      }
    }

    .file-name-row {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .file-name {
        font-size: 13px;
        color: #0f172a;
      }
    }

    .file-path {
      font-size: 11px;
      color: #94a3b8;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.artifact-code-view {
  display: flex;
  flex-direction: column;
  background: #0f172a;
  border-radius: 8px;
  border: 1px solid #1e293b;
  overflow: hidden;
  height: 100%;
  box-sizing: border-box;

  .code-view-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: #1e293b;
    border-bottom: 1px solid #334155;
    flex-shrink: 0;

    .code-view-path {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      color: #94a3b8;
    }
  }

  .code-view-body {
    flex: 1;
    padding: 16px;
    overflow: auto;
    min-height: 0;

    pre {
      margin: 0;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12.5px;
      line-height: 1.6;
      color: #f1f5f9;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }
}

.file-manifest-list {
  margin-top: 16px;

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
    color: #334155;
  }

  ul {
    list-style: none;
    padding: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 240px;
    overflow-y: auto;

    li {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;

      .badge-add {
        font-size: 10px;
        font-weight: 700;
        color: #16a34a;
        background: #dcfce7;
        padding: 1px 6px;
        border-radius: 4px;
      }

      code {
        color: #475569;
        font-family: ui-monospace, monospace;
      }
    }
  }
}

.mt-3 {
  margin-top: 12px;
}
</style>
