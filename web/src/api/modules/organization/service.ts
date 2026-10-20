import { http } from "@/api/http";
import type { ApiResponse, PageResponse } from "@/types/http";
import type {
  OrgAssignmentCreatePayload,
  OrgAssignmentRow,
  OrgAssignmentUpdatePayload,
  OrgEngagementCreatePayload,
  OrgEngagementRow,
  OrgPersonCreatePayload,
  OrgPersonRow,
  OrgPositionCreatePayload,
  OrgPositionRow,
  OrgPositionUpdatePayload,
  OrgUnitCreatePayload,
  OrgUnitMovePayload,
  OrgUnitRow,
  OrgUnitUpdatePayload,
  OrganizationCreatePayload,
  OrganizationRow,
  OrganizationUpdatePayload
} from "./types";

export async function fetchOrganizations(page = 1, size = 100): Promise<PageResponse<OrganizationRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrganizationRow>>>("/organizations", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrganization(payload: OrganizationCreatePayload): Promise<OrganizationRow> {
  const response = await http.post<ApiResponse<OrganizationRow>>("/organizations", payload);
  return response.data.data;
}

export async function updateOrganization(publicId: string, payload: OrganizationUpdatePayload): Promise<OrganizationRow> {
  const response = await http.put<ApiResponse<OrganizationRow>>(`/organizations/${publicId}`, payload);
  return response.data.data;
}

export async function fetchOrgUnits(page = 1, size = 200): Promise<PageResponse<OrgUnitRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrgUnitRow>>>("/org-units", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrgUnit(payload: OrgUnitCreatePayload): Promise<OrgUnitRow> {
  const response = await http.post<ApiResponse<OrgUnitRow>>("/org-units", payload);
  return response.data.data;
}

export async function updateOrgUnit(publicId: string, payload: OrgUnitUpdatePayload): Promise<OrgUnitRow> {
  const response = await http.put<ApiResponse<OrgUnitRow>>(`/org-units/${publicId}`, payload);
  return response.data.data;
}

export async function moveOrgUnit(publicId: string, payload: OrgUnitMovePayload): Promise<OrgUnitRow> {
  const response = await http.post<ApiResponse<OrgUnitRow>>(`/org-units/${publicId}/move`, payload);
  return response.data.data;
}

export async function deleteOrgUnit(publicId: string, expectedVersion: number): Promise<void> {
  await http.delete(`/org-units/${publicId}`, {
    params: { expectedVersion }
  });
}

export async function fetchOrgPositions(page = 1, size = 200): Promise<PageResponse<OrgPositionRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrgPositionRow>>>("/positions", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrgPosition(payload: OrgPositionCreatePayload): Promise<OrgPositionRow> {
  const response = await http.post<ApiResponse<OrgPositionRow>>("/positions", payload);
  return response.data.data;
}

export async function updateOrgPosition(publicId: string, payload: OrgPositionUpdatePayload): Promise<OrgPositionRow> {
  const response = await http.put<ApiResponse<OrgPositionRow>>(`/positions/${publicId}`, payload);
  return response.data.data;
}

export async function deleteOrgPosition(publicId: string, expectedVersion: number): Promise<void> {
  await http.delete(`/positions/${publicId}`, {
    params: { expectedVersion }
  });
}

export async function fetchOrgPersons(page = 1, size = 200): Promise<PageResponse<OrgPersonRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrgPersonRow>>>("/persons", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrgPerson(payload: OrgPersonCreatePayload): Promise<OrgPersonRow> {
  const response = await http.post<ApiResponse<OrgPersonRow>>("/persons", payload);
  return response.data.data;
}

export async function fetchOrgEngagements(page = 1, size = 200): Promise<PageResponse<OrgEngagementRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrgEngagementRow>>>("/engagements", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrgEngagement(payload: OrgEngagementCreatePayload): Promise<OrgEngagementRow> {
  const response = await http.post<ApiResponse<OrgEngagementRow>>("/engagements", payload);
  return response.data.data;
}

export async function fetchOrgAssignments(page = 1, size = 200): Promise<PageResponse<OrgAssignmentRow>> {
  const response = await http.get<ApiResponse<PageResponse<OrgAssignmentRow>>>("/assignments", {
    params: { page, size }
  });
  return response.data.data;
}

export async function createOrgAssignment(payload: OrgAssignmentCreatePayload): Promise<OrgAssignmentRow> {
  const response = await http.post<ApiResponse<OrgAssignmentRow>>("/assignments", payload);
  return response.data.data;
}

export async function updateOrgAssignment(publicId: string, payload: OrgAssignmentUpdatePayload): Promise<OrgAssignmentRow> {
  const response = await http.put<ApiResponse<OrgAssignmentRow>>(`/assignments/${publicId}`, payload);
  return response.data.data;
}

export async function deleteOrgAssignment(publicId: string, expectedVersion: number): Promise<void> {
  await http.delete(`/assignments/${publicId}`, {
    params: { expectedVersion }
  });
}
