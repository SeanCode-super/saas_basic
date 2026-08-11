<script setup lang="ts">
import { Refresh } from "@element-plus/icons-vue";

defineProps<{
  loading?: boolean;
}>();

defineEmits<{
  click: [];
}>();
</script>

<template>
  <el-tooltip content="刷新" placement="bottom">
    <el-button :icon="Refresh" :loading="loading" aria-label="刷新" @click="$emit('click')" />
  </el-tooltip>
</template>
