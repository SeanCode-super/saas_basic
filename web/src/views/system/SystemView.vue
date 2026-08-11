<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createSystemConfig,
  createSystemDictItem,
  createSystemDictType,
  fetchSystemConfigs,
  fetchSystemDictItems,
  fetchSystemDictTypes,
  updateSystemDictItem,
  updateSystemDictItemStatus,
  updateSystemDictType,
  updateSystemDictTypeStatus
} from "@/api/modules/system";
import type {
  SystemConfigRow,
  SystemDictItemRow,
  SystemDictItemSavePayload,
  SystemDictTypeRow,
  SystemDictTypeSavePayload
} from "@/api/modules/system";
import {
  createPortalClient,
  createPortalTerminal,
  fetchPortalClients,
  fetchPortalTerminals,
  updatePortalClient,
  updatePortalTerminal
} from "@/api/modules/portal";
import type {
  PortalClientRow,
  PortalClientSavePayload,
  PortalTerminalRow,
  PortalTerminalSavePayload
} from "@/api/modules/portal";
import { fetchIamLoginPolicies, fetchIamPasswordPolicies } from "@/api/modules/iam";
import type { IamLoginPolicyRow, IamPasswordPolicyRow } from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import RegistryImportDialog from "@/components/platform/RegistryImportDialog.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import { useAuthStore } from "@/stores/modules/auth";
import { useLocaleStore } from "@/stores/modules/locale";
import { useModuleSection } from "@/hooks/useModuleSection";
import { formatJsonManifest, parseJsonManifest } from "@/utils/manifest-import";
import SystemLocaleSection from "./sections/SystemLocaleSection.vue";
import SystemOverviewSection from "./sections/SystemOverviewSection.vue";
import SystemRegistrySection from "./sections/SystemRegistrySection.vue";
import SystemSecuritySection from "./sections/SystemSecuritySection.vue";
import SystemSequenceSection from "./sections/SystemSequenceSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const authStore = useAuthStore();
const localeStore = useLocaleStore();
const loading = ref(false);
const submitting = ref(false);
const importing = ref(false);
const createVisible = ref(false);
const importVisible = ref(false);
const portalClientVisible = ref(false);
const portalTerminalVisible = ref(false);
const dictTypeVisible = ref(false);
const dictItemVisible = ref(false);
const portalClientEditingId = ref<number | null>(null);
const portalTerminalEditingId = ref<number | null>(null);
const dictTypeEditingId = ref<number | null>(null);
const dictItemEditingId = ref<number | null>(null);
const importContent = ref("");
const rows = ref<SystemConfigRow[]>([]);
const dictTypes = ref<SystemDictTypeRow[]>([]);
const dictItems = ref<SystemDictItemRow[]>([]);
const activeDictType = ref<SystemDictTypeRow | null>(null);
const portalClients = ref<PortalClientRow[]>([]);
const portalTerminals = ref<PortalTerminalRow[]>([]);
const loginPolicies = ref<IamLoginPolicyRow[]>([]);
const passwordPolicies = ref<IamPasswordPolicyRow[]>([]);
const query = reactive({
  keyword: "",
  group: ""
});
const form = reactive({
  group: "GLOBAL",
  key: "",
  name: "",
  value: "",
  valueType: "STRING",
  status: "ENABLED",
  remark: ""
});
const portalClientForm = reactive<PortalClientSavePayload>({
  tenantId: 1,
  clientId: "",
  tenantCode: "platform",
  clientName: "",
  portalTitle: "",
  welcomeTitle: "",
  welcomeText: "",
  logoUrl: "",
  themeCode: "saas-basics",
  backgroundImageUrl: "",
  backgroundColor: "#0f2740",
  filingInfo: "",
  loginPolicyId: 1,
  passwordPolicyId: 1,
  captchaMode: "IMAGE",
  sliderReserved: true,
  isDefault: false,
  status: "ENABLED",
  remark: ""
});
const portalTerminalForm = reactive<PortalTerminalSavePayload>({
  tenantId: 1,
  portalClientId: 1,
  terminalCode: "web",
  terminalName: "Web 控制台",
  terminalType: "BROWSER",
  portalTitle: "SaaS 基础底座控制台",
  logoUrl: "",
  themeCode: "saas-basics",
  backgroundImageUrl: "",
  backgroundColor: "#0f2740",
  loginPolicyId: 1,
  passwordPolicyId: 1,
  captchaMode: "IMAGE",
  sliderReserved: true,
  isDefault: true,
  status: "ENABLED",
  remark: ""
});
const securityPolicy = reactive({
  minLength: 12,
  requireSpecial: false,
  sessionTimeoutMinutes: 480,
  passwordHistory: 5,
  forceMfa: false
});
const localePolicy = reactive({
  defaultLocale: "zh-CN",
  fallbackLocale: "en-US",
  dictionaryCacheMinutes: 60,
  autoPublishI18n: true
});
const sequencePolicy = reactive({
  tenantPrefix: "TEN",
  orderPrefix: "ORD",
  resetCycle: "DAILY",
  sequenceLength: 6
});
const dictTypeForm = reactive<SystemDictTypeSavePayload>({
  tenantId: 1,
  dictCode: "",
  dictName: "",
  dictScope: "TENANT",
  status: "ENABLED",
  cacheable: true,
  extJson: "",
  remark: ""
});
const dictItemForm = reactive<SystemDictItemSavePayload>({
  tenantId: 1,
  dictTypeId: 0,
  itemValue: "",
  itemLabel: "",
  itemColor: "",
  itemTag: "",
  parentId: 0,
  sortNo: 10,
  status: "ENABLED",
  defaultItem: false,
  extJson: "",
  remark: ""
});

const sectionItems = [
  { key: "overview", label: "总览", description: "查看系统底座的安全、国际化和配置注册总貌。" },
  { key: "security", label: "安全策略", description: "统一密码复杂度、会话时长和多因子基线。" },
  { key: "locale", label: "语言字典", description: "管理国际化入口语言、回退语言和缓存周期。" },
  { key: "sequence", label: "序列规则", description: "约束租户编码、订单号和编号重置周期。" },
  { key: "registry", label: "配置注册表", description: "维护参数分组、键名和值类型台账。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/system",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => [
  { label: "配置项总数", value: rows.value.length, note: "统一配置注册表" },
  { label: "密码基线", value: `${securityPolicy.minLength} 位起`, note: "默认复杂度要求" },
  { label: "默认语言", value: localePolicy.defaultLocale, note: "国际化入口语言" },
  { label: "序列长度", value: `${sequencePolicy.sequenceLength} 位`, note: "编号规则基线" }
]);

const systemSpotlight = computed(() => [
  {
    label: "MFA 策略",
    value: securityPolicy.forceMfa ? "强制开启" : "按需开启"
  },
  {
    label: "会话时长",
    value: `${securityPolicy.sessionTimeoutMinutes} 分钟`
  },
  {
    label: "回退语言",
    value: localePolicy.fallbackLocale
  },
  {
    label: "序列周期",
    value: sequencePolicy.resetCycle
  }
]);

const operatingBlueprint = [
  { title: "安全策略", description: "统一密码长度、密码历史、会话超时和 MFA 规则，不允许业务系统各自定义。" },
  { title: "语言与字典", description: "国际化、字典缓存和发布刷新必须有平台级统一机制。" },
  { title: "序列规则", description: "租户编码、订单号、工单号等编号规则要在底座里统一定义。" },
  { title: "配置注册表", description: "所有系统级参数都要有分组、键名、值类型和启停状态。" }
];

const columns: ProTableColumn[] = [
  { prop: "group", label: "分组", minWidth: 120 },
  { prop: "key", label: "配置键", minWidth: 240 },
  { prop: "name", label: "配置名称", minWidth: 200 },
  { prop: "value", label: "配置值", minWidth: 180 },
  { prop: "valueType", label: "值类型", minWidth: 120 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" }
];

const portalClientColumns: ProTableColumn[] = [
  { prop: "clientId", label: "Client ID", minWidth: 240 },
  { prop: "clientName", label: "客户端名称", minWidth: 160 },
  { prop: "tenantCode", label: "租户编码", minWidth: 120 },
  { prop: "themeCode", label: "主题", minWidth: 120 },
  { prop: "captchaMode", label: "验证码", minWidth: 100 },
  { prop: "isDefault", label: "默认门户", minWidth: 100, slot: "defaultClient" },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" }
];

const portalTerminalColumns: ProTableColumn[] = [
  { prop: "terminalCode", label: "Terminal", minWidth: 140 },
  { prop: "terminalName", label: "终端名称", minWidth: 160 },
  { prop: "portalClientId", label: "Client", minWidth: 100 },
  { prop: "terminalType", label: "终端类型", minWidth: 120 },
  { prop: "themeCode", label: "主题", minWidth: 120 },
  { prop: "captchaMode", label: "验证码", minWidth: 100 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" }
];
const dictTypeColumns: ProTableColumn[] = [
  { prop: "dictCode", label: "字典编码", minWidth: 180 },
  { prop: "dictName", label: "字典名称", minWidth: 160 },
  { prop: "dictScope", label: "作用域", minWidth: 100 },
  { prop: "itemCount", label: "字典项数", minWidth: 100 },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 220, slot: "actions" }
];
const dictItemColumns: ProTableColumn[] = [
  { prop: "itemValue", label: "值", minWidth: 120 },
  { prop: "itemLabel", label: "标签", minWidth: 140 },
  { prop: "itemColor", label: "颜色", minWidth: 100 },
  { prop: "itemTag", label: "标签类型", minWidth: 110 },
  { prop: "sortNo", label: "排序", minWidth: 90 },
  { prop: "defaultItem", label: "默认项", minWidth: 100, slot: "defaultItem" },
  { prop: "status", label: "状态", minWidth: 100, slot: "status" },
  { prop: "actions", label: "操作", minWidth: 180, slot: "actions" }
];

const schema = computed(() => [
  { field: "keyword", label: "关键词", component: "input" as const, placeholder: "请输入配置键或配置名称" },
  {
    field: "group",
    label: "分组",
    component: "select" as const,
    placeholder: "请选择分组",
    options: [
      { label: "全部", value: "" },
      { label: "全局", value: "GLOBAL" },
      { label: "安全", value: "SECURITY" }
    ]
  }
]);

const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const matchesKeyword = !query.keyword || item.key.toLowerCase().includes(query.keyword.toLowerCase()) || item.name.toLowerCase().includes(query.keyword.toLowerCase());
    const matchesGroup = !query.group || item.group === query.group;
    return matchesKeyword && matchesGroup;
  })
);

const sectionComponentMap = {
  overview: SystemOverviewSection,
  security: SystemSecuritySection,
  locale: SystemLocaleSection,
  sequence: SystemSequenceSection,
  registry: SystemRegistrySection
} as const;

const configImportSample = formatJsonManifest([
  {
    group: "GLOBAL",
    key: "platform.default_theme",
    name: "默认主题",
    value: "enterprise",
    valueType: "STRING",
    status: "ENABLED",
    remark: "示例配置"
  }
]);

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? SystemOverviewSection
);

async function loadData() {
  loading.value = true;
  try {
    const [configRows, nextDictTypes, nextPortalClients, nextPortalTerminals, nextLoginPolicies, nextPasswordPolicies] = await Promise.all([
      fetchSystemConfigs(),
      fetchSystemDictTypes(),
      fetchPortalClients(),
      fetchPortalTerminals(),
      fetchIamLoginPolicies(),
      fetchIamPasswordPolicies()
    ]);
    rows.value = configRows;
    dictTypes.value = nextDictTypes;
    portalClients.value = nextPortalClients;
    portalTerminals.value = nextPortalTerminals;
    loginPolicies.value = nextLoginPolicies;
    passwordPolicies.value = nextPasswordPolicies;
    if (!activeDictType.value && dictTypes.value.length) {
      await selectDictType(dictTypes.value[0]);
    } else if (activeDictType.value) {
      const nextActive = dictTypes.value.find((item) => item.id === activeDictType.value?.id) ?? dictTypes.value[0] ?? null;
      activeDictType.value = nextActive;
      if (nextActive) {
        dictItems.value = await fetchSystemDictItems(nextActive.id);
      } else {
        dictItems.value = [];
      }
    }
    const minLength = rows.value.find((item) => item.key === "password.min_length");
    const needSpecial = rows.value.find((item) => item.key === "password.need_special");
    const defaultLocale = rows.value.find((item) => item.key === "i18n.default_locale");
    if (minLength) {
      securityPolicy.minLength = Number(minLength.value);
    }
    if (needSpecial) {
      securityPolicy.requireSpecial = needSpecial.value === "true";
    }
    if (defaultLocale) {
      localePolicy.defaultLocale = defaultLocale.value;
    }
  } finally {
    loading.value = false;
  }
}

async function selectDictType(row: SystemDictTypeRow) {
  activeDictType.value = row;
  dictItems.value = await fetchSystemDictItems(row.id);
}

function previewPortalClient(row: PortalClientRow) {
  window.open(`/login?clientId=${encodeURIComponent(row.clientId)}&terminalCode=web`, "_blank");
}

function previewPortalTerminal(row: PortalTerminalRow) {
  const client = portalClients.value.find((item) => item.id === row.portalClientId);
  if (!client) {
    ElMessage.warning("当前终端没有绑定门户 client");
    return;
  }
  window.open(`/login?clientId=${encodeURIComponent(client.clientId)}&terminalCode=${encodeURIComponent(row.terminalCode)}`, "_blank");
}

function resetPortalClientForm() {
  portalClientEditingId.value = null;
  portalClientForm.tenantId = authStore.currentUser?.tenantId ?? 1;
  portalClientForm.clientId = "";
  portalClientForm.tenantCode = authStore.currentUser?.tenantCode ?? "platform";
  portalClientForm.clientName = "";
  portalClientForm.portalTitle = "SaaS 基础底座控制台";
  portalClientForm.welcomeTitle = "企业级底座控制台";
  portalClientForm.welcomeText = "统一门户入口";
  portalClientForm.logoUrl = "";
  portalClientForm.themeCode = "saas-basics";
  portalClientForm.backgroundImageUrl = "";
  portalClientForm.backgroundColor = "#0f2740";
  portalClientForm.filingInfo = "";
  portalClientForm.loginPolicyId = loginPolicies.value[0]?.id ?? 1;
  portalClientForm.passwordPolicyId = passwordPolicies.value[0]?.id ?? 1;
  portalClientForm.captchaMode = "IMAGE";
  portalClientForm.sliderReserved = true;
  portalClientForm.isDefault = portalClients.value.length === 0;
  portalClientForm.status = "ENABLED";
  portalClientForm.remark = "";
}

function resetPortalTerminalForm() {
  portalTerminalEditingId.value = null;
  portalTerminalForm.tenantId = authStore.currentUser?.tenantId ?? 1;
  portalTerminalForm.portalClientId = portalClients.value[0]?.id ?? 1;
  portalTerminalForm.terminalCode = "web";
  portalTerminalForm.terminalName = "Web 控制台";
  portalTerminalForm.terminalType = "BROWSER";
  portalTerminalForm.portalTitle = portalClientForm.portalTitle || "SaaS 基础底座控制台";
  portalTerminalForm.logoUrl = "";
  portalTerminalForm.themeCode = "saas-basics";
  portalTerminalForm.backgroundImageUrl = "";
  portalTerminalForm.backgroundColor = "#0f2740";
  portalTerminalForm.loginPolicyId = loginPolicies.value[0]?.id ?? 1;
  portalTerminalForm.passwordPolicyId = passwordPolicies.value[0]?.id ?? 1;
  portalTerminalForm.captchaMode = "IMAGE";
  portalTerminalForm.sliderReserved = true;
  portalTerminalForm.isDefault = true;
  portalTerminalForm.status = "ENABLED";
  portalTerminalForm.remark = "";
}

function openCreatePortalClient() {
  resetPortalClientForm();
  portalClientVisible.value = true;
}

function openCreatePortalTerminal() {
  resetPortalTerminalForm();
  portalTerminalVisible.value = true;
}

function editPortalClient(row: PortalClientRow) {
  portalClientEditingId.value = row.id;
  portalClientForm.tenantId = row.tenantId;
  portalClientForm.clientId = row.clientId;
  portalClientForm.tenantCode = row.tenantCode;
  portalClientForm.clientName = row.clientName;
  portalClientForm.portalTitle = row.portalTitle;
  portalClientForm.welcomeTitle = row.welcomeTitle;
  portalClientForm.welcomeText = row.welcomeText;
  portalClientForm.logoUrl = row.logoUrl || "";
  portalClientForm.themeCode = row.themeCode;
  portalClientForm.backgroundImageUrl = row.backgroundImageUrl || "";
  portalClientForm.backgroundColor = row.backgroundColor;
  portalClientForm.filingInfo = row.filingInfo || "";
  portalClientForm.loginPolicyId = row.loginPolicyId;
  portalClientForm.passwordPolicyId = row.passwordPolicyId;
  portalClientForm.captchaMode = row.captchaMode;
  portalClientForm.sliderReserved = row.sliderReserved;
  portalClientForm.isDefault = row.isDefault;
  portalClientForm.status = row.status;
  portalClientForm.remark = row.remark || "";
  portalClientVisible.value = true;
}

function editPortalTerminal(row: PortalTerminalRow) {
  portalTerminalEditingId.value = row.id;
  portalTerminalForm.tenantId = row.tenantId;
  portalTerminalForm.portalClientId = row.portalClientId;
  portalTerminalForm.terminalCode = row.terminalCode;
  portalTerminalForm.terminalName = row.terminalName;
  portalTerminalForm.terminalType = row.terminalType;
  portalTerminalForm.portalTitle = row.portalTitle;
  portalTerminalForm.logoUrl = row.logoUrl || "";
  portalTerminalForm.themeCode = row.themeCode;
  portalTerminalForm.backgroundImageUrl = row.backgroundImageUrl || "";
  portalTerminalForm.backgroundColor = row.backgroundColor;
  portalTerminalForm.loginPolicyId = row.loginPolicyId;
  portalTerminalForm.passwordPolicyId = row.passwordPolicyId;
  portalTerminalForm.captchaMode = row.captchaMode;
  portalTerminalForm.sliderReserved = row.sliderReserved;
  portalTerminalForm.isDefault = row.isDefault;
  portalTerminalForm.status = row.status;
  portalTerminalForm.remark = row.remark || "";
  portalTerminalVisible.value = true;
}

function resetDictTypeForm() {
  dictTypeEditingId.value = null;
  dictTypeForm.tenantId = authStore.currentUser?.tenantId ?? 1;
  dictTypeForm.dictCode = "";
  dictTypeForm.dictName = "";
  dictTypeForm.dictScope = "TENANT";
  dictTypeForm.status = "ENABLED";
  dictTypeForm.cacheable = true;
  dictTypeForm.extJson = "";
  dictTypeForm.remark = "";
}

function resetDictItemForm() {
  dictItemEditingId.value = null;
  dictItemForm.tenantId = authStore.currentUser?.tenantId ?? 1;
  dictItemForm.dictTypeId = activeDictType.value?.id ?? 0;
  dictItemForm.itemValue = "";
  dictItemForm.itemLabel = "";
  dictItemForm.itemColor = "";
  dictItemForm.itemTag = "";
  dictItemForm.parentId = 0;
  dictItemForm.sortNo = (dictItems.value.length + 1) * 10;
  dictItemForm.status = "ENABLED";
  dictItemForm.defaultItem = false;
  dictItemForm.extJson = "";
  dictItemForm.remark = "";
}

function openCreateDictType() {
  resetDictTypeForm();
  dictTypeVisible.value = true;
}

function openCreateDictItem() {
  if (!activeDictType.value) {
    ElMessage.warning("请先选择一个字典类型");
    return;
  }
  resetDictItemForm();
  dictItemVisible.value = true;
}

function editDictType(row: SystemDictTypeRow) {
  dictTypeEditingId.value = row.id;
  dictTypeForm.tenantId = row.tenantId;
  dictTypeForm.dictCode = row.dictCode;
  dictTypeForm.dictName = row.dictName;
  dictTypeForm.dictScope = row.dictScope;
  dictTypeForm.status = row.status;
  dictTypeForm.cacheable = row.cacheable;
  dictTypeForm.extJson = row.extJson || "";
  dictTypeForm.remark = row.remark || "";
  dictTypeVisible.value = true;
}

function editDictItem(row: SystemDictItemRow) {
  dictItemEditingId.value = row.id;
  dictItemForm.tenantId = row.tenantId;
  dictItemForm.dictTypeId = row.dictTypeId;
  dictItemForm.itemValue = row.itemValue;
  dictItemForm.itemLabel = row.itemLabel;
  dictItemForm.itemColor = row.itemColor || "";
  dictItemForm.itemTag = row.itemTag || "";
  dictItemForm.parentId = row.parentId;
  dictItemForm.sortNo = row.sortNo;
  dictItemForm.status = row.status;
  dictItemForm.defaultItem = row.defaultItem;
  dictItemForm.extJson = row.extJson || "";
  dictItemForm.remark = row.remark || "";
  dictItemVisible.value = true;
}

async function handleSaveDictType() {
  submitting.value = true;
  try {
    if (dictTypeEditingId.value) {
      await updateSystemDictType(dictTypeEditingId.value, dictTypeForm);
    } else {
      await createSystemDictType(dictTypeForm);
    }
    dictTypeVisible.value = false;
    await loadData();
    ElMessage.success("字典类型已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存字典类型失败");
  } finally {
    submitting.value = false;
  }
}

async function handleSaveDictItem() {
  if (!activeDictType.value && !dictItemForm.dictTypeId) {
    ElMessage.warning("当前没有可用的字典类型");
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      ...dictItemForm,
      dictTypeId: activeDictType.value?.id ?? dictItemForm.dictTypeId
    };
    if (dictItemEditingId.value) {
      await updateSystemDictItem(dictItemEditingId.value, payload);
    } else {
      await createSystemDictItem(payload.dictTypeId, payload);
    }
    dictItemVisible.value = false;
    if (activeDictType.value) {
      dictItems.value = await fetchSystemDictItems(activeDictType.value.id);
    }
    await loadData();
    ElMessage.success("字典项已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存字典项失败");
  } finally {
    submitting.value = false;
  }
}

async function toggleDictTypeStatus(row: SystemDictTypeRow) {
  try {
    await updateSystemDictTypeStatus(row.id, {
      tenantId: authStore.currentUser?.tenantId ?? 1,
      status: row.status === "ENABLED" ? "DISABLED" : "ENABLED"
    });
    await loadData();
    ElMessage.success("字典类型状态已更新");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "更新字典类型状态失败");
  }
}

async function toggleDictItemStatus(row: SystemDictItemRow) {
  try {
    await updateSystemDictItemStatus(row.id, {
      tenantId: authStore.currentUser?.tenantId ?? 1,
      status: row.status === "ENABLED" ? "DISABLED" : "ENABLED"
    });
    if (activeDictType.value) {
      dictItems.value = await fetchSystemDictItems(activeDictType.value.id);
    }
    await loadData();
    ElMessage.success("字典项状态已更新");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "更新字典项状态失败");
  }
}

function resetQuery() {
  query.keyword = "";
  query.group = "";
}

function openCreateDialog() {
  form.group = "GLOBAL";
  form.key = "";
  form.name = "";
  form.value = "";
  form.valueType = "STRING";
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
    await createSystemConfig({
      tenantId,
      group: form.group,
      key: form.key,
      name: form.name,
      value: form.value,
      valueType: form.valueType,
      status: form.status,
      remark: form.remark
    });
    createVisible.value = false;
    ElMessage.success("系统配置已创建");
    await loadData();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "创建系统配置失败");
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
        await createSystemConfig({
          tenantId,
          group: String(item.group ?? "GLOBAL"),
          key: String(item.key ?? ""),
          name: String(item.name ?? ""),
          value: String(item.value ?? ""),
          valueType: String(item.valueType ?? "STRING"),
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
    ElMessage.success(`配置清单导入完成，成功 ${success} 条${errors.length ? `，失败 ${errors.length} 条` : ""}`);
    if (errors.length) {
      ElMessage.warning(errors.slice(0, 3).join("；"));
    }
  } catch (error: any) {
    ElMessage.error(error?.message ?? "解析导入清单失败");
  } finally {
    importing.value = false;
  }
}

async function handleSavePortalClient() {
  submitting.value = true;
  try {
    if (portalClientEditingId.value) {
      await updatePortalClient(portalClientEditingId.value, portalClientForm);
    } else {
      await createPortalClient(portalClientForm);
    }
    portalClientVisible.value = false;
    await loadData();
    ElMessage.success("门户 Client 已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存门户 Client 失败");
  } finally {
    submitting.value = false;
  }
}

async function handleSavePortalTerminal() {
  submitting.value = true;
  try {
    if (portalTerminalEditingId.value) {
      await updatePortalTerminal(portalTerminalEditingId.value, portalTerminalForm);
    } else {
      await createPortalTerminal(portalTerminalForm);
    }
    portalTerminalVisible.value = false;
    await loadData();
    ElMessage.success("门户终端已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存门户终端失败");
  } finally {
    submitting.value = false;
  }
}

function savePolicy(message: string) {
  if (currentSection.value === "locale") {
    localeStore.setLocale(localePolicy.defaultLocale === "en-US" ? "en-US" : "zh-CN");
  }
  ElMessage.success(message);
}

onMounted(loadData);
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="系统中心"
    title="系统中心"
    description="系统中心负责收口整个平台的安全、国际化、参数和编号行为，它不是一个配置表，而是一条基础运行规则链。"
    :summary="summary"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
      <el-button type="primary" @click="openCreateDialog">新增配置</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>系统运行基线</strong>
        <p>系统中心先控制安全、国际化和编号规则，再开放参数注册表，不让平台公共行为分散到各模块。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in systemSpotlight" :key="item.label" class="module-workbench__spotlight-item">
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
            <h3>系统治理地图</h3>
            <p>系统中心负责约束平台公共行为，而不是被动承接业务参数。</p>
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
            <h3>执行基线</h3>
            <p>当前系统中心的默认控制方向。</p>
          </div>
        </template>
        <div class="signal-stack">
          <article class="signal-item">
            <span>安全控制</span>
            <strong>{{ securityPolicy.forceMfa ? "强制 MFA" : "按需 MFA" }}</strong>
          </article>
          <article class="signal-item">
            <span>国际化</span>
            <strong>{{ localePolicy.autoPublishI18n ? "自动刷新缓存" : "手动刷新" }}</strong>
          </article>
          <article class="signal-item">
            <span>序列周期</span>
            <strong>{{ sequencePolicy.resetCycle }}</strong>
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
        :security-policy="securityPolicy"
        :locale-policy="localePolicy"
        :sequence-policy="sequencePolicy"
        :dict-type-columns="dictTypeColumns"
        :dict-types="dictTypes"
        :dict-item-columns="dictItemColumns"
        :dict-items="dictItems"
        :active-dict-type="activeDictType"
        :query="query"
        :schema="schema"
        :columns="columns"
        :rows="filteredRows"
        :portal-client-columns="portalClientColumns"
        :portal-clients="portalClients"
        :portal-terminal-columns="portalTerminalColumns"
        :portal-terminals="portalTerminals"
        :loading="loading"
        :reset-query="resetQuery"
        :load-data="loadData"
        :open-create-dialog="openCreateDialog"
        :open-import-dialog="openImportDialog"
        :open-create-portal-client="openCreatePortalClient"
        :open-create-portal-terminal="openCreatePortalTerminal"
        :edit-portal-client="editPortalClient"
        :edit-portal-terminal="editPortalTerminal"
        :preview-portal-client="previewPortalClient"
        :preview-portal-terminal="previewPortalTerminal"
        :select-dict-type="selectDictType"
        :open-create-dict-type="openCreateDictType"
        :open-create-dict-item="openCreateDictItem"
        :edit-dict-type="editDictType"
        :edit-dict-item="editDictItem"
        :toggle-dict-type-status="toggleDictTypeStatus"
        :toggle-dict-item-status="toggleDictItemStatus"
        :save-policy="savePolicy"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="系统控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
    </template>

    <component
      :is="currentSectionComponent"
      :security-policy="securityPolicy"
      :locale-policy="localePolicy"
      :sequence-policy="sequencePolicy"
      :dict-type-columns="dictTypeColumns"
      :dict-types="dictTypes"
      :dict-item-columns="dictItemColumns"
      :dict-items="dictItems"
      :active-dict-type="activeDictType"
      :query="query"
      :schema="schema"
      :columns="columns"
      :rows="filteredRows"
      :portal-client-columns="portalClientColumns"
      :portal-clients="portalClients"
      :portal-terminal-columns="portalTerminalColumns"
      :portal-terminals="portalTerminals"
      :loading="loading"
      :reset-query="resetQuery"
      :load-data="loadData"
      :open-create-dialog="openCreateDialog"
      :open-import-dialog="openImportDialog"
      :open-create-portal-client="openCreatePortalClient"
      :open-create-portal-terminal="openCreatePortalTerminal"
      :edit-portal-client="editPortalClient"
      :edit-portal-terminal="editPortalTerminal"
      :preview-portal-client="previewPortalClient"
      :preview-portal-terminal="previewPortalTerminal"
      :select-dict-type="selectDictType"
      :open-create-dict-type="openCreateDictType"
      :open-create-dict-item="openCreateDictItem"
      :edit-dict-type="editDictType"
      :edit-dict-item="editDictItem"
      :toggle-dict-type-status="toggleDictTypeStatus"
      :toggle-dict-item-status="toggleDictItemStatus"
      :save-policy="savePolicy"
    />
  </ControlSurface>

  <el-dialog v-model="createVisible" title="新增系统配置" width="520px">
    <el-form label-position="top">
      <el-form-item label="分组">
        <el-input v-model="form.group" />
      </el-form-item>
      <el-form-item label="配置键">
        <el-input v-model="form.key" />
      </el-form-item>
      <el-form-item label="配置名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="配置值">
        <el-input v-model="form.value" />
      </el-form-item>
      <el-form-item label="值类型">
        <el-select v-model="form.valueType">
          <el-option label="字符串" value="STRING" />
          <el-option label="数字" value="NUMBER" />
          <el-option label="布尔" value="BOOLEAN" />
          <el-option label="JSON" value="JSON" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
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
    title="导入配置注册表"
    description="使用 JSON 数组导入系统配置清单。系统会按当前租户逐条创建配置项，并反馈导入结果。"
    :sample="configImportSample"
    :loading="importing"
    @confirm="handleImport"
  />

  <el-dialog v-model="dictTypeVisible" :title="dictTypeEditingId ? '编辑字典类型' : '新增字典类型'" width="560px">
    <el-form label-position="top">
      <el-form-item label="字典编码"><el-input v-model="dictTypeForm.dictCode" /></el-form-item>
      <el-form-item label="字典名称"><el-input v-model="dictTypeForm.dictName" /></el-form-item>
      <el-form-item label="作用域">
        <el-select v-model="dictTypeForm.dictScope">
          <el-option label="平台" value="PLATFORM" />
          <el-option label="租户" value="TENANT" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="dictTypeForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item><el-switch v-model="dictTypeForm.cacheable" active-text="启用字典缓存" /></el-form-item>
      <el-form-item label="扩展 JSON"><el-input v-model="dictTypeForm.extJson" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="dictTypeForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dictTypeVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSaveDictType">保存字典类型</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dictItemVisible" :title="dictItemEditingId ? '编辑字典项' : '新增字典项'" width="560px">
    <el-form label-position="top">
      <el-form-item label="所属字典">
        <el-input :model-value="activeDictType ? `${activeDictType.dictName}（${activeDictType.dictCode}）` : '未选择'" disabled />
      </el-form-item>
      <el-form-item label="字典值"><el-input v-model="dictItemForm.itemValue" /></el-form-item>
      <el-form-item label="显示标签"><el-input v-model="dictItemForm.itemLabel" /></el-form-item>
      <el-form-item label="颜色"><el-input v-model="dictItemForm.itemColor" placeholder="success / warning / danger" /></el-form-item>
      <el-form-item label="标签类型"><el-input v-model="dictItemForm.itemTag" placeholder="SUCCESS / INFO / API" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="dictItemForm.sortNo" :min="0" :max="9999" style="width: 100%" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="dictItemForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item><el-switch v-model="dictItemForm.defaultItem" active-text="设为默认项" /></el-form-item>
      <el-form-item label="扩展 JSON"><el-input v-model="dictItemForm.extJson" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="dictItemForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dictItemVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSaveDictItem">保存字典项</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="portalClientVisible" :title="portalClientEditingId ? '编辑门户 Client' : '新增门户 Client'" width="640px">
    <el-form label-position="top">
      <el-form-item label="Client ID"><el-input v-model="portalClientForm.clientId" /></el-form-item>
      <el-form-item label="客户端名称"><el-input v-model="portalClientForm.clientName" /></el-form-item>
      <el-form-item label="租户编码"><el-input v-model="portalClientForm.tenantCode" /></el-form-item>
      <el-form-item label="门户标题"><el-input v-model="portalClientForm.portalTitle" /></el-form-item>
      <el-form-item label="欢迎标题"><el-input v-model="portalClientForm.welcomeTitle" /></el-form-item>
      <el-form-item label="欢迎文案"><el-input v-model="portalClientForm.welcomeText" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="Logo 地址"><el-input v-model="portalClientForm.logoUrl" /></el-form-item>
      <el-form-item label="主题编码"><el-input v-model="portalClientForm.themeCode" /></el-form-item>
      <el-form-item label="背景图地址"><el-input v-model="portalClientForm.backgroundImageUrl" /></el-form-item>
      <el-form-item label="背景色"><el-input v-model="portalClientForm.backgroundColor" /></el-form-item>
      <el-form-item label="备案信息"><el-input v-model="portalClientForm.filingInfo" /></el-form-item>
      <el-form-item label="登录策略">
        <el-select v-model="portalClientForm.loginPolicyId">
          <el-option v-for="item in loginPolicies" :key="item.id" :label="item.policyName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="密码策略">
        <el-select v-model="portalClientForm.passwordPolicyId">
          <el-option v-for="item in passwordPolicies" :key="item.id" :label="item.policyName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="验证码模式">
        <el-select v-model="portalClientForm.captchaMode">
          <el-option label="图片验证码" value="IMAGE" />
          <el-option label="关闭" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item><el-switch v-model="portalClientForm.sliderReserved" active-text="预留滑块验证位" /></el-form-item>
      <el-form-item><el-switch v-model="portalClientForm.isDefault" active-text="设为默认门户" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="portalClientForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="portalClientForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="portalClientVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSavePortalClient">保存 Client</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="portalTerminalVisible" :title="portalTerminalEditingId ? '编辑门户终端' : '新增门户终端'" width="640px">
    <el-form label-position="top">
      <el-form-item label="绑定 Client">
        <el-select v-model="portalTerminalForm.portalClientId">
          <el-option v-for="item in portalClients" :key="item.id" :label="item.clientName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="终端编码"><el-input v-model="portalTerminalForm.terminalCode" /></el-form-item>
      <el-form-item label="终端名称"><el-input v-model="portalTerminalForm.terminalName" /></el-form-item>
      <el-form-item label="终端类型">
        <el-select v-model="portalTerminalForm.terminalType">
          <el-option label="浏览器" value="BROWSER" />
          <el-option label="移动端" value="MOBILE" />
          <el-option label="大屏" value="SCREEN" />
        </el-select>
      </el-form-item>
      <el-form-item label="终端标题"><el-input v-model="portalTerminalForm.portalTitle" /></el-form-item>
      <el-form-item label="Logo 地址"><el-input v-model="portalTerminalForm.logoUrl" /></el-form-item>
      <el-form-item label="主题编码"><el-input v-model="portalTerminalForm.themeCode" /></el-form-item>
      <el-form-item label="背景图地址"><el-input v-model="portalTerminalForm.backgroundImageUrl" /></el-form-item>
      <el-form-item label="背景色"><el-input v-model="portalTerminalForm.backgroundColor" /></el-form-item>
      <el-form-item label="登录策略">
        <el-select v-model="portalTerminalForm.loginPolicyId">
          <el-option v-for="item in loginPolicies" :key="item.id" :label="item.policyName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="密码策略">
        <el-select v-model="portalTerminalForm.passwordPolicyId">
          <el-option v-for="item in passwordPolicies" :key="item.id" :label="item.policyName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="验证码模式">
        <el-select v-model="portalTerminalForm.captchaMode">
          <el-option label="图片验证码" value="IMAGE" />
          <el-option label="关闭" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item><el-switch v-model="portalTerminalForm.sliderReserved" active-text="预留滑块验证位" /></el-form-item>
      <el-form-item><el-switch v-model="portalTerminalForm.isDefault" active-text="设为默认终端" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="portalTerminalForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="portalTerminalForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="portalTerminalVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSavePortalTerminal">保存终端</el-button>
    </template>
  </el-dialog>
</template>
