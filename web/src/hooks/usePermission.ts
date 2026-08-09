import { storeToRefs } from "pinia";
import { useAuthStore } from "@/stores/modules/auth";

export function usePermission() {
  const authStore = useAuthStore();
  const { currentUser } = storeToRefs(authStore);

  return {
    currentUser,
    hasPermission: authStore.hasPermission,
    hasFeature: authStore.hasFeature
  };
}
