export interface DimensionOption {
  label: string;
  value: string;
  count?: number;
}

export interface DimensionAxisConfig {
  key: string;
  label: string;
  accessor?: (row: Record<string, unknown>) => string | string[] | null | undefined;
  options?: Array<{ label: string; value: string }>;
}

export interface DimensionFacetGroup {
  key: string;
  label: string;
  totalCount: number;
  options: DimensionOption[];
}

export interface SavedDimensionView {
  id: string;
  name: string;
  selections: Record<string, string>;
}

export function extractRowDimensionValues(
  row: Record<string, unknown>,
  axis: DimensionAxisConfig
): string[] {
  const raw = axis.accessor ? axis.accessor(row) : row[axis.key];
  if (raw == null) {
    return [];
  }
  const items = Array.isArray(raw) ? raw : [raw];
  const deduped = new Set<string>();
  for (const item of items) {
    if (item == null) continue;
    const text = String(item).trim();
    if (text) {
      deduped.add(text);
    }
  }
  return Array.from(deduped);
}

export function rowMatchesSelections(
  row: Record<string, unknown>,
  axes: DimensionAxisConfig[],
  selections: Record<string, string>,
  excludeKey?: string
): boolean {
  for (const axis of axes) {
    if (excludeKey && axis.key === excludeKey) {
      continue;
    }
    const targetValue = selections[axis.key];
    if (!targetValue) {
      continue;
    }
    const values = extractRowDimensionValues(row, axis);
    if (!values.includes(targetValue)) {
      return false;
    }
  }
  return true;
}

export function filterRowsByDimensions<T extends object>(
  rows: T[],
  axes: DimensionAxisConfig[],
  selections: Record<string, string>
): T[] {
  if (!axes.length) {
    return rows;
  }
  const hasAnySelection = axes.some((axis) => Boolean(selections[axis.key]));
  if (!hasAnySelection) {
    return rows;
  }
  return rows.filter((row) =>
    rowMatchesSelections(row as Record<string, unknown>, axes, selections)
  );
}

export function computeDimensionFacets<T extends object>(
  rows: T[],
  axes: DimensionAxisConfig[],
  selections: Record<string, string>
): DimensionFacetGroup[] {
  return axes.map((axis) => {
    const candidateRows = rows.filter((row) =>
      rowMatchesSelections(row as Record<string, unknown>, axes, selections, axis.key)
    );

    const counts = new Map<string, number>();
    for (const row of candidateRows) {
      const vals = extractRowDimensionValues(row as Record<string, unknown>, axis);
      for (const val of vals) {
        counts.set(val, (counts.get(val) ?? 0) + 1);
      }
    }

    let options: DimensionOption[];
    if (axis.options?.length) {
      const declaredValues = new Set(axis.options.map((o) => o.value));
      options = axis.options.map((opt) => ({
        label: opt.label,
        value: opt.value,
        count: counts.get(opt.value) ?? 0
      }));
      for (const [val, count] of counts.entries()) {
        if (!declaredValues.has(val) && count > 0) {
          options.push({ label: val, value: val, count });
        }
      }
    } else {
      const discovered = new Map<string, number>();
      for (const row of rows) {
        const vals = extractRowDimensionValues(row as Record<string, unknown>, axis);
        for (const val of vals) {
          discovered.set(val, (discovered.get(val) ?? 0) + 1);
        }
      }
      options = Array.from(discovered.entries())
        .sort((a, b) => (b[1] !== a[1] ? b[1] - a[1] : a[0].localeCompare(b[0], "zh-CN")))
        .map(([val]) => ({
          label: val,
          value: val,
          count: counts.get(val) ?? 0
        }));
    }

    return {
      key: axis.key,
      label: axis.label,
      totalCount: candidateRows.length,
      options
    };
  });
}
