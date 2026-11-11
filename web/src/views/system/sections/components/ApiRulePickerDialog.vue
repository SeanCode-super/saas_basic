<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { Filter, RefreshRight, Search } from "@element-plus/icons-vue";
import { fetchTransportApiCatalog } from "@/api/modules/system";
import type {
  ApiCatalogSnapshot,
  ApiEndpointStatView,
  ApiModuleStatItem,
  ApiPackageTreeNode,
  ApiPathGroupItem
} from "@/api/modules/system";
import { antMatch } from "@/utils/transport-crypto";

export type RuleTargetKey = "include" | "sensitive" | "exclude";
type ExplorerDimension = "package" | "path" | "module";
type QuickTrafficFilter = "all" | "active" | "plaintext" | "uncovered";

const props = defineProps<{
  visible: boolean;
  targetField: RuleTargetKey;
  includePaths: string[];
  sensitivePaths: string[];
  excludePaths: string[];
}>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
  "update:targetField": [value: RuleTargetKey];
  apply: [drafts: Record<RuleTargetKey, string[]>];
  catalogLoaded: [snapshot: ApiCatalogSnapshot];
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit("update:visible", val)
});

const activeTarget = computed<RuleTargetKey>({
  get: () => props.targetField,
  set: (val: RuleTargetKey) => emit("update:targetField", val)
});

const loading = ref(false);
const catalog = ref<ApiCatalogSnapshot | null>(null);
const dimension = ref<ExplorerDimension>("package");
const keyword = ref("");
const methodFilter = ref("ALL");
const trafficFilter = ref<QuickTrafficFilter>("all");
const autoCollapsePrefix = ref(true);
const quickInputRule = ref("");
const selectedTableRows = ref<ApiEndpointStatView[]>([]);

// 当前在左侧树选中的筛选范围
const selectedScope = ref<{
  type: "ALL" | "PROJECT" | "PACKAGE" | "CONTROLLER" | "PATH" | "MODULE";
  value: string;
  label: string;
}>({ type: "ALL", value: "", label: "全部已扫描接口" });

const drafts = reactive<Record<RuleTargetKey, string[]>>({
  include: [],
  sensitive: [],
  exclude: []
});

const targetTabs: Array<{ key: RuleTargetKey; label: string; hint: string }> = [
  { key: "include", label: "包含路径 (ALL / CUSTOM)", hint: "决定全部或自定义模式下哪些接口进入加密范围" },
  { key: "sensitive", label: "高敏接口列表", hint: "当作用域设为“仅高敏接口”时生效" },
  { key: "exclude", label: "明文豁免白名单", hint: "无论何种加密模式都始终跳过加解密的接口" }
];

const targetLabelMap: Record<RuleTargetKey, string> = {
  include: "包含路径",
  sensitive: "高敏接口列表",
  exclude: "明文豁免白名单"
};

function dedupeRules(rules: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const r of rules) {
    const trimmed = r.trim();
    if (trimmed && !seen.has(trimmed)) {
      seen.add(trimmed);
      out.push(trimmed);
    }
  }
  return out;
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      drafts.include = dedupeRules(props.includePaths);
      drafts.sensitive = dedupeRules(props.sensitivePaths);
      drafts.exclude = dedupeRules(props.excludePaths);
      selectedScope.value = { type: "ALL", value: "", label: "全部已扫描接口" };
      void loadCatalog();
    }
  },
  { immediate: true }
);

async function loadCatalog() {
  loading.value = true;
  try {
    const snap = await fetchTransportApiCatalog();
    catalog.value = snap;
    emit("catalogLoaded", snap);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "加载接口目录与统计失败");
  } finally {
    loading.value = false;
  }
}

const allEndpoints = computed<ApiEndpointStatView[]>(() => catalog.value?.endpoints ?? []);
const currentDraftRules = computed<string[]>(() => drafts[activeTarget.value]);

function matchesRules(antPattern: string, rules: string[]): boolean {
  return rules.some((rule) => antMatch(rule, antPattern));
}

function countRuleHits(rule: string): number {
  return allEndpoints.value.filter((ep) => antMatch(rule, ep.antPattern)).length;
}

function getEndpointsForPackageNode(node: ApiPackageTreeNode): ApiEndpointStatView[] {
  if (node.nodeType === "PROJECT") {
    return allEndpoints.value;
  }
  if (node.nodeType === "CONTROLLER") {
    return allEndpoints.value.filter((ep) => `${ep.packageName}.${ep.controllerName}` === node.fullName);
  }
  return allEndpoints.value.filter(
    (ep) => ep.packageName === node.fullName || ep.packageName.startsWith(`${node.fullName}.`)
  );
}

function getNodeCoverage(endpoints: ApiEndpointStatView[]): "none" | "partial" | "all" {
  if (endpoints.length === 0) return "none";
  const rules = currentDraftRules.value;
  let hit = 0;
  for (const ep of endpoints) {
    if (matchesRules(ep.antPattern, rules)) {
      hit++;
    }
  }
  if (hit === 0) return "none";
  return hit === endpoints.length ? "all" : "partial";
}

function getPackageNodeCoverage(node: ApiPackageTreeNode): "none" | "partial" | "all" {
  return getNodeCoverage(getEndpointsForPackageNode(node));
}

function getPathGroupCoverage(item: ApiPathGroupItem): "none" | "partial" | "all" {
  return getNodeCoverage(allEndpoints.value.filter((ep) => antMatch(item.pattern, ep.antPattern)));
}

function getModuleCoverage(mod: ApiModuleStatItem): "none" | "partial" | "all" {
  return getNodeCoverage(allEndpoints.value.filter((ep) => ep.moduleCode === mod.moduleCode));
}

const previewHitsCount = computed(() => {
  const rules = currentDraftRules.value;
  return allEndpoints.value.filter((ep) => matchesRules(ep.antPattern, rules)).length;
});

const activeCallEndpointCount = computed(
  () => allEndpoints.value.filter((ep) => ep.totalCalls > 0).length
);

const plaintextEndpointCount = computed(
  () => allEndpoints.value.filter((ep) => ep.plaintextCalls > 0).length
);

const filteredEndpoints = computed<ApiEndpointStatView[]>(() => {
  const kw = keyword.value.trim().toLowerCase();
  const rules = currentDraftRules.value;
  return allEndpoints.value.filter((ep) => {
    if (selectedScope.value.type === "PACKAGE") {
      const pkg = selectedScope.value.value;
      if (ep.packageName !== pkg && !ep.packageName.startsWith(`${pkg}.`)) return false;
    } else if (selectedScope.value.type === "CONTROLLER") {
      if (`${ep.packageName}.${ep.controllerName}` !== selectedScope.value.value) return false;
    } else if (selectedScope.value.type === "PATH") {
      if (!antMatch(selectedScope.value.value, ep.antPattern)) return false;
    } else if (selectedScope.value.type === "MODULE") {
      if (ep.moduleCode !== selectedScope.value.value) return false;
    }

    if (methodFilter.value !== "ALL" && ep.httpMethod !== methodFilter.value) {
      return false;
    }
    if (trafficFilter.value === "active" && ep.totalCalls === 0) {
      return false;
    }
    if (trafficFilter.value === "plaintext" && ep.plaintextCalls === 0) {
      return false;
    }
    if (trafficFilter.value === "uncovered" && matchesRules(ep.antPattern, rules)) {
      return false;
    }
    if (kw) {
      const hay = `${ep.pathPattern} ${ep.antPattern} ${ep.packageName} ${ep.controllerName} ${ep.handlerMethod} ${ep.moduleName}`.toLowerCase();
      if (!hay.includes(kw)) return false;
    }
    return true;
  });
});

function addRulesToActiveDraft(rulesToAdd: string[]) {
  const current = [...currentDraftRules.value];
  const merged = dedupeRules([...current, ...rulesToAdd]);
  // 若加入的是 /api/** 这种全量通配，自动提示或保留即可；若已有更宽规则覆盖也可以共存或由用户整理
  drafts[activeTarget.value] = merged;
}

function removePatternsCoveringEndpoints(patternsToRemove: string[], subset: ApiEndpointStatView[]) {
  const subsetAnts = new Set(subset.map((e) => e.antPattern));
  const patternSet = new Set(patternsToRemove);
  drafts[activeTarget.value] = currentDraftRules.value.filter(
    (rule) => !patternSet.has(rule) && !subsetAnts.has(rule)
  );
}

function togglePackageNodeSelection(node: ApiPackageTreeNode) {
  const nodeEndpoints = getEndpointsForPackageNode(node);
  const coverage = getNodeCoverage(nodeEndpoints);
  const patternsToUse = autoCollapsePrefix.value && node.suggestedAntPatterns.length > 0
    ? node.suggestedAntPatterns
    : Array.from(new Set(nodeEndpoints.map((e) => e.antPattern)));

  if (coverage === "all") {
    removePatternsCoveringEndpoints(patternsToUse, nodeEndpoints);
  } else {
    addRulesToActiveDraft(patternsToUse);
  }
}

function togglePathGroupSelection(group: ApiPathGroupItem) {
  const subset = allEndpoints.value.filter((ep) => antMatch(group.pattern, ep.antPattern));
  const coverage = getNodeCoverage(subset);
  if (coverage === "all" && currentDraftRules.value.includes(group.pattern)) {
    drafts[activeTarget.value] = currentDraftRules.value.filter((r) => r !== group.pattern);
  } else if (coverage === "all") {
    removePatternsCoveringEndpoints([group.pattern], subset);
  } else {
    addRulesToActiveDraft([group.pattern]);
  }
}

function toggleModuleSelection(mod: ApiModuleStatItem) {
  const subset = allEndpoints.value.filter((ep) => ep.moduleCode === mod.moduleCode);
  const coverage = getNodeCoverage(subset);
  const patternsToUse = autoCollapsePrefix.value && mod.suggestedAntPatterns.length > 0
    ? mod.suggestedAntPatterns
    : Array.from(new Set(subset.map((e) => e.antPattern)));

  if (coverage === "all") {
    removePatternsCoveringEndpoints(patternsToUse, subset);
  } else {
    addRulesToActiveDraft(patternsToUse);
  }
}

function handleTreeNodeClick(node: ApiPackageTreeNode) {
  selectedScope.value = {
    type: node.nodeType,
    value: node.fullName,
    label: node.nodeType === "PROJECT" ? "全项目 (/api/**)" : `${node.label} (${node.endpointCount})`
  };
}

function selectPathScope(item: ApiPathGroupItem) {
  selectedScope.value = {
    type: "PATH",
    value: item.pattern,
    label: `${item.pattern} (${item.endpointCount})`
  };
}

function selectModuleScope(mod: ApiModuleStatItem) {
  selectedScope.value = {
    type: "MODULE",
    value: mod.moduleCode,
    label: `${mod.moduleName} (${mod.endpointCount})`
  };
}

function addSingleEndpoint(row: ApiEndpointStatView) {
  addRulesToActiveDraft([row.antPattern]);
}

function removeSingleRule(rule: string) {
  drafts[activeTarget.value] = currentDraftRules.value.filter((r) => r !== rule);
}

function addSelectedTableRows() {
  if (selectedTableRows.value.length === 0) return;
  if (autoCollapsePrefix.value && selectedScope.value.type !== "ALL") {
    // 若把当前筛选范围内的全部接口都勾选了且筛选有对应节点前缀，可直接折叠
    const scopeEps = filteredEndpoints.value;
    if (selectedTableRows.value.length === scopeEps.length && selectedScope.value.type === "PATH") {
      addRulesToActiveDraft([selectedScope.value.value]);
      return;
    }
  }
  addRulesToActiveDraft(selectedTableRows.value.map((r) => r.antPattern));
}

function removeSelectedTableRows() {
  if (selectedTableRows.value.length === 0) return;
  const targets = new Set(selectedTableRows.value.map((r) => r.antPattern));
  drafts[activeTarget.value] = currentDraftRules.value.filter((r) => !targets.has(r));
}

function handleQuickAddInput() {
  const val = quickInputRule.value.trim();
  if (!val) return;
  addRulesToActiveDraft([val]);
  quickInputRule.value = "";
}

function compactDuplicateRules() {
  const list = dedupeRules(currentDraftRules.value);
  if (list.includes("/api/**")) {
    drafts[activeTarget.value] = ["/api/**"];
    ElMessage.success("已合并为全项目通配规则 /api/**");
    return;
  }
  const prefixes = list.filter((r) => r.endsWith("/**")).sort((a, b) => a.length - b.length);
  const minimal = list.filter((rule) => {
    return !prefixes.some((pref) => pref !== rule && antMatch(pref, rule.replace("/**", "/probe")));
  });
  drafts[activeTarget.value] = minimal;
  ElMessage.success(`已收敛冗余规则（${list.length} -> ${minimal.length} 条）`);
}

function handleConfirmApply() {
  emit("apply", {
    include: dedupeRules(drafts.include),
    sensitive: dedupeRules(drafts.sensitive),
    exclude: dedupeRules(drafts.exclude)
  });
  dialogVisible.value = false;
}

function methodTagType(method: string): "success" | "primary" | "warning" | "danger" | "info" {
  switch (method) {
    case "GET":
      return "success";
    case "POST":
      return "primary";
    case "PUT":
    case "PATCH":
      return "warning";
    case "DELETE":
      return "danger";
    default:
      return "info";
  }
}

function actionLabel(action: string): string {
  if (action === "REQUIRED") return "强制密文";
  if (action === "OPTIONAL") return "兼容密文";
  return "明文旁路";
}

function actionTagType(action: string): "success" | "primary" | "info" {
  if (action === "REQUIRED") return "success";
  if (action === "OPTIONAL") return "primary";
  return "info";
}

function nodeBadgeLabel(nodeType: ApiPackageTreeNode["nodeType"]): string {
  if (nodeType === "PROJECT") return "项目";
  if (nodeType === "PACKAGE") return "包名";
  return "控制类";
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="多维度接口选择器与实时调用统计 (项目级 / 各级包名级 / 路径层级)"
    width="min(1160px, 95vw)"
    top="4vh"
    destroy-on-close
  >
    <div v-loading="loading" class="picker-shell">
      <!-- 1. 目标规则切换 + 实时接口统计概览 -->
      <div class="picker-top-bar">
        <div class="target-tabs">
          <button
            v-for="tab in targetTabs"
            :key="tab.key"
            type="button"
            class="target-tab"
            :class="{ 'is-active': activeTarget === tab.key }"
            @click="activeTarget = tab.key"
          >
            <span>{{ tab.label }}</span>
            <em class="count-chip">{{ drafts[tab.key].length }}</em>
          </button>
        </div>

        <div class="catalog-stats-pills" v-if="catalog">
          <span class="stat-chip">
            已扫描 <strong>{{ catalog.summary.totalEndpoints }}</strong> 接口 / {{ catalog.summary.totalPackages }} 包
          </span>
          <span class="stat-chip">
            总调用 <strong>{{ catalog.summary.totalCalls }}</strong>
            （密 {{ catalog.summary.encryptedCalls }} · 明 {{ catalog.summary.plaintextCalls }}）
          </span>
          <el-button size="small" :icon="RefreshRight" :loading="loading" @click="loadCatalog">刷新统计</el-button>
        </div>
      </div>

      <!-- 2. 搜索与多维度工具栏 -->
      <div class="picker-toolbar">
        <div class="dimension-switcher">
          <button
            type="button"
            class="dim-btn"
            :class="{ 'is-active': dimension === 'package' }"
            @click="dimension = 'package'"
          >
            项目 / 各级包名树
          </button>
          <button
            type="button"
            class="dim-btn"
            :class="{ 'is-active': dimension === 'path' }"
            @click="dimension = 'path'"
          >
            URL 路径前缀
          </button>
          <button
            type="button"
            class="dim-btn"
            :class="{ 'is-active': dimension === 'module' }"
            @click="dimension = 'module'"
          >
            业务模块统计
          </button>
        </div>

        <div class="filter-controls">
          <el-input
            v-model="keyword"
            size="small"
            :prefix-icon="Search"
            placeholder="搜索路径、包名、Controller 或方法"
            clearable
            class="search-input"
          />
          <el-select v-model="methodFilter" size="small" class="method-select">
            <el-option label="全部请求方式" value="ALL" />
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="PATCH" value="PATCH" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <el-select v-model="trafficFilter" size="small" class="traffic-select">
            <el-option :label="`全部接口 (${allEndpoints.length})`" value="all" />
            <el-option :label="`有调用记录 (${activeCallEndpointCount})`" value="active" />
            <el-option :label="`曾明文调用 (${plaintextEndpointCount})`" value="plaintext" />
            <el-option label="未被当前规则命中" value="uncovered" />
          </el-select>
          <el-tooltip content="按包/模块批量勾选时，自动合并为简洁的 /api/xxx/** 前缀通配，而非冗长明细列表" placement="top">
            <div class="fold-switch">
              <span>自动折叠前缀</span>
              <el-switch v-model="autoCollapsePrefix" size="small" />
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- 3. 左树右表联动浏览 -->
      <div class="explorer-body">
        <!-- 左栏：多维度浏览树 -->
        <aside class="explorer-left">
          <div class="left-header">
            <span>{{ dimension === "package" ? "项目 -> 多级包名 -> 控制类" : dimension === "path" ? "URL 前缀层级" : "业务模块分布" }}</span>
            <el-button
              v-if="selectedScope.type !== 'ALL'"
              link
              type="primary"
              size="small"
              @click="selectedScope = { type: 'ALL', value: '', label: '全部已扫描接口' }"
            >
              重置筛选
            </el-button>
          </div>

          <div class="left-scroll">
            <!-- 维度 A: 项目 + 多级 Java 包名树 -->
            <el-tree
              v-if="dimension === 'package' && catalog"
              :data="catalog.packageTree"
              node-key="id"
              :props="{ label: 'label', children: 'children' }"
              :default-expanded-keys="catalog.packageTree.flatMap((root) => [root.id, ...root.children.map((c) => c.id)])"
              :expand-on-click-node="false"
              highlight-current
              @node-click="handleTreeNodeClick"
            >
              <template #default="{ data }">
                <div class="tree-node-row" :title="`${data.fullName}\n建议规则：${data.suggestedAntPatterns.join(', ')}`">
                  <div class="tree-node-main">
                    <span class="node-kind" :class="data.nodeType.toLowerCase()">{{ nodeBadgeLabel(data.nodeType) }}</span>
                    <span class="node-label">{{ data.label }}</span>
                    <span class="node-count">{{ data.endpointCount }}</span>
                    <span v-if="data.plaintextCalls > 0" class="node-plain-dot" :title="`明文调用 ${data.plaintextCalls} 次`">明</span>
                  </div>
                  <button
                    type="button"
                    class="node-pick-btn"
                    :class="getPackageNodeCoverage(data)"
                    @click.stop="togglePackageNodeSelection(data)"
                  >
                    {{ getPackageNodeCoverage(data) === "all" ? "✓ 已选" : getPackageNodeCoverage(data) === "partial" ? "+ 补全" : "+ 选整组" }}
                  </button>
                </div>
              </template>
            </el-tree>

            <!-- 维度 B: URL 路径前缀树 -->
            <div v-else-if="dimension === 'path' && catalog" class="flat-group-list">
              <div
                v-for="group in catalog.pathGroups"
                :key="group.pattern"
                class="group-item-row"
                :class="{ 'is-selected': selectedScope.type === 'PATH' && selectedScope.value === group.pattern }"
                :style="{ paddingLeft: `${8 + (group.depth - 1) * 14}px` }"
                @click="selectPathScope(group)"
              >
                <div class="group-item-main">
                  <code>{{ group.pattern }}</code>
                  <span class="node-count">{{ group.endpointCount }}</span>
                  <span v-if="group.totalCalls > 0" class="mini-call">{{ group.totalCalls }}次</span>
                </div>
                <button
                  type="button"
                  class="node-pick-btn"
                  :class="getPathGroupCoverage(group)"
                  @click.stop="togglePathGroupSelection(group)"
                >
                  {{ getPathGroupCoverage(group) === "all" ? "✓ 已选" : getPathGroupCoverage(group) === "partial" ? "+ 补全" : "+ 选前缀" }}
                </button>
              </div>
            </div>

            <!-- 维度 C: 业务模块统计视图 -->
            <div v-else-if="dimension === 'module' && catalog" class="flat-group-list">
              <div
                v-for="mod in catalog.modules"
                :key="mod.moduleCode"
                class="module-item-card"
                :class="{ 'is-selected': selectedScope.type === 'MODULE' && selectedScope.value === mod.moduleCode }"
                @click="selectModuleScope(mod)"
              >
                <div class="mod-top">
                  <strong>{{ mod.moduleName }}</strong>
                  <button
                    type="button"
                    class="node-pick-btn"
                    :class="getModuleCoverage(mod)"
                    @click.stop="toggleModuleSelection(mod)"
                  >
                    {{ getModuleCoverage(mod) === "all" ? "✓ 已选" : getModuleCoverage(mod) === "partial" ? "+ 补全" : "+ 选整模块" }}
                  </button>
                </div>
                <div class="mod-meta">
                  <span>{{ mod.endpointCount }} 个接口</span>
                  <span>总 {{ mod.totalCalls }} · 密 {{ mod.encryptedCalls }} · 明 {{ mod.plaintextCalls }}</span>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <!-- 右栏：当前维度下的接口清单与实时调用统计表 -->
        <section class="explorer-right">
          <div class="right-header">
            <div class="scope-indicator">
              <el-icon><Filter /></el-icon>
              <span>{{ selectedScope.label }}</span>
              <span class="showing-count">显示 {{ filteredEndpoints.length }} 条</span>
            </div>
            <div class="batch-actions">
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="selectedTableRows.length === 0"
                @click="addSelectedTableRows"
              >
                + 加入所选 ({{ selectedTableRows.length }})
              </el-button>
              <el-button
                size="small"
                plain
                :disabled="selectedTableRows.length === 0"
                @click="removeSelectedTableRows"
              >
                移出精准规则
              </el-button>
            </div>
          </div>

          <el-table
            :data="filteredEndpoints"
            size="small"
            height="325px"
            border
            @selection-change="(rows: ApiEndpointStatView[]) => (selectedTableRows = rows)"
          >
            <el-table-column type="selection" width="38" />
            <el-table-column label="接口路径与包归属" min-width="265">
              <template #default="{ row }">
                <div class="ep-main-line">
                  <el-tag size="small" :type="methodTagType(row.httpMethod)" effect="light">{{ row.httpMethod }}</el-tag>
                  <code class="ep-path">{{ row.pathPattern }}</code>
                </div>
                <div class="ep-sub-line">
                  {{ row.moduleName }} · {{ row.controllerName }}.{{ row.handlerMethod }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="实时调用与明/密文统计" width="195">
              <template #default="{ row }">
                <div v-if="row.totalCalls > 0 || row.encryptedCalls > 0 || row.plaintextCalls > 0 || row.rejectedCalls > 0" class="ep-stats-cell">
                  <span>总 <b>{{ row.totalCalls }}</b></span>
                  <span class="st-enc">密 {{ row.encryptedCalls }}</span>
                  <span v-if="row.plaintextCalls > 0" class="st-plain">明 {{ row.plaintextCalls }}</span>
                  <span v-if="row.rejectedCalls > 0" class="st-rej">拦 {{ row.rejectedCalls }}</span>
                  <span class="st-ms">{{ row.avgLatencyMs }}ms</span>
                </div>
                <span v-else class="st-zero">暂无调用记录</span>
              </template>
            </el-table-column>
            <el-table-column label="现行生效态" width="96" align="center">
              <template #default="{ row }">
                <el-tooltip :content="row.matchedReason" placement="top">
                  <el-tag size="small" :type="actionTagType(row.effectiveAction)" effect="plain">
                    {{ actionLabel(row.effectiveAction) }}
                  </el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="目标规则" width="96" align="right">
              <template #default="{ row }">
                <el-tag
                  v-if="matchesRules(row.antPattern, currentDraftRules)"
                  size="small"
                  type="success"
                  effect="light"
                >
                  已命中
                </el-tag>
                <el-button v-else link type="primary" size="small" @click="addSingleEndpoint(row)">
                  + 加入
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <!-- 4. 底部待生效规则预览与快捷微调区 -->
      <div class="draft-bar">
        <div class="draft-head">
          <div class="draft-title">
            <span>{{ targetLabelMap[activeTarget] }}（{{ currentDraftRules.length }} 条规则）</span>
            <el-tag type="primary" size="small" effect="light">
              预计覆盖 {{ previewHitsCount }} / {{ allEndpoints.length }} 个接口
            </el-tag>
          </div>
          <div class="draft-tools">
            <el-input
              v-model="quickInputRule"
              size="small"
              placeholder="输入路径如 /api/erp/**，回车添加"
              class="quick-add-input"
              @keyup.enter="handleQuickAddInput"
            />
            <el-button size="small" @click="compactDuplicateRules">智能去重</el-button>
            <el-button size="small" @click="drafts[activeTarget] = []">清空</el-button>
          </div>
        </div>

        <div class="draft-tags-wrap">
          <el-tag
            v-for="rule in currentDraftRules"
            :key="rule"
            closable
            size="small"
            class="rule-tag"
            @close="removeSingleRule(rule)"
          >
            <code>{{ rule }}</code>
            <span class="tag-hit-num">({{ countRuleHits(rule) }})</span>
          </el-tag>
          <span v-if="currentDraftRules.length === 0" class="empty-draft">暂未添加规则，可从左侧项目/包名树或右侧接口列表快捷加入</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleConfirmApply">应用所选规则到配置页</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.picker-shell {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.picker-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.target-tabs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.target-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;

  &.is-active {
    border-color: #2563eb;
    background: #eff6ff;
    color: #1d4ed8;
  }

  .count-chip {
    font-style: normal;
    font-size: 11px;
    padding: 0 6px;
    border-radius: 9999px;
    background: rgb(15 23 42 / 0.08);
  }
}

.catalog-stats-pills {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.stat-chip {
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 9999px;
  padding: 4px 10px;

  strong {
    color: #0f172a;
  }
}

.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.dimension-switcher {
  display: inline-flex;
  background: #e2e8f0;
  border-radius: 7px;
  padding: 2px;
  gap: 2px;
}

.dim-btn {
  border: none;
  background: transparent;
  padding: 4px 10px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;

  &.is-active {
    background: #fff;
    color: #1d4ed8;
    box-shadow: 0 1px 2px rgb(15 23 42 / 0.08);
  }
}

.filter-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.search-input {
  width: 210px;
}

.method-select {
  width: 118px;
}

.traffic-select {
  width: 158px;
}

.fold-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}

.explorer-body {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 10px;

  @media (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.explorer-left {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  display: flex;
  flex-direction: column;
  height: 368px;
  min-width: 0;
}

.left-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.left-scroll {
  flex: 1;
  overflow: auto;
  padding: 6px;
}

.tree-node-row {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
  padding-right: 4px;
}

.tree-node-main {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  flex: 1;
}

.node-kind {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 4px;
  font-weight: 600;
  flex-shrink: 0;

  &.project {
    background: #e0e7ff;
    color: #3730a3;
  }

  &.package {
    background: #eff6ff;
    color: #1d4ed8;
  }

  &.controller {
    background: #ecfdf5;
    color: #047857;
  }
}

.node-label {
  font-size: 12px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-count {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 9999px;
  padding: 0 5px;
  flex-shrink: 0;
}

.node-plain-dot {
  font-size: 10px;
  color: #b45309;
  background: #fef3c7;
  border-radius: 4px;
  padding: 0 4px;
  flex-shrink: 0;
}

.node-pick-btn {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  border-radius: 5px;
  font-size: 11px;
  padding: 1px 6px;
  cursor: pointer;
  flex-shrink: 0;

  &:hover {
    border-color: #2563eb;
    color: #2563eb;
  }

  &.all {
    border-color: #16a34a;
    background: #f0fdf4;
    color: #15803d;
  }

  &.partial {
    border-color: #d97706;
    background: #fffbeb;
    color: #b45309;
  }
}

.flat-group-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 7px;
  cursor: pointer;

  &:hover {
    background: #f8fafc;
  }

  &.is-selected {
    background: #eff6ff;
  }

  .group-item-main {
    display: flex;
    align-items: center;
    gap: 5px;
    min-width: 0;

    code {
      font-family: var(--sb-font-mono, monospace);
      font-size: 11.5px;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.mini-call {
  font-size: 11px;
  color: #0369a1;
}

.module-item-card {
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    border-color: #93c5fd;
  }

  &.is-selected {
    border-color: #2563eb;
    background: #eff6ff;
  }

  .mod-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    font-size: 12.5px;
    color: #0f172a;
  }

  .mod-meta {
    display: flex;
    justify-content: space-between;
    margin-top: 4px;
    font-size: 11px;
    color: #64748b;
  }
}

.explorer-right {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  display: flex;
  flex-direction: column;
  height: 368px;
  min-width: 0;
  overflow: hidden;
}

.right-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 7px 10px;
  border-bottom: 1px solid #f1f5f9;
}

.scope-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: #0f172a;

  .showing-count {
    font-weight: 400;
    color: #64748b;
    font-size: 12px;
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ep-main-line {
  display: flex;
  align-items: center;
  gap: 6px;

  .ep-path {
    font-family: var(--sb-font-mono, monospace);
    font-size: 12px;
    color: #0f172a;
    word-break: break-all;
  }
}

.ep-sub-line {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.ep-stats-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 11.5px;
  color: #334155;

  .st-enc {
    color: #15803d;
  }

  .st-plain {
    color: #b45309;
    font-weight: 600;
  }

  .st-rej {
    color: #dc2626;
    font-weight: 600;
  }

  .st-ms {
    color: #64748b;
  }
}

.st-zero {
  font-size: 11.5px;
  color: #94a3b8;
}

.draft-bar {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.draft-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.draft-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #0f172a;
}

.draft-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.quick-add-input {
  width: 245px;
}

.draft-tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 76px;
  overflow: auto;
}

.rule-tag {
  code {
    font-family: var(--sb-font-mono, monospace);
    font-size: 11.5px;
  }

  .tag-hit-num {
    margin-left: 3px;
    color: #64748b;
    font-size: 11px;
  }
}

.empty-draft {
  font-size: 12px;
  color: #94a3b8;
}
</style>
