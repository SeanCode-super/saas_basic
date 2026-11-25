<script setup lang="ts">
import { onMounted, ref } from "vue";
import { industryEntries, type IndustryEntry } from "@/api/modules/industry";

const entries = ref<IndustryEntry[]>([]);
const loading = ref(true);
const error = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    entries.value = await industryEntries();
  } catch {
    error.value = "应用目录暂时不可用";
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <section v-loading="loading" class="industry-directory">
    <h2>我的应用</h2>
    <el-result v-if="error" icon="warning" :title="error">
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>
    <el-empty v-else-if="!loading && entries.length === 0" description="当前账号还没有可用的行业应用" />
    <router-link v-for="entry in entries" :key="entry.code" :to="{ name: 'industry-app', params: { appCode: entry.code } }">
      {{ entry.name }}
    </router-link>
  </section>
</template>

<style scoped>
.industry-directory { padding: 24px; }
h2 { margin: 0 0 20px; }
a { display: block; padding: 14px 0; color: var(--el-color-primary); border-bottom: 1px solid var(--el-border-color-lighter); }
</style>
