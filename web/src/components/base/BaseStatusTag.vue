<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    status: string;
    type?: "success" | "warning" | "danger" | "info";
  }>(),
  {
    type: "info"
  }
);

const statusLabelMap: Record<string, string> = {
  ENABLED: "启用",
  DISABLED: "停用",
  FROZEN: "冻结",
  TRIAL: "试用",
  LOCKED: "锁定",
  ACTIVE: "生效",
  INACTIVE: "未生效"
};

const displayStatus = computed(() => statusLabelMap[props.status] ?? props.status);
</script>

<template>
  <el-tag effect="light" round :type="type">
    {{ displayStatus }}
  </el-tag>
</template>
