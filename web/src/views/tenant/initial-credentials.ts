import { h } from "vue";
import { ElMessageBox } from "element-plus";
import { createTenant } from "@/api/modules/tenant";
import type { TenantCreateReceipt, TenantSavePayload } from "@/api/modules/tenant/types";

export async function showInitialCredentials(receipts: TenantCreateReceipt[]) {
  if (!receipts.length) return;
  const content = h("div", { style: { maxHeight: "55vh", overflowY: "auto" } }, [
    h("p", "初始密码仅在此处显示一次，请交付给对应的租户管理员。"),
    ...receipts.map(({ tenantName, tenantCode, initialAdmin }) =>
      h("section", { key: tenantCode }, [
        h("p", [h("strong", tenantName), " · ", tenantCode]),
        h("p", ["登录账号　", h("code", initialAdmin.username)]),
        h("p", ["初始密码　", h("code", { style: { userSelect: "all" } }, initialAdmin.temporaryPassword)])
      ])
    )
  ]);
  await ElMessageBox.alert(content, "租户管理员登录凭据", {
    confirmButtonText: "已收妥",
    closeOnClickModal: false,
    closeOnPressEscape: false,
    showClose: false
  });
}

export async function createTenantWithReceipt(payload: TenantSavePayload) {
  const receipt = await createTenant(payload);
  await showInitialCredentials([receipt]);
}
