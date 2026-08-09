<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
defineProps<{
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

const jobColumns: ProTableColumn[] = [
  { prop: "code", label: "任务编码", minWidth: 160 },
  { prop: "name", label: "任务名称", minWidth: 180 },
  { prop: "jobType", label: "类型", minWidth: 120 },
  { prop: "handlerName", label: "处理器", minWidth: 180 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>执行策略</h3>
        <p>先定义任务如何执行、如何补偿，再考虑任务本身。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="最大重试次数">
        <el-input-number v-model="schedulerPolicy.maxRetryCount" :min="0" :max="10" style="width: 100%" />
      </el-form-item>
      <el-form-item label="Misfire 策略">
        <el-select v-model="schedulerPolicy.misfirePolicy">
          <el-option label="立即补偿执行" value="FIRE_NOW" />
          <el-option label="跳过本次" value="DO_NOTHING" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行器路由">
        <el-select v-model="schedulerPolicy.workerRoute">
          <el-option label="轮询" value="ROUND_ROBIN" />
          <el-option label="一致性哈希" value="CONSISTENT_HASH" />
          <el-option label="故障转移" value="FAILOVER" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-switch v-model="schedulerPolicy.blockConcurrent" active-text="同一任务禁止并发执行" />
      </el-form-item>
      <el-button type="primary" @click="savePolicy">保存执行策略</el-button>

      <el-divider />
      <el-form-item label="任务编码">
        <el-input v-model="jobForm.code" />
      </el-form-item>
      <el-form-item label="任务名称">
        <el-input v-model="jobForm.name" />
      </el-form-item>
      <el-form-item label="任务类型">
        <el-input v-model="jobForm.jobType" />
      </el-form-item>
      <el-form-item label="执行器 ID">
        <el-input-number v-model="jobForm.executorId" :min="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="处理器">
        <el-input v-model="jobForm.handlerName" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="jobForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="saveJob">保存任务</el-button>
    </el-form>

    <ProTable
      title="任务列表"
      subtitle="真实调度任务。"
      :loading="loading"
      :columns="jobColumns"
      :data="jobs"
      compact
      empty-title="当前没有任务"
      empty-description="保存上方表单即可创建第一条任务。"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="editJob(row)">编辑</el-button>
        <el-button link @click="triggerJob(row)">手动触发</el-button>
        <el-button link @click="toggleJobStatus(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>
  </el-card>
</template>
