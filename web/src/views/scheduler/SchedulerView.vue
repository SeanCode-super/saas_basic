<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createSchedulerAlarm,
  createSchedulerJob,
  fetchSchedulerAlarms,
  fetchSchedulerJobs,
  fetchSchedulerLogs,
  retrySchedulerJob,
  triggerSchedulerJob,
  updateSchedulerAlarm,
  updateSchedulerJob,
  updateSchedulerJobStatus
} from "@/api/modules/scheduler";
import type { SchedulerJobAlarmRow, SchedulerJobLogRow, SchedulerJobRow } from "@/api/modules/scheduler";
import BaseCard from "@/components/base/BaseCard.vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import BaseRefreshButton from "@/components/base/BaseRefreshButton.vue";
import ModuleSectionNav from "@/components/platform/ModuleSectionNav.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import { useModuleSection } from "@/hooks/useModuleSection";
import { useAuthStore } from "@/stores/modules/auth";
import SchedulerAlarmSection from "./sections/SchedulerAlarmSection.vue";
import SchedulerLogSection from "./sections/SchedulerLogSection.vue";
import SchedulerOverviewSection from "./sections/SchedulerOverviewSection.vue";
import SchedulerPipelineSection from "./sections/SchedulerPipelineSection.vue";
import SchedulerStrategySection from "./sections/SchedulerStrategySection.vue";

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
const jobs = ref<SchedulerJobRow[]>([]);
const logs = ref<SchedulerJobLogRow[]>([]);
const alarms = ref<SchedulerJobAlarmRow[]>([]);
const editingJobId = ref<number | null>(null);
const editingAlarmId = ref<number | null>(null);
const schedulerPolicy = reactive({
  maxRetryCount: 3,
  misfirePolicy: "FIRE_NOW",
  workerRoute: "ROUND_ROBIN",
  logRetentionDays: 30,
  blockConcurrent: true
});
const alarmPolicy = reactive({
  jobId: 1,
  alarmCode: "tenantQuotaRefresh_failure",
  alarmName: "租户配额刷新失败告警",
  onFailure: true,
  onTimeout: true,
  onMisfire: true,
  notifyChannel: "WEBHOOK",
  silenceMinutes: 10,
  receiverJson: "{\"webhook\":\"https://ops.example.com/hooks/scheduler\"}",
  templateCode: "scheduler_failure",
  status: "ENABLED",
  remark: ""
});
const jobForm = reactive({
  code: "tenantQuotaRefresh",
  name: "租户配额刷新",
  jobType: "JAVA",
  executorId: 1,
  handlerName: "tenantQuotaRefreshHandler",
  status: "ENABLED",
  remark: ""
});

const sectionItems = [
  { key: "overview", label: "总览", description: "查看调度底座的执行、告警和日志治理总貌。" },
  { key: "strategy", label: "执行策略", description: "统一重试、Misfire、路由和并发控制。" },
  { key: "pipeline", label: "执行链路", description: "明确任务定义、触发、执行和归档闭环。" },
  { key: "alarm", label: "告警基线", description: "统一失败、超时、漏触发的告警出口。" },
  { key: "log", label: "日志治理", description: "定义日志保留、查询维度和审计关联。" }
];

const { activeSection, updateSection } = useModuleSection(sectionItems, {
  mode: "path",
  basePath: "/scheduler",
  fallback: "overview"
});

const currentSection = computed(() => props.section || activeSection.value);
const sectionLocked = computed(() => Boolean(props.section));
const isOverviewSection = computed(() => currentSection.value === "overview");
const activeSectionMeta = computed(
  () => sectionItems.find((item) => item.key === currentSection.value) ?? sectionItems[0]
);

const summary = computed(() => [
  { label: "任务数量", value: jobs.value.length, note: "真实调度任务数" },
  { label: "执行日志", value: logs.value.length, note: "真实执行日志数" },
  { label: "最大重试", value: schedulerPolicy.maxRetryCount, note: "失败任务默认补偿次数" },
  { label: "告警渠道", value: alarmPolicy.notifyChannel, note: "默认通知出口" }
]);

const schedulerSpotlight = computed(() => [
  { label: "并发阻塞", value: schedulerPolicy.blockConcurrent ? "已开启" : "未开启" },
  { label: "Misfire", value: schedulerPolicy.misfirePolicy },
  { label: "告警触发", value: alarmPolicy.onFailure ? "失败即告警" : "手动确认" },
  { label: "超时检测", value: alarmPolicy.onTimeout ? "已开启" : "未开启" }
]);

const blueprint = [
  { title: "执行策略", description: "统一定义重试、并发、Misfire 和路由策略，不让任务系统各写一套。" },
  { title: "执行链路", description: "任务定义、触发器、执行器、日志和补偿要形成闭环。" },
  { title: "告警基线", description: "失败、超时、漏触发、重复执行要统一进入同一条告警链路。" },
  { title: "日志治理", description: "任务日志的保留天数、查询维度和审计关联都要有统一标准。" }
];

const sectionComponentMap = {
  overview: SchedulerOverviewSection,
  strategy: SchedulerStrategySection,
  pipeline: SchedulerPipelineSection,
  alarm: SchedulerAlarmSection,
  log: SchedulerLogSection
} as const;

const currentSectionComponent = computed(
  () => sectionComponentMap[currentSection.value as keyof typeof sectionComponentMap] ?? SchedulerOverviewSection
);

function getTenantId() {
  return authStore.currentUser?.tenantId ?? 1;
}

function hydrateJobPolicy() {
  const primaryJob = jobs.value[0];
  if (primaryJob) {
    jobForm.code = primaryJob.code;
    jobForm.name = primaryJob.name;
    jobForm.jobType = primaryJob.jobType;
    jobForm.executorId = primaryJob.executorId;
    jobForm.handlerName = primaryJob.handlerName;
    jobForm.status = primaryJob.status;
    jobForm.remark = primaryJob.remark || "";
    editingJobId.value = primaryJob.id;
  }
  const primaryAlarm = alarms.value[0];
  if (primaryAlarm) {
    editingAlarmId.value = primaryAlarm.id;
    alarmPolicy.jobId = primaryAlarm.jobId;
    alarmPolicy.alarmCode = primaryAlarm.alarmCode;
    alarmPolicy.alarmName = primaryAlarm.alarmName;
    alarmPolicy.notifyChannel = primaryAlarm.channelType;
    alarmPolicy.onFailure = primaryAlarm.triggerRule === "ON_FAILURE";
    alarmPolicy.onTimeout = primaryAlarm.triggerRule === "ON_TIMEOUT";
    alarmPolicy.onMisfire = primaryAlarm.triggerRule === "ON_MISFIRE";
    alarmPolicy.silenceMinutes = primaryAlarm.silenceMinutes;
    alarmPolicy.receiverJson = primaryAlarm.receiverJson || "";
    alarmPolicy.templateCode = primaryAlarm.templateCode || "";
    alarmPolicy.status = primaryAlarm.status;
    alarmPolicy.remark = primaryAlarm.remark || "";
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [nextJobs, nextLogs, nextAlarms] = await Promise.all([
      fetchSchedulerJobs(),
      fetchSchedulerLogs(),
      fetchSchedulerAlarms()
    ]);
    jobs.value = nextJobs;
    logs.value = nextLogs;
    alarms.value = nextAlarms;
    hydrateJobPolicy();
  } finally {
    loading.value = false;
  }
}

async function saveJob() {
  try {
    const payload = {
      tenantId: getTenantId(),
      code: jobForm.code,
      name: jobForm.name,
      jobType: jobForm.jobType,
      executorId: jobForm.executorId,
      handlerName: jobForm.handlerName,
      status: jobForm.status,
      remark: jobForm.remark
    };
    if (editingJobId.value) {
      await updateSchedulerJob(editingJobId.value, payload);
    } else {
      await createSchedulerJob(payload);
    }
    await loadData();
    ElMessage.success("任务已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存任务失败");
  }
}

function editJob(row: SchedulerJobRow) {
  editingJobId.value = row.id;
  jobForm.code = row.code;
  jobForm.name = row.name;
  jobForm.jobType = row.jobType;
  jobForm.executorId = row.executorId;
  jobForm.handlerName = row.handlerName;
  jobForm.status = row.status;
  jobForm.remark = row.remark || "";
}

async function triggerJob(row: SchedulerJobRow) {
  try {
    await triggerSchedulerJob(row.id, {
      tenantId: getTenantId(),
      remark: `手动触发：${row.name}`
    });
    await loadData();
    ElMessage.success("任务已手动触发");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "触发任务失败");
  }
}

async function retryJob(log: SchedulerJobLogRow) {
  try {
    await retrySchedulerJob(log.jobId, {
      tenantId: getTenantId(),
      remark: `基于执行号 ${log.executionNo} 发起手动重试`
    });
    await loadData();
    ElMessage.success("任务已生成重试执行记录");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "重试任务失败");
  }
}

async function toggleJobStatus(row: SchedulerJobRow) {
  try {
    await updateSchedulerJobStatus(row.id, {
      tenantId: getTenantId(),
      status: row.status === "ENABLED" ? "DISABLED" : "ENABLED"
    });
    await loadData();
    ElMessage.success("任务状态已更新");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "更新任务状态失败");
  }
}

function editAlarm(row: SchedulerJobAlarmRow) {
  editingAlarmId.value = row.id;
  alarmPolicy.jobId = row.jobId;
  alarmPolicy.alarmCode = row.alarmCode;
  alarmPolicy.alarmName = row.alarmName;
  alarmPolicy.notifyChannel = row.channelType;
  alarmPolicy.onFailure = row.triggerRule === "ON_FAILURE";
  alarmPolicy.onTimeout = row.triggerRule === "ON_TIMEOUT";
  alarmPolicy.onMisfire = row.triggerRule === "ON_MISFIRE";
  alarmPolicy.silenceMinutes = row.silenceMinutes;
  alarmPolicy.receiverJson = row.receiverJson || "";
  alarmPolicy.templateCode = row.templateCode || "";
  alarmPolicy.status = row.status;
  alarmPolicy.remark = row.remark || "";
}

async function savePolicy() {
  try {
    const triggerRule = alarmPolicy.onFailure
      ? "ON_FAILURE"
      : alarmPolicy.onTimeout
        ? "ON_TIMEOUT"
        : "ON_MISFIRE";
    const payload = {
      tenantId: getTenantId(),
      jobId: alarmPolicy.jobId,
      alarmCode: alarmPolicy.alarmCode,
      alarmName: alarmPolicy.alarmName,
      channelType: alarmPolicy.notifyChannel,
      triggerRule,
      receiverJson: alarmPolicy.receiverJson,
      templateCode: alarmPolicy.templateCode,
      silenceMinutes: alarmPolicy.silenceMinutes,
      status: alarmPolicy.status,
      remark: alarmPolicy.remark
    };
    if (editingAlarmId.value) {
      await updateSchedulerAlarm(editingAlarmId.value, payload);
    } else {
      await createSchedulerAlarm(payload);
    }
    await loadData();
    ElMessage.success("调度告警策略已保存");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? "保存调度告警策略失败");
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <ModuleWorkbench
    v-if="isOverviewSection"
    eyebrow="调度中心"
    title="调度中心"
    description="调度底座的重点是执行策略、路由规则、失败补偿、统一告警和日志留存，而不是孤立的任务表。"
    :summary="summary"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
      <el-button type="primary" @click="saveJob">保存任务</el-button>
    </template>

    <template #spotlight>
      <div class="module-workbench__spotlight">
        <strong>调度运行态</strong>
        <p>调度中心先看并发控制、Misfire、失败告警和超时检测，再进入执行策略、链路和日志治理。</p>
        <div class="module-workbench__spotlight-grid">
          <article v-for="item in schedulerSpotlight" :key="item.label" class="module-workbench__spotlight-item">
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
            <h3>调度治理地图</h3>
            <p>调度要按执行链路和告警链路组织，而不是按表结构组织。</p>
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
        :scheduler-policy="schedulerPolicy"
        :alarm-policy="alarmPolicy"
        :save-policy="savePolicy"
        :loading="loading"
        :jobs="jobs"
        :logs="logs"
        :alarms="alarms"
        :job-form="jobForm"
        :save-job="saveJob"
        :edit-job="editJob"
        :trigger-job="triggerJob"
        :retry-job="retryJob"
        :toggle-job-status="toggleJobStatus"
        :edit-alarm="editAlarm"
      />
    </div>
  </ModuleWorkbench>

  <ControlSurface
    v-else
    eyebrow="调度控制面"
    :title="activeSectionMeta.label"
    :description="activeSectionMeta.description"
  >
    <template #actions>
      <BaseRefreshButton :loading="loading" @click="loadData" />
    </template>

    <component
      :is="currentSectionComponent"
      :scheduler-policy="schedulerPolicy"
      :alarm-policy="alarmPolicy"
      :save-policy="savePolicy"
      :loading="loading"
      :jobs="jobs"
      :logs="logs"
      :alarms="alarms"
      :job-form="jobForm"
      :save-job="saveJob"
      :edit-job="editJob"
      :trigger-job="triggerJob"
      :retry-job="retryJob"
      :toggle-job-status="toggleJobStatus"
      :edit-alarm="editAlarm"
    />
  </ControlSurface>
</template>
