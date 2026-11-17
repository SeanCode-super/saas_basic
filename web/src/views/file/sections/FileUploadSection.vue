<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  UploadFilled,
  Document,
  CircleCheck,
  View,
  Download,
  CopyDocument,
  Plus,
  Setting
} from "@element-plus/icons-vue";
import { resolveAuthorizedFileUrl } from "@/api/modules/file";
import type { FileObjectRow, FileUploadRouteRow, FileUploadSessionRow, StorageAvailability } from "@/api/modules/file";
import { useAuthStore } from "@/stores/modules/auth";

const props = defineProps<{
  uploadPolicy: {
    maxFileMb: number;
    directUpload: boolean;
    chunkUpload: boolean;
    imageCompression: boolean;
    virusScan: boolean;
  };
  storageStatus?: StorageAvailability | null;
  uploadRoutes?: FileUploadRouteRow[];
  loading?: boolean;
  objectRows: FileObjectRow[];
  uploadSessions: FileUploadSessionRow[];
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
  createUpload: () => boolean | Promise<boolean>;
  completeUpload: (row: FileUploadSessionRow) => void | Promise<void>;
  directUpload: (file: File, bizType: string) => Promise<boolean>;
  goToStorageConfig: () => void;
}>();

const authStore = useAuthStore();
const createSessionDialogVisible = ref(false);
const uploadZoneExpanded = ref(true);
const uploadingDirect = ref(false);
const previewVisible = ref(false);
const previewItem = ref<FileObjectRow | null>(null);
const activeFileTab = ref<"objects" | "sessions">("objects");
const selectedBizType = ref("COMMON");

const bizTypeOptions = computed(() => {
  const presets = [
    { value: "COMMON", label: "COMMON · 默认通用目录" },
    { value: "USER_AVATAR", label: "USER_AVATAR · 用户头像" },
    { value: "AI_KNOWLEDGE", label: "AI_KNOWLEDGE · AI知识库" },
    { value: "PORTAL_ASSET", label: "PORTAL_ASSET · 门户素材" },
    { value: "TENANT_CONTRACT", label: "TENANT_CONTRACT · 合同附件" }
  ];
  const seen = new Set(presets.map((p) => p.value));
  for (const r of props.uploadRoutes ?? []) {
    if (!seen.has(r.bizType)) {
      presets.push({ value: r.bizType, label: `${r.bizType} · ${r.routeName}` });
      seen.add(r.bizType);
    }
  }
  return presets;
});

const resolvedTargetPreview = computed(() => {
  if (!props.storageStatus?.configured) {
    return null;
  }
  const route = (props.uploadRoutes ?? []).find(
    (r) => r.status === "ENABLED" && r.bizType.toUpperCase() === selectedBizType.value.toUpperCase()
  );
  const folder = route?.targetDir || props.storageStatus.defaultFolder || "uploads";
  const mode = route?.uploadMode || props.storageStatus.uploadMode || "DIRECT_SERVER";
  return {
    folder,
    mode,
    matchedRule: route ? `${route.routeName} (${route.bizType})` : "未匹配专项规则，使用默认目录"
  };
});

function isStorageReady(): boolean {
  if (props.storageStatus && !props.storageStatus.configured) {
    ElMessage.warning(props.storageStatus.message || "未配置可用文件存储源，请先前往「存储策略」配置并启用");
    return false;
  }
  return true;
}

async function handleFileDrop(uploadFile: { raw?: File; name: string }) {
  if (!isStorageReady()) {
    return;
  }
  if (!uploadFile.raw) {
    return;
  }
  uploadingDirect.value = true;
  try {
    await props.directUpload(uploadFile.raw, selectedBizType.value);
  } finally {
    uploadingDirect.value = false;
  }
}

function openCreateSessionDialog() {
  if (!isStorageReady()) {
    return;
  }
  props.uploadForm.bizType = selectedBizType.value;
  props.uploadForm.uploadMode = resolvedTargetPreview.value?.mode || "DIRECT_SERVER";
  createSessionDialogVisible.value = true;
}

async function handleConfirmCreateSession() {
  const ok = await props.createUpload();
  if (ok) {
    createSessionDialogVisible.value = false;
  }
}

function resolveObjectUrl(row: FileObjectRow): string {
  const base = row.publicUrl || `/api/file/raw-download/${row.objectKey}`;
  return resolveAuthorizedFileUrl(base, authStore.token);
}

function isImageObject(row: FileObjectRow | null): boolean {
  if (!row) return false;
  const ct = (row.contentType || "").toLowerCase();
  const ext = (row.fileExt || "").toLowerCase();
  return ct.startsWith("image/") || ["png", "jpg", "jpeg", "webp", "gif", "svg"].includes(ext);
}

function handlePreview(row: FileObjectRow) {
  previewItem.value = row;
  previewVisible.value = true;
}

function handleCopyDirectUrl(row: FileObjectRow) {
  const url = `${window.location.origin}${row.publicUrl || `/api/file/raw-download/${row.objectKey}`}`;
  navigator.clipboard.writeText(url);
  ElMessage.success("已复制对象路径至剪贴板");
}

function handleDownload(row: FileObjectRow) {
  const url = resolveObjectUrl(row);
  window.open(url, "_blank");
}
</script>

<template>
  <div class="file-upload-studio">
    <!-- 未配置存储源强制警示横幅 -->
    <el-alert
      v-if="storageStatus && !storageStatus.configured"
      type="error"
      :closable="false"
      show-icon
      class="missing-source-banner"
    >
      <template #title>
        <span style="font-weight: 700">当前未配置可用的文件存储源，无法上传文件</span>
      </template>
      <div class="banner-row">
        <span>{{ storageStatus.message }}</span>
        <el-button type="danger" size="small" :icon="Setting" @click="goToStorageConfig">前往配置存储源与目录</el-button>
      </div>
    </el-alert>

    <!-- 路径预览与操作条 -->
    <div class="upload-action-bar">
      <div class="policy-pills">
        <span class="policy-lead-pill">
          <el-icon><CircleCheck /></el-icon>
          <span>上传通道与目录路由</span>
        </span>
        <el-select v-model="selectedBizType" size="small" style="width: 220px" placeholder="选择业务类型">
          <el-option v-for="opt in bizTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <template v-if="resolvedTargetPreview">
          <span class="policy-chip active">目标文件夹: <strong>/{{ resolvedTargetPreview.folder }}</strong></span>
          <span class="policy-chip">方式: <strong>{{ resolvedTargetPreview.mode }}</strong></span>
          <span class="policy-chip">{{ resolvedTargetPreview.matchedRule }}</span>
        </template>
        <span v-else class="policy-chip warn">待配置存储源</span>
      </div>

      <div class="bar-buttons">
        <el-button
          size="small"
          :type="uploadZoneExpanded ? 'info' : 'primary'"
          plain
          :icon="UploadFilled"
          @click="uploadZoneExpanded = !uploadZoneExpanded"
        >
          {{ uploadZoneExpanded ? '收起上传区' : '展开直传区' }}
        </el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="openCreateSessionDialog">
          新建分片/元数据会话
        </el-button>
      </div>
    </div>

    <!-- 真实直传拖拽区 -->
    <el-collapse-transition>
      <div v-show="uploadZoneExpanded" class="compact-dropzone-wrap" v-loading="uploadingDirect">
        <el-upload
          class="upload-dropzone"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileDrop"
          :disabled="Boolean(storageStatus && !storageStatus.configured)"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            <template v-if="storageStatus && !storageStatus.configured">
              请先前往「存储策略」配置并启用存储源，再进行文件上传
            </template>
            <template v-else>
              将文件拖拽至此处，或 <em>点击立即上传</em> 至目录 <strong>/{{ resolvedTargetPreview?.folder }}</strong>
            </template>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              单文件上限 {{ uploadPolicy.maxFileMb }} MB · 未单独设置目录规则的业务类型将统一存入存储源默认文件夹。
            </div>
          </template>
        </el-upload>
      </div>
    </el-collapse-transition>

    <!-- 已落盘对象与会话台账 -->
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

      <div v-if="activeFileTab === 'objects'">
        <el-table :data="objectRows" border stripe v-loading="Boolean(loading)" style="width: 100%">
          <el-table-column prop="fileName" label="文件名" min-width="180">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon style="color: #2563eb"><Document /></el-icon>
                <span style="font-weight: 500; color: #0f172a">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="bizType" label="业务类型" width="140">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.bizType || 'COMMON' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetDir" label="落盘目录" min-width="140">
            <template #default="{ row }">
              <el-tag v-if="row.targetDir" size="small" type="success" effect="light">/{{ row.targetDir }}</el-tag>
              <span v-else style="color: #64748b">--</span>
            </template>
          </el-table-column>
          <el-table-column prop="uploadMode" label="上传方式" width="130">
            <template #default="{ row }">
              <span>{{ row.uploadMode || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="objectKey" label="对象 Key / 物理路径" min-width="230" show-overflow-tooltip />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">
              <span>{{ row.fileSize ? (Number(row.fileSize) / 1024).toFixed(1) + ' KB' : '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="handlePreview(row)">预览</el-button>
              <el-button link type="primary" :icon="CopyDocument" @click="handleCopyDirectUrl(row)">直链</el-button>
              <el-button link type="success" :icon="Download" @click="handleDownload(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else>
        <el-table :data="uploadSessions" border stripe v-loading="Boolean(loading)" style="width: 100%">
          <el-table-column prop="sessionCode" label="会话编码" min-width="190" />
          <el-table-column prop="fileName" label="目标文件名" min-width="180">
            <template #default="{ row }">
              <span style="font-weight: 600; color: #0f172a">{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="objectKey" label="目标路径 Key" min-width="200" show-overflow-tooltip />
          <el-table-column prop="uploadMode" label="模式" width="130">
            <template #default="{ row }">
              <el-tag size="small">{{ row.uploadMode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="会话状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
                {{ row.status === 'COMPLETED' ? '已完成' : '待合并' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="130">
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

    <!-- 创建上传会话弹窗 -->
    <el-dialog v-model="createSessionDialogVisible" title="创建文件上传会话" width="600px" destroy-on-close>
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
        <el-form-item label="自定义对象 Key（留空将自动按业务目录规则生成）">
          <el-input v-model="uploadForm.objectKey" placeholder="留空将由系统按目录策略自动分配" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="上传模式">
              <el-select v-model="uploadForm.uploadMode" style="width: 100%">
                <el-option label="服务端直传 (DIRECT_SERVER)" value="DIRECT_SERVER" />
                <el-option label="预签名直传 (PRESIGNED)" value="PRESIGNED" />
                <el-option label="分片断点 (CHUNKED)" value="CHUNKED" />
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
          v-if="previewItem && isImageObject(previewItem)"
          :src="resolveObjectUrl(previewItem)"
          :alt="previewItem.fileName"
          style="max-width: 100%; max-height: 380px; border-radius: 8px; box-shadow: 0 4px 12px rgb(0 0 0 / 0.1)"
        />
        <div v-else-if="previewItem" style="color: #334155; padding: 20px">
          <p><strong>对象路径：</strong>{{ previewItem.objectKey }}</p>
          <p><strong>物理存储路径：</strong>{{ previewItem.storagePath }}</p>
          <el-button type="primary" :icon="Download" @click="handleDownload(previewItem)">在新窗口下载/查看</el-button>
        </div>
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

.missing-source-banner {
  border-radius: 10px;
}

.banner-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.upload-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #ffffff;
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 10px;
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
      color: #334155;
      padding: 3px 9px;
      background: #f1f5f9;
      border-radius: 6px;

      &.active {
        background: #ecfdf5;
        color: #047857;
        font-weight: 500;
      }

      &.warn {
        background: #fef2f2;
        color: #b91c1c;
        font-weight: 600;
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
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px;
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
