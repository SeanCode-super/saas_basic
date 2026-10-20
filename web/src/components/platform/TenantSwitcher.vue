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
}

.tenant-switcher__icon {
  color: var(--sb-primary-color);
  font-size: 14px;
}

@media (max-width: 900px) {
  .tenant-switcher {
    flex-basis: 120px;
    width: 120px;
  }
}
</style>
