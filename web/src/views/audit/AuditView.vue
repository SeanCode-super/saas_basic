<script setup lang="ts">
import { computed, reactive } from "vue";
import { ElMessage } from "element-plus";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import { useModuleSection } from "@/hooks/useModuleSection";
import AuditBlacklistSection from "./sections/AuditBlacklistSection.vue";
import AuditCatalogSection from "./sections/AuditCatalogSection.vue";
import AuditOverviewSection from "./sections/AuditOverviewSection.vue";
import AuditRiskSection from "./sections/AuditRiskSection.vue";
import AuditTrailSection from "./sections/AuditTrailSection.vue";

const props = withDefaults(
  defineProps<{
    section?: string;
  }>(),
  {
    section: ""
  }
);

const auditPolicy = reactive({
  recordPermissionChange: true,
  recordConfigChange: true,
  recordDataExport: true,
  recordFileDownload: true,
  retentionDays: 365
});
const riskPolicy = reactive({
  abnormalLoginAlert: true,
  privilegeEscalationAlert: true,
  exportBurstAlert: true,
  blacklistEnabled: true
});

const sectionItems = [
  { key: "overview", label: "总览", description: "查看审计、风险和黑名单治理全貌。" },
  { key: "trail", label: "审计留痕", description: "统一登录、配置、权限和导出留痕目录。" },
  { key: "risk", label: "风险识别", description: "配置异常登录、提权和批量导出预警规则。" },
  { key: "blacklist", label: "黑名单", description: "统一账号、设备、IP 和租户禁用策略。" },
  { key: "catalog", label: "高危目录", description: "定义必须被审计和预警的动作集合。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/audit",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => [
  { label: "留痕范围", value: "登录 / 操作 / 导出 / 下载", note: "统一审计事件目录" },
  { label: "审计保留", value: `${auditPolicy.retentionDays} 天`, note: "日志保留周期" },
  { label: "风险规则", value: riskPolicy.blacklistEnabled ? "已启用" : "未启用", note: "黑名单与预警规则" },
  { label: "高危监听", value: riskPolicy.privilegeEscalationAlert ? "已开启" : "未开启", note: "权限提升预警" }
]);

const auditSpotlight = computed(() => [
  { label: "权限留痕", value: auditPolicy.recordPermissionChange ? "已开启" : "未开启" },
  { label: "配置留痕", value: auditPolicy.recordConfigChange ? "已开启" : "未开启" },
  { label: "异常登录", value: riskPolicy.abnormalLoginAlert ? "已预警" : "未预警" },
  { label: "黑名单", value: riskPolicy.blacklistEnabled ? "已启用" : "未启用" }
]);

const blueprint = [
  { title: "登录审计", description: "登录成功、失败、锁定、会话失效和异常来源都要统一记录。" },
  { title: "操作审计", description: "权限变更、配置变更、数据导出、文件下载等高风险动作必须留痕。" },
  { title: "风险识别", description: "异常登录、权限提升、批量导出等行为要触发规则和预警。" },
  { title: "黑名单治理", description: "账号、IP、设备和租户级禁用策略应该纳入同一控制面。" }
];

const sectionComponentMap = {
  overview: AuditOverviewSection,
  trail: AuditTrailSection,
  risk: AuditRiskSection,
  blacklist: AuditBlacklistSection,
  catalog: AuditCatalogSection
} as const;

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? AuditOverviewSection
);

function savePolicy(message: string) {
  ElMessage.success(message);
}
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="审计中心"
    title="审计中心"
    description="登录日志、操作日志、审计事件、风险规则和黑名单都属于企业底座的核心范围，必须统一治理。"
    :summary="summary"
  >
    <template #actions>
      <BaseRefreshButton @click="savePolicy('已刷新审计视图')" />
      <el-button type="primary" @click="savePolicy('审计与风险策略已保存')">保存审计策略</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>审计运行态</strong>
        <p>审计中心先看权限留痕、配置留痕、异常登录和黑名单状态，再进入风险规则和高危目录。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in auditSpotlight" :key="item.label" class="module-workbench__spotlight-item">
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
            <h3>审计治理地图</h3>
            <p>审计中心负责把留痕、风险识别、黑名单和预警统一起来。</p>
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
        :audit-policy="auditPolicy"
        :risk-policy="riskPolicy"
        :save-policy="savePolicy"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="审计控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton @click="savePolicy('已刷新审计视图')" />
    </template>

    <component
      :is="currentSectionComponent"
      :audit-policy="auditPolicy"
      :risk-policy="riskPolicy"
      :save-policy="savePolicy"
    />
  </ControlSurface>
</template>
