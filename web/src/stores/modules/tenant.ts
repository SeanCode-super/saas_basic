import { ref } from "vue";
import { defineStore } from "pinia";
import type { AuthCurrentUser } from "@/api/modules/auth/types";
import { extractSubdomainTenant } from "@/utils/portal-context";
import { fetchTenantPage } from "@/api/modules/tenant/service";

export interface TenantSnapshot {
  id: number;
  code: string;
  name: string;
  plan: string;
  isolationMode: string;
}

const PLATFORM_DEFAULT: TenantSnapshot = {
  id: 1,
  code: "platform",
  name: "平台空间",
  plan: "SUPREME",
  isolationMode: "PLATFORM"
};

export const useTenantStore = defineStore("tenant", () => {
  const currentTenant = ref<TenantSnapshot | null>(null);
  const tenantOptions = ref<TenantSnapshot[]>([]);

  function syncWithUser(user: AuthCurrentUser | null) {
    if (!user) {
      bootstrap();
      return;
    }
    const isPlatform = !user.tenantCode || user.tenantCode.toLowerCase() === "platform";
    const name = user.tenantName || (isPlatform ? "平台空间" : user.tenantCode);
    const snapshot: TenantSnapshot = {
      id: user.tenantId,
      code: user.tenantCode,
      name,
      plan: isPlatform ? "SUPREME" : "ENTERPRISE",
      isolationMode: isPlatform ? "PLATFORM" : "SHARED_SCHEMA"
    };

    currentTenant.value = snapshot;

    if (!isPlatform) {
      tenantOptions.value = [snapshot];
    } else {
      if (!tenantOptions.value.some((t) => t.code === snapshot.code)) {
        tenantOptions.value = [snapshot, ...tenantOptions.value];
      }
      void loadPlatformTenants();
    }
  }

  async function loadPlatformTenants() {
    try {
      const rows = await fetchTenantPage();
      if (Array.isArray(rows) && rows.length > 0) {
        const mapped: TenantSnapshot[] = [
          PLATFORM_DEFAULT,
          ...rows
            .filter((r) => r.tenantCode.toLowerCase() !== "platform")
            .map((r) => ({
              id: r.id,
              code: r.tenantCode,
              name: r.tenantName,
              plan: r.packageName || "ENTERPRISE",
              isolationMode: r.isolationMode || "SHARED_SCHEMA"
            }))
        ];
        tenantOptions.value = mapped;
      }
    } catch {
      // Ignore if user doesn't have tenant:query permission
    }
  }

  function bootstrap(user?: AuthCurrentUser | null) {
    if (user) {
      syncWithUser(user);
      return;
    }

    const subdomain = extractSubdomainTenant();
    if (subdomain && subdomain !== "platform") {
      const tenantSnap: TenantSnapshot = {
        id: 0,
        code: subdomain,
        name: subdomain,
        plan: "ENTERPRISE",
        isolationMode: "SHARED_SCHEMA"
      };
      currentTenant.value = tenantSnap;
      tenantOptions.value = [tenantSnap];
      return;
    }

    currentTenant.value = { ...PLATFORM_DEFAULT };
    tenantOptions.value = [{ ...PLATFORM_DEFAULT }];
  }

  function switchTenant(code: string) {
    const next = tenantOptions.value.find((tenant) => tenant.code === code);
    if (next) {
      currentTenant.value = next;
    }
  }

  function setCurrentTenant(snapshot: TenantSnapshot) {
    currentTenant.value = snapshot;
    if (!tenantOptions.value.some((t) => t.id === snapshot.id)) {
      tenantOptions.value.push(snapshot);
    }
  }

  return {
    currentTenant,
    tenantOptions,
    bootstrap,
    syncWithUser,
    switchTenant,
    setCurrentTenant,
    loadPlatformTenants
  };
});
