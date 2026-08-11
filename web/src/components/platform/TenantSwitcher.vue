<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useTenantStore } from "@/stores/modules/tenant";

const tenantStore = useTenantStore();
const { currentTenant, tenantOptions } = storeToRefs(tenantStore);
</script>

<template>
  <el-select
    class="tenant-switcher"
    :model-value="currentTenant?.code"
    placeholder="租户"
    aria-label="租户空间"
    @update:model-value="tenantStore.switchTenant"
  >
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
  flex: 0 0 150px;
  width: 150px;
}

@media (max-width: 900px) {
  .tenant-switcher {
    flex-basis: 110px;
    width: 110px;
  }
}
</style>
