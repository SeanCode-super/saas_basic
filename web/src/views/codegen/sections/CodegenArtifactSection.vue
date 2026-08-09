<script setup lang="ts">
defineProps<{
  outputMatrix: string[];
}>();
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>输出矩阵</h3>
        <p>一份元数据必须能同时驱动数据库、后端、前端和权限骨架。</p>
      </div>
    </template>

    <ul class="output-list">
      <li v-for="item in outputMatrix" :key="item">{{ item }}</li>
    </ul>
  </el-card>
</template>

<style scoped lang="scss">
.output-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 10px;
}
</style>
