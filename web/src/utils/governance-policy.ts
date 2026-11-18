import { fetchSystemConfigs, upsertSystemConfigByKey } from "@/api/modules/system";

/**
 * Load a JSON-serialized governance policy object from sys_config into a reactive target.
 */
export async function loadGovernancePolicy<T extends Record<string, unknown>>(
  group: string,
  key: string,
  target: T
): Promise<void> {
  try {
    const configs = await fetchSystemConfigs();
    const matched = configs.find(
      (item) =>
        item.status === "ENABLED" &&
        item.key === key &&
        (!group || item.group === group)
    );
    if (!matched?.value) {
      return;
    }
    const parsed = JSON.parse(matched.value);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      Object.keys(target).forEach((prop) => {
        if (Object.prototype.hasOwnProperty.call(parsed, prop)) {
          (target as Record<string, unknown>)[prop] = parsed[prop];
        }
      });
    }
  } catch {
    // Keep current defaults cleanly if config is absent or unparseable
  }
}

/**
 * Persist a governance policy object to sys_config via upsert-by-key.
 */
export async function saveGovernancePolicy(
  group: string,
  key: string,
  name: string,
  value: unknown,
  tenantId = 1
): Promise<void> {
  await upsertSystemConfigByKey({
    tenantId,
    group,
    key,
    name,
    value: typeof value === "string" ? value : JSON.stringify(value),
    valueType: "JSON",
    status: "ENABLED",
    remark: `${name}持久化配置`
  });
}
