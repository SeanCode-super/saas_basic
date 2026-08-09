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
  tokenType: string;
  expireAt: string;
  currentUser: AuthCurrentUser;
}
