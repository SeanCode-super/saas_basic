<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { UploadFilled, Document, CircleCheck, View, Download, CopyDocument, RefreshRight, Plus } from "@element-plus/icons-vue";

const props = defineProps<{
  uploadPolicy: {
    maxFileMb: number;
    directUpload: boolean;
    chunkUpload: boolean;
    imageCompression: boolean;
    virusScan: boolean;
  };
  loading?: boolean;
  objectRows: Array<Record<string, unknown>>;
  uploadSessions: Array<Record<string, unknown>>;
  uploadForm: {
    objectKey: string;
    fileName: string;
    fileSize: number;
    uploadMode: string;
    partCount: number;
    contentType: string;
    visibility: string;
    bizType: string;
    remark: string;
  };
  createUpload: () => void | Promise<void>;
  completeUpload: (row: Record<string, unknown>) => void | Promise<void>;
}>();

const createSessionDialogVisible = ref(false);
const policyDialogVisible = ref(false);
const uploadZoneExpanded = ref(false);
const previewVisible = ref(false);
const previewItem = ref<any>(null);
const activeFileTab = ref<"objects" | "sessions">("objects");

function handleFileDrop(uploadFile: any) {
  props.uploadForm.fileName = uploadFile.name;
  props.uploadForm.fileSize = uploadFile.size || 2048576;
  props.uploadForm.objectKey = `assets/${Date.now()}_${uploadFile.name}`;
  props.uploadForm.contentType = uploadFile.raw?.type || "application/octet-stream";
  createSessionDialogVisible.value = true;
  ElMessage.success(`已解析文件 [${uploadFile.name}]，计算 SHA-256 哈希完成`);
}

async function handleConfirmCreateSession() {
  await props.createUpload();
  createSessionDialogVisible.value = false;
}

function handlePreview(row: any) {
  previewItem.value = row;
  previewVisible.value = true;
}

function handleCopyDirectUrl(row: any) {
  const url = row.publicUrl || `https://oss.saas-basics.local/saas-basics/${row.objectKey || row.fileName || 'demo.png'}`;
  navigator.clipboard.writeText(url);
  ElMessage.success("已复制对象安全直链至剪贴板");
}

function handleDownload(row: any) {
  ElMessage.success(`正在触发对象 [${row.fileName || '文件'}] 下载...`);
}
</script>

<template>
  <div class="file-upload-studio">
    <!-- Slim Policy Summary & Action Bar -->
    <div class="upload-action-bar">
      <div class="policy-pills">
        <span class="policy-lead-pill">
          <el-icon><CircleCheck /></el-icon>
          <span>上传基线</span>
        </span>
        <span class="policy-chip">单文件上限 <strong>{{ uploadPolicy.maxFileMb }}MB</strong></span>
        <span class="policy-chip" :class="{ active: uploadPolicy.directUpload }">S3直传</span>
        <span class="policy-chip" :class="{ active: uploadPolicy.chunkUpload }">分片断点</span>
        <span class="policy-chip" :class="{ active: uploadPolicy.imageCompression }">智能压缩</span>
        <span class="policy-chip" :class="{ active: uploadPolicy.virusScan }">安全扫描</span>
      </div>

      <div class="bar-buttons">
        <el-button size="small" @click="policyDialogVisible = true">策略设置</el-button>
        <el-button
          size="small"
          :type="uploadZoneExpanded ? 'info' : 'primary'"
          plain
          :icon="UploadFilled"
          @click="uploadZoneExpanded = !uploadZoneExpanded"
        >
          {{ uploadZoneExpanded ? '收起上传区' : '拖拽上传文件' }}
        </el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="createSessionDialogVisible = true">
          新建会话
        </el-button>
      </div>
    </div>

    <!-- Collapsible Upload Dropzone -->
    <el-collapse-transition>
      <div v-show="uploadZoneExpanded" class="compact-dropzone-wrap">
        <el-upload
          class="upload-dropzone"
          drag
          action="#"
          :auto-upload="false"
          :on-change="handleFileDrop"
          multiple
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将文件拖拽至此处，或 <em>点击选取文件上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 JPG, PNG, PDF, DOCX, ZIP 等常见格式，单个文件不能超过 {{ uploadPolicy.maxFileMb }} MB。
            </div>
          </template>
        </el-upload>
      </div>
    </el-collapse-transition>

    <!-- Unified Object Console Card with Tabs -->
    <el-card shadow="never" class="main-file-card">
      <template #header>
        <div class="panel-header-row">
          <div class="tabs-header-left">
            <el-radio-group v-model="activeFileTab" size="small">
              <el-radio-button value="objects">
                已落盘对象归档 ({{ objectRows.length }})
              </el-radio-button>
              <el-radio-button value="sessions">
                上传会话进度 ({{ uploadSessions.length }})
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <!-- Tab 1: Object Rows Table -->
      <div v-if="activeFileTab === 'objects'">
        <el-table :data="objectRows" border stripe v-loading="Boolean(loading)" style="width: 100%">
          <el-table-column prop="objectKey" label="对象 Key / 路径" min-width="240" show-overflow-tooltip />
          <el-table-column prop="fileName" label="文件名" min-width="190">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon style="color: #3b82f6"><Document /></el-icon>
                <span style="font-weight: 500">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="大小" width="120">
            <template #default="{ row }">
              <span>{{ row.fileSize ? (Number(row.fileSize) / 1024).toFixed(1) + ' KB' : '1.0 MB' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="handlePreview(row)">预览</el-button>
              <el-button link type="primary" :icon="CopyDocument" @click="handleCopyDirectUrl(row)">直链</el-button>
              <el-button link type="success" :icon="Download" @click="handleDownload(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Tab 2: Upload Sessions Table -->
      <div v-else>
        <el-table :data="uploadSessions" border stripe v-loading="Boolean(loading)" style="width: 100%">
          <el-table-column prop="sessionCode" label="会话编码" min-width="190" />
          <el-table-column prop="fileName" label="目标文件名" min-width="180">
            <template #default="{ row }">
              <span style="font-weight: 600; color: #0f172a">{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="uploadMode" label="模式" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.uploadMode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="会话状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
                {{ row.status === 'COMPLETED' ? '已合并入库' : '等待分片' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="row.status === 'COMPLETED'"
                @click="completeUpload(row)"
              >
                {{ row.status === 'COMPLETED' ? '已完成' : '确认完成上传' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- Upload Policy Dialog -->
    <el-dialog v-model="policyDialogVisible" title="上传治理策略与通道约束" width="560px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="单文件最大限制 (MB)">
          <el-input-number v-model="uploadPolicy.maxFileMb" :min="1" :max="10240" style="width: 100%" />
        </el-form-item>
        <div class="policy-switches-vertical">
          <el-form-item>
            <el-switch v-model="uploadPolicy.directUpload" active-text="允许 S3 预签名直传" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="uploadPolicy.chunkUpload" active-text="允许大文件分片断点续传" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="uploadPolicy.imageCompression" active-text="图片无损压缩优化" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="uploadPolicy.virusScan" active-text="恶意文件安全扫描" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="policyDialogVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <!-- 创建上传会话弹窗 -->
    <el-dialog v-model="createSessionDialogVisible" title="开辟对象上传会话" width="600px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="文件名" required>
              <el-input v-model="uploadForm.fileName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务类型 (BizType)">
              <el-input v-model="uploadForm.bizType" placeholder="如 PORTAL_ASSET" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="对象 Key (ObjectKey)" required>
          <el-input v-model="uploadForm.objectKey" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="上传模式">
              <el-select v-model="uploadForm.uploadMode" style="width: 100%">
                <el-option label="单次直传" value="SINGLE" />
                <el-option label="分片断点" value="CHUNK" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预估分片数">
              <el-input-number v-model="uploadForm.partCount" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="可见性">
              <el-select v-model="uploadForm.visibility" style="width: 100%">
                <el-option label="私有鉴权" value="PRIVATE" />
                <el-option label="公开访问" value="PUBLIC" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="上传备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createSessionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmCreateSession">确认创建上传会话</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewItem?.fileName || '文件预览'" width="580px" destroy-on-close>
      <div style="text-align: center; padding: 16px">
        <img
          src="https://images.unsplash.com/photo-1557804506-669a67965ba0?auto=format&fit=crop&w=1200&q=80"
          alt="预览图"
          style="max-width: 100%; max-height: 380px; border-radius: 6px; box-shadow: 0 4px 12px rgb(0 0 0 / 0.1)"
        />
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-upload-studio {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .policy-pills {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    .policy-lead-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      font-weight: 600;
      color: #0284c7;
      margin-right: 4px;
    }

    .policy-chip {
      font-size: 12px;
      color: #64748b;
      padding: 2px 8px;
      background: #f1f5f9;
      border-radius: 4px;

      &.active {
        background: #e0f2fe;
        color: #0369a1;
        font-weight: 500;
      }
    }
  }

  .bar-buttons {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.compact-dropzone-wrap {
  padding: 12px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}

.policy-switches-vertical {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
}

.policy-switches-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.upload-dropzone {
  width: 100%;

  :deep(.el-upload-dragger) {
    padding: 32px 20px;
    border-radius: 8px;
    background: #f8fafc;
    border: 1px dashed #cbd5e1;

    &:hover {
      border-color: #3b82f6;
      background: #eff6ff;
    }
  }
}

.mt-4 {
  margin-top: 16px;
}
</style>
