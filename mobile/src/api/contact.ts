import { request } from "../utils/request";

export interface ContactUser {
  id: number;
  username: string;
  nickname: string;
  email?: string;
  phone?: string;
  avatar?: string;
  deptId?: number;
  deptName?: string;
  positionName?: string;
  status?: string;
  roles?: string[];
}

export function apiGetContactUsers() {
  return request<ContactUser[]>({
    url: "/api/system/users",
    method: "GET",
    silent: true
  });
}
