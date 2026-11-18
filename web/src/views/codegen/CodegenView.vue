<script setup lang="ts">
import { computed, onMounted, reactive } from "vue";
import { ElMessage } from "element-plus";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import { useModuleSection } from "@/hooks/useModuleSection";
import { useAuthStore } from "@/stores/modules/auth";
import { loadGovernancePolicy, saveGovernancePolicy } from "@/utils/governance-policy";
import CodegenArtifactSection from "./sections/CodegenArtifactSection.vue";
import CodegenGateSection from "./sections/CodegenGateSection.vue";
import CodegenMetadataSection from "./sections/CodegenMetadataSection.vue";
import CodegenOverviewSection from "./sections/CodegenOverviewSection.vue";
import CodegenTemplateSection from "./sections/CodegenTemplateSection.vue";
import CodegenAiBuilderSection from "./sections/CodegenAiBuilderSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const authStore = useAuthStore();

const templatePolicy = reactive({
  generateSql: true,
  generatePermission: true,
  generateMenu: true,
  includeOptimisticLock: true,
  includeTenantColumn: true,
  includeDeletedColumn: true
});
const styleGuide = reactive({
  packagePrefix: "com.saasbasics.platform",
  tablePrefix: "biz_",
  routeStyle: "resource",
  formStyle: "drawer",
  apiStyle: "restful"
});
const qualityGate = reactive({
  requireAuditField: true,
  requireDictBinding: true,
  requireQueryForm: true,
  requireListPermission: true,
  requireSqlArtifact: true
});

const sectionItems = [
  { key: "overview", label: "总览", description: "查看生成器的元数据驱动、输出矩阵和门禁概况。" },
  { key: "ai-builder", label: "AI 智能应用工坊", description: "零代码自然语言一键生成多租户业务应用、物理表与工作台。" },
  { key: "metadata", label: "元数据建模", description: "定义表、字段、字典、索引和权限输出模型。" },
  { key: "template", label: "模板基线", description: "统一后端、前端、SQL 与菜单模板骨架。" },
  { key: "gate", label: "交付门禁", description: "用强约束保障生成物符合企业级规范。" },
  { key: "artifact", label: "产物矩阵", description: "明确 SQL、Java、Vue 和权限骨架输出范围。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/codegen",
  fallback: "ai-builder"
});

const currentSection = computed(() => props.section || activeSection.value);
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);



const outputMatrix = [
  "MySQL DDL / DML 种子脚本",
  "Entity / DTO / Mapper / Service / Controller",
  "Vue 路由 / API Service / 页面工作台",
  "权限码 / 菜单骨架 / 数据权限占位",
  "字段字典绑定 / 审计字段 / 乐观锁列"
];

const sectionComponentMap = {
  overview: CodegenOverviewSection,
  "ai-builder": CodegenAiBuilderSection,
  metadata: CodegenMetadataSection,
  template: CodegenTemplateSection,
  gate: CodegenGateSection,
  artifact: CodegenArtifactSection
} as const;

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? CodegenOverviewSection
);

async function loadData() {
  await Promise.all([
    loadGovernancePolicy("CODEGEN", "codegen.template_policy", templatePolicy),
    loadGovernancePolicy("CODEGEN", "codegen.style_guide", styleGuide),
    loadGovernancePolicy("CODEGEN", "codegen.quality_gate", qualityGate)
  ]);
}

async function savePolicy(message: string) {
  const tenantId = authStore.currentUser?.tenantId ?? 1;
  try {
    await Promise.all([
      saveGovernancePolicy("CODEGEN", "codegen.template_policy", "代码生成模板基线", templatePolicy, tenantId),
      saveGovernancePolicy("CODEGEN", "codegen.style_guide", "代码生成工程规范", styleGuide, tenantId),
      saveGovernancePolicy("CODEGEN", "codegen.quality_gate", "代码生成交付门禁", qualityGate, tenantId)
    ]);
    ElMessage.success(message);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存代码生成策略失败");
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <ControlSurface
    eyebrow="代码生成中心"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton @click="loadData" />
    </template>

    <div class="module-section-stack">
      <component
        :is="currentSectionComponent"
        :template-policy="templatePolicy"
        :style-guide="styleGuide"
        :quality-gate="qualityGate"
        :output-matrix="outputMatrix"
        :save-policy="savePolicy"
        @navigate-section="updateSection"
      />
    </div>
  </ControlSurface>
</template>
