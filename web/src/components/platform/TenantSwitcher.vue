<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useTenantStore } from "@/stores/modules/tenant";

const tenantStore = useTenantStore();
const { currentTenant, tenantOptions } = storeToRefs(tenantStore);

function handleChange(code: string) {
  tenantStore.switchTenant(code);
}
</script>

<template>
  <div class="tenant-switcher">
    <div class="tenant-switcher__copy">
      <span class="tenant-switcher__label">租户空间</span>
      <strong>{{ currentTenant?.name ?? "未选择租户" }}</strong>
    </div>
    <el-select
      :model-value="currentTenant?.code"
      placeholder="请选择租户"
      style="width: 220px"
      @update:model-value="handleChange"
    >
      <el-option
        v-for="tenant in tenantOptions"
        :key="tenant.code"
        :label="tenant.name"
        :value="tenant.code"
      />
    </el-select>
  </div>
</template>

<style scoped lang="scss">
.tenant-switcher {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border-radius: 18px;
  background: rgb(15 39 64 / 0.04);
}

.tenant-switcher__label {
  color: var(--sb-text-secondary);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.tenant-switcher__copy {
  display: grid;
  gap: 4px;

  strong {
    font-size: 14px;
  }
}
</style>
