<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowRight,
  CircleClose,
  Close,
  Odometer,
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

function openContextMenu(tag: TagItem, e: MouseEvent) {
  e.preventDefault();
  selectedTag.value = tag;
  contextMenuLeft.value = e.clientX;
  contextMenuTop.value = e.clientY;
  contextMenuVisible.value = true;
}

function closeContextMenu() {
  contextMenuVisible.value = false;
}

watch(contextMenuVisible, (val) => {
  if (val) {
    document.addEventListener("click", closeContextMenu);
  } else {
    document.removeEventListener("click", closeContextMenu);
  }
});

function refreshSelectedTag() {
  closeContextMenu();
  if (!selectedTag.value) return;
  router.replace({ path: "/redirect" + selectedTag.value.path }).catch(() => {
    router.go(0);
  });
}

function closeOtherTags() {
  closeContextMenu();
  if (!selectedTag.value) return;
  visitedTags.value = visitedTags.value.filter(
    (t) => !t.closable || t.path === selectedTag.value?.path
  );
  if (!isActive(selectedTag.value)) {
    router.push(selectedTag.value.path);
  }
}

function closeRightTags() {
  closeContextMenu();
  if (!selectedTag.value) return;
  const idx = visitedTags.value.findIndex((t) => t.path === selectedTag.value?.path);
  if (idx !== -1) {
    visitedTags.value = visitedTags.value.filter((t, i) => !t.closable || i <= idx);
    if (!visitedTags.value.some((t) => isActive(t))) {
      router.push(selectedTag.value.path);
    }
  }
}

function closeAllTags() {
  closeContextMenu();
  visitedTags.value = [fixedHome];
  router.push(fixedHome.path);
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

    <!-- 右键快捷菜单 -->
    <ul
      v-show="contextMenuVisible"
      :style="{ left: contextMenuLeft + 'px', top: contextMenuTop + 'px' }"
      class="context-menu"
    >
      <li @click="refreshSelectedTag">
        <el-icon><Refresh /></el-icon>
        <span>刷新当前页</span>
      </li>
      <li v-if="selectedTag?.closable" @click="selectedTag && handleCloseTag(selectedTag)">
        <el-icon><Close /></el-icon>
        <span>关闭此标签</span>
      </li>
      <li @click="closeOtherTags">
        <el-icon><CircleClose /></el-icon>
        <span>关闭其他标签</span>
      </li>
      <li @click="closeRightTags">
        <el-icon><ArrowRight /></el-icon>
        <span>关闭右侧标签</span>
      </li>
      <li @click="closeAllTags" class="danger-item">
        <el-icon><Remove /></el-icon>
        <span>全部关闭</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.tags-view-container {
  height: 36px;
  width: 100%;
  background: #ffffff;
  border-bottom: 1px solid var(--sb-border-subtle, #e2e8f0);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  display: flex;
  align-items: center;
  position: relative;
  z-index: 10;
  user-select: none;
}

.tags-view-scroll {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 14px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tags-view-scroll::-webkit-scrollbar {
  display: none;
}

.tags-view-item {
  display: inline-flex;
  align-items: center;
  height: 26px;
  line-height: 26px;
  border: 1px solid var(--sb-border-subtle, #e2e8f0);
  color: #475569;
  background: #ffffff;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.12s cubic-bezier(0.16, 1, 0.3, 1);
  white-space: nowrap;
  gap: 6px;

  &:hover {
    color: var(--sb-text-primary, #090d16);
    background: #f8fafc;
    border-color: var(--sb-border-color, #cbd5e1);
  }

  &.is-active {
    background: #eff6ff;
    color: #1d4ed8;
    border-color: rgba(37, 99, 235, 0.35);
    font-weight: 600;
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
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 10px 25px rgb(15 23 42 / 0.12);
  border: 1px solid var(--sb-border-color, #cbd5e1);
  padding: 6px 0;
  margin: 0;
  list-style: none;
  z-index: 9999;
  min-width: 130px;
}

.context-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  font-size: 12.5px;
  color: #1e293b;
  cursor: pointer;
  transition: background 0.15s;
}

.context-menu li:hover {
  background: #f1f5f9;
  color: #2563eb;
}

.context-menu li.danger-item:hover {
  color: #dc2626;
  background: #fef2f2;
}
</style>
