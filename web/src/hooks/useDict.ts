import { computed } from "vue";
import { useDictStore } from "@/stores/modules/dict";

export function useDict(dictCode: string) {
  const dictStore = useDictStore();

  if (!dictStore.dictMap[dictCode] && !dictStore.loadingMap[dictCode]) {
    void dictStore.loadDict(dictCode);
  }

  return computed(() => dictStore.getOptions(dictCode));
}
