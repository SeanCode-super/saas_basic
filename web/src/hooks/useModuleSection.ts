import { computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

export interface ModuleSectionItem {
  key: string;
  label: string;
  description?: string;
}

export function useModuleSection(
  items: ModuleSectionItem[],
  options?: {
    queryKey?: string;
    fallback?: string;
    mode?: "query" | "path";
    basePath?: string;
  }
) {
  const route = useRoute();
  const router = useRouter();
  const queryKey = options?.queryKey ?? "view";
  const fallback = options?.fallback ?? items[0]?.key ?? "";
  const mode = options?.mode ?? "query";
  const basePath = normalizeBasePath(options?.basePath);
  const validKeys = new Set(items.map((item) => item.key));

  const activeSection = computed(() => {
    return resolveSectionKey({
      path: route.path,
      queryValue: route.query[queryKey],
      mode,
      queryKey,
      basePath,
      fallback,
      validKeys
    });
  });

  function updateSection(value: string) {
    if (!validKeys.has(value) || value === activeSection.value) {
      return;
    }
    if (mode === "path" && basePath) {
      router.replace({
        path: joinPath(basePath, value),
        query: filterQuery(route.query, queryKey)
      });
      return;
    }
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        [queryKey]: value
      }
    });
  }

  watch(
    () => route.fullPath,
    () => {
      if (mode !== "path" || !basePath) {
        return;
      }
      const next = resolveSectionKey({
        path: route.path,
        queryValue: route.query[queryKey],
        mode,
        queryKey,
        basePath,
        fallback,
        validKeys
      });
      const canonicalPath = joinPath(basePath, next);
      const hasLegacyQuery = route.query[queryKey] !== undefined;
      if (route.path !== canonicalPath || hasLegacyQuery) {
        void router.replace({
          path: canonicalPath,
          query: filterQuery(route.query, queryKey)
        });
      }
    },
    { immediate: true }
  );

  return {
    activeSection,
    updateSection
  };
}

function resolveSectionKey(params: {
  path: string;
  queryValue: unknown;
  mode: "query" | "path";
  queryKey: string;
  basePath?: string;
  fallback: string;
  validKeys: Set<string>;
}) {
  if (params.mode === "path" && params.basePath) {
    const fromPath = sectionFromPath(params.path, params.basePath);
    if (fromPath && params.validKeys.has(fromPath)) {
      return fromPath;
    }
  }
  const candidate = String(params.queryValue ?? "");
  return params.validKeys.has(candidate) ? candidate : params.fallback;
}

function sectionFromPath(path: string, basePath: string) {
  if (path === basePath) {
    return "";
  }
  if (!path.startsWith(`${basePath}/`)) {
    return "";
  }
  return path.slice(basePath.length + 1);
}

function normalizeBasePath(basePath?: string) {
  if (!basePath) {
    return undefined;
  }
  return `/${basePath.replace(/^\/+|\/+$/g, "")}`;
}

function joinPath(basePath: string, section: string) {
  return `${basePath}/${section}`.replace(/\/+/g, "/");
}

function filterQuery(query: ReturnType<typeof useRoute>["query"], queryKey: string) {
  const nextQuery = { ...query };
  delete nextQuery[queryKey];
  return nextQuery;
}
