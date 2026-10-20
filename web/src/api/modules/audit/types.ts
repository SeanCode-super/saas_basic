export interface AuditOverview {
  loginEventsToday: number;
  operationEventsToday: number;
  riskMatchesToday: number;
  activeBlackRules: number;
}

export interface LoginLog {
  id: number;
  tenantId: number;
  userId: number;
  username: string;
  loginType: string;
  loginIp: string;
  success: boolean;
  failReason?: string;
  occurredAt: string;
}

export interface OperationLog {
  id: number;
  tenantId: number;
  operatorUserId: number;
  operatorName: string;
  bizModule: string;
  bizType: string;
  bizId: string;
  operationType: string;
  success: boolean;
  occurredAt: string;
}

export interface AuditQueryParam {
  username?: string;
  success?: boolean;
  bizModule?: string;
  operationType?: string;
}
