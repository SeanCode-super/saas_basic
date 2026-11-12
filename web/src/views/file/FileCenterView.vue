<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRouter } from "vue-router";
import {
  completeFileUploadSession,
  createFileAccessLog,
  createFileLifecyclePolicy,
  createFileStorage,
  createFileUploadRoute,
  createFileUploadSession,
  deleteFileStorage,
  deleteFileUploadRoute,
  fetchFileAccessLogs,
  fetchFileCapability,
  fetchFileLifecyclePolicies,
  fetchFileObjects,
  fetchFileStorages,
  fetchFileStorageStatus,
  fetchFileUploadRoutes,
  fetchFileUploadSessions,
  setDefaultFileStorage,
  testFileStorage,
  updateFileLifecyclePolicy,
  updateFileStorage,
  updateFileUploadRoute,
  uploadRawFile
} from "@/api/modules/file";
import type {
  FileAccessLogRow,
  FileCapability,
  FileLifecyclePolicyRow,
  FileObjectRow,
  FileStorageRow,
  FileStorageTestResult,
  FileUploadRouteRow,
  FileUploadRouteSavePayload,
  FileUploadSessionRow,
  StorageAvailability
} from "@/api/modules/file";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import { useModuleSection } from "@/hooks/useModuleSection";
import { useAuthStore } from "@/stores/modules/auth";
import FileAuditSection from "./sections/FileAuditSection.vue";
import FileLifecycleSection from "./sections/FileLifecycleSection.vue";
import FileOverviewSection from "./sections/FileOverviewSection.vue";
import FileStorageSection from "./sections/FileStorageSection.vue";
import FileUploadSection from "./sections/FileUploadSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const capability = ref<FileCapability | null>(null);
const storageStatus = ref<StorageAvailability | null>(null);
const storageRows = ref<FileStorageRow[]>([]);
const uploadRoutes = ref<FileUploadRouteRow[]>([]);
const objectRows = ref<FileObjectRow[]>([]);
const uploadSessions = ref<FileUploadSessionRow[]>([]);
const lifecycleRows = ref<FileLifecyclePolicyRow[]>([]);
const accessLogs = ref<FileAccessLogRow[]>([]);
const editingStorageId = ref<number | null>(null);
const editingLifecycleId = ref<number | null>(null);

const storagePolicy = reactive({
  defaultAdapter: "LOCAL",
  versioning: true,
  deduplicateByHash: true,
  privateBucketOnly: true,
  retentionDays: 180
});
const uploadPolicy = reactive({
  chunkUpload: true,
  directUpload: true,
  maxFileMb: 100,
  virusScan: false,
  imageCompression: true
});
const storageForm = reactive({
  storageCode: "local_default",
  storageName: "本地高可用文件存储",
  storageType: "LOCAL",
  uploadMode: "DIRECT_SERVER",
  endpoint: "./data/storage",
  bucketDefault: "local-disk",
  regionName: "",
  accessKey: "",
  secretKey: "",
  basePath: "./data/storage",
  defaultFolder: "uploads",
  pathPattern: "{folder}/{yyyy}/{MM}/{uuid}_{filename}",
  maxFileMb: 100,
  allowedExts: "",
  publicBaseUrl: "/api/file/raw-download",
  status: "ENABLED",
  isDefault: true,
  remark: ""
});
const uploadForm = reactive({
  objectKey: "",
  fileName: "门户背景图.png",
  fileSize: 1048576,
  uploadMode: "DIRECT_SERVER",
  partCount: 1,
  contentType: "image/png",
  visibility: "PRIVATE",
  bizType: "COMMON",
  remark: "控制台手动创建会话"
});
const lifecycleForm = reactive({
  policyCode: "default_archive_policy",
  policyName: "默认归档策略",
  fileScope: "GENERAL",
  retentionDays: 180,
  archiveAfterDays: 30,
  deleteAfterDays: 365,
  deduplicateEnabled: true,
  versionRetentionCount: 5,
  status: "ENABLED",
  remark: ""
});
const accessLogForm = reactive({
  fileId: 0,
  accessType: "PREVIEW",
  operatorIp: "127.0.0.1",
  success: true,
  remark: "控制台手动登记访问日志"
});

const summary = computed(() => [
  {
    label: "生效存储源",
    value: storageStatus.value?.configured
      ? storageStatus.value.storageName || storageStatus.value.storageType || "已就绪"
      : "未配置",
    note: storageStatus.value?.configured ? `默认目录: /${storageStatus.value.defaultFolder || "uploads"}` : "请先启用存储源"
  },
  { label: "目录路由规则", value: uploadRoutes.value.length, note: "按业务模块映射目录" },
  { label: "文件对象", value: objectRows.value.length, note: "真实落盘对象数量" },
  { label: "上传会话", value: uploadSessions.value.length, note: "当前可见上传会话" }
]);

const fileSpotlight = computed(() => [
  { label: "存储状态", value: storageStatus.value?.configured ? "已就绪" : "待配置" },
  { label: "默认上传方式", value: storageStatus.value?.uploadMode || "--" },
  { label: "默认文件夹", value: storageStatus.value?.defaultFolder ? `/${storageStatus.value.defaultFolder}` : "--" },
  { label: "业务目录规则", value: `${uploadRoutes.value.filter((r) => r.status === "ENABLED").length} 条启用` }
]);

const blueprint = [
  { title: "存储源与缺失校验", description: "所有上传入口在执行前强制核验当前租户是否存在启用的存储源，缺失即拦截并引导配置。" },
  { title: "上传方式可配", description: "支持服务端直传、本地磁盘、S3 预签名直传与大文件分片断点四种方式按需配置。" },
  { title: "目录路由治理", description: "未配置业务路由时统一落盘至存储源默认文件夹；配置了业务目录规则即精确路由至目标文件夹。" },
  { title: "访问审计与留痕", description: "上传、下载、预览等动作均留痕于访问日志台账，可追溯精确对象路径与存储源。" }
];

const sectionItems = [
  { key: "overview", label: "总览", description: "查看文件底座的存储源状态、目录规则与能力边界。" },
  { key: "storage", label: "存储策略", description: "维护存储源、默认上传方式、默认目录与按业务分目录路由规则。" },
  { key: "upload", label: "上传治理", description: "核验存储源状态、按配置目录真实上传文件并管理会话与归档对象。" },
  { key: "lifecycle", label: "生命周期", description: "定义保留、归档、去重和清理规则。" },
  { key: "audit", label: "访问审计", description: "统一下载、分享、预览和访问留痕。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/file",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const sectionComponentMap = {
  overview: FileOverviewSection,
  storage: FileStorageSection,
  upload: FileUploadSection,
  lifecycle: FileLifecycleSection,
  audit: FileAuditSection
} as const;

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? FileOverviewSection
);

function getTenantId() {
  return authStore.currentUser?.tenantId ?? 1;
}

function hydrateStoragePolicy() {
  const defaultStorage =
    storageRows.value.find((s) => s.isDefault && s.status === "ENABLED") ||
    storageRows.value.find((s) => s.status === "ENABLED") ||
    storageRows.value[0];
  if (defaultStorage) {
    storagePolicy.defaultAdapter = defaultStorage.storageType;
    uploadPolicy.maxFileMb = defaultStorage.maxFileMb || 100;
  }
  if (capability.value) {
    uploadPolicy.directUpload = capability.value.directUploadEnabled;
    uploadPolicy.chunkUpload = capability.value.chunkUploadEnabled;
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [
      nextCapability,
      nextStatus,
      nextStorages,
      nextRoutes,
      nextObjects,
      nextUploadSessions,
      nextLifecycleRows,
      nextAccessLogs
    ] = await Promise.all([
      fetchFileCapability(),
      fetchFileStorageStatus(uploadForm.bizType || "COMMON"),
      fetchFileStorages(),
      fetchFileUploadRoutes(),
      fetchFileObjects(),
      fetchFileUploadSessions(),
      fetchFileLifecyclePolicies(),
      fetchFileAccessLogs()
    ]);
    capability.value = nextCapability;
    storageStatus.value = nextStatus;
    storageRows.value = nextStorages;
    uploadRoutes.value = nextRoutes;
    objectRows.value = nextObjects;
    uploadSessions.value = nextUploadSessions;
    lifecycleRows.value = nextLifecycleRows;
    accessLogs.value = nextAccessLogs;
    hydrateStoragePolicy();
    hydrateLifecyclePolicy();
  } finally {
    loading.value = false;
  }
}

function hydrateLifecyclePolicy() {
  const primaryPolicy = lifecycleRows.value[0];
  if (primaryPolicy) {
    editingLifecycleId.value = primaryPolicy.id;
    lifecycleForm.policyCode = primaryPolicy.policyCode;
    lifecycleForm.policyName = primaryPolicy.policyName;
    lifecycleForm.fileScope = primaryPolicy.fileScope;
    lifecycleForm.retentionDays = primaryPolicy.retentionDays;
    lifecycleForm.archiveAfterDays = primaryPolicy.archiveAfterDays;
    lifecycleForm.deleteAfterDays = primaryPolicy.deleteAfterDays;
    lifecycleForm.deduplicateEnabled = primaryPolicy.deduplicateEnabled;
    lifecycleForm.versionRetentionCount = primaryPolicy.versionRetentionCount;
    lifecycleForm.status = primaryPolicy.status;
    lifecycleForm.remark = primaryPolicy.remark || "";
  }
  accessLogForm.fileId = objectRows.value[0]?.id ?? 0;
}

function resetStorageForm() {
  editingStorageId.value = null;
  storageForm.storageCode = `storage_${Date.now().toString().slice(-4)}`;
  storageForm.storageName = "新建本地存储源";
  storageForm.storageType = "LOCAL";
  storageForm.uploadMode = "DIRECT_SERVER";
  storageForm.endpoint = "./data/storage";
  storageForm.bucketDefault = "local-disk";
  storageForm.regionName = "";
  storageForm.accessKey = "";
  storageForm.secretKey = "";
  storageForm.basePath = "./data/storage";
  storageForm.defaultFolder = "uploads";
  storageForm.pathPattern = "{folder}/{yyyy}/{MM}/{uuid}_{filename}";
  storageForm.maxFileMb = 100;
  storageForm.allowedExts = "";
  storageForm.publicBaseUrl = "/api/file/raw-download";
  storageForm.status = "ENABLED";
  storageForm.isDefault = storageRows.value.length === 0;
  storageForm.remark = "";
}

async function saveStorage() {
  if (!storageForm.storageCode?.trim()) {
    ElMessage.warning("请输入存储标识编码");
    return false;
  }
  if (!storageForm.storageName?.trim()) {
    ElMessage.warning("请输入存储配置名称");
    return false;
  }
  if (storageForm.storageType === "LOCAL" && !storageForm.basePath?.trim()) {
    ElMessage.warning("本地磁盘存储必须填写存储根目录 (basePath)");
    return false;
  }
  if (storageForm.storageType !== "LOCAL" && (!storageForm.endpoint?.trim() || !storageForm.bucketDefault?.trim())) {
    ElMessage.warning("对象存储必须填写 Endpoint 和默认存储桶");
    return false;
  }
  try {
    const payload = {
      tenantId: getTenantId(),
      storageCode: storageForm.storageCode.trim(),
      storageName: storageForm.storageName.trim(),
      storageType: storageForm.storageType,
      uploadMode: storageForm.uploadMode,
      endpoint: storageForm.storageType === "LOCAL" ? storageForm.basePath.trim() : storageForm.endpoint.trim(),
      bucketDefault: storageForm.bucketDefault?.trim() || "local-disk",
      regionName: storageForm.regionName?.trim() || undefined,
      accessKey: storageForm.accessKey?.trim() || undefined,
      secretKey: storageForm.secretKey?.trim() || undefined,
      basePath: storageForm.basePath?.trim() || undefined,
      defaultFolder: storageForm.defaultFolder?.trim() || "uploads",
      pathPattern: storageForm.pathPattern?.trim() || "{folder}/{yyyy}/{MM}/{uuid}_{filename}",
      maxFileMb: storageForm.maxFileMb || 100,
      allowedExts: storageForm.allowedExts?.trim() || undefined,
      publicBaseUrl: storageForm.publicBaseUrl?.trim() || undefined,
      status: storageForm.status || "ENABLED",
      isDefault: Boolean(storageForm.isDefault),
      remark: storageForm.remark
    };
    if (editingStorageId.value) {
      await updateFileStorage(editingStorageId.value, payload);
    } else {
      await createFileStorage(payload);
    }
    await loadData();
    ElMessage.success("存储源与路径配置已保存");
    return true;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存存储配置失败");
    return false;
  }
}

function editStorage(row: FileStorageRow) {
  editingStorageId.value = row.id;
  storageForm.storageCode = row.storageCode;
  storageForm.storageName = row.storageName;
  storageForm.storageType = row.storageType;
  storageForm.uploadMode = row.uploadMode || "DIRECT_SERVER";
  storageForm.endpoint = row.endpoint || "";
  storageForm.bucketDefault = row.bucketDefault || "";
  storageForm.regionName = row.regionName || "";
  storageForm.accessKey = "";
  storageForm.secretKey = "";
  storageForm.basePath = row.basePath || row.endpoint || "./data/storage";
  storageForm.defaultFolder = row.defaultFolder || "uploads";
  storageForm.pathPattern = row.pathPattern || "{folder}/{yyyy}/{MM}/{uuid}_{filename}";
  storageForm.maxFileMb = row.maxFileMb || 100;
  storageForm.allowedExts = row.allowedExts || "";
  storageForm.publicBaseUrl = row.publicBaseUrl || "";
  storageForm.status = row.status;
  storageForm.isDefault = Boolean(row.isDefault);
  storageForm.remark = row.remark || "";
}

async function handleSetDefaultStorage(row: FileStorageRow) {
  try {
    await setDefaultFileStorage(row.id);
    await loadData();
    ElMessage.success(`已将 [${row.storageName}] 设为默认启用存储源`);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "设置默认存储源失败");
  }
}

async function handleToggleStorageStatus(row: FileStorageRow) {
  const nextStatus = row.status === "ENABLED" ? "DISABLED" : "ENABLED";
  try {
    await updateFileStorage(row.id, {
      tenantId: row.tenantId || getTenantId(),
      storageCode: row.storageCode,
      storageName: row.storageName,
      storageType: row.storageType,
      uploadMode: row.uploadMode,
      endpoint: row.endpoint,
      bucketDefault: row.bucketDefault,
      regionName: row.regionName,
      basePath: row.basePath,
      defaultFolder: row.defaultFolder,
      pathPattern: row.pathPattern,
      maxFileMb: row.maxFileMb,
      allowedExts: row.allowedExts,
      publicBaseUrl: row.publicBaseUrl,
      status: nextStatus,
      isDefault: nextStatus === "ENABLED" ? Boolean(row.isDefault) : false,
      remark: row.remark
    });
    await loadData();
    ElMessage.success(nextStatus === "ENABLED" ? `已启用存储源 [${row.storageName}]` : `已停用存储源 [${row.storageName}]`);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "切换存储源状态失败");
  }
}

async function handleDeleteStorage(row: FileStorageRow) {
  try {
    await ElMessageBox.confirm(`确认删除存储源「${row.storageName}」？停用或删除后若无其他可用存储源，上传功能将被提示拦截。`, "确认删除", {
      type: "warning"
    });
    await deleteFileStorage(row.id);
    await loadData();
    ElMessage.success("存储源已移除");
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err?.response?.data?.message ?? "删除存储源失败");
    }
  }
}

async function handleTestStorage(row: FileStorageRow): Promise<FileStorageTestResult | null> {
  try {
    return await testFileStorage(row.id);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "连通性测试失败");
    return null;
  }
}

async function saveUploadRoute(id: number | null, payload: FileUploadRouteSavePayload): Promise<boolean> {
  try {
    const body = { ...payload, tenantId: getTenantId() };
    if (id) {
      await updateFileUploadRoute(id, body);
    } else {
      await createFileUploadRoute(body);
    }
    await loadData();
    ElMessage.success("业务目录路由配置已保存");
    return true;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存目录路由失败");
    return false;
  }
}

async function handleDeleteUploadRoute(row: FileUploadRouteRow) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.routeName} (${row.bizType})」目录规则？删除后该类型文件将回落到默认存储目录。`, "确认删除", {
      type: "warning"
    });
    await deleteFileUploadRoute(row.id);
    await loadData();
    ElMessage.success("目录路由已删除");
  } catch (err: any) {
    if (err !== "cancel") {
      ElMessage.error(err?.response?.data?.message ?? "删除目录路由失败");
    }
  }
}

async function handleDirectUpload(file: File, bizType: string): Promise<boolean> {
  try {
    const res = await uploadRawFile(file, bizType);
    await loadData();
    ElMessage.success(`文件「${res.fileName}」已通过 ${res.uploadMode} 上传至目录 [${res.targetDir}]`);
    return true;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "文件上传失败");
    return false;
  }
}

function goToStorageConfig() {
  if (sectionLocked.value) {
    void router.push("/file/storage");
  } else {
    updateSection("storage");
  }
}

async function saveLifecyclePolicy() {
  if (!lifecycleForm.policyCode?.trim()) {
    ElMessage.warning("请输入生命周期策略编码");
    return;
  }
  if (!lifecycleForm.policyName?.trim()) {
    ElMessage.warning("请输入生命周期策略名称");
    return;
  }
  try {
    const payload = {
      tenantId: getTenantId(),
      policyCode: lifecycleForm.policyCode.trim(),
      policyName: lifecycleForm.policyName.trim(),
      fileScope: lifecycleForm.fileScope,
      retentionDays: lifecycleForm.retentionDays,
      archiveAfterDays: lifecycleForm.archiveAfterDays,
      deleteAfterDays: lifecycleForm.deleteAfterDays,
      deduplicateEnabled: lifecycleForm.deduplicateEnabled,
      versionRetentionCount: lifecycleForm.versionRetentionCount,
      status: lifecycleForm.status,
      remark: lifecycleForm.remark
    };
    if (editingLifecycleId.value) {
      await updateFileLifecyclePolicy(editingLifecycleId.value, payload);
    } else {
      await createFileLifecyclePolicy(payload);
    }
    await loadData();
    ElMessage.success("生命周期策略已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存生命周期策略失败");
  }
}

function editLifecycle(row: FileLifecyclePolicyRow) {
  editingLifecycleId.value = row.id;
  lifecycleForm.policyCode = row.policyCode;
  lifecycleForm.policyName = row.policyName;
  lifecycleForm.fileScope = row.fileScope;
  lifecycleForm.retentionDays = row.retentionDays;
  lifecycleForm.archiveAfterDays = row.archiveAfterDays;
  lifecycleForm.deleteAfterDays = row.deleteAfterDays;
  lifecycleForm.deduplicateEnabled = row.deduplicateEnabled;
  lifecycleForm.versionRetentionCount = row.versionRetentionCount;
  lifecycleForm.status = row.status;
  lifecycleForm.remark = row.remark || "";
}

async function createUpload() {
  if (!uploadForm.fileName?.trim()) {
    ElMessage.warning("请输入文件名称");
    return false;
  }
  try {
    await createFileUploadSession({
      tenantId: getTenantId(),
      bucketId: 1,
      storageId: storageStatus.value?.activeStorageId ?? undefined,
      objectKey: uploadForm.objectKey?.trim() || undefined,
      fileName: uploadForm.fileName.trim(),
      fileSize: uploadForm.fileSize,
      uploadMode: uploadForm.uploadMode,
      partCount: uploadForm.partCount,
      ownerUserId: authStore.currentUser?.userId ?? 1,
      bizType: uploadForm.bizType || "COMMON"
    });
    await loadData();
    ElMessage.success("上传会话已创建");
    return true;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "创建上传会话失败");
    return false;
  }
}

async function completeUpload(session: FileUploadSessionRow) {
  try {
    await completeFileUploadSession(session.id, {
      tenantId: getTenantId(),
      contentType: uploadForm.contentType,
      visibility: uploadForm.visibility,
      bizType: uploadForm.bizType,
      remark: uploadForm.remark
    });
    await loadData();
    ElMessage.success("上传会话已完成，文件对象已生成");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "完成上传失败");
  }
}

async function createAccessAudit() {
  if (!accessLogForm.fileId) {
    ElMessage.warning("当前没有可记录的文件对象");
    return;
  }
  try {
    await createFileAccessLog({
      tenantId: getTenantId(),
      fileId: accessLogForm.fileId,
      accessType: accessLogForm.accessType,
      operatorUserId: authStore.currentUser?.userId ?? 1,
      operatorIp: accessLogForm.operatorIp,
      success: accessLogForm.success,
      remark: accessLogForm.remark
    });
    await loadData();
    ElMessage.success("访问日志已登记");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "登记访问日志失败");
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="文件中心"
    title="文件中心"
    description="统一配置存储源引擎、上传方式、默认及业务目录路由、缺失校验拦截与全链路访问审计。"
    :summary="summary"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
      <el-button type="primary" @click="goToStorageConfig">配置存储源与路径</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>文件运行态</strong>
        <p>实时监测当前租户存储源可用状态、默认上传方式及目录路由规则。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in fileSpotlight" :key="item.label" class="module-workbench__spotlight-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
      </div>
    </template>

    <template #rail>
      <BaseCard>
        <template #header>
          <div class="panel-header">
            <h3>文件治理地图</h3>
            <p>存储源、上传方式与目标目录全部可配置，缺失存储源时自动提示拦截。</p>
          </div>
        </template>
        <div class="blueprint-stack">
          <article v-for="item in blueprint" :key="item.title" class="blueprint-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </BaseCard>
    </template>

    <div class="module-section-stack">
      <ModuleSectionNav
        v-if="!sectionLocked"
        :model-value="currentSection"
        :items="sectionItems"
        @update:model-value="updateSection"
      />

      <component
        :is="currentSectionComponent"
        :storage-policy="storagePolicy"
        :upload-policy="uploadPolicy"
        :storage-status="storageStatus"
        :loading="loading"
        :storage-rows="storageRows"
        :upload-routes="uploadRoutes"
        :object-rows="objectRows"
        :upload-sessions="uploadSessions"
        :lifecycle-rows="lifecycleRows"
        :access-logs="accessLogs"
        :storage-form="storageForm"
        :upload-form="uploadForm"
        :lifecycle-form="lifecycleForm"
        :access-log-form="accessLogForm"
        :save-storage="saveStorage"
        :reset-storage-form="resetStorageForm"
        :set-default-storage="handleSetDefaultStorage"
        :toggle-storage-status="handleToggleStorageStatus"
        :delete-storage="handleDeleteStorage"
        :test-storage="handleTestStorage"
        :save-upload-route="saveUploadRoute"
        :delete-upload-route="handleDeleteUploadRoute"
        :direct-upload="handleDirectUpload"
        :go-to-storage-config="goToStorageConfig"
        :save-lifecycle-policy="saveLifecyclePolicy"
        :edit-storage="editStorage"
        :edit-lifecycle="editLifecycle"
        :create-upload="createUpload"
        :complete-upload="completeUpload"
        :create-access-audit="createAccessAudit"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="文件控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
    </template>

    <component
      :is="currentSectionComponent"
      :storage-policy="storagePolicy"
      :upload-policy="uploadPolicy"
      :storage-status="storageStatus"
      :loading="loading"
      :storage-rows="storageRows"
      :upload-routes="uploadRoutes"
      :object-rows="objectRows"
      :upload-sessions="uploadSessions"
      :lifecycle-rows="lifecycleRows"
      :access-logs="accessLogs"
      :storage-form="storageForm"
      :upload-form="uploadForm"
      :lifecycle-form="lifecycleForm"
      :access-log-form="accessLogForm"
      :save-storage="saveStorage"
      :reset-storage-form="resetStorageForm"
      :set-default-storage="handleSetDefaultStorage"
      :toggle-storage-status="handleToggleStorageStatus"
      :delete-storage="handleDeleteStorage"
      :test-storage="handleTestStorage"
      :save-upload-route="saveUploadRoute"
      :delete-upload-route="handleDeleteUploadRoute"
      :direct-upload="handleDirectUpload"
      :go-to-storage-config="goToStorageConfig"
      :save-lifecycle-policy="saveLifecyclePolicy"
      :edit-storage="editStorage"
      :edit-lifecycle="editLifecycle"
      :create-upload="createUpload"
      :complete-upload="completeUpload"
      :create-access-audit="createAccessAudit"
    />
  </ControlSurface>
</template>
