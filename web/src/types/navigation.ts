export interface NavMeta {
  title: string;
  icon?: string;
  permissionCode?: string;
  featureFlag?: string;
  tenantApp?: string;
  hidden?: boolean;
  keepAlive?: boolean;
  affix?: boolean;
}

export interface NavItem {
  name: string;
  path: string;
  icon?: string;
  meta: NavMeta;
  children?: NavItem[];
}
