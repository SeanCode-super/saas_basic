import { ref } from "vue";
import { defineStore } from "pinia";
import { fetchSystemDictOptions } from "@/api/modules/system";
import type { DictOption } from "@/types/dict";

type DictMap = Record<string, DictOption[]>;
const DEFAULT_DICT_CODES = ["tenant_status", "job_status", "datasource_type", "common_yes_no"];

export const useDictStore = defineStore("dict", () => {
  const dictMap = ref<DictMap>({});
  const loadingMap = ref<Record<string, boolean>>({});

  async function bootstrap() {
    await Promise.all(DEFAULT_DICT_CODES.map((dictCode) => loadDict(dictCode)));
  }

  async function loadDict(dictCode: string) {
    if (loadingMap.value[dictCode]) {
      return;
    }
    loadingMap.value = {
      ...loadingMap.value,
      [dictCode]: true
    };
    try {
      const options = await fetchSystemDictOptions(dictCode);
      dictMap.value = {
        ...dictMap.value,
        [dictCode]: options.map((item) => ({
          label: item.label,
          value: item.value,
          color: item.color,
          tag: item.tag
        }))
      };
    } catch {
      if (!dictMap.value[dictCode]) {
        dictMap.value = {
          ...dictMap.value,
          [dictCode]: []
        };
      }
    } finally {
      loadingMap.value = {
        ...loadingMap.value,
        [dictCode]: false
      };
    }
  }

  function getOptions(dictCode: string) {
    return dictMap.value[dictCode] ?? [];
  }

  return {
    dictMap,
    loadingMap,
    bootstrap,
    loadDict,
    getOptions
  };
});
