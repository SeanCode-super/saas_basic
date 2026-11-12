<script setup lang="ts">
import { reactive, ref } from "vue";
import {
  Plus,
  Connection,
  Edit,
  Delete,
  FolderOpened,
  CircleCheckFilled
} from "@element-plus/icons-vue";
import type {
  FileStorageRow,
  FileStorageTestResult,
  FileUploadRouteRow,
  FileUploadRouteSavePayload,
  StorageAvailability
} from "@/api/modules/file";

const props = defineProps<{
  storagePolicy: {
    defaultAdapter: string;
    retentionDays: number;
    versioning: boolean;
    deduplicateByHash: boolean;
    privateBucketOnly: boolean;
  };
  storageStatus?: StorageAvailability | null;
  loading?: boolean;
  storageRows: FileStorageRow[];
  uploadRoutes: FileUploadRouteRow[];
  storageForm: {
    storageCode: string;
    storageName: string;
    storageType: string;
    uploadMode: string;
    endpoint: string;
    bucketDefault: string;
    regionName: string;
    accessKey: string;
    secretKey: string;
    basePath: string;
    defaultFolder: string;
    pathPattern: string;
    maxFileMb: number;
    allowedExts: string;
    publicBaseUrl: string;
    status: string;
    isDefault: boolean;
    remark: string;
  };
  saveStorage: () => boolean | Promise<boolean>;
  resetStorageForm: () => void;
  editStorage: (row: FileStorageRow) => void;
  setDefaultStorage: (row: FileStorageRow) => void | Promise<void>;
  toggleStorageStatus: (row: FileStorageRow) => void | Promise<void>;
  deleteStorage: (row: FileStorageRow) => void | Promise<void>;
  testStorage: (row: FileStorageRow) => Promise<FileStorageTestResult | null>;
  saveUploadRoute: (id: number | null, payload: FileUploadRouteSavePayload) => Promise<boolean>;
  deleteUploadRoute: (row: FileUploadRouteRow) => void | Promise<void>;
}>();

const uploadModeLabelMap: Record<string, string> = {
  DIRECT_SERVER: "服务端直传落盘",
  LOCAL_DISK: "本地高速落盘",
  PRESIGNED: "客户端预签名直传",
  CHUNKED: "大文件分片断点"
};

const dialogVisible = ref(false);
const isEditing = ref(false);
const savingStorage = ref(false);

const pingModalVisible = ref(false);
const pinging = ref(false);
const pingTarget = ref<FileStorageRow | null>(null);
const pingReport = ref<FileStorageTestResult | null>(null);

const routeDialogVisible = ref(false);
const editingRouteId = ref<number | null>(null);
const savingRoute = ref(false);
const routeForm = reactive({
  bizType: "USER_AVATAR",
  routeName: "",
  storageId: null as number | null,
  uploadMode: "",
  targetDir: "",
  pathPattern: "",
  maxFileMb: undefined as number | undefined,
  allowedExts: "",
  priority: 100,
  status: "ENABLED",
  remark: ""
});

function formatUploadMode(mode?: string | null) {
  if (!mode) return "服务端直传落盘";
  return uploadModeLabelMap[mode] || mode;
}

function openCreateModal() {
  isEditing.value = false;
  props.resetStorageForm();
  dialogVisible.value = true;
}

function openEditModal(row: FileStorageRow) {
  isEditing.value = true;
  props.editStorage(row);
  dialogVisible.value = true;
}

async function handleConfirmSave() {
  savingStorage.value = true;
  try {
    const ok = await props.saveStorage();
    if (ok) {
      dialogVisible.value = false;
    }
  } finally {
    savingStorage.value = false;
  }
}

async function handlePingTest(row: FileStorageRow) {
  pingTarget.value = row;
  pingReport.value = null;
  pinging.value = true;
  pingModalVisible.value = true;
  try {
    pingReport.value = await props.testStorage(row);
  } finally {
    pinging.value = false;
  }
}

function openCreateRouteModal() {
  editingRouteId.value = null;
  routeForm.bizType = "";
  routeForm.routeName = "";
  routeForm.storageId = null;
  routeForm.uploadMode = "";
  routeForm.targetDir = "";
  routeForm.pathPattern = "";
  routeForm.maxFileMb = undefined;
  routeForm.allowedExts = "";
  routeForm.priority = 50;
  routeForm.status = "ENABLED";
  routeForm.remark = "";
  routeDialogVisible.value = true;
}

function openEditRouteModal(row: FileUploadRouteRow) {
  editingRouteId.value = row.id;
  routeForm.bizType = row.bizType;
  routeForm.routeName = row.routeName;
  routeForm.storageId = row.storageId ?? null;
  routeForm.uploadMode = row.uploadMode || "";
  routeForm.targetDir = row.targetDir;
  routeForm.pathPattern = row.pathPattern || "";
  routeForm.maxFileMb = row.maxFileMb ?? undefined;
  routeForm.allowedExts = row.allowedExts || "";
  routeForm.priority = row.priority ?? 100;
  routeForm.status = row.status;
  routeForm.remark = row.remark || "";
  routeDialogVisible.value = true;
}

async function handleSaveRoute() {
  savingRoute.value = true;
  try {
    const ok = await props.saveUploadRoute(editingRouteId.value, {
      bizType: routeForm.bizType.trim(),
      routeName: routeForm.routeName.trim() || `${routeForm.bizType.trim()}目录规则`,
      storageId: routeForm.storageId || undefined,
      uploadMode: routeForm.uploadMode || undefined,
      targetDir: routeForm.targetDir.trim(),
      pathPattern: routeForm.pathPattern?.trim() || undefined,
      maxFileMb: routeForm.maxFileMb,
      allowedExts: routeForm.allowedExts?.trim() || undefined,
      priority: routeForm.priority,
      status: routeForm.status,
      remark: routeForm.remark?.trim() || undefined
    });
    if (ok) {
      routeDialogVisible.value = false;
    }
  } finally {
    savingRoute.value = false;
  }
}
</script>

<template>
  <div class="file-storage">
    <!-- 存储源可用性全局提示条 -->
    <el-alert
      v-if="storageStatus && !storageStatus.configured"
      type="error"
      :closable="false"
      show-icon
      class="source-missing-alert"
    >
      <template #title>
        <span style="font-weight: 700">未检测到可用的默认文件存储源</span>
      </template>
      <div class="missing-alert-body">
        <span>{{ storageStatus.message }}</span>
        <el-button size="small" type="danger" :icon="Plus" @click="openCreateModal">立即新增/启用存储源</el-button>
      </div>
    </el-alert>

    <div v-else-if="storageStatus?.configured" class="storage-policy-strip">
      <div class="policy-pills">
        <span class="policy-lead-pill">
          <el-icon><CircleCheckFilled /></el-icon>
          <span>存储源已就绪</span>
        </span>
        <span class="policy-chip active">默认源: <strong>{{ storageStatus.storageName }} ({{ storageStatus.storageType }})</strong></span>
        <span class="policy-chip">上传方式: <strong>{{ formatUploadMode(storageStatus.uploadMode) }}</strong></span>
        <span class="policy-chip">默认上传目录: <strong>/{{ storageStatus.defaultFolder || 'uploads' }}</strong></span>
        <span class="policy-chip">目录规则: <strong>{{ uploadRoutes.filter(r => r.status === 'ENABLED').length }} 条启用</strong></span>
      </div>
      <div class="bar-buttons">
        <el-button size="small" :icon="FolderOpened" @click="openCreateRouteModal">新增业务目录</el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="openCreateModal">新增存储源</el-button>
      </div>
    </div>

    <!-- 存储源与默认上传目录配置 -->
    <el-card shadow="never" class="main-storage-card">
      <template #header>
        <div class="panel-header-row">
          <div class="header-left-box">
            <h3 class="panel-title">存储源与默认路径配置</h3>
            <span class="panel-subtitle">共 {{ storageRows.length }} 个存储源 · 支持本地磁盘 (LOCAL) / MinIO / OSS / S3 / COS</span>
          </div>
          <el-button v-if="storageStatus && !storageStatus.configured" size="small" type="primary" :icon="Plus" @click="openCreateModal">
            新增存储源
          </el-button>
        </div>
      </template>

      <el-table :data="storageRows" border stripe v-loading="Boolean(loading)" style="width: 100%">
        <el-table-column prop="storageCode" label="编码" min-width="120" />
        <el-table-column prop="storageName" label="存储源名称" min-width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap">
              <span style="font-weight: 600; color: #0f172a">{{ row.storageName }}</span>
              <el-tag v-if="row.isDefault && row.status === 'ENABLED'" size="small" type="success" effect="dark">默认生效</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="storageType" label="存储引擎" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.storageType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadMode" label="上传方式" min-width="140">
          <template #default="{ row }">
            <span>{{ formatUploadMode(row.uploadMode) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="根目录 / Endpoint" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="path-code">{{ row.storageType === 'LOCAL' ? (row.basePath || row.endpoint) : row.endpoint }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="defaultFolder" label="默认文件夹" min-width="130">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="light">/{{ row.defaultFolder || 'uploads' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="290">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEditModal(row)">编辑</el-button>
            <el-button link type="success" :icon="Connection" @click="handlePingTest(row)">探测</el-button>
            <el-button
              v-if="!(row.isDefault && row.status === 'ENABLED')"
              link
              type="warning"
              @click="setDefaultStorage(row)"
            >
              设为默认
            </el-button>
            <el-button
              link
              :type="row.status === 'ENABLED' ? 'info' : 'success'"
              @click="toggleStorageStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="deleteStorage(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 业务目录路由与上传方式规则 -->
    <el-card shadow="never" class="main-storage-card">
      <template #header>
        <div class="panel-header-row">
          <div class="header-left-box">
            <h3 class="panel-title">业务目录路由与上传方式维护</h3>
            <span class="panel-subtitle">配置指定业务类型（如用户头像、AI知识库、合同附件）的目标目录；未匹配时落盘到默认文件夹</span>
          </div>
          <el-button size="small" type="primary" plain :icon="Plus" @click="openCreateRouteModal">新增目录规则</el-button>
        </div>
      </template>

      <el-table :data="uploadRoutes" border stripe v-loading="Boolean(loading)" style="width: 100%">
        <el-table-column prop="bizType" label="业务标识 (BizType)" min-width="150">
          <template #default="{ row }">
            <code class="path-code">{{ row.bizType }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="routeName" label="规则名称" min-width="150" />
        <el-table-column prop="targetDir" label="目标上传目录" min-width="170">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain" size="small">/{{ row.targetDir }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadMode" label="上传方式" min-width="140">
          <template #default="{ row }">
            <span>{{ row.uploadMode ? formatUploadMode(row.uploadMode) : '跟随默认存储源' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="限制策略" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.maxFileMb ? `${row.maxFileMb}MB` : '默认上限' }}{{ row.allowedExts ? ` · ${row.allowedExts}` : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="140">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEditRouteModal(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="deleteUploadRoute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑存储源弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑存储源与默认路径' : '新增文件存储源'" width="680px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="存储编码" required>
              <el-input v-model="storageForm.storageCode" :disabled="isEditing" placeholder="如 local_default" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存储源名称" required>
              <el-input v-model="storageForm.storageName" placeholder="如 企业本地存储源" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="存储引擎" required>
              <el-select v-model="storageForm.storageType" style="width: 100%">
                <el-option label="LOCAL 本地服务器磁盘" value="LOCAL" />
                <el-option label="MinIO 对象存储" value="MINIO" />
                <el-option label="阿里云 OSS" value="OSS" />
                <el-option label="AWS S3 兼容" value="S3" />
                <el-option label="腾讯云 COS" value="COS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认上传方式" required>
              <el-select v-model="storageForm.uploadMode" style="width: 100%">
                <el-option label="服务端直传落盘 (DIRECT_SERVER)" value="DIRECT_SERVER" />
                <el-option label="本地高速落盘 (LOCAL_DISK)" value="LOCAL_DISK" />
                <el-option label="客户端预签名直传 (PRESIGNED)" value="PRESIGNED" />
                <el-option label="大文件分片断点 (CHUNKED)" value="CHUNKED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认上传文件夹" required>
              <el-input v-model="storageForm.defaultFolder" placeholder="如 uploads 或 common/files" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="storageForm.storageType === 'LOCAL'" :gutter="16">
          <el-col :span="16">
            <el-form-item label="本地磁盘根目录 (BasePath)" required>
              <el-input v-model="storageForm.basePath" placeholder="如 ./data/storage 或 /data/tianshu/files" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="访问前缀 URL">
              <el-input v-model="storageForm.publicBaseUrl" placeholder="/api/file/raw-download" />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="服务 Endpoint" required>
                <el-input v-model="storageForm.endpoint" placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="默认存储桶 (Bucket)" required>
                <el-input v-model="storageForm.bucketDefault" placeholder="如 saas-basics" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="AccessKey">
                <el-input v-model="storageForm.accessKey" placeholder="留空表示不修改原凭证" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="SecretKey">
                <el-input v-model="storageForm.secretKey" type="password" show-password placeholder="留空表示不修改原密钥" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="公开访问 Base URL">
            <el-input v-model="storageForm.publicBaseUrl" placeholder="可选 CDN 或桶访问域名" />
          </el-form-item>
        </template>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路径命名模板">
              <el-input v-model="storageForm.pathPattern" placeholder="{folder}/{yyyy}/{MM}/{uuid}_{filename}" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单文件上限 (MB)">
              <el-input-number v-model="storageForm.maxFileMb" :min="1" :max="10240" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用状态">
              <el-select v-model="storageForm.status" style="width: 100%">
                <el-option label="启用 (ENABLED)" value="ENABLED" />
                <el-option label="停用 (DISABLED)" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="允许扩展名（逗号分隔，留空不限制）">
              <el-input v-model="storageForm.allowedExts" placeholder="如 png,jpg,pdf,docx" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设为默认存储源">
              <el-switch v-model="storageForm.isDefault" active-text="作为租户默认源" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注说明">
          <el-input v-model="storageForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingStorage" @click="handleConfirmSave">保存配置</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑业务目录路由弹窗 -->
    <el-dialog v-model="routeDialogVisible" :title="editingRouteId ? '编辑业务目录规则' : '新增业务目录规则'" width="580px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="业务类型标识 (BizType)" required>
              <el-select
                v-model="routeForm.bizType"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入自定义标识"
                style="width: 100%"
                :disabled="Boolean(editingRouteId)"
              >
                <el-option label="USER_AVATAR (用户头像)" value="USER_AVATAR" />
                <el-option label="AI_KNOWLEDGE (知识库文档)" value="AI_KNOWLEDGE" />
                <el-option label="PORTAL_ASSET (门户素材)" value="PORTAL_ASSET" />
                <el-option label="TENANT_CONTRACT (合同附件)" value="TENANT_CONTRACT" />
                <el-option label="AUDIT_EXPORT (审计导出)" value="AUDIT_EXPORT" />
                <el-option label="COMMON (通用文件)" value="COMMON" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则名称" required>
              <el-input v-model="routeForm.routeName" placeholder="如 业务合同归档目录" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="目标上传文件夹 (TargetDir)" required>
              <el-input v-model="routeForm.targetDir" placeholder="如 contracts/archive" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="覆盖上传方式（可选）">
              <el-select v-model="routeForm.uploadMode" clearable placeholder="跟随默认存储源" style="width: 100%">
                <el-option label="服务端直传落盘 (DIRECT_SERVER)" value="DIRECT_SERVER" />
                <el-option label="本地高速落盘 (LOCAL_DISK)" value="LOCAL_DISK" />
                <el-option label="客户端预签名直传 (PRESIGNED)" value="PRESIGNED" />
                <el-option label="大文件分片断点 (CHUNKED)" value="CHUNKED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="指定存储源（可选）">
              <el-select v-model="routeForm.storageId" clearable placeholder="跟随租户默认存储源" style="width: 100%">
                <el-option
                  v-for="s in storageRows"
                  :key="s.id"
                  :label="`${s.storageName} (${s.storageType})`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="体积上限(MB)">
              <el-input-number v-model="routeForm.maxFileMb" :min="1" :max="10240" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态">
              <el-select v-model="routeForm.status" style="width: 100%">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="允许扩展名（留空则继承存储源配置）">
          <el-input v-model="routeForm.allowedExts" placeholder="如 pdf,docx,xlsx" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="routeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoute" @click="handleSaveRoute">保存规则</el-button>
      </template>
    </el-dialog>

    <!-- 实时连通性探测报告弹窗 -->
    <el-dialog v-model="pingModalVisible" title="存储源连通性与读写实时探测" width="560px" destroy-on-close>
      <div v-if="pingTarget" class="ping-box">
        <div class="ping-header">
          <span class="target-name">{{ pingTarget.storageName }} ({{ pingTarget.storageType }})</span>
          <span class="target-endpoint">{{ pingTarget.storageType === 'LOCAL' ? (pingTarget.basePath || pingTarget.endpoint) : pingTarget.endpoint }}</span>
        </div>

        <div v-if="pinging" style="padding: 20px; text-align: center; color: #475569">
          正在真实探测存储目录/网络连通性...
        </div>

        <div v-else-if="pingReport" class="ping-steps mt-3">
          <el-alert
            :type="pingReport.reachable ? 'success' : 'error'"
            :title="pingReport.summary"
            :closable="false"
            show-icon
          />
          <div v-for="(p, i) in pingReport.steps" :key="i" class="ping-step-row">
            <div class="step-head">
              <span class="step-title">{{ p.step }}</span>
              <el-tag :type="p.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ p.status === 'SUCCESS' ? p.latency : '失败' }}
              </el-tag>
            </div>
            <p class="step-detail">{{ p.detail }}</p>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" :disabled="pinging" @click="pingModalVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-storage {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.source-missing-alert {
  border-radius: 10px;
}

.missing-alert-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.storage-policy-strip {
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
      color: #059669;
      margin-right: 4px;
    }

    .policy-chip {
      font-size: 12px;
      color: #334155;
      padding: 3px 10px;
      background: #f1f5f9;
      border-radius: 6px;

      &.active {
        background: #ecfdf5;
        color: #047857;
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

.path-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  padding: 2px 6px;
  background: #f1f5f9;
  color: #0f172a;
  border-radius: 4px;
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .header-left-box {
    display: flex;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;

    .panel-title {
      margin: 0;
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
}

.ping-box {
  .ping-header {
    display: flex;
    flex-direction: column;
    gap: 4px;
    background: #f8fafc;
    padding: 12px 16px;
    border-radius: 8px;
    border: 1px solid #e2e8f0;

    .target-name {
      font-weight: 700;
      color: #0f172a;
      font-size: 14px;
    }

    .target-endpoint {
      font-size: 12px;
      color: #475569;
      font-family: monospace;
    }
  }

  .ping-steps {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .ping-step-row {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 10px 14px;

      .step-head {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .step-title {
          font-weight: 600;
          font-size: 13px;
          color: #0f172a;
        }
      }

      .step-detail {
        margin: 4px 0 0;
        font-size: 12px;
        color: #475569;
      }
    }
  }
}

.mt-3 {
  margin-top: 12px;
}
</style>
