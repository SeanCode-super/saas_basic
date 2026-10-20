<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    type?: string;
    value: string | number | boolean;
    label?: string;
    effect?: "light" | "dark" | "plain";
  }>(),
  {
    type: "default",
    effect: "light"
  }
);

const stringVal = computed(() => String(props.value ?? ""));

const tagConfig = computed(() => {
  const v = stringVal.value.toUpperCase();

  if (["ENABLED", "ACTIVE", "SUCCESS", "TRUE", "1", "NORMAL", "WON", "APPROVED", "PUBLISHED"].includes(v)) {
    return {
      type: "success" as const,
      text: props.label || (v === "ENABLED" || v === "ACTIVE" ? "启用" : v === "SUCCESS" ? "成功" : "正常")
    };
  }

  if (["DISABLED", "INACTIVE", "FAIL", "FALSE", "0", "DANGER", "CLOSED", "REJECTED", "LOST"].includes(v)) {
    return {
      type: "danger" as const,
      text: props.label || (v === "DISABLED" || v === "INACTIVE" ? "停用" : v === "FAIL" ? "失败" : "关闭/驳回")
    };
  }

  if (["PENDING", "WARNING", "FOLLOWING", "EXPIRING", "IN_STORAGE", "DRAFT"].includes(v)) {
    return {
      type: "warning" as const,
      text: props.label || (v === "PENDING" ? "待审核" : v === "DRAFT" ? "草稿" : "进行中")
    };
  }

  if (["ONLINE", "PRIMARY", "CONVERTED", "INFO"].includes(v)) {
    return {
      type: "primary" as const,
      text: props.label || stringVal.value
    };
  }

  return {
    type: "info" as const,
    text: props.label || stringVal.value
  };
});
</script>

<template>
  <el-tag
    :type="tagConfig.type"
    :effect="effect"
    size="small"
    class="dict-tag-badge"
  >
    {{ tagConfig.text }}
  </el-tag>
</template>

<style scoped>
.dict-tag-badge {
  font-weight: 500;
  border-radius: 4px;
}
</style>
