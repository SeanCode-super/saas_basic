<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  completeFileUploadSession,
  createFileAccessLog,
  createFileLifecyclePolicy,
  createFileStorage,
  createFileUploadSession,
  fetchFileAccessLogs,
  fetchFileCapability,
  fetchFileLifecyclePolicies,
  fetchFileObjects,
  fetchFileStorages,
  fetchFileUploadSessions,
  updateFileLifecyclePolicy,
  updateFileStorage
} from "@/api/modules/file";
import type {
  FileAccessLogRow,
  FileCapability,
  FileLifecyclePolicyRow,
  FileObjectRow,
  FileStorageRow,
  FileUploadSessionRow
} from "@/api/modules/file";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
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

const authStore = useAuthStore();
const loading = ref(false);
const capability = ref<FileCapability | null>(null);
const storageRows = ref<FileStorageRow[]>([]);
const objectRows = ref<FileObjectRow[]>([]);
const uploadSessions = ref<FileUploadSessionRow[]>([]);
const lifecycleRows = ref<FileLifecyclePolicyRow[]>([]);
const accessLogs = ref<FileAccessLogRow[]>([]);
const editingStorageId = ref<number | null>(null);
const editingLifecycleId = ref<number | null>(null);
const storagePolicy = reactive({
  defaultAdapter: "MINIO",
  versioning: true,
  deduplicateByHash: true,
  privateBucketOnly: true,
  retentionDays: 180
});
const uploadPolicy = reactive({
  chunkUpload: true,
  directUpload: true,
  maxFileMb: 512,
  virusScan: false,
  imageCompression: true
});
const storageForm = reactive({
  storageCode: "main_minio",
  storageName: "主存储池",
  storageType: "MINIO",
  endpoint: "http://127.0.0.1:9000",
  bucketDefault: "saas-basics",
  publicBaseUrl: "http://127.0.0.1:9000/saas-basics",
  status: "ENABLED",
  remark: ""
});
const uploadForm = reactive({
  objectKey: "manual/upload/demo.png",
  fileName: "门户背景图.png",
  fileSize: 1048576,
  uploadMode: "SINGLE",
  partCount: 1,
  contentType: "image/png",
  visibility: "PRIVATE",
  bizType: "PORTAL_ASSET",
  remark: "控制台手动完成上传"
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
  { label: "默认适配器", value: storagePolicy.defaultAdapter, note: "当前主存储策略" },
  { label: "版本治理", value: storagePolicy.versioning ? "已启用" : "未启用", note: "对象版本与追溯" },
  { label: "文件对象", value: objectRows.value.length, note: "真实文件对象数量" },
  { label: "上传会话", value: uploadSessions.value.length, note: "当前可见上传会话" }
]);

const fileSpotlight = computed(() => [
  { label: "对象版本", value: storagePolicy.versioning ? "已开启" : "未开启" },
  { label: "哈希去重", value: storagePolicy.deduplicateByHash ? "强制去重" : "未强制" },
  { label: "上传方式", value: uploadPolicy.directUpload ? "直传可用" : "中转上传" },
  { label: "安全扫描", value: uploadPolicy.virusScan ? "已启用" : "未启用" }
]);

const blueprint = [
  { title: "存储适配层", description: "MinIO、OSS、S3 等适配器在底座统一收口，业务不直接感知厂商差异。" },
  { title: "上传治理", description: "直传、分片、压缩、病毒扫描、断点续传都应该由平台统一定义。" },
  { title: "生命周期", description: "文件保留、归档、版本、去重、清理和回收策略必须可配置。" },
  { title: "访问审计", description: "上传、下载、分享、预览等动作都要有统一留痕能力。" }
];

const sectionItems = [
  { key: "overview", label: "总览", description: "查看文件底座的策略雷达与能力边界。" },
  { key: "storage", label: "存储策略", description: "统一对象存储适配、版本与默认桶策略。" },
  { key: "upload", label: "上传治理", description: "约束直传、分片、压缩和恶意文件扫描。" },
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
  const primaryStorage = storageRows.value[0];
  if (primaryStorage) {
    storagePolicy.defaultAdapter = primaryStorage.storageType;
    storageForm.storageCode = primaryStorage.storageCode;
    storageForm.storageName = primaryStorage.storageName;
    storageForm.storageType = primaryStorage.storageType;
    storageForm.endpoint = primaryStorage.endpoint || "";
    storageForm.bucketDefault = primaryStorage.bucketDefault || "";
    storageForm.publicBaseUrl = primaryStorage.publicBaseUrl || "";
    storageForm.status = primaryStorage.status;
    storageForm.remark = primaryStorage.remark || "";
    editingStorageId.value = primaryStorage.id;
  }
  if (capability.value) {
    uploadPolicy.directUpload = capability.value.directUploadEnabled;
    uploadPolicy.chunkUpload = capability.value.chunkUploadEnabled;
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [nextCapability, nextStorages, nextObjects, nextUploadSessions, nextLifecycleRows, nextAccessLogs] = await Promise.all([
      fetchFileCapability(),
      fetchFileStorages(),
      fetchFileObjects(),
      fetchFileUploadSessions(),
      fetchFileLifecyclePolicies(),
      fetchFileAccessLogs()
    ]);
    capability.value = nextCapability;
    storageRows.value = nextStorages;
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

async function saveStorage() {
  try {
    const payload = {
      tenantId: getTenantId(),
      storageCode: storageForm.storageCode,
      storageName: storageForm.storageName,
      storageType: storageForm.storageType,
      endpoint: storageForm.endpoint,
      bucketDefault: storageForm.bucketDefault,
      publicBaseUrl: storageForm.publicBaseUrl,
      status: storageForm.status,
      remark: storageForm.remark
    };
    if (editingStorageId.value) {
      await updateFileStorage(editingStorageId.value, payload);
    } else {
      await createFileStorage(payload);
    }
    await loadData();
    ElMessage.success("存储策略已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存存储策略失败");
  }
}

function editStorage(row: FileStorageRow) {
  editingStorageId.value = row.id;
  storageForm.storageCode = row.storageCode;
  storageForm.storageName = row.storageName;
  storageForm.storageType = row.storageType;
  storageForm.endpoint = row.endpoint || "";
  storageForm.bucketDefault = row.bucketDefault || "";
  storageForm.publicBaseUrl = row.publicBaseUrl || "";
  storageForm.status = row.status;
  storageForm.remark = row.remark || "";
}

async function saveLifecyclePolicy() {
  try {
    const payload = {
      tenantId: getTenantId(),
      policyCode: lifecycleForm.policyCode,
      policyName: lifecycleForm.policyName,
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
  try {
    await createFileUploadSession({
      tenantId: getTenantId(),
      bucketId: 1,
      storageId: editingStorageId.value || 1,
      objectKey: uploadForm.objectKey,
      fileName: uploadForm.fileName,
      fileSize: uploadForm.fileSize,
      uploadMode: uploadForm.uploadMode,
      partCount: uploadForm.partCount,
      ownerUserId: authStore.currentUser?.userId ?? 1
    });
    await loadData();
    ElMessage.success("上传会话已创建");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "创建上传会话失败");
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

function savePolicy(message: string) {
  ElMessage.success(message);
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
    description="文件底座的重点不是文件列表，而是存储适配、版本策略、上传规则、生命周期和访问审计基线。"
    :summary="summary"
  >
    <template #actions>
      <el-button @click="loadData">刷新视图</el-button>
      <el-button type="primary" @click="saveStorage">保存存储策略</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>文件运行态</strong>
        <p>文件中心先看版本、去重、上传链路和安全扫描，再进入存储策略、生命周期和访问审计。</p>
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
            <p>文件模块要围绕生命周期和访问控制组织，而不是只围绕对象存储厂商组织。</p>
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
        :loading="loading"
        :storage-rows="storageRows"
        :object-rows="objectRows"
        :upload-sessions="uploadSessions"
        :lifecycle-rows="lifecycleRows"
        :access-logs="accessLogs"
        :storage-form="storageForm"
        :upload-form="uploadForm"
        :lifecycle-form="lifecycleForm"
        :access-log-form="accessLogForm"
        :save-storage="saveStorage"
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
      <el-button @click="loadData">刷新视图</el-button>
    </template>

    <component
      :is="currentSectionComponent"
      :storage-policy="storagePolicy"
      :upload-policy="uploadPolicy"
      :loading="loading"
      :storage-rows="storageRows"
      :object-rows="objectRows"
      :upload-sessions="uploadSessions"
      :lifecycle-rows="lifecycleRows"
      :access-logs="accessLogs"
      :storage-form="storageForm"
      :upload-form="uploadForm"
      :lifecycle-form="lifecycleForm"
      :access-log-form="accessLogForm"
      :save-storage="saveStorage"
      :save-lifecycle-policy="saveLifecyclePolicy"
      :edit-storage="editStorage"
      :edit-lifecycle="editLifecycle"
      :create-upload="createUpload"
      :complete-upload="completeUpload"
      :create-access-audit="createAccessAudit"
    />
  </ControlSurface>
</template>
