<script setup lang="ts">
import { computed } from "vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import en from "element-plus/es/locale/lang/en";
import { useLocaleStore } from "@/stores/modules/locale";

const localeStore = useLocaleStore();
const currentLocale = computed(() => (localeStore.locale === "en-US" ? en : zhCn));
</script>

<template>
  <el-config-provider :locale="currentLocale">
    <router-view />
  </el-config-provider>
</template>
