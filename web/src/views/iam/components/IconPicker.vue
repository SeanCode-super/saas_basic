<script setup lang="ts">
import { computed, ref } from "vue";
import { Check, CircleClose, Search } from "@element-plus/icons-vue";

interface IconItem {
  name: string;
  label: string;
  category: string;
  keywords: string;
}

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
    disabled?: boolean;
    size?: "default" | "small" | "large";
    clearable?: boolean;
  }>(),
  {
    modelValue: "",
    placeholder: "点击选择图标",
    disabled: false,
    size: "default",
    clearable: true
  }
);

const emit = defineEmits<{
  (e: "update:modelValue", val: string): void;
  (e: "change", val: string): void;
}>();

const popoverVisible = ref(false);
const searchKeyword = ref("");
const activeCategory = ref("all");

const ICON_CATEGORIES = [
  { key: "all", label: "全部图标" },
  { key: "nav", label: "通用导航" },
  { key: "system", label: "系统治理" },
  { key: "org", label: "组织人员" },
  { key: "data", label: "数据报表" },
  { key: "security", label: "安全合规" },
  { key: "flow", label: "业务协同" }
];

const CURATED_ICONS: IconItem[] = [
  // 1. 通用导航 (nav)
  { name: "House", label: "主页/工作台", category: "nav", keywords: "home house zhuye 首页 门户" },
  { name: "HomeFilled", label: "实体主页", category: "nav", keywords: "home filled 首页 主控" },
  { name: "Compass", label: "指南/导航", category: "nav", keywords: "compass daohang 罗盘 探索" },
  { name: "Navigation", label: "导航定向", category: "nav", keywords: "navigation daohang 定位" },
  { name: "Menu", label: "菜单目录", category: "nav", keywords: "menu caidan 列表 目录" },
  { name: "Location", label: "位置定位", category: "nav", keywords: "location weizhi 坐标" },
  { name: "Guide", label: "操作指引", category: "nav", keywords: "guide zhiyin 导引 路标" },
  { name: "Promotion", label: "火箭/发布", category: "nav", keywords: "promotion fabu 推广 部署" },
  { name: "Pointer", label: "指针指示", category: "nav", keywords: "pointer zhizhen 鼠标" },
  { name: "Link", label: "外部链接", category: "nav", keywords: "link lianjie 超链接 外链" },
  { name: "Place", label: "地点标签", category: "nav", keywords: "place didian 位置" },
  { name: "Position", label: "纸飞机/定位", category: "nav", keywords: "position feiji 投递" },
  { name: "Opportunity", label: "灵感/灯泡", category: "nav", keywords: "bulb light idea 提示" },
  { name: "TopRight", label: "斜上跳转", category: "nav", keywords: "arrow jump tiaozhuan 路由" },

  // 2. 系统治理 (system)
  { name: "Setting", label: "系统设置", category: "system", keywords: "setting shezhi 齿轮 配置 系统" },
  { name: "Tools", label: "运维工具", category: "system", keywords: "tools gongju 维护 修理 扳手" },
  { name: "Operation", label: "运营管理", category: "system", keywords: "operation yunying 控制 调度" },
  { name: "Management", label: "体系管控", category: "system", keywords: "management guanli 矩阵" },
  { name: "Monitor", label: "大屏监控", category: "system", keywords: "monitor jiankong 显示器 监视" },
  { name: "Cpu", label: "计算引擎", category: "system", keywords: "cpu hexin 处理器 芯片 资源" },
  { name: "Platform", label: "平台底座", category: "system", keywords: "platform pingtai 架构" },
  { name: "Coin", label: "数据资产", category: "system", keywords: "coin zichan 金币 账本 配额" },
  { name: "Box", label: "容器魔盒", category: "system", keywords: "box hezi 软件包 构件" },
  { name: "SetUp", label: "装配配置", category: "system", keywords: "setup peizhi 拔插 开关" },
  { name: "Connection", label: "服务集成", category: "system", keywords: "connection jicheng 网络 连接" },
  { name: "Switch", label: "交换节点", category: "system", keywords: "switch jiaohuan 切换 网关" },
  { name: "Notification", label: "通告通知", category: "system", keywords: "notification tongzhi 广播" },
  { name: "Goods", label: "应用物料", category: "system", keywords: "goods wuliao 商品 资产" },

  // 3. 组织人员 (org)
  { name: "User", label: "用户账号", category: "org", keywords: "user yonghu 账号 个人 人员" },
  { name: "UserFilled", label: "实名用户", category: "org", keywords: "user filled 实名 员工" },
  { name: "Avatar", label: "员工档案", category: "org", keywords: "avatar dangan 头像 人才" },
  { name: "OfficeBuilding", label: "部门租户", category: "org", keywords: "office building bumen 机构 企业" },
  { name: "School", label: "分公司/学院", category: "org", keywords: "school xueyuan 校区 集团" },
  { name: "Postcard", label: "身份工牌", category: "org", keywords: "postcard gongpai 卡片 证件" },
  { name: "Stamp", label: "印章核准", category: "org", keywords: "stamp yinzhang 盖章 认证" },
  { name: "Tickets", label: "工单凭证", category: "org", keywords: "tickets gongdan 票据 流程" },
  { name: "Suitcase", label: "岗位管理", category: "org", keywords: "suitcase gangwei 职务 公文包" },
  { name: "Coordinate", label: "协作岗位", category: "org", keywords: "coordinate xiezuo 矩阵" },
  { name: "Service", label: "服务支持", category: "org", keywords: "service keful 客服 专席" },
  { name: "Medal", label: "职级勋章", category: "org", keywords: "medal xunzhang 资质 认证" },

  // 4. 数据报表 (data)
  { name: "DataAnalysis", label: "数据分析", category: "data", keywords: "data analysis fenxi 看板 统计" },
  { name: "DataBoard", label: "综合大屏", category: "data", keywords: "databoard daping 仪表盘 报表" },
  { name: "TrendCharts", label: "趋势折线", category: "data", keywords: "trend charts qushi 走向 折线" },
  { name: "Histogram", label: "柱状分布", category: "data", keywords: "histogram zhuzhuang 柱状图 统计" },
  { name: "PieChart", label: "占比饼图", category: "data", keywords: "pie chart bingtu 份额" },
  { name: "DataLine", label: "指标走势", category: "data", keywords: "dataline zoushi 曲线" },
  { name: "Rank", label: "排行天梯", category: "data", keywords: "rank paihang 权重 排序" },
  { name: "ScaleToOriginal", label: "报表钻取", category: "data", keywords: "scale zhuanqu 缩放" },
  { name: "Document", label: "电子文档", category: "data", keywords: "document wendang 文件 表单" },
  { name: "DocumentChecked", label: "审计合规", category: "data", keywords: "document checked shenji 审查" },
  { name: "Files", label: "文件库集", category: "data", keywords: "files wenjianku 资料" },
  { name: "Folder", label: "资源目录", category: "data", keywords: "folder mulu 文件夹" },
  { name: "FolderOpened", label: "展开目录", category: "data", keywords: "folder opened mulu 树形" },
  { name: "List", label: "明细清单", category: "data", keywords: "list qingdan 列表 台账" },

  // 5. 安全合规 (security)
  { name: "Lock", label: "权限锁闭", category: "security", keywords: "lock suobi 密码 安全" },
  { name: "Unlock", label: "放行解锁", category: "security", keywords: "unlock jiesuo 授权" },
  { name: "Key", label: "访问密钥", category: "security", keywords: "key miyue 凭证 秘钥" },
  { name: "View", label: "明文视图", category: "security", keywords: "view shitu 眼睛 观察" },
  { name: "Hide", label: "脱敏掩蔽", category: "security", keywords: "hide tuomin 隐藏 保密" },
  { name: "CircleCheck", label: "合规验真", category: "security", keywords: "check circle tongguo 通过 校验" },
  { name: "CircleClose", label: "违规熔断", category: "security", keywords: "close circle rongduan 拦截 阻断" },
  { name: "Warning", label: "风险预警", category: "security", keywords: "warning yujing 告警 隐患" },
  { name: "Bell", label: "告警通知", category: "security", keywords: "bell gaojing 铃铛 提醒" },
  { name: "BellFilled", label: "紧急警报", category: "security", keywords: "bell filled jinji 强提醒" },
  { name: "WarnTriangleFilled", label: "高危告急", category: "security", keywords: "triangle gaowei 危机" },
  { name: "Finished", label: "准入闭环", category: "security", keywords: "finished bihuan 完成" },

  // 6. 业务协同 (flow)
  { name: "ChatDotRound", label: "即时通讯", category: "flow", keywords: "chat tongxun 沟通 对话" },
  { name: "Message", label: "消息信箱", category: "flow", keywords: "message xiaoxi 站内信" },
  { name: "Calendar", label: "排期日历", category: "flow", keywords: "calendar rili 日程 规划" },
  { name: "Clock", label: "定时调度", category: "flow", keywords: "clock dingdi 任务 时间" },
  { name: "Timer", label: "流水线耗时", category: "flow", keywords: "timer haoshi 计时 延时" },
  { name: "Stopwatch", label: "性能秒表", category: "flow", keywords: "stopwatch miaobiao 性能 跑分" },
  { name: "Edit", label: "在线编辑", category: "flow", keywords: "edit bianji 修改 录入" },
  { name: "EditPen", label: "签署批注", category: "flow", keywords: "pen qianming 笔迹" },
  { name: "Share", label: "共享分发", category: "flow", keywords: "share gongxiang 协作 转发" },
  { name: "Upload", label: "上云备份", category: "flow", keywords: "upload shangchuan 导出" },
  { name: "Download", label: "导出归档", category: "flow", keywords: "download daochu 下载" },
  { name: "Reading", label: "知识底座", category: "flow", keywords: "reading zhishi 文库 百科" }
];

const filteredIcons = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  const cat = activeCategory.value;

  return CURATED_ICONS.filter((item) => {
    const matchCat = cat === "all" || item.category === cat;
    if (!matchCat) return false;
    if (!kw) return true;
    return (
      item.name.toLowerCase().includes(kw) ||
      item.label.toLowerCase().includes(kw) ||
      item.keywords.toLowerCase().includes(kw)
    );
  });
});

function handleSelect(iconName: string) {
  emit("update:modelValue", iconName);
  emit("change", iconName);
  popoverVisible.value = false;
}

function handleClear(event: MouseEvent) {
  event.stopPropagation();
  emit("update:modelValue", "");
  emit("change", "");
}
</script>

<template>
  <div class="enterprise-icon-picker">
    <el-popover
      v-model:visible="popoverVisible"
      placement="bottom-start"
      :width="460"
      trigger="click"
      popper-class="icon-picker-popper"
    >
      <template #reference>
        <div
          class="picker-trigger"
          :class="{
            'is-empty': !modelValue,
            'is-disabled': disabled,
            'is-active': popoverVisible,
            [`is-${size}`]: true
          }"
        >
          <div v-if="modelValue" class="selected-badge">
            <el-icon class="selected-icon">
              <component :is="modelValue" />
            </el-icon>
            <span class="selected-name">{{ modelValue }}</span>
          </div>
          <span v-else class="placeholder-text">{{ placeholder }}</span>

          <div class="trigger-actions">
            <el-icon
              v-if="clearable && modelValue && !disabled"
              class="clear-btn"
              title="清除图标"
              @click="handleClear"
            >
              <CircleClose />
            </el-icon>
            <span class="trigger-caret">▼</span>
          </div>
        </div>
      </template>

      <!-- Popover Content -->
      <div class="picker-panel">
        <!-- Panel Header: Search Bar -->
        <div class="panel-header">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索图标名称或中文关键字 (如: user, 设置, 锁, 报表...)"
            :prefix-icon="Search"
            clearable
            size="default"
          />
        </div>

        <!-- Category Tabs -->
        <div class="panel-categories">
          <button
            v-for="cat in ICON_CATEGORIES"
            :key="cat.key"
            class="cat-chip"
            :class="{ 'is-active': activeCategory === cat.key }"
            @click="activeCategory = cat.key"
          >
            {{ cat.label }}
          </button>
        </div>

        <!-- Icons Grid Scroll Container -->
        <div class="panel-grid-container">
          <div v-if="filteredIcons.length" class="icons-grid">
            <el-tooltip
              v-for="icon in filteredIcons"
              :key="icon.name"
              :content="`${icon.label} (${icon.name})`"
              placement="top"
              :show-after="300"
            >
              <button
                class="icon-grid-item"
                :class="{ 'is-selected': modelValue === icon.name }"
                @click="handleSelect(icon.name)"
              >
                <el-icon class="icon-svg">
                  <component :is="icon.name" />
                </el-icon>
                <span class="icon-label">{{ icon.name }}</span>
                <el-icon v-if="modelValue === icon.name" class="check-badge"><Check /></el-icon>
              </button>
            </el-tooltip>
          </div>
          <div v-else class="empty-state">
            <p>未找到匹配的图标，请尝试其他关键词</p>
          </div>
        </div>

        <!-- Panel Footer -->
        <div class="panel-footer">
          <div class="footer-status">
            <span>当前选中: </span>
            <strong v-if="modelValue" class="selected-text">
              <el-icon><component :is="modelValue" /></el-icon>
              {{ modelValue }}
            </strong>
            <span v-else class="text-muted">未选择</span>
          </div>
          <el-button v-if="modelValue" size="small" link type="danger" @click="handleClear">清除</el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<style scoped lang="scss">
.enterprise-icon-picker {
  width: 100%;
}

.picker-trigger {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 11px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background-color: #fff;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: #c0c4cc;
  }

  &.is-active {
    border-color: #2563eb;
    box-shadow: 0 0 0 2px rgb(37 99 235 / 0.15);
  }

  &.is-disabled {
    background-color: #f5f7fa;
    border-color: #e4e7ed;
    color: #a8abb2;
    cursor: not-allowed;
  }

  &.is-small {
    min-height: 28px;
    padding: 2px 8px;
    font-size: 12px;
  }

  &.is-large {
    min-height: 42px;
    padding: 6px 14px;
    font-size: 15px;
  }
}

.selected-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-weight: 500;
  font-size: 13.5px;

  .selected-icon {
    font-size: 16px;
    color: #2563eb;
  }

  .selected-name {
    font-family: var(--sb-font-mono, monospace);
  }
}

.placeholder-text {
  color: #94a3b8;
  font-size: 13px;
}

.trigger-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #94a3b8;

  .clear-btn {
    font-size: 14px;
    transition: color 0.15s;

    &:hover {
      color: #ef4444;
    }
  }

  .trigger-caret {
    font-size: 9px;
    transform: scale(0.85);
    color: #cbd5e1;
  }
}

.picker-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-header {
  padding-bottom: 2px;
}

.panel-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid #f1f5f9;
}

.cat-chip {
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid transparent;
  background: #f8fafc;
  color: #475569;
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: #e2e8f0;
    color: #0f172a;
  }

  &.is-active {
    background: #eff6ff;
    border-color: #bfdbfe;
    color: #2563eb;
    font-weight: 600;
  }
}

.panel-grid-container {
  height: 240px;
  overflow-y: auto;
  padding-right: 4px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #e2e8f0;
    border-radius: 4px;
  }
}

.icons-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
}

.icon-grid-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 56px;
  padding: 6px 2px;
  border-radius: 6px;
  border: 1px solid #f1f5f9;
  background: #fff;
  color: #334155;
  cursor: pointer;
  transition: all 0.15s;

  .icon-svg {
    font-size: 18px;
    transition: transform 0.15s;
  }

  .icon-label {
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    text-align: center;
    font-size: 10px;
    font-family: var(--sb-font-mono, monospace);
    color: #64748b;
  }

  &:hover {
    border-color: #bfdbfe;
    background: #f0f7ff;
    color: #2563eb;

    .icon-svg {
      transform: scale(1.15);
      color: #2563eb;
    }

    .icon-label {
      color: #2563eb;
    }
  }

  &.is-selected {
    border-color: #2563eb;
    background: #eff6ff;
    color: #2563eb;

    .icon-label {
      color: #2563eb;
      font-weight: 600;
    }
  }

  .check-badge {
    position: absolute;
    top: 2px;
    right: 2px;
    font-size: 10px;
    color: #2563eb;
  }
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
  font-size: 12px;

  .footer-status {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #64748b;

    .selected-text {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: #2563eb;
      font-family: var(--sb-font-mono, monospace);
    }

    .text-muted {
      color: #94a3b8;
    }
  }
}
</style>
