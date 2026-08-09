<script setup lang="ts">
import type { IamLoginPolicyRow } from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  loginPolicyColumns: ProTableColumn[];
  loginPolicies: IamLoginPolicyRow[];
  loading: boolean;
  canWrite: boolean;
  summarizeLoginChannels: (row: IamLoginPolicyRow) => string;
  openLoginPolicyCreate: () => void;
  openLoginPolicyEdit: (row: IamLoginPolicyRow) => void;
  toggleLoginPolicy: (row: IamLoginPolicyRow) => void;
  batchEnableLoginPolicies: (rows: IamLoginPolicyRow[]) => void | Promise<void>;
  batchDisableLoginPolicies: (rows: IamLoginPolicyRow[]) => void | Promise<void>;
}>();

function asLoginPolicyRows(rows: object[]) {
  return rows as IamLoginPolicyRow[];
}
</script>

<template>
  <div class="governance-grid">
    <ProTable
      :columns="loginPolicyColumns"
      :data="loginPolicies"
      :loading="loading"
      selectable
      title="登录策略中心"
      subtitle="把失败阈值、设备信任、会话时长和 MFA 统一纳入登录入口治理。"
    >
      <template #toolbar>
        <el-button v-if="canWrite" type="primary" @click="openLoginPolicyCreate">新增登录策略</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button
          v-if="canWrite"
          type="primary"
          plain
          @click="batchEnableLoginPolicies(asLoginPolicyRows(rows))"
        >
          批量启用
        </el-button>
        <el-button v-if="canWrite" plain @click="batchDisableLoginPolicies(asLoginPolicyRows(rows))">批量停用</el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-if="canWrite" link type="primary" @click="openLoginPolicyEdit(row)">编辑</el-button>
        <el-button v-if="canWrite" link @click="toggleLoginPolicy(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>

    <BaseCard>
      <template #header>
        <div class="header-stack">
          <h3>登录控制面</h3>
          <p>登录策略负责入口安全，不只是“能不能登录”，还包括失败阈值、锁定时长、设备信任和 MFA。</p>
        </div>
      </template>
      <div class="policy-stack">
        <article v-for="item in loginPolicies" :key="item.id" class="policy-row">
          <div>
            <strong>{{ item.policyName }}</strong>
            <p>{{ summarizeLoginChannels(item) }}，会话 {{ item.sessionTimeoutMinutes }} 分钟，失败 {{ item.maxFailedCount }} 次锁定</p>
          </div>
          <div class="policy-meta">
            <span>{{ item.forceMfa ? "强制 MFA" : "按需 MFA" }}</span>
            <code>{{ item.deviceTrustDays }} 天设备信任</code>
          </div>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
