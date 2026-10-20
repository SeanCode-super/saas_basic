import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";

export interface PersonalLoginLogItem {
  id: number;
  loginIp: string;
  success: boolean;
  failReason?: string;
  occurredAt?: string;
}

export interface UserProfileData {
  id: number;
  tenantId: number;
  tenantCode: string;
  username: string;
  nickname: string;
  mobile: string;
  email: string;
  userType: string;
  status: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
  roles: string[];
  recentLogs: PersonalLoginLogItem[];
}

export interface UserProfileUpdatePayload {
  nickname: string;
  mobile?: string;
  email?: string;
}

export interface UserPasswordUpdatePayload {
  oldPassword: string;
  newPassword: string;
}

export async function fetchUserProfile(): Promise<ApiResponse<UserProfileData>> {
  const res = await http.get<ApiResponse<UserProfileData>>("/iam/profile");
  return res.data;
}

export async function updateUserProfile(data: UserProfileUpdatePayload): Promise<ApiResponse<UserProfileData>> {
  const res = await http.put<ApiResponse<UserProfileData>>("/iam/profile", data);
  return res.data;
}

export async function updateUserPassword(data: UserPasswordUpdatePayload): Promise<ApiResponse<void>> {
  const res = await http.put<ApiResponse<void>>("/iam/profile/password", data);
  return res.data;
}
