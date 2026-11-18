<script setup lang="ts">
import {
  onBeforeUnmount,
  ref,
  watch
} from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowDown,
  ArrowRight,
  CircleClose,
  Close,
  Refresh,
  Remove
} from "@element-plus/icons-vue";

interface TagItem {
  title: string;
  path: string;
  name?: string;
  closable: boolean;
}

const route = useRoute();
const router = useRouter();

const fixedHome: TagItem = {
  title: "控制台首页",
  path: "/dashboard",
  name: "dashboard",
  closable: false
};

const visitedTags = ref<TagItem[]>([fixedHome]);
const contextMenuVisible = ref(false);
const contextMenuTop = ref(0);
const contextMenuLeft = ref(0);
const selectedTag = ref<TagItem | null>(null);

function addTag(r: any) {
  if (!r.path || r.path === "/" || r.path === "/login") return;

  const title = (r.meta?.title as string) || r.name || "新标签";
  const existing = visitedTags.value.find((t) => t.path === r.fullPath || t.path === r.path);
  if (!existing) {
    visitedTags.value.push({
      title,
      path: r.fullPath || r.path,
      name: r.name ? String(r.name) : undefined,
      closable: r.path !== "/dashboard"
    });
  }
}

watch(
  () => route.fullPath,
  () => {
    addTag(route);
  },
  { immediate: true }
);

function isActive(tag: TagItem) {
  return route.fullPath === tag.path || route.path === tag.path;
}

function handleSelectTag(tag: TagItem) {
  if (!isActive(tag)) {
    router.push(tag.path);
  }
}

function handleCloseTag(tag: TagItem, e?: Event) {
  if (e) e.stopPropagation();
  const idx = visitedTags.value.findIndex((t) => t.path === tag.path);
  if (idx === -1) return;

  visitedTags.value.splice(idx, 1);
  if (isActive(tag)) {
    const nextTag = visitedTags.value[idx] || visitedTags.value[idx - 1] || fixedHome;
    router.push(nextTag.path);
  }
}

function getActiveTag(): TagItem {
  return visitedTags.value.find((t) => isActive(t)) ?? fixedHome;
}

function openContextMenu(tag: TagItem, e: MouseEvent) {
  e.preventDefault();
  e.stopPropagation();
  selectedTag.value = tag;
  const menuWidth = 168;
  const menuHeight = tag.closable ? 208 : 172;
  const margin = 8;
  const maxLeft = Math.max(margin, window.innerWidth - menuWidth - margin);
  const maxTop = Math.max(margin, window.innerHeight - menuHeight - margin);
  contextMenuLeft.value = Math.min(Math.max(margin, e.clientX), maxLeft);
  contextMenuTop.value = Math.min(Math.max(margin, e.clientY + 6), maxTop);
  contextMenuVisible.value = true;
}

function closeContextMenu() {
  contextMenuVisible.value = false;
}

function handleEscapeKey(e: KeyboardEvent) {
  if (e.key === "Escape") {
    closeContextMenu();
  }
}

function bindGlobalDismissListeners() {
  document.addEventListener("click", closeContextMenu);
  window.addEventListener("scroll", closeContextMenu, true);
  window.addEventListener("resize", closeContextMenu);
  window.addEventListener("keydown", handleEscapeKey);
}

function unbindGlobalDismissListeners() {
  document.removeEventListener("click", closeContextMenu);
  window.removeEventListener("scroll", closeContextMenu, true);
  window.removeEventListener("resize", closeContextMenu);
  window.removeEventListener("keydown", handleEscapeKey);
}

watch(contextMenuVisible, (val) => {
  if (val) {
    bindGlobalDismissListeners();
  } else {
    unbindGlobalDismissListeners();
  }
});

onBeforeUnmount(() => {
  unbindGlobalDismissListeners();
});

async function refreshSelectedTag(targetTag?: TagItem) {
  const target = targetTag ?? selectedTag.value ?? getActiveTag();
  closeContextMenu();
  if (!target) return;
  if (!isActive(target)) {
    await router.push(target.path);
  }
  window.dispatchEvent(new CustomEvent("sb-refresh-active-view"));
}

function closeSelectedTag() {
  const target = selectedTag.value;
  closeContextMenu();
  if (target?.closable) {
    handleCloseTag(target);
  }
}

function closeOtherTags(targetTag?: TagItem) {
  const target = targetTag ?? selectedTag.value ?? getActiveTag();
  closeContextMenu();
  visitedTags.value = visitedTags.value.filter(
    (t) => !t.closable || t.path === target.path
  );
  if (!isActive(target)) {
    router.push(target.path);
  }
}

function closeRightTags() {
  const target = selectedTag.value ?? getActiveTag();
  closeContextMenu();
  const idx = visitedTags.value.findIndex((t) => t.path === target.path);
  if (idx !== -1) {
    visitedTags.value = visitedTags.value.filter((t, i) => !t.closable || i <= idx);
    if (!visitedTags.value.some((t) => isActive(t))) {
      router.push(target.path);
    }
  }
}

function closeAllTags() {
  closeContextMenu();
  visitedTags.value = [fixedHome];
  router.push(fixedHome.path);
}

async function handleToolbarCommand(command: string | number | Record<string, unknown>) {
  const activeTag = getActiveTag();
  if (command === "refresh") {
    await refreshSelectedTag(activeTag);
  } else if (command === "closeOthers") {
    closeOtherTags(activeTag);
  } else if (command === "closeAll") {
    closeAllTags();
  }
}
</script>

<template>
  <div class="tags-view-container">
    <div class="tags-view-scroll">
      <div
        v-for="tag in visitedTags"
        :key="tag.path"
        class="tags-view-item"
        :class="{ 'is-active': isActive(tag) }"
        @click="handleSelectTag(tag)"
        @contextmenu="openContextMenu(tag, $event)"
      >
        <span class="tag-dot" v-if="isActive(tag)" />
        <span class="tag-title">{{ tag.title }}</span>
        <el-icon
          v-if="tag.closable"
          class="tag-close-btn"
          @click="handleCloseTag(tag, $event)"
        >
          <Close />
        </el-icon>
      </div>
    </div>

    <!-- 右侧标签快捷操作下拉 -->
    <el-dropdown class="tags-view-actions" trigger="click" @command="handleToolbarCommand">
      <button type="button" class="tags-action-trigger" title="标签页操作" aria-label="标签页操作">
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="refresh">
            <el-icon><Refresh /></el-icon>
            <span>刷新当前页</span>
          </el-dropdown-item>
          <el-dropdown-item command="closeOthers">
            <el-icon><CircleClose /></el-icon>
            <span>关闭其他标签</span>
          </el-dropdown-item>
          <el-dropdown-item command="closeAll" divided>
            <el-icon><Remove /></el-icon>
            <span>全部关闭</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 右键快捷菜单：Teleport 到 body，避免被祖先 backdrop-filter 劫持 position: fixed 坐标系 -->
    <Teleport to="body">
      <ul
        v-show="contextMenuVisible"
        :style="{ left: `${contextMenuLeft}px`, top: `${contextMenuTop}px` }"
        class="context-menu"
        role="menu"
        @contextmenu.prevent
      >
        <li role="menuitem" @click="refreshSelectedTag()">
          <el-icon><Refresh /></el-icon>
          <span>刷新当前页</span>
        </li>
        <li v-if="selectedTag?.closable" role="menuitem" @click="closeSelectedTag">
          <el-icon><Close /></el-icon>
          <span>关闭此标签</span>
        </li>
        <li role="menuitem" @click="closeOtherTags()">
          <el-icon><CircleClose /></el-icon>
          <span>关闭其他标签</span>
        </li>
        <li role="menuitem" @click="closeRightTags">
          <el-icon><ArrowRight /></el-icon>
          <span>关闭右侧标签</span>
        </li>
        <li role="menuitem" class="danger-item" @click="closeAllTags">
          <el-icon><Remove /></el-icon>
          <span>全部关闭</span>
        </li>
      </ul>
    </Teleport>
  </div>
</template>

<style scoped>
.tags-view-container {
  height: 40px;
  width: 100%;
  background: var(--sb-glass-bg, rgba(255, 255, 255, 0.88));
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  border-radius: 16px;
  border: 1px solid var(--sb-border-color, rgba(226, 232, 240, 0.90));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    0 6px 18px -6px rgba(15, 23, 42, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-right: 10px;
  position: relative;
  z-index: 10;
  user-select: none;
  box-sizing: border-box;
}

.tags-view-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 6px 0 16px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tags-view-actions {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
}

.tags-action-trigger {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid var(--sb-border-color, #e2e8f0);
  background: var(--el-bg-color-overlay, rgba(248, 250, 252, 0.92));
  color: var(--el-text-color-secondary, #64748b);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;

  &:hover {
    color: var(--sb-text-primary, #0f172a);
    border-color: #93c5fd;
    background: var(--el-fill-color-light, #f1f5f9);
  }
}

.tags-view-scroll::-webkit-scrollbar {
  display: none;
}

.tags-view-item {
  display: inline-flex;
  align-items: center;
  height: 28px;
  line-height: 28px;
  border: 1px solid rgba(226, 232, 240, 0.90);
  color: #334155;
  background: rgba(248, 250, 252, 0.92);
  padding: 0 12px;
  font-size: 12px;
  font-weight: 550;
  border-radius: 9999px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  cursor: pointer;
  transition: all 0.16s cubic-bezier(0.16, 1, 0.3, 1);
  white-space: nowrap;
  gap: 6px;

  &:hover {
    color: var(--sb-text-primary, #0f172a);
    background: #ffffff;
    border-color: #cbd5e1;
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.06);
    transform: translateY(-0.5px);
  }

  &.is-active {
    background: #ffffff;
    color: #1d4ed8;
    border-color: #bfdbfe;
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.10);
    font-weight: 650;
  }
}

.tag-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
}

.tag-title {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-close-btn {
  font-size: 11px;
  border-radius: 4px;
  padding: 1px;
  color: #94a3b8;
  transition: all 0.12s ease;

  &:hover {
    background: #dbeafe;
    color: #1d4ed8;
  }
}

.context-menu {
  position: fixed;
  background: var(--el-bg-color-overlay, rgba(255, 255, 255, 0.96));
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  border-radius: 12px;
  box-shadow:
    0 12px 32px -4px rgb(15 23 42 / 0.14),
    0 0 0 1px rgb(15 23 42 / 0.04);
  border: 1px solid var(--sb-border-color, #cbd5e1);
  padding: 6px;
  margin: 0;
  list-style: none;
  z-index: 9999;
  min-width: 156px;
  box-sizing: border-box;
}

.context-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  color: var(--sb-text-primary, #1e293b);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.context-menu li:hover {
  background: var(--el-fill-color-light, #f1f5f9);
  color: var(--el-color-primary, #2563eb);
}

.context-menu li.danger-item:hover {
  color: var(--el-color-danger, #dc2626);
  background: var(--el-color-danger-light-9, #fef2f2);
}

@media (max-width: 760px) {
  .tags-view-container {
    display: none !important;
  }
}
</style>
