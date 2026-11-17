export interface OrganizationAccessContext {
  organizationPublicId: string;
  engagementPublicId: string;
  orgUnitPublicId: string;
  positionPublicId: string;
  assignmentPublicId: string;
}

export interface UserWorkspacePreferences {
  locale: string;
  timezone: string;
  layoutMode: "mix" | "side" | "top";
  railTheme: "light" | "dark";
  showBreadcrumbs: boolean;
  sidebarAccordion: boolean;
  enableWatermark: boolean;
  emailNotify: boolean;
  siteNotify: boolean;
  securityAlertNotify: boolean;
  defaultAiModel: string;
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
  avatarUrl?: string | null;
  locale?: string | null;
  timezone?: string | null;
  preferences?: UserWorkspacePreferences | null;
}

export interface AuthLoginRequest {
  tenantCode?: string;
  clientId?: string;
  terminalCode?: string;
  captchaId?: string;
  captchaCode?: string;
  totpCode?: string;
  username: string;
  password: string;
}

export interface CaptchaChallenge {
  captchaId: string;
  imageDataUrl: string;
  expiresInSeconds: number;
}

export interface MfaStatusResponse {
  enabled: boolean;
  policyForceMfa: boolean;
  remainingRecoveryCodes: number;
}

export interface MfaSetupResponse {
  secret: string;
  otpAuthUri: string;
  recoveryCodes: string[];
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
