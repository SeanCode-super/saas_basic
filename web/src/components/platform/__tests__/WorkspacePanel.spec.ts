import { describe, it, expect } from "vitest";
import { createSSRApp, h } from "vue";
import { renderToString } from "@vue/server-renderer";
import WorkspacePanel from "../WorkspacePanel.vue";

describe("WorkspacePanel", () => {
  it("renders title, subtitle, back button, body and footer slots when visible", async () => {
    const app = createSSRApp({
      render() {
        return h(
          WorkspacePanel,
          {
            modelValue: true,
            title: "新增员工",
            subtitle: "入职建档与账号开通",
            destroyOnClose: true
          },
          {
            default: () => h("div", { class: "test-body" }, "Form Content"),
            footer: () => h("button", { class: "test-save" }, "保存")
          }
        );
      }
    });

    const html = await renderToString(app);
    expect(html).toContain("返回列表");
    expect(html).toContain("新增员工");
    expect(html).toContain("入职建档与账号开通");
    expect(html).toContain("Form Content");
    expect(html).toContain("保存");
  });

  it("omits content when hidden with destroyOnClose", async () => {
    const app = createSSRApp({
      render() {
        return h(
          WorkspacePanel,
          {
            modelValue: false,
            title: "隐藏面板",
            destroyOnClose: true
          },
          {
            default: () => h("div", "Hidden Content")
          }
        );
      }
    });

    const html = await renderToString(app);
    expect(html).not.toContain("Hidden Content");
    expect(html).not.toContain("隐藏面板");
  });
});
