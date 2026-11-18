function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function formatInline(escapedLine: string): string {
  let out = escapedLine.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>');
  out = out.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  return out;
}

/**
 * 零依赖 XSS 安全 Markdown 渲染器（用于 AI 流式气泡展示）。
 * 先对全量原始文本执行 HTML 实体转义，再解析代码块、标题、粗体与无序列表。
 */
export function renderSafeMarkdown(raw: string): string {
  if (!raw) {
    return "";
  }
  const normalized = raw.replace(/\r\n/g, "\n");
  const segments = normalized.split(/(```[\s\S]*?```)/g);
  const htmlParts: string[] = [];

  for (const seg of segments) {
    if (!seg) continue;
    if (seg.startsWith("```") && seg.endsWith("```") && seg.length >= 6) {
      const inner = seg.slice(3, -3);
      const firstNl = inner.indexOf("\n");
      const lang = firstNl > 0 ? inner.slice(0, firstNl).trim() : "";
      const codeBody = firstNl >= 0 ? inner.slice(firstNl + 1) : inner;
      const escapedLang = escapeHtml(lang);
      const escapedCode = escapeHtml(codeBody);
      const langBadge = escapedLang ? `<span class="md-code-lang">${escapedLang}</span>` : "";
      htmlParts.push(`<pre class="md-code-block">${langBadge}<code>${escapedCode}</code></pre>`);
      continue;
    }

    const lines = seg.split("\n");
    let inList = false;
    for (const rawLine of lines) {
      const escaped = escapeHtml(rawLine);
      const trimmed = escaped.trim();
      if (trimmed.startsWith("### ")) {
        if (inList) {
          htmlParts.push("</ul>");
          inList = false;
        }
        htmlParts.push(`<h4 class="md-heading">${formatInline(trimmed.slice(4))}</h4>`);
      } else if (trimmed.startsWith("## ")) {
        if (inList) {
          htmlParts.push("</ul>");
          inList = false;
        }
        htmlParts.push(`<h3 class="md-heading">${formatInline(trimmed.slice(3))}</h3>`);
      } else if (trimmed.startsWith("- ")) {
        if (!inList) {
          htmlParts.push('<ul class="md-list">');
          inList = true;
        }
        htmlParts.push(`<li>${formatInline(trimmed.slice(2))}</li>`);
      } else if (trimmed === "") {
        if (inList) {
          htmlParts.push("</ul>");
          inList = false;
        }
        htmlParts.push('<div class="md-spacer"></div>');
      } else {
        if (inList) {
          htmlParts.push("</ul>");
          inList = false;
        }
        htmlParts.push(`<p class="md-paragraph">${formatInline(escaped)}</p>`);
      }
    }
    if (inList) {
      htmlParts.push("</ul>");
    }
  }

  return htmlParts.join("");
}
