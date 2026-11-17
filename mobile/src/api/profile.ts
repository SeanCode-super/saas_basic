import { request, uploadFile } from "../utils/request";

export interface MobileProfileData {
  id: number;
  userCode?: string;
  tenantId: number;
  tenantCode: string;
  tenantName?: string;
  username: string;
  nickname: string;
  mobile?: string;
  email?: string;
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
  lastLoginAt?: string;
  lastLoginIp?: string;
  passwordChangedAt?: string;
  passwordMaxAgeDays?: number;
  passwordDaysRemaining?: number;
  mfaEnabled?: boolean;
  securityScore?: number;
  roles: string[];
  employeeInfo?: {
    employeeNo?: string;
    employeeName?: string;
    deptName?: string;
    deptFullPath?: string;
    positionName?: string;
    hireDate?: string;
  } | null;
  preferences?: {
    locale: string;
    timezone: string;
    layoutMode: string;
    railTheme: string;
    showBreadcrumbs: boolean;
    sidebarAccordion: boolean;
    enableWatermark: boolean;
    emailNotify: boolean;
    siteNotify: boolean;
    securityAlertNotify: boolean;
    defaultAiModel: string;
  };
}

export interface MobileOnlineSession {
  sessionNo: string;
  loginIp?: string;
  userAgent?: string;
  loginTime?: string;
  lastAccessAt?: string;
  isCurrent: boolean;
}

export interface MobileStorageStatus {
  configured: boolean;
  reasonCode: string;
  message: string;
  defaultFolder?: string;
  resolvedTargetDir?: string;
}

export function apiGetProfile() {
  return request<MobileProfileData>({
    url: "/api/iam/profile",
    method: "GET"
  });
}

export function apiUpdateProfile(payload: {
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
}) {
  return request<MobileProfileData>({
    url: "/api/iam/profile",
    method: "PUT",
    data: payload
  });
}

export function apiUpdatePassword(payload: { oldPassword: string; newPassword: string }) {
  return request<void>({
    url: "/api/iam/profile/password",
    method: "PUT",
    data: payload
  });
}

export function apiUploadProfileAvatar(filePath: string) {
  return uploadFile<MobileProfileData>({
    url: "/api/iam/profile/avatar",
    filePath,
    name: "file"
  });
}

export function apiGetStorageStatus(bizType = "USER_AVATAR") {
  return request<MobileStorageStatus>({
    url: "/api/file/storage-status",
    method: "GET",
    params: { bizType },
    silent: true
  });
}

export function apiGetMySessions() {
  return request<MobileOnlineSession[]>({
    url: "/api/iam/profile/sessions",
    method: "GET"
  });
}

export function apiRevokeMySession(sessionNo: string) {
  return request<void>({
    url: `/api/iam/profile/sessions/${encodeURIComponent(sessionNo)}`,
    method: "DELETE"
  });
}

export function apiRevokeOtherMySessions() {
  return request<{ revokedCount: number }>({
    url: "/api/iam/profile/sessions/others",
    method: "DELETE"
  });
}
