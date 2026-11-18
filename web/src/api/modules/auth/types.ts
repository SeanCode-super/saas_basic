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

export interface SmartCaptchaChallenge {
  captchaId: string;
  challengeType: "SMART" | "SLIDER";
  powSalt?: string | null;
  powDifficulty: number;
  backgroundImageDataUrl?: string | null;
  pieceImageDataUrl?: string | null;
  targetY?: number | null;
  expiresInSeconds: number;
}

export interface VerifyCaptchaPayload {
  captchaId: string;
  nonce?: string;
  slideX?: number;
  durationMs: number;
  trailCount: number;
}

export interface VerifyCaptchaResponse {
  verified: boolean;
  passToken: string;
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

export interface PasswordResetSendCodePayload {
  username: string;
  tenantCode?: string;
  contact: string;
}

export interface PasswordResetSendCodeResponse {
  accepted: boolean;
  expireSeconds: number;
  deliveryChannel: string;
  sandboxOtpCode?: string | null;
}

export interface PasswordResetConfirmPayload {
  username: string;
  tenantCode?: string;
  otpCode: string;
  newPassword: string;
}

export interface TenantInviteResponse {
  id: number;
  inviteToken: string;
  invitePath: string;
  tenantCode: string;
  tenantName: string;
  invitedEmail?: string | null;
  maxUses: number;
  usedCount: number;
  status: string;
  expireAt: string;
}

export interface TenantInviteAcceptPayload {
  username: string;
  nickname: string;
  password: string;
  email?: string;
  mobile?: string;
}
