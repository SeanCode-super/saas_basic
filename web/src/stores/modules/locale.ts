import { defineStore } from "pinia";
import type { AppLocale } from "@/locales/messages";
import { translate } from "@/locales/messages";

const STORAGE_KEY = "saas-basics-locale";

function normalizeLocale(source?: string | null): AppLocale {
  return source === "en-US" ? "en-US" : "zh-CN";
}

export const useLocaleStore = defineStore("locale", {
  state: () => ({
    locale: normalizeLocale(typeof window !== "undefined" ? localStorage.getItem(STORAGE_KEY) : null) as AppLocale
  }),
  actions: {
    bootstrap(preferred?: string | null) {
      this.locale = normalizeLocale(preferred ?? (typeof navigator !== "undefined" ? navigator.language : "zh-CN"));
      if (typeof window !== "undefined") {
        localStorage.setItem(STORAGE_KEY, this.locale);
      }
    },
    setLocale(locale: AppLocale) {
      this.locale = normalizeLocale(locale);
      if (typeof window !== "undefined") {
        localStorage.setItem(STORAGE_KEY, this.locale);
      }
    },
    t(key: string, params?: Record<string, string | number>) {
      return translate(this.locale, key, params);
    }
  }
});
