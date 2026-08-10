<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { createDatasource, fetchDatasources } from "@/api/modules/integration";
import type { DatasourceRow } from "@/api/modules/integration";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import RegistryImportDialog from "@/components/platform/RegistryImportDialog.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import { useAuthStore } from "@/stores/modules/auth";
import { useModuleSection } from "@/hooks/useModuleSection";
import { formatJsonManifest, parseJsonManifest } from "@/utils/manifest-import";
import IntegrationCallbackSection from "./sections/IntegrationCallbackSection.vue";
import IntegrationConnectionSection from "./sections/IntegrationConnectionSection.vue";
import IntegrationHealthSection from "./sections/IntegrationHealthSection.vue";
import IntegrationOverviewSection from "./sections/IntegrationOverviewSection.vue";
import IntegrationRegistrySection from "./sections/IntegrationRegistrySection.vue";

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
const submitting = ref(false);
const importing = ref(false);
const createVisible = ref(false);
const importVisible = ref(false);
const importContent = ref("");
const rows = ref<DatasourceRow[]>([]);
const query = reactive({
  keyword: "",
  type: ""
});
const form = reactive({
  code: "",
  name: "",
  type: "MYSQL",
  usageType: "BUSINESS",
  host: "",
  port: 3306,
  databaseName: "",
  username: "",
  testStatus: "UNTESTED",
  status: "ENABLED",
  remark: ""
});
const connectionPolicy = reactive({
  encryptSecrets: true,
  testBeforeEnable: true,
  allowPublicNetwork: false,
  rotationCycleDays: 90,
  defaultTimeoutMs: 5000
});
const webhookPolicy = reactive({
  retryCount: 5,
  signRequest: true,
  deduplicateEvent: true,
  callbackTimeoutMs: 3000,
  deadLetterQueue: true
});

const sectionItems = [
  { key: "overview", label: "总览", description: "查看连接治理、回调基线和集成能力矩阵。" },
  { key: "connection", label: "连接治理", description: "统一超时、检测、凭证加密和网络边界。" },
  { key: "callback", label: "回调策略", description: "统一签名、重试、去重和死信队列策略。" },
  { key: "health", label: "健康检测", description: "监控连接可达性、异常补偿和平台健康信号。" },
  { key: "registry", label: "接入台账", description: "查看并管理数据源与接入对象台账。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/integration",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => [
  { label: "接入数据源", value: rows.value.length, note: "纳入治理的连接对象" },
  { label: "已通过检测", value: rows.value.filter((item) => item.testStatus === "PASSED").length, note: "已验证连通性" },
  { label: "凭证治理", value: connectionPolicy.encryptSecrets ? "加密存储" : "明文风险", note: "连接凭证策略" },
  { label: "回调重试", value: `${webhookPolicy.retryCount} 次`, note: "统一回调补偿策略" }
]);

const integrationSpotlight = computed(() => [
  {
    label: "默认超时",
    value: `${connectionPolicy.defaultTimeoutMs} ms`
  },
  {
    label: "公网接入",
    value: connectionPolicy.allowPublicNetwork ? "允许" : "默认禁止"
  },
  {
    label: "签名投递",
    value: webhookPolicy.signRequest ? "强制签名" : "未强制"
  },
  {
    label: "死信兜底",
    value: webhookPolicy.deadLetterQueue ? "已开启" : "未开启"
  }
]);

const operatingBlueprint = [
  { title: "连接安全", description: "控制网络可达范围、连接超时、启用前检测和凭证保存方式。" },
  { title: "凭证轮换", description: "所有连接凭证都应该有轮换周期和加密保存标准。" },
  { title: "回调投递", description: "Webhook 要统一重试、签名、去重、死信队列和超时规则。" },
  { title: "接入台账", description: "所有数据源、API Client、Webhook 目标都必须进入统一注册表。" }
];

const columns: ProTableColumn[] = [
  { prop: "code", label: "数据源编码", minWidth: 140 },
  { prop: "name", label: "数据源名称", minWidth: 200 },
  { prop: "type", label: "类型", minWidth: 120 },
  { prop: "usageType", label: "用途", minWidth: 120 },
  { prop: "host", label: "主机", minWidth: 160 },
  { prop: "databaseName", label: "数据库", minWidth: 160 },
  { prop: "testStatus", label: "测试状态", minWidth: 110, slot: "testStatus" },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" }
];

const schema = computed(() => [
  { field: "keyword", label: "关键词", component: "input" as const, placeholder: "请输入数据源编码或名称" },
  {
    field: "type",
    label: "类型",
    component: "select" as const,
    placeholder: "请选择类型",
    options: [
      { label: "全部", value: "" },
      { label: "MySQL", value: "MYSQL" },
      { label: "HTTP", value: "HTTP" },
      { label: "PostgreSQL", value: "POSTGRESQL" }
    ]
  }
]);

const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const matchesKeyword = !query.keyword || item.code.toLowerCase().includes(query.keyword.toLowerCase()) || item.name.toLowerCase().includes(query.keyword.toLowerCase());
    const matchesType = !query.type || item.type === query.type;
    return matchesKeyword && matchesType;
  })
);

const sectionComponentMap = {
  overview: IntegrationOverviewSection,
  connection: IntegrationConnectionSection,
  callback: IntegrationCallbackSection,
  health: IntegrationHealthSection,
  registry: IntegrationRegistrySection
} as const;

const datasourceImportSample = formatJsonManifest([
  {
    code: "core_mysql",
    name: "核心业务库",
    type: "MYSQL",
    usageType: "BUSINESS",
    host: "127.0.0.1",
    port: 3306,
    databaseName: "core_db",
    username: "root",
    testStatus: "UNTESTED",
    status: "ENABLED",
    remark: "示例数据源"
  }
]);

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? IntegrationOverviewSection
);

async function loadData() {
  loading.value = true;
  try {
    rows.value = await fetchDatasources();
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = "";
  query.type = "";
}

function openCreateDialog() {
  form.code = "";
  form.name = "";
  form.type = "MYSQL";
  form.usageType = "BUSINESS";
  form.host = "";
  form.port = 3306;
  form.databaseName = "";
  form.username = "";
  form.testStatus = "UNTESTED";
  form.status = "ENABLED";
  form.remark = "";
  createVisible.value = true;
}

function openImportDialog() {
  importContent.value = "";
  importVisible.value = true;
}

async function handleCreate() {
  const tenantId = authStore.currentUser?.tenantId;
  if (!tenantId) {
    ElMessage.error("当前登录态缺少租户信息");
    return;
  }
  submitting.value = true;
  try {
    await createDatasource({
      tenantId,
      code: form.code,
      name: form.name,
      type: form.type,
      usageType: form.usageType,
      host: form.host,
      port: form.port,
      databaseName: form.databaseName,
      username: form.username,
      testStatus: form.testStatus,
      status: form.status,
      remark: form.remark
    });
    createVisible.value = false;
    ElMessage.success("数据源已创建");
    await loadData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "创建数据源失败");
  } finally {
    submitting.value = false;
  }
}

async function handleImport() {
  const tenantId = authStore.currentUser?.tenantId;
  if (!tenantId) {
    ElMessage.error("当前登录态缺少租户信息");
    return;
  }
  importing.value = true;
  try {
    const manifest = parseJsonManifest<Array<Record<string, unknown>>[number]>(importContent.value);
    if (!manifest.length) {
      throw new Error("导入清单不能为空数组");
    }

    let success = 0;
    const errors: string[] = [];

    for (const [index, item] of manifest.entries()) {
      try {
        await createDatasource({
          tenantId,
          code: String(item.code ?? ""),
          name: String(item.name ?? ""),
          type: String(item.type ?? "MYSQL"),
          usageType: String(item.usageType ?? "BUSINESS"),
          host: item.host ? String(item.host) : undefined,
          port: item.port == null ? undefined : Number(item.port),
          databaseName: item.databaseName ? String(item.databaseName) : undefined,
          username: item.username ? String(item.username) : undefined,
          testStatus: String(item.testStatus ?? "UNTESTED"),
          status: String(item.status ?? "ENABLED"),
          remark: item.remark ? String(item.remark) : undefined
        });
        success += 1;
      } catch (error: any) {
        errors.push(`第 ${index + 1} 条：${error?.response?.data?.message ?? error?.message ?? "导入失败"}`);
      }
    }

    importVisible.value = false;
    await loadData();
    ElMessage.success(`接入清单导入完成，成功 ${success} 条${errors.length ? `，失败 ${errors.length} 条` : ""}`);
    if (errors.length) {
      ElMessage.warning(errors.slice(0, 3).join("；"));
    }
  } catch (error: any) {
    ElMessage.error(error?.message ?? "解析导入清单失败");
  } finally {
    importing.value = false;
  }
}

function savePolicy(message: string) {
  ElMessage.success(message);
}

onMounted(loadData);
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="集成中心"
    title="集成中心"
    description="企业级集成底座要统一连接安全、凭证轮换、回调补偿、健康检测和接入台账，而不是只记录连接配置。"
    :summary="summary"
  >
    <template #actions>
      <el-button @click="loadData">刷新数据源</el-button>
      <el-button type="primary" @click="openCreateDialog">新增数据源</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>集成运行态</strong>
        <p>集成中心先看连接超时、凭证策略、回调签名和死信兜底，再进入接入台账和健康检测。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in integrationSpotlight" :key="item.label" class="module-workbench__spotlight-item">
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
            <h3>集成治理地图</h3>
            <p>连接安全、回调投递和台账登记必须纳入同一条治理链路。</p>
          </div>
        </template>
        <div class="blueprint-stack">
          <article v-for="item in operatingBlueprint" :key="item.title" class="blueprint-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </BaseCard>

      <BaseCard>
        <template #header>
          <div class="panel-header">
            <h3>当前执行基线</h3>
            <p>当前平台在连接和回调侧的默认控制方式。</p>
          </div>
        </template>
        <div class="signal-stack">
          <article class="signal-item">
            <span>凭证保存</span>
            <strong>{{ connectionPolicy.encryptSecrets ? "强制加密" : "未强制加密" }}</strong>
          </article>
          <article class="signal-item">
            <span>公网接入</span>
            <strong>{{ connectionPolicy.allowPublicNetwork ? "允许" : "默认禁止" }}</strong>
          </article>
          <article class="signal-item">
            <span>回调补偿</span>
            <strong>{{ webhookPolicy.deadLetterQueue ? "死信兜底" : "无死信兜底" }}</strong>
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
        :connection-policy="connectionPolicy"
        :webhook-policy="webhookPolicy"
        :rows="filteredRows"
        :query="query"
        :schema="schema"
        :columns="columns"
        :loading="loading"
        :reset-query="resetQuery"
        :load-data="loadData"
        :open-create-dialog="openCreateDialog"
        :open-import-dialog="openImportDialog"
        :save-policy="savePolicy"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="集成控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <el-button @click="loadData">刷新数据源</el-button>
    </template>

    <component
      :is="currentSectionComponent"
      :connection-policy="connectionPolicy"
      :webhook-policy="webhookPolicy"
      :rows="filteredRows"
      :query="query"
      :schema="schema"
      :columns="columns"
      :loading="loading"
      :reset-query="resetQuery"
      :load-data="loadData"
      :open-create-dialog="openCreateDialog"
      :open-import-dialog="openImportDialog"
      :save-policy="savePolicy"
    />
  </ControlSurface>

  <el-dialog v-model="createVisible" title="新增数据源" width="560px">
    <el-form label-position="top">
      <el-form-item label="数据源编码">
        <el-input v-model="form.code" />
      </el-form-item>
      <el-form-item label="数据源名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <div class="dialog-grid">
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="HTTP" value="HTTP" />
            <el-option label="PostgreSQL" value="POSTGRESQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="用途">
          <el-select v-model="form.usageType">
            <el-option label="业务数据源" value="BUSINESS" />
            <el-option label="集成中转" value="INTEGRATION" />
            <el-option label="平台管理" value="PLATFORM" />
          </el-select>
        </el-form-item>
      </div>
      <div class="dialog-grid">
        <el-form-item label="主机">
          <el-input v-model="form.host" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
      </div>
      <div class="dialog-grid">
        <el-form-item label="数据库名">
          <el-input v-model="form.databaseName" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleCreate">确认新增</el-button>
    </template>
  </el-dialog>

  <RegistryImportDialog
    v-model:visible="importVisible"
    v-model:content="importContent"
    title="导入接入台账"
    description="使用 JSON 数组导入数据源或接入对象清单。系统会按当前租户逐条创建，并返回导入结果。"
    :sample="datasourceImportSample"
    :loading="importing"
    @confirm="handleImport"
  />
</template>
