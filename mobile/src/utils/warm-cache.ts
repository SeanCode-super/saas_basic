/**
 * 天枢移动端瞬时渲染温热数据缓存器 (Tianshu Warm Memory Cache & SWR)
 * 解决切换 Tab 或页面时闪现骨架屏/加载转圈的问题，实现“即点即显·零感无缝加载”
 */

import {
  formatIndustry,
  formatCustomerLevel,
  formatOwnerName,
  formatUserDisplayName
} from "./dict";

// 内存单例缓存
const memoryCache = new Map<string, any>();

/**
 * 获取本地/内存温热缓存
 */
export function getCachedData<T>(key: string, fallback: T): T {
  if (memoryCache.has(key)) {
    return memoryCache.get(key) as T;
  }
  try {
    const raw = uni.getStorageSync(`TIANSHU_WARM_${key}`);
    if (raw) {
      const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (parsed) {
        memoryCache.set(key, parsed);
        return parsed as T;
      }
    }
  } catch {}
  memoryCache.set(key, fallback);
  return fallback;
}

/**
 * 写入本地/内存温热缓存
 */
export function setCachedData<T>(key: string, data: T): void {
  memoryCache.set(key, data);
  try {
    uni.setStorage({
      key: `TIANSHU_WARM_${key}`,
      data: JSON.stringify(data)
    });
  } catch {}
}

// -----------------------------------------------------------------------------
// 高保真开箱即用温热种子数据 (与后端 V60 数据库强一致，保证毫秒级首帧直接出图)
// -----------------------------------------------------------------------------

export const WARM_CUSTOMERS = [
  {
    id: 1,
    name: "北京华科盛世软件技术有限公司",
    customerCode: "KH-2026-001",
    inPool: false,
    contactName: "张建国",
    phone: "13911223344",
    industry: formatIndustry("INTERNET"),
    ownerName: "平台管理员",
    level: "战略大客户",
    address: "北京市海淀区中关村软件园二期 12 号楼"
  },
  {
    id: 2,
    name: "上海智联达智能装备制造有限公司",
    customerCode: "KH-2026-002",
    inPool: false,
    contactName: "李总监",
    phone: "13855667788",
    industry: formatIndustry("MANUFACTURING"),
    ownerName: "平台管理员",
    level: "重点商机客户",
    address: "上海市嘉定区安亭国际汽车城核心园区 88 号"
  },
  {
    id: 3,
    name: "深圳创越数智供应链管理集团",
    customerCode: "KH-2026-003",
    inPool: false,
    contactName: "王海涛",
    phone: "13799887766",
    industry: formatIndustry("SUPPLY_CHAIN"),
    ownerName: "平台管理员",
    level: "战略大客户",
    address: "深圳市南山区深圳湾科技生态园 9 栋 A 座"
  },
  {
    id: 4,
    name: "杭州聚数云享网络科技有限公司",
    customerCode: "KH-2026-004",
    inPool: true,
    contactName: "陈晨",
    phone: "13612345678",
    industry: formatIndustry("INTERNET"),
    ownerName: "公海共享 (待认领)",
    level: "标准企业客户",
    address: "浙江省杭州市余杭区阿里巴巴未来科技城 6 幢"
  },
  {
    id: 5,
    name: "苏州汇能新能源动力科技有限公司",
    customerCode: "KH-2026-005",
    inPool: false,
    contactName: "商务负责人",
    phone: "13566778899",
    industry: formatIndustry("NEW_ENERGY"),
    ownerName: "平台管理员",
    level: "重点商机客户",
    address: "江苏省苏州市相城区阳澄湖节能环保产业园 5 号"
  }
];

export const WARM_WORKFLOW_PENDING = [
  {
    id: 101,
    instanceNo: "SP-202611021114064106",
    instanceTitle: "2026 全链路复测自动化申请单 (企业级验证)",
    title: "2026 全链路复测自动化申请单 (企业级验证)",
    nodeName: "直属部门负责人审批",
    initiatorName: "平台管理员",
    createdAt: "2026-11-02 11:14",
    status: "RUNNING"
  },
  {
    id: 102,
    instanceNo: "SP-20261102002",
    instanceTitle: "研发团队云原生容器集群高可用存储扩容申领",
    title: "研发团队云原生容器集群高可用存储扩容申领",
    nodeName: "技术总监审批",
    initiatorName: "平台管理员",
    createdAt: "2026-11-02 11:09",
    status: "RUNNING"
  },
  {
    id: 103,
    instanceNo: "SP-20261102001",
    instanceTitle: "出差拜访华东重点客户及高铁住宿报销",
    title: "出差拜访华东重点客户及高铁住宿报销",
    nodeName: "直属部门负责人审批",
    initiatorName: "平台管理员",
    createdAt: "2026-11-02 11:09",
    status: "RUNNING"
  }
];

export const WARM_WORKFLOW_MY = [
  {
    id: 201,
    instanceNo: "SP-20261102005",
    instanceTitle: "天枢 PaaS 企业底座全套私有化部署采购立项",
    title: "天枢 PaaS 企业底座全套私有化部署采购立项",
    nodeName: "采购委员会核决",
    initiatorName: "平台管理员",
    createdAt: "2026-11-02 09:30",
    status: "RUNNING"
  }
];

export const WARM_WORKFLOW_COMPLETED = [
  {
    id: 301,
    instanceNo: "SP-20261101003",
    instanceTitle: "2026 年度带薪年假申请 (3天)",
    title: "2026 年度带薪年假申请 (3天)",
    nodeName: "流程归档办结",
    initiatorName: "平台管理员",
    createdAt: "2026-11-01 16:45",
    status: "COMPLETED",
    comment: "工作交接完备，准予带薪休假。"
  }
];

export const WARM_CONTACTS = [
  {
    id: 1,
    username: "admin",
    nickname: "平台管理员",
    phone: "13800138000",
    email: "admin@tianshu.cloud",
    deptName: "技术架构中心",
    positionName: "研发总监"
  },
  {
    id: 2,
    username: "zhidan",
    nickname: "沈志丹",
    phone: "13911223344",
    email: "shenzhidan@tianshu.cloud",
    deptName: "平台架构部",
    positionName: "资深系统架构师"
  },
  {
    id: 3,
    username: "sales_lead",
    nickname: "王敏",
    phone: "13766778899",
    email: "wangmin@tianshu.cloud",
    deptName: "商务销售部",
    positionName: "销售总监"
  },
  {
    id: 4,
    username: "crm_support",
    nickname: "李欣",
    phone: "13699887711",
    email: "lixin@tianshu.cloud",
    deptName: "客户服务部",
    positionName: "大客户经理"
  },
  {
    id: 5,
    username: "dev_ops",
    nickname: "赵强",
    phone: "13522334455",
    email: "zhaoqiang@tianshu.cloud",
    deptName: "系统保障部",
    positionName: "系统运维工程师"
  }
];

export const WARM_INVENTORY = [
  {
    id: 1,
    productCode: "WL-2026-001",
    productName: "工业级智能边缘计算网关 Edge-5000",
    specification: "8核ARM / 16GB内存 / 双千兆网口 / 4G-5G全网通",
    warehouseName: "华东一号中心立体总仓",
    stockQuantity: 42,
    availableQuantity: 40,
    minStock: 10,
    unit: "台",
    unitPrice: 1850,
    totalValue: 77700,
    isLowStock: false
  },
  {
    id: 2,
    productCode: "WL-2026-002",
    productName: "高精度数字激光测距测厚传感器",
    specification: "量程 0-50mm / 精度 0.1μm / RS485工业总线",
    warehouseName: "华东一号中心立体总仓",
    stockQuantity: 4,
    availableQuantity: 4,
    minStock: 10,
    unit: "套",
    unitPrice: 3200,
    totalValue: 12800,
    isLowStock: true
  },
  {
    id: 3,
    productCode: "WL-2026-003",
    productName: "天枢云原生边缘计算盒 Edge-Box-800",
    specification: "AI 算力 32TOPS / 双路 2.5G 网卡 / 无风扇散热",
    warehouseName: "华东一号中心立体总仓",
    stockQuantity: 28,
    availableQuantity: 28,
    minStock: 8,
    unit: "台",
    unitPrice: 4500,
    totalValue: 126000,
    isLowStock: false
  }
];

export const WARM_NOTIFY = [
  {
    id: 1,
    title: "审批流转待办通知",
    content: "您有一笔来自平台管理员提交的【2026 全链路复测自动化申请单 (企业级验证)】待核准，请及时查阅办理。",
    category: "WORKFLOW",
    read: false,
    createdAt: "10分钟前"
  },
  {
    id: 2,
    title: "库存安全预警提醒",
    content: "物资【高精度数字激光测距测厚传感器】当前在库数量为 4 套，已触及安全库存预警线下限（10套），请及时补货采购。",
    category: "WARNING",
    read: false,
    createdAt: "1小时前"
  },
  {
    id: 3,
    title: "系统安全加固升级公告",
    content: "天枢协同云平台已完成核心安全模块升级，多租户隔离与端到端传输加密机制平稳运行中。",
    category: "SYSTEM",
    read: true,
    createdAt: "昨天"
  }
];
