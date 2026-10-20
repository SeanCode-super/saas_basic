export interface OrganizationRow {
  publicId: string;
  organizationCode: string;
  displayName: string;
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrganizationCreatePayload {
  organizationCode: string;
  displayName: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrganizationUpdatePayload {
  organizationCode: string;
  displayName: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  expectedVersion: number;
}

export interface OrgUnitRow {
  publicId: string;
  organizationPublicId: string;
  parentUnitPublicId?: string;
  unitCode: string;
  displayName: string;
  treePath?: string;
  sortOrder: number;
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrgUnitCreatePayload {
  organizationPublicId: string;
  parentUnitPublicId?: string;
  unitCode: string;
  displayName: string;
  sortOrder?: number;
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrgUnitUpdatePayload {
  unitCode: string;
  displayName: string;
  sortOrder?: number;
  validFrom: string;
  validTo?: string;
  remark?: string;
  expectedVersion: number;
}

export interface OrgUnitMovePayload {
  parentUnitPublicId?: string;
  expectedVersion: number;
}

export interface OrgPositionRow {
  publicId: string;
  organizationPublicId: string;
  orgUnitPublicId?: string;
  positionCode: string;
  displayName: string;
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrgPositionCreatePayload {
  organizationPublicId: string;
  orgUnitPublicId?: string;
  positionCode: string;
  displayName: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrgPositionUpdatePayload {
  orgUnitPublicId?: string;
  positionCode: string;
  displayName: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  expectedVersion: number;
}

export interface OrgPersonRow {
  publicId: string;
  personCode: string;
  displayName: string;
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrgPersonCreatePayload {
  personCode: string;
  displayName: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrgEngagementRow {
  publicId: string;
  engagementCode: string;
  personPublicId: string;
  organizationPublicId: string;
  type: { scheme: string; code: string };
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrgEngagementCreatePayload {
  engagementCode: string;
  personPublicId: string;
  organizationPublicId: string;
  type: { scheme: string; code: string };
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrgAssignmentRow {
  publicId: string;
  engagementPublicId: string;
  organizationPublicId: string;
  orgUnitPublicId: string;
  positionPublicId: string;
  primary: boolean;
  status: string;
  validFrom: string;
  validTo?: string;
  remark?: string;
  version: number;
}

export interface OrgAssignmentCreatePayload {
  engagementPublicId: string;
  orgUnitPublicId: string;
  positionPublicId: string;
  primary: boolean;
  validFrom: string;
  validTo?: string;
  remark?: string;
}

export interface OrgAssignmentUpdatePayload {
  primary: boolean;
  validFrom: string;
  validTo?: string;
  remark?: string;
  expectedVersion: number;
}
