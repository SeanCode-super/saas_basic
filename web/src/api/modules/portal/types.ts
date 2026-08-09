export interface PortalTerminal {
  terminalCode: string;
  terminalName: string;
  terminalType: string;
}

export interface PortalLoginPolicy {
  id: number;
  policyCode: string;
  policyName: string;
  allowPasswordLogin: boolean;
  allowSmsLogin: boolean;
  allowEmailLogin: boolean;
  allowSocialLogin: boolean;
  forceMfa: boolean;
  sessionTimeoutMinutes: number;
  maxFailedCount: number;
  lockMinutes: number;
}

export interface PortalPasswordPolicy {
  id: number;
  policyCode: string;
  policyName: string;
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireNumber: boolean;
  requireSpecial: boolean;
  passwordHistoryLimit: number;
  passwordExpireDays: number;
}

export interface PortalCaptcha {
  mode: string;
  sliderReserved: boolean;
}

export interface PortalEntry {
  clientId: string;
  clientName: string;
  tenantCode: string;
  portalTitle: string;
  welcomeTitle: string;
  welcomeText: string;
  logoUrl: string;
  themeCode: string;
  backgroundImageUrl: string;
  backgroundColor: string;
  filingInfo: string;
  terminal: PortalTerminal;
  loginPolicy: PortalLoginPolicy;
  passwordPolicy: PortalPasswordPolicy;
  captcha: PortalCaptcha;
}

export interface PortalClientRow {
  id: number;
  tenantId: number;
  clientId: string;
  tenantCode: string;
  clientName: string;
  portalTitle: string;
  welcomeTitle: string;
  welcomeText: string;
  logoUrl?: string;
  themeCode: string;
  backgroundImageUrl?: string;
  backgroundColor: string;
  filingInfo: string;
  loginPolicyId: number;
  passwordPolicyId: number;
  captchaMode: string;
  sliderReserved: boolean;
  status: string;
  remark?: string;
}

export interface PortalClientSavePayload {
  tenantId: number;
  clientId: string;
  tenantCode: string;
  clientName: string;
  portalTitle: string;
  welcomeTitle: string;
  welcomeText: string;
  logoUrl?: string;
  themeCode: string;
  backgroundImageUrl?: string;
  backgroundColor: string;
  filingInfo?: string;
  loginPolicyId: number;
  passwordPolicyId: number;
  captchaMode: string;
  sliderReserved: boolean;
  status: string;
  remark?: string;
}

export interface PortalTerminalRow {
  id: number;
  tenantId: number;
  portalClientId: number;
  terminalCode: string;
  terminalName: string;
  terminalType: string;
  portalTitle: string;
  logoUrl?: string;
  themeCode: string;
  backgroundImageUrl?: string;
  backgroundColor: string;
  loginPolicyId: number;
  passwordPolicyId: number;
  captchaMode: string;
  sliderReserved: boolean;
  isDefault: boolean;
  status: string;
  remark?: string;
}

export interface PortalTerminalSavePayload {
  tenantId: number;
  portalClientId: number;
  terminalCode: string;
  terminalName: string;
  terminalType: string;
  portalTitle: string;
  logoUrl?: string;
  themeCode: string;
  backgroundImageUrl?: string;
  backgroundColor: string;
  loginPolicyId: number;
  passwordPolicyId: number;
  captchaMode: string;
  sliderReserved: boolean;
  isDefault: boolean;
  status: string;
  remark?: string;
}
