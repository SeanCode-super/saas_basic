import { ref } from "vue";
import { defineStore } from "pinia";

export interface TenantSnapshot {
  id: number;
  code: string;
  name: string;
  plan: string;
  isolationMode: string;
}

export const useTenantStore = defineStore("tenant", () => {
  const currentTenant = ref<TenantSnapshot | null>(null);
  const tenantOptions = ref<TenantSnapshot[]>([]);

  function bootstrap() {
    tenantOptions.value = [
      {
        id: 1,
        code: "platform",
        name: "平台空间",
        plan: "SUPREME",
        isolationMode: "PLATFORM"
      },
      {
        id: 10001,
        code: "nebula",
        name: "星云制造",
        plan: "ENTERPRISE",
        isolationMode: "SHARED_SCHEMA"
      }
    ];
    currentTenant.value = tenantOptions.value[0];
  }

  function switchTenant(code: string) {
    const next = tenantOptions.value.find((tenant) => tenant.code === code);
    if (next) {
      currentTenant.value = next;
    }
  }

  return {
    currentTenant,
    tenantOptions,
    bootstrap,
    switchTenant
  };
});
