export interface OrganizationAccessContext {
  organizationPublicId: string;
  engagementPublicId: string;
  orgUnitPublicId: string;
  positionPublicId: string;
  assignmentPublicId: string;
}

export interface AuthCurrentUser {
  sessionId: number;
  tenantId: number;
  tenantCode: string;
  userId: number;
  username: string;
  nickname: string;
  userType: string;
  sessionStatus: string;
  expireAt: string;
  permissions: string[];
  featureFlags: string[];
  roleIds?: number[];
  buttonPermissions?: string[];
  sessionPublicId: string;
  userPublicId: string;
  subjectBindingPublicId?: string | null;
  personPublicId?: string | null;
  organizationContext?: OrganizationAccessContext | null;
}

export interface AuthLoginRequest {
  tenantCode: string;
  clientId?: string;
  terminalCode?: string;
  captchaCode?: string;
  username: string;
  password: string;
}

export interface AuthLoginResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expireAt: string;
  currentUser: AuthCurrentUser;
}

export interface OnlineSession {
  sessionNo: string;
  userId: number;
  username: string;
  nickname?: string;
  userType: string;
  loginIp?: string;
  userAgent?: string;
  loginTime: string;
  lastAccessAt?: string;
  expireAt: string;
  status: string;
  isCurrent: boolean;
}
