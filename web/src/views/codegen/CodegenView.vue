<script setup lang="ts">
import { computed, reactive } from "vue";
import { ElMessage } from "element-plus";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import { useModuleSection } from "@/hooks/useModuleSection";
import CodegenArtifactSection from "./sections/CodegenArtifactSection.vue";
import CodegenGateSection from "./sections/CodegenGateSection.vue";
import CodegenMetadataSection from "./sections/CodegenMetadataSection.vue";
import CodegenOverviewSection from "./sections/CodegenOverviewSection.vue";
import CodegenTemplateSection from "./sections/CodegenTemplateSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

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
  { key: "metadata", label: "元数据建模", description: "定义表、字段、字典、索引和权限输出模型。" },
  { key: "template", label: "模板基线", description: "统一后端、前端、SQL 与菜单模板骨架。" },
  { key: "gate", label: "交付门禁", description: "用强约束保障生成物符合企业级规范。" },
  { key: "artifact", label: "产物矩阵", description: "明确 SQL、Java、Vue 和权限骨架输出范围。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/codegen",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => [
  { label: "生成产物", value: "SQL + Java + Vue", note: "同一元数据一次输出" },
  { label: "工程约束", value: qualityGate.requireSqlArtifact ? "强校验" : "弱校验", note: "交付门禁" },
  { label: "路由风格", value: styleGuide.routeStyle, note: "资源式或模块式" },
  { label: "表单载体", value: styleGuide.formStyle, note: "抽屉 / 弹窗 / 独立页" }
]);

const codegenSpotlight = computed(() => [
  { label: "SQL 产物", value: templatePolicy.generateSql ? "同步输出" : "未输出" },
  { label: "权限骨架", value: templatePolicy.generatePermission ? "已生成" : "未生成" },
  { label: "租户列", value: templatePolicy.includeTenantColumn ? "自动带出" : "未带出" },
  { label: "交付门禁", value: qualityGate.requireSqlArtifact ? "强校验" : "弱校验" }
]);

const operatingBlueprint = [
  { title: "元数据建模", description: "表、字段、索引、字典、权限、菜单、SQL 输出都必须由统一模型驱动。" },
  { title: "模板约束", description: "生成的不只是 CRUD，还要带租户列、逻辑删除、乐观锁、审计字段和权限码。" },
  { title: "前后端对齐", description: "后端 DTO、Mapper、Controller 与前端 API、路由、页面结构同时生成。" },
  { title: "交付门禁", description: "没有 SQL 产物、权限骨架、菜单骨架和查询表单的模块不允许发布。" }
];

const outputMatrix = [
  "MySQL DDL / DML 种子脚本",
  "Entity / DTO / Mapper / Service / Controller",
  "Vue 路由 / API Service / 页面工作台",
  "权限码 / 菜单骨架 / 数据权限占位",
  "字段字典绑定 / 审计字段 / 乐观锁列"
];

const sectionComponentMap = {
  overview: CodegenOverviewSection,
  metadata: CodegenMetadataSection,
  template: CodegenTemplateSection,
  gate: CodegenGateSection,
  artifact: CodegenArtifactSection
} as const;

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? CodegenOverviewSection
);

function savePolicy(message: string) {
  ElMessage.success(message);
}
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="代码生成中心"
    title="代码生成中心"
    description="代码生成器要对企业交付结果负责，必须统一元数据建模、模板基线、SQL 产物、权限骨架和交付门禁。"
    :summary="summary"
  >
    <template #actions>
      <el-button @click="savePolicy('已刷新代码生成策略视图')">刷新策略视图</el-button>
      <el-button type="primary" @click="savePolicy('代码生成模板策略已保存')">保存工程策略</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>生成运行态</strong>
        <p>代码生成中心先看 SQL、权限骨架、租户字段和交付门禁，再进入模板基线和产物矩阵。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in codegenSpotlight" :key="item.label" class="module-workbench__spotlight-item">
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
            <h3>生成治理地图</h3>
            <p>代码生成要为企业交付负责，而不是只负责出一套脚手架。</p>
          </div>
        </template>
        <div class="blueprint-stack">
          <article v-for="item in operatingBlueprint" :key="item.title" class="blueprint-item">
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
        :template-policy="templatePolicy"
        :style-guide="styleGuide"
        :quality-gate="qualityGate"
        :output-matrix="outputMatrix"
        :save-policy="savePolicy"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="代码生成控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <el-button @click="savePolicy('已刷新代码生成策略视图')">刷新视图</el-button>
    </template>

    <component
      :is="currentSectionComponent"
      :template-policy="templatePolicy"
      :style-guide="styleGuide"
      :quality-gate="qualityGate"
      :output-matrix="outputMatrix"
      :save-policy="savePolicy"
    />
  </ControlSurface>
</template>
