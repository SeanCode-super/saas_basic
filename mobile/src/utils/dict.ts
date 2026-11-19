/**
 * 天枢移动端统一字典与枚举格式化工具 (Tianshu Mobile Dict Formatter)
 * 将数据库/后端返回的英文字典 Key 转译为自然友好的中文标签
 */

export const INDUSTRY_DICT: Record<string, string> = {
  NEW_ENERGY: "新能源动力科技",
  INTERNET: "互联网/信息技术",
  MANUFACTURING: "高端智能装备制造",
  SUPPLY_CHAIN: "现代数智供应链",
  TELECOM: "通信与数字网络",
  FINANCE: "金融科技与服务",
  HEALTHCARE: "医疗健康与生物",
  EDUCATION: "教育培训与科研",
  RETAIL: "新零售与现代消费",
  GOVERNMENT: "政务与数字央国企",
  ENERGY: "能源电力与电网",
  AUTOMOTIVE: "智能汽车与车联网",
  HARDWARE: "智能硬件设备",
  SOFTWARE: "软件与信息技术",
  DEFAULT: "软件与信息技术",
  OTHER: "其他多元产业"
};

export const CUSTOMER_LEVEL_DICT: Record<string, string> = {
  A: "战略大客户",
  B: "重点商机客户",
  C: "标准企业客户",
  D: "潜在意向客户"
};

export const CUSTOMER_SOURCE_DICT: Record<string, string> = {
  REFERRAL: "客户转介绍",
  ONLINE: "线上营销官网",
  CONFERENCE: "行业峰会展会",
  INBOUND: "主动进线咨询",
  OUTBOUND: "商务拓客外勤",
  OFFICIAL: "官方渠道合作"
};

export const BUSINESS_STAGE_DICT: Record<string, string> = {
  QUALIFICATION: "初步接洽/资格审查",
  PROPOSAL: "方案宣讲/商务报价",
  NEGOTIATION: "商务谈判/合同推进",
  WON: "赢单签约/成功结单",
  LOST: "遗憾输单/归档关闭"
};

export const USER_DISPLAY_MAP: Record<string, string> = {
  admin: "平台管理员",
  user: "业务专员",
  zhidan: "沈志丹",
  sales_lead: "王敏",
  crm_support: "李欣",
  dev_ops: "赵强"
};

export const INVENTORY_CAT_DICT: Record<string, string> = {
  HARDWARE: "智能硬件设备",
  SENSOR: "精密传感仪器",
  SERVER: "计算存储服务器",
  OFFICE: "行政办公物资",
  NETWORK: "网络通信器材"
};

/**
 * 格式化行业名称
 */
export function formatIndustry(raw?: string): string {
  if (!raw || raw.trim() === "" || raw === "DEFAULT") {
    return "软件与信息技术";
  }
  const upper = raw.trim().toUpperCase();
  if (INDUSTRY_DICT[upper]) {
    return INDUSTRY_DICT[upper];
  }
  // 若本身已是中文（例如“互联网高科技”），直接保留返回
  return raw;
}

/**
 * 格式化客户等级
 */
export function formatCustomerLevel(raw?: string): string {
  if (!raw) return "重点商机客户";
  const upper = raw.trim().toUpperCase();
  if (CUSTOMER_LEVEL_DICT[upper]) {
    return CUSTOMER_LEVEL_DICT[upper];
  }
  return raw;
}

/**
 * 格式化来源渠道
 */
export function formatCustomerSource(raw?: string): string {
  if (!raw) return "商务拓客外勤";
  const upper = raw.trim().toUpperCase();
  if (CUSTOMER_SOURCE_DICT[upper]) {
    return CUSTOMER_SOURCE_DICT[upper];
  }
  return raw;
}

/**
 * 格式化归属人 / 客户经理
 */
export function formatOwnerName(raw?: string, inPool?: boolean): string {
  if (inPool) {
    return "公海共享 (待认领)";
  }
  if (!raw || raw.trim() === "" || raw.toLowerCase() === "admin") {
    return "平台管理员";
  }
  if (raw.toLowerCase() === "user") {
    return "业务专员";
  }
  return USER_DISPLAY_MAP[raw] || raw;
}

/**
 * 格式化人员昵称
 */
export function formatUserDisplayName(raw?: string, fallback = "经办人员"): string {
  if (!raw || raw.trim() === "") return fallback;
  return USER_DISPLAY_MAP[raw] || raw;
}

/**
 * 格式化商品/物料分类
 */
export function formatInventoryCategory(raw?: string): string {
  if (!raw) return "通用标准物资";
  const upper = raw.trim().toUpperCase();
  return INVENTORY_CAT_DICT[upper] || raw;
}
