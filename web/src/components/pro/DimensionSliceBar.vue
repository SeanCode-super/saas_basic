<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { CollectionTag, Close, Plus, RefreshLeft } from "@element-plus/icons-vue";
import {
  computeDimensionFacets,
  type DimensionAxisConfig,
  type SavedDimensionView
} from "@/utils/dimension-slice";

const props = defineProps<{
  axes: DimensionAxisConfig[];
  rows: object[];
  modelValue: Record<string, string>;
  storageKey?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [Record<string, string>];
}>();

const facets = computed(() =>
  computeDimensionFacets(props.rows, props.axes, props.modelValue)
);

const hasActiveSelection = computed(() =>
  props.axes.some((axis) => Boolean(props.modelValue[axis.key]))
);

const storageFullKey = computed(() =>
  props.storageKey ? `sb_dim_views_${props.storageKey}` : ""
);

function loadSavedViews(): SavedDimensionView[] {
  if (!storageFullKey.value || typeof localStorage === "undefined") {
    return [];
  }
  try {
    const raw = localStorage.getItem(storageFullKey.value);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is SavedDimensionView =>
        Boolean(item && typeof item.id === "string" && typeof item.name === "string" && typeof item.selections === "object")
    );
  } catch {
    return [];
  }
}

const savedViews = ref<SavedDimensionView[]>(loadSavedViews());
const savingView = ref(false);
const draftViewName = ref("");

watch(storageFullKey, () => {
  savedViews.value = loadSavedViews();
});

function persistViews(next: SavedDimensionView[]) {
  savedViews.value = next;
  if (storageFullKey.value && typeof localStorage !== "undefined") {
    localStorage.setItem(storageFullKey.value, JSON.stringify(next));
  }
}

function selectOption(axisKey: string, value: string) {
  const next: Record<string, string> = { ...props.modelValue };
  if (!value || next[axisKey] === value) {
    delete next[axisKey];
  } else {
    next[axisKey] = value;
  }
  emit("update:modelValue", next);
}

function resetAllDimensions() {
  emit("update:modelValue", {});
}

function isViewActive(view: SavedDimensionView): boolean {
  return props.axes.every(
    (axis) => (props.modelValue[axis.key] || "") === (view.selections[axis.key] || "")
  );
}

function applySavedView(view: SavedDimensionView) {
  emit("update:modelValue", { ...view.selections });
}

function removeSavedView(viewId: string) {
  persistViews(savedViews.value.filter((item) => item.id !== viewId));
}

function openSaveBookmark() {
  draftViewName.value = "";
  savingView.value = true;
}

function commitSaveBookmark() {
  const name = draftViewName.value.trim();
  if (!name) {
    savingView.value = false;
    return;
  }
  const cleanSelections: Record<string, string> = {};
  for (const axis of props.axes) {
    const v = props.modelValue[axis.key];
    if (v) cleanSelections[axis.key] = v;
  }
  const next: SavedDimensionView[] = [
    ...savedViews.value.filter((item) => item.name !== name),
    {
      id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
      name,
      selections: cleanSelections
    }
  ];
  persistViews(next);
  savingView.value = false;
  draftViewName.value = "";
}
</script>

<template>
  <div v-if="facets.length" class="dimension-slice-bar">
    <div v-if="storageKey" class="dimension-slice-bar__bookmarks">
      <div class="bookmark-heading">
        <el-icon><CollectionTag /></el-icon>
        <span>维度视图</span>
      </div>
      <div class="bookmark-pills">
        <button
          type="button"
          class="bookmark-pill"
          :class="{ 'is-active': !hasActiveSelection }"
          @click="resetAllDimensions"
        >
          全部数据
        </button>
        <div
          v-for="view in savedViews"
          :key="view.id"
          class="bookmark-pill-wrap"
          :class="{ 'is-active': isViewActive(view) }"
        >
          <button type="button" class="bookmark-pill-btn" @click="applySavedView(view)">
            {{ view.name }}
          </button>
          <button
            type="button"
            class="bookmark-del-btn"
            aria-label="删除维度视图"
            @click.stop="removeSavedView(view.id)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <template v-if="hasActiveSelection">
          <div v-if="savingView" class="bookmark-inline-save">
            <el-input
              v-model="draftViewName"
              size="small"
              placeholder="输入视图名称"
              style="width: 140px;"
              @keyup.enter="commitSaveBookmark"
            />
            <el-button size="small" type="primary" @click="commitSaveBookmark">保存</el-button>
            <el-button size="small" @click="savingView = false">取消</el-button>
          </div>
          <button
            v-else
            type="button"
            class="bookmark-add-btn"
            @click="openSaveBookmark"
          >
            <el-icon><Plus /></el-icon>
            <span>存为维度视图</span>
          </button>
        </template>
      </div>
      <button
        v-if="hasActiveSelection"
        type="button"
        class="dimension-reset-btn"
        @click="resetAllDimensions"
      >
        <el-icon><RefreshLeft /></el-icon>
        <span>重置筛选</span>
      </button>
    </div>

    <div class="dimension-slice-bar__axes">
      <div v-for="facet in facets" :key="facet.key" class="dimension-axis-row">
        <span class="dimension-axis-label">{{ facet.label }}</span>
        <div class="dimension-axis-options">
          <button
            type="button"
            class="dimension-chip"
            :class="{ 'is-active': !modelValue[facet.key] }"
            @click="selectOption(facet.key, '')"
          >
            <span>全部</span>
            <span class="chip-count">{{ facet.totalCount }}</span>
          </button>
          <button
            v-for="opt in facet.options"
            :key="opt.value"
            type="button"
            class="dimension-chip"
            :class="{
              'is-active': modelValue[facet.key] === opt.value,
              'is-empty': (opt.count ?? 0) === 0 && modelValue[facet.key] !== opt.value
            }"
            @click="selectOption(facet.key, opt.value)"
          >
            <span>{{ opt.label }}</span>
            <span class="chip-count">{{ opt.count ?? 0 }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dimension-slice-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sb-border-subtle);
  background: var(--sb-tier1-bg);
}

.dimension-slice-bar__bookmarks {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--sb-border-subtle);
}

.bookmark-heading {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 72px;
  font-size: 12px;
  font-weight: 650;
  color: var(--sb-text-muted);
}

.bookmark-pills {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.bookmark-pill,
.bookmark-pill-wrap,
.bookmark-add-btn {
  display: inline-flex;
  align-items: center;
  height: 24px;
  border-radius: 9999px;
  font-size: 11.5px;
  font-weight: 550;
  border: 1px solid var(--sb-border-subtle);
  background: var(--sb-control-bg);
  color: var(--sb-text-tertiary);
  transition: all 130ms ease;
}

.bookmark-pill {
  padding: 0 10px;
  cursor: pointer;

  &:hover {
    border-color: var(--sb-border-color);
    color: var(--sb-text-primary);
  }

  &.is-active {
    background: var(--sb-badge-primary-bg);
    border-color: var(--sb-badge-primary-border);
    color: var(--sb-badge-primary-text);
    font-weight: 650;
  }
}

.bookmark-pill-wrap {
  padding: 0 4px 0 10px;
  gap: 2px;

  &.is-active {
    background: var(--sb-badge-primary-bg);
    border-color: var(--sb-badge-primary-border);
    color: var(--sb-badge-primary-text);
    font-weight: 650;
  }
}

.bookmark-pill-btn,
.bookmark-del-btn {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  font-size: inherit;
  font-weight: inherit;
}

.bookmark-del-btn {
  width: 16px;
  height: 16px;
  justify-content: center;
  border-radius: 50%;
  opacity: 0.65;

  &:hover {
    opacity: 1;
    background: rgba(220, 38, 38, 0.14);
    color: var(--sb-danger-color);
  }
}

.bookmark-add-btn {
  padding: 0 9px;
  gap: 4px;
  border-style: dashed;
  border-color: var(--sb-primary-border);
  color: var(--sb-primary-color);
  cursor: pointer;

  &:hover {
    background: var(--sb-badge-primary-bg);
  }
}

.bookmark-inline-save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dimension-reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 9px;
  border-radius: 6px;
  border: 1px solid var(--sb-border-subtle);
  background: transparent;
  color: var(--sb-text-muted);
  font-size: 11.5px;
  cursor: pointer;

  &:hover {
    color: var(--sb-text-primary);
    background: var(--sb-surface-contrast);
  }
}

.dimension-slice-bar__axes {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dimension-axis-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.dimension-axis-label {
  min-width: 72px;
  padding-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sb-text-muted);
  flex-shrink: 0;
}

.dimension-axis-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.dimension-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 25px;
  padding: 0 10px;
  border-radius: 7px;
  border: 1px solid var(--sb-border-subtle);
  background: var(--sb-control-bg);
  color: var(--sb-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 130ms ease;

  .chip-count {
    font-size: 11px;
    font-variant-numeric: tabular-nums;
    color: var(--sb-text-muted);
    opacity: 0.85;
  }

  &:hover {
    border-color: var(--sb-border-color);
    background: var(--sb-control-bg-hover);
    color: var(--sb-text-primary);
  }

  &.is-active {
    background: var(--sb-badge-primary-bg);
    border-color: var(--sb-badge-primary-border);
    color: var(--sb-badge-primary-text);
    font-weight: 650;

    .chip-count {
      color: var(--sb-badge-primary-text);
    }
  }

  &.is-empty {
    opacity: 0.5;
  }
}
</style>
