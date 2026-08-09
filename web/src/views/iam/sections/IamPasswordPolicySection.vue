<script setup lang="ts">
import type { IamPasswordPolicyRow } from "@/api/modules/iam";
import BaseCard from "@/components/base/BaseCard.vue";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";

defineProps<{
  passwordPolicyColumns: ProTableColumn[];
  passwordPolicies: IamPasswordPolicyRow[];
  loading: boolean;
  canWrite: boolean;
  summarizePasswordRules: (row: IamPasswordPolicyRow) => string;
  openPasswordPolicyCreate: () => void;
  openPasswordPolicyEdit: (row: IamPasswordPolicyRow) => void;
  togglePasswordPolicy: (row: IamPasswordPolicyRow) => void;
  batchEnablePasswordPolicies: (rows: IamPasswordPolicyRow[]) => void | Promise<void>;
  batchDisablePasswordPolicies: (rows: IamPasswordPolicyRow[]) => void | Promise<void>;
}>();

function asPasswordPolicyRows(rows: object[]) {
  return rows as IamPasswordPolicyRow[];
}
</script>

<template>
  <div class="governance-grid">
    <ProTable
      :columns="passwordPolicyColumns"
      :data="passwordPolicies"
      :loading="loading"
      selectable
      title="密码策略中心"
      subtitle="密码复杂度、过期周期、历史复用和临时密码窗口都应归入统一生命周期控制面。"
    >
      <template #toolbar>
        <el-button v-if="canWrite" type="primary" @click="openPasswordPolicyCreate">新增密码策略</el-button>
      </template>
      <template #bulkActions="{ rows }">
        <el-button
          v-if="canWrite"
          type="primary"
          plain
          @click="batchEnablePasswordPolicies(asPasswordPolicyRows(rows))"
        >
          批量启用
        </el-button>
        <el-button
          v-if="canWrite"
          plain
          @click="batchDisablePasswordPolicies(asPasswordPolicyRows(rows))"
        >
          批量停用
        </el-button>
      </template>
      <template #status="{ row }">
        <BaseStatusTag :status="String(row.status)" :type="row.status === 'ENABLED' ? 'success' : 'danger'" />
      </template>
      <template #actions="{ row }">
        <el-button v-if="canWrite" link type="primary" @click="openPasswordPolicyEdit(row)">编辑</el-button>
        <el-button v-if="canWrite" link @click="togglePasswordPolicy(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
      </template>
    </ProTable>

    <BaseCard>
      <template #header>
        <div class="header-stack">
          <h3>密码生命周期控制面</h3>
          <p>密码策略不止复杂度，还需要控制历史复用、有效期、临时密码窗口和首次登录改密。</p>
        </div>
      </template>
      <div class="policy-stack">
        <article v-for="item in passwordPolicies" :key="item.id" class="policy-row">
          <div>
            <strong>{{ item.policyName }}</strong>
            <p>{{ summarizePasswordRules(item) }}</p>
          </div>
          <div class="policy-meta">
            <span>{{ item.passwordExpireDays }} 天过期</span>
            <code>历史 {{ item.passwordHistoryLimit }} 次 / 临时密码 {{ item.tempPasswordExpireHours }} 小时</code>
          </div>
        </article>
      </div>
    </BaseCard>
  </div>
</template>
