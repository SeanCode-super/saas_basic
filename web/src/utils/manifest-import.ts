export function parseJsonManifest<T>(source: string): T[] {
  const trimmed = source.trim();
  if (!trimmed) {
    throw new Error("导入清单不能为空");
  }

  const parsed = JSON.parse(trimmed) as unknown;
  if (!Array.isArray(parsed)) {
    throw new Error("导入清单必须是 JSON 数组");
  }

  return parsed as T[];
}

export function formatJsonManifest<T>(sample: T[]): string {
  return JSON.stringify(sample, null, 2);
}
