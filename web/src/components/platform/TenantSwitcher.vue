<script setup lang="ts">
import { OfficeBuilding } from "@element-plus/icons-vue";
import { storeToRefs } from "pinia";
import { useTenantStore } from "@/stores/modules/tenant";

const tenantStore = useTenantStore();
const { currentTenant, tenantOptions } = storeToRefs(tenantStore);
</script>

<template>
  <el-select
    class="tenant-switcher"
    :model-value="currentTenant?.code"
    placeholder="切换租户"
    aria-label="租户空间"
    @update:model-value="tenantStore.switchTenant"
  >
    <template #prefix>
      <el-icon class="tenant-switcher__icon"><OfficeBuilding /></el-icon>
    </template>
    <el-option
      v-for="tenant in tenantOptions"
      :key="tenant.code"
      :label="tenant.name"
      :value="tenant.code"
    />
  </el-select>
</template>

<style scoped lang="scss">
.tenant-switcher {
  flex: 0 0 160px;
  width: 160px;

  :deep(.el-select__wrapper) {
    height: 30px;
    padding: 0 10px;
    font-size: 12.5px;
    font-weight: 600;
    background: #f8fafc;
    border-radius: var(--sb-radius-sm, 6px);
    box-shadow: 0 0 0 1px var(--sb-border-subtle, #e2e8f0) inset;
    transition: all 0.12s ease;

    &:hover {
      background: #ffffff;
      box-shadow: 0 0 0 1px var(--sb-border-color, #cbd5e1) inset;
    }
  }
}

.tenant-switcher__icon {
  color: var(--sb-primary-color, #2563eb);
  font-size: 13px;
  margin-right: 2px;
}

@media (max-width: 900px) {
  .tenant-switcher {
    flex-basis: 120px;
    width: 120px;
  }
}
</style>
