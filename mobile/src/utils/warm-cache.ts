import type { MobileProfileData } from "../api/profile";

const prefix = "TIANSHU_WARM_";
let generation = 0;

function currentUser() {
  const raw = uni.getStorageSync("sb_current_user");
  try {
    return typeof raw === "string" ? JSON.parse(raw) : raw;
  } catch {
    return null;
  }
}

function scopedKey(key: string) {
  const user = currentUser();
  const userId = user?.userId ?? user?.id;
  if (!user?.tenantId || !userId || !uni.getStorageSync("sb_access_token")) return null;
  return `${prefix}v2:${user.tenantId}:${userId}:${key}`;
}

export function sessionGeneration() {
  return generation;
}

export function clearBusinessCache() {
  for (const key of uni.getStorageInfoSync().keys) {
    if (key.startsWith(prefix)) uni.removeStorageSync(key);
  }
}

export function clearMobileSession() {
  generation++;
  clearBusinessCache();
  for (const key of ["sb_current_user", "sb_access_token", "sb_refresh_token", "sb_tenant_id", "sb_tenant_code", "sb_tenant_name"]) {
    uni.removeStorageSync(key);
  }
}

export function getCachedData<T>(key: string, fallback: T): T {
  const storageKey = scopedKey(key);
  if (!storageKey) return fallback;
  try {
    const raw = uni.getStorageSync(storageKey);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

export function setCachedData<T>(key: string, data: T) {
  const storageKey = scopedKey(key);
  if (!storageKey) return;
  try {
    uni.setStorageSync(storageKey, JSON.stringify(data));
  } catch {
    // A storage quota error must not turn a confirmed server response into a failure.
  }
}

export type WarmUserProfileModel = MobileProfileData;

export function getCachedUserProfile(): MobileProfileData {
  const u = currentUser();
  return getCachedData<MobileProfileData>("USER_PROFILE", {
    id: u?.userId ?? u?.id ?? 0, tenantId: u?.tenantId ?? 0, tenantCode: u?.tenantCode ?? "",
    username: u?.username ?? "", nickname: u?.nickname ?? "", userType: u?.userType ?? "",
    avatarUrl: u?.avatarUrl, status: "", roles: []
  });
}

export function setCachedUserProfile(data: MobileProfileData) {
  const u = currentUser();
  if (!u || String(data.id) !== String(u.userId ?? u.id) || String(data.tenantId) !== String(u.tenantId)) return;
  setCachedData("USER_PROFILE", data);
  uni.setStorageSync("sb_current_user", JSON.stringify({ ...u, nickname: data.nickname, avatarUrl: data.avatarUrl }));
}
