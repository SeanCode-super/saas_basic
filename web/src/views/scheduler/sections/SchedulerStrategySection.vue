<script setup lang="ts">
import { ref } from "vue";
import { Plus, Setting, VideoPlay, Switch, RefreshRight } from "@element-plus/icons-vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";

const props = defineProps<{
  schedulerPolicy: {
    maxRetryCount: number;
    misfirePolicy: string;
    workerRoute: string;
    blockConcurrent: boolean;
  };
  loading?: boolean;
  jobs: Array<Record<string, unknown>>;
  jobForm: {
    code: string;
    name: string;
    jobType: string;
    executorId: number;
    handlerName: string;
    status: string;
    remark: string;
  };
  saveJob: () => void | Promise<void>;
  editJob: (row: any) => void;
  triggerJob: (row: any) => void | Promise<void>;
  toggleJobStatus: (row: any) => void | Promise<void>;
  savePolicy: () => void | Promise<void>;
}>();

const policyDialogVisible = ref(false);
const jobDialogVisible = ref(false);
const isEditingJob = ref(false);

const jobColumns: ProTableColumn[] = [
  { prop: "code", label: "任务编码", minWidth: 160 },
  { prop: "name", label: "任务名称", minWidth: 180 },
  { prop: "jobType", label: "类型", minWidth: 110 },
  { prop: "executorId", label: "执行器 ID", minWidth: 110 },
  { prop: "handlerName", label: "处理器名称", minWidth: 190 },
  { prop: "status", label: "状态", minWidth: 100 }
];

function openCreateJobModal() {
  isEditingJob.value = false;
  props.jobForm.code = "";
  props.jobForm.name = "";
  props.jobForm.jobType = "JAVA";
  props.jobForm.executorId = 1;
  props.jobForm.handlerName = "";
  props.jobForm.status = "ENABLED";
  props.jobForm.remark = "";
  jobDialogVisible.value = true;
}

function handleEditJob(row: any) {
  isEditingJob.value = true;
  props.editJob(row);
  jobDialogVisible.value = true;
}

async function handleSaveJob() {
  await props.saveJob();
  jobDialogVisible.value = false;
}

async function handleSavePolicy() {
  await props.savePolicy();
  policyDialogVisible.value = false;
}
</script>

<template>
  <div class="scheduler-strategy-container">
    <!-- Top Execution Policy Radar Bar -->
    <div class="strategy-radar-bar">
      <div class="radar-metrics">
        <div class="radar-item">
          <span class="radar-label">补偿重试</span>
          <strong>{{ schedulerPolicy.maxRetryCount }} 次</strong>
        </div>
        <div class="radar-divider" />
        <div class="radar-item">
          <span class="radar-label">Misfire 策略</span>
          <strong>{{ schedulerPolicy.misfirePolicy === 'FIRE_NOW' ? '立即补偿执行' : '跳过本次' }}</strong>
        </div>
        <div class="radar-divider" />
        <div class="radar-item">
          <span class="radar-label">执行器路由</span>
          <strong>{{ schedulerPolicy.workerRoute }}</strong>
        </div>
        <div class="radar-divider" />
        <div class="radar-item">
          <span class="radar-label">并发互斥锁</span>
          <el-tag size="small" :type="schedulerPolicy.blockConcurrent ? 'success' : 'info'">
            {{ schedulerPolicy.blockConcurrent ? '禁止同一任务并发' : '允许并发' }}
          </el-tag>
        </div>
      </div>

      <el-button type="primary" plain :icon="Setting" @click="policyDialogVisible = true">
        配置执行策略
      </el-button>
    </div>

    <!-- Main Job Management ProTable -->
    <ProTable
      title="调度任务台账"
      subtitle="集中管理后台异步任务、定时执行器、任务处理器及执行状态。"
      :loading="loading"
      :columns="jobColumns"
      :data="jobs"
      empty-title="当前暂无调度任务"
      empty-description="点击上方「新增任务」按钮创建第一条调度任务。"
    >
      <template #toolbar>
        <el-button type="primary" :icon="Plus" @click="openCreateJobModal">新增任务</el-button>
      </template>

      <template #jobType="{ row }">
        <el-tag size="small" type="info" effect="plain">{{ row.jobType }}</el-tag>
      </template>

      <template #handlerName="{ row }">
        <code>{{ row.handlerName }}</code>
      </template>

      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" />
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" size="small" :icon="Setting" @click="handleEditJob(row)">编辑</el-button>
        <el-button link type="success" size="small" :icon="VideoPlay" @click="triggerJob(row)">手动触发</el-button>
        <el-button
          link
          :type="row.status === 'ENABLED' ? 'danger' : 'success'"
          size="small"
          :icon="Switch"
          @click="toggleJobStatus(row)"
        >
          {{ row.status === "ENABLED" ? "停用" : "启用" }}
        </el-button>
      </template>
    </ProTable>

    <!-- Dialog 1: Execution Policy Modal -->
    <el-dialog
      v-model="policyDialogVisible"
      title="配置调度全局执行策略"
      width="540px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="最大自动补偿重试次数">
          <el-input-number v-model="schedulerPolicy.maxRetryCount" :min="0" :max="10" style="width: 100%" />
        </el-form-item>

        <el-form-item label="错过触发 (Misfire) 决策策略">
          <el-select v-model="schedulerPolicy.misfirePolicy" style="width: 100%">
            <el-option label="立即补偿执行 (FIRE_NOW)" value="FIRE_NOW" />
            <el-option label="跳过本次并等待下次触发 (DO_NOTHING)" value="DO_NOTHING" />
          </el-select>
        </el-form-item>

        <el-form-item label="执行器节点路由算法">
          <el-select v-model="schedulerPolicy.workerRoute" style="width: 100%">
            <el-option label="轮询分配 (ROUND_ROBIN)" value="ROUND_ROBIN" />
            <el-option label="一致性哈希 (CONSISTENT_HASH)" value="CONSISTENT_HASH" />
            <el-option label="故障转移 (FAILOVER)" value="FAILOVER" />
          </el-select>
        </el-form-item>

        <el-form-item label="并发控制开关">
          <div style="padding-top: 4px">
            <el-switch v-model="schedulerPolicy.blockConcurrent" active-text="同一任务禁止并发执行（排他阻塞）" />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="policyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePolicy">保存策略</el-button>
      </template>
    </el-dialog>

    <!-- Dialog 2: Job Create / Edit Modal -->
    <el-dialog
      v-model="jobDialogVisible"
      :title="isEditingJob ? '编辑调度任务' : '新建调度任务'"
      width="600px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <div class="form-row-2">
          <el-form-item label="任务编码" required>
            <el-input v-model="jobForm.code" placeholder="例如: tenantQuotaRefresh" />
          </el-form-item>
          <el-form-item label="任务名称" required>
            <el-input v-model="jobForm.name" placeholder="例如: 租户配额定时刷新" />
          </el-form-item>
        </div>

        <div class="form-row-2">
          <el-form-item label="任务类型" required>
            <el-select v-model="jobForm.jobType" style="width: 100%">
              <el-option label="Java 处理器 (JAVA)" value="JAVA" />
              <el-option label="Shell 脚本 (SHELL)" value="SHELL" />
              <el-option label="HTTP 接口调用 (HTTP)" value="HTTP" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行器 ID" required>
            <el-input-number v-model="jobForm.executorId" :min="1" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="处理器名称 (Handler Name)" required>
          <el-input v-model="jobForm.handlerName" placeholder="例如: tenantQuotaRefreshHandler" />
        </el-form-item>

        <el-form-item label="任务状态">
          <el-select v-model="jobForm.status" style="width: 100%">
            <el-option label="启用 (ENABLED)" value="ENABLED" />
            <el-option label="停用 (DISABLED)" value="DISABLED" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注说明">
          <el-input v-model="jobForm.remark" type="textarea" :rows="2" placeholder="任务调度业务说明与执行周期备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="jobDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveJob">保存任务</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.scheduler-strategy-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Radar Bar */
.strategy-radar-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.02);
}

.radar-metrics {
  display: flex;
  align-items: center;
  gap: 24px;

  .radar-item {
    display: flex;
    flex-direction: column;
    gap: 3px;

    .radar-label {
      font-size: 12px;
      color: #64748b;
    }

    strong {
      font-size: 14px;
      color: #0f172a;
    }
  }

  .radar-divider {
    width: 1px;
    height: 28px;
    background: #e2e8f0;
  }
}

.job-table-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .strategy-radar-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .radar-metrics {
    flex-wrap: wrap;
    gap: 16px;
  }

  .radar-divider {
    display: none;
  }
}
</style>
