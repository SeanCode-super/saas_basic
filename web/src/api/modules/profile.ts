import { http } from "@/api/http";
import type { ApiResponse } from "@/types/http";
import type { OnlineSession, UserWorkspacePreferences } from "@/api/modules/auth";

export interface PersonalLoginLogItem {
  id: number;
  loginIp: string;
  success: boolean;
  failReason?: string;
  occurredAt?: string;
}

export interface DataScopeSummaryItem {
  roleName: string;
  scopeCode?: string;
  scopeName?: string;
  scopeType: string;
}

export interface IdentityBindingItem {
  identityType: string;
  identifier: string;
  verified: boolean;
  status: string;
  boundAt?: string;
}

export interface ProfileAssignmentItem {
  assignmentPublicId: string;
  organizationName?: string;
  orgUnitName?: string;
  positionName?: string;
  primary: boolean;
  active: boolean;
}

export interface EmployeeProfileInfo {
  employeeId?: number;
  employeeNo?: string;
  employeeName?: string;
  deptId?: number;
  deptName?: string;
  deptFullPath?: string;
  positionId?: number;
  positionName?: string;
  hireDate?: string;
  employeeStatus?: string;
  personPublicId?: string;
  activeAssignmentPublicId?: string;
  assignments: ProfileAssignmentItem[];
}

export interface UserProfileData {
  id: number;
  publicId?: string;
  userCode?: string;
  tenantId: number;
  tenantCode: string;
  tenantName?: string;
  username: string;
  nickname: string;
  mobile: string;
  email: string;
  avatarFileId?: number | null;
  avatarUrl?: string | null;
  gender?: string;
  bio?: string;
  workLocation?: string;
  extContact?: string;
  locale?: string;
  timezone?: string;
  userType: string;
  status: string;
  createdAt?: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
  passwordChangedAt?: string;
  needResetPassword?: boolean;
  passwordMaxAgeDays?: number;
  passwordDaysRemaining?: number;
  mfaEnabled?: boolean;
  securityScore?: number;
  roles: string[];
  dataScopes: DataScopeSummaryItem[];
  identities: IdentityBindingItem[];
  employeeInfo?: EmployeeProfileInfo | null;
  preferences: UserWorkspacePreferences;
  recentLogs: PersonalLoginLogItem[];
}

export interface UserProfileUpdatePayload {
  nickname: string;
  mobile?: string;
  email?: string;
  gender?: string;
  bio?: string;
  workLocation?: string;
  extContact?: string;
  locale?: string;
  timezone?: string;
  confirmPassword?: string;
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

export async function uploadUserAvatar(file: File): Promise<ApiResponse<UserProfileData>> {
  const form = new FormData();
  form.append("file", file);
  const res = await http.post<ApiResponse<UserProfileData>>("/iam/profile/avatar", form);
  return res.data;
}

export async function updateUserPreferences(data: UserWorkspacePreferences): Promise<ApiResponse<UserWorkspacePreferences>> {
  const res = await http.put<ApiResponse<UserWorkspacePreferences>>("/iam/profile/preferences", data);
  return res.data;
}

export async function fetchMyProfileSessions(): Promise<ApiResponse<OnlineSession[]>> {
  const res = await http.get<ApiResponse<OnlineSession[]>>("/iam/profile/sessions");
  return res.data;
}

export async function revokeMyProfileSession(sessionNo: string): Promise<ApiResponse<void>> {
  const res = await http.delete<ApiResponse<void>>(`/iam/profile/sessions/${encodeURIComponent(sessionNo)}`);
  return res.data;
}

export async function revokeOtherMyProfileSessions(): Promise<ApiResponse<{ revokedCount: number }>> {
  const res = await http.delete<ApiResponse<{ revokedCount: number }>>("/iam/profile/sessions/others");
  return res.data;
}

export async function updateUserPassword(data: UserPasswordUpdatePayload): Promise<ApiResponse<void>> {
  const res = await http.put<ApiResponse<void>>("/iam/profile/password", data);
  return res.data;
}
