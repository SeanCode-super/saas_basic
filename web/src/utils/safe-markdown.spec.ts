import { describe, expect, it } from "vitest";
import { renderSafeMarkdown } from "./safe-markdown";

describe("renderSafeMarkdown", () => {
  it("escapes HTML/script tags to prevent XSS injection", () => {
    const html = renderSafeMarkdown('<script>alert("xss")</script> **bold**');
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;");
    expect(html).toContain("<strong>bold</strong>");
  });

  it("renders fenced code blocks, headings, inline code, and lists", () => {
    const md = [
      "### Architecture",
      "- Uses `CloudEvents` 1.0",
      "```sql",
      "SELECT id FROM plat_tenant;",
      "```"
    ].join("\n");
    const html = renderSafeMarkdown(md);
    expect(html).toContain('<h4 class="md-heading">Architecture</h4>');
    expect(html).toContain('<ul class="md-list">');
    expect(html).toContain('<code class="md-inline-code">CloudEvents</code>');
    expect(html).toContain('<pre class="md-code-block"><span class="md-code-lang">sql</span>');
  });
});
