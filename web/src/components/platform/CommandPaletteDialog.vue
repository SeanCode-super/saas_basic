<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  Bell,
  Briefcase,
  Cpu,
  CreditCard,
  DataBoard,
  Document,
  Grid,
  Key,
  Monitor,
  Operation,
  Printer,
  Search,
  Setting,
  SwitchButton,
  TrendCharts,
  User
} from "@element-plus/icons-vue";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "command", action: string): void;
}>();

const router = useRouter();
const searchKeyword = ref("");
const selectedIndex = ref(0);
const inputRef = ref();

interface CommandItem {
  id: string;
  category: "FAST_ACTION" | "APPS" | "PLATFORM";
  title: string;
  subtitle: string;
  icon: any;
  action: () => void;
}

const allCommands: CommandItem[] = [
  // 快速指令
  {
    id: "action-profile",
    category: "FAST_ACTION",
    title: "个人中心 (Profile)",
    subtitle: "查看个人账号信息、修改密码与登录审计",
    icon: User,
    action: () => router.push("/profile")
  },
  {
    id: "action-monitor",
    category: "FAST_ACTION",
    title: "服务与应用监控 (Server APM)",
    subtitle: "实时 CPU、JVM 堆内存、连接池水位与死锁巡检",
    icon: Monitor,
    action: () => router.push("/server-monitor")
  },
  {
    id: "action-screen",
    category: "FAST_ACTION",
    title: "数字大屏驾驶舱 (Data Screen)",
    subtitle: "全屏自适应 3D 科技感运营数据大屏",
    icon: DataBoard,
    action: () => {
      const routeUrl = router.resolve({ path: "/screen" });
      window.open(routeUrl.href, "_blank");
    }
  },
  {
    id: "action-doc",
    category: "FAST_ACTION",
    title: "Knife4j 在线 API 接口文档",
    subtitle: "SpringDoc OpenAPI 3 交互式接口调试台",
    icon: Document,
    action: () => window.open("http://localhost:8080/doc.html", "_blank")
  },
  // 业务应用
  {
    id: "app-crm",
    category: "APPS",
    title: "客户关系 CRM",
    subtitle: "公海池抢单、销售线索、商机阶段漏斗、合同回款",
    icon: TrendCharts,
    action: () => router.push("/crm")
  },
  {
    id: "app-erp",
    category: "APPS",
    title: "进销存 ERP",
    subtitle: "商品档案 SKU、实时多仓库存、采购履约入库台账",
    icon: Briefcase,
    action: () => router.push("/erp")
  },
  {
    id: "app-workflow",
    category: "APPS",
    title: "工作流中心 (BPM)",
    subtitle: "流程定义、节点拖拽设计器、待办与审批历史",
    icon: Operation,
    action: () => router.push("/workflow")
  },
  {
    id: "app-print",
    category: "APPS",
    title: "单据套打与报表中心 (Print)",
    subtitle: "增值税发票、采购单、物流面单套打，防伪印章与一键打印",
    icon: Printer,
    action: () => router.push("/print")
  },
  {
    id: "app-pay",
    category: "APPS",
    title: "统一聚合支付中台 (Pay Center)",
    subtitle: "微信支付 Native 扫码、支付宝当面付与聚合收银台全景沙箱体验",
    icon: CreditCard,
    action: () => router.push("/integration/pay")
  },
  {
    id: "app-message",
    category: "APPS",
    title: "多渠道消息与通知网关 (Notify)",
    subtitle: "企业微信、钉钉、飞书机器人群推送，短信与邮件统一调度及审计",
    icon: Bell,
    action: () => router.push("/integration/message")
  },
  {
    id: "app-tenant-apps",
    category: "PLATFORM",
    title: "租户应用授权体系 (App Entitlements)",
    subtitle: "顶级管理员控制租户应用权限，未授权应用在系统内完全隐形",
    icon: Grid,
    action: () => router.push("/tenant/apps")
  },
  // 平台底座
  {
    id: "plat-tenant",
    category: "PLATFORM",
    title: "多租户管控中枢",
    subtitle: "租户开通入驻、套餐配额管理、数据隔离模式",
    icon: Setting,
    action: () => router.push("/tenant")
  },
  {
    id: "plat-iam",
    category: "PLATFORM",
    title: "身份与权限中心 (IAM)",
    subtitle: "用户账号、角色权限矩阵、数据权限范围、部门组织",
    icon: Key,
    action: () => router.push("/iam")
  },
  {
    id: "plat-codegen",
    category: "PLATFORM",
    title: "代码生成引擎 (Codegen)",
    subtitle: "单表/主子表/树表 CRUD 生成，一键物理写盘",
    icon: Cpu,
    action: () => router.push("/codegen")
  },
  {
    id: "plat-system",
    category: "PLATFORM",
    title: "系统中心与数据字典",
    subtitle: "全局参数配置、数据字典、回收站与系统序列号",
    icon: Setting,
    action: () => router.push("/system")
  }
];

const filteredCommands = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return allCommands;
  return allCommands.filter(
    (c) =>
      c.title.toLowerCase().includes(kw) ||
      c.subtitle.toLowerCase().includes(kw) ||
      c.id.toLowerCase().includes(kw)
  );
});

watch(
  () => props.visible,
  (val) => {
    if (val) {
      searchKeyword.value = "";
      selectedIndex.value = 0;
      nextTick(() => {
        inputRef.value?.focus();
      });
    }
  }
);

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === "ArrowDown") {
    e.preventDefault();
    if (filteredCommands.value.length > 0) {
      selectedIndex.value = (selectedIndex.value + 1) % filteredCommands.value.length;
    }
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    if (filteredCommands.value.length > 0) {
      selectedIndex.value =
        (selectedIndex.value - 1 + filteredCommands.value.length) % filteredCommands.value.length;
    }
  } else if (e.key === "Enter") {
    e.preventDefault();
    if (filteredCommands.value[selectedIndex.value]) {
      executeCommand(filteredCommands.value[selectedIndex.value]);
    }
  } else if (e.key === "Escape") {
    emit("update:visible", false);
  }
}

function executeCommand(cmd: CommandItem) {
  emit("update:visible", false);
  cmd.action();
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :show-close="false"
    width="640px"
    class="command-palette-dialog"
    destroy-on-close
    align-center
    @update:model-value="(val) => emit('update:visible', val)"
    @keydown="handleKeyDown"
  >
    <div class="palette-container">
      <div class="palette-search-bar">
        <el-icon class="search-icon"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="键入关键字盲打搜索功能、页面或快捷指令..."
        />
        <kbd class="esc-kbd">ESC 退出</kbd>
      </div>

      <div class="palette-list" v-if="filteredCommands.length > 0">
        <div
          v-for="(cmd, idx) in filteredCommands"
          :key="cmd.id"
          class="palette-item"
          :class="{ 'is-selected': idx === selectedIndex }"
          @mouseenter="selectedIndex = idx"
          @click="executeCommand(cmd)"
        >
          <div class="item-icon-box">
            <el-icon><component :is="cmd.icon" /></el-icon>
          </div>
          <div class="item-info">
            <div class="item-title">
              {{ cmd.title }}
              <span class="category-tag">
                {{ cmd.category === 'FAST_ACTION' ? '指令' : (cmd.category === 'APPS' ? '应用' : '底座') }}
              </span>
            </div>
            <div class="item-subtitle">{{ cmd.subtitle }}</div>
          </div>
          <div v-if="idx === selectedIndex" class="item-enter-hint">
            <kbd>↵ Enter</kbd>
          </div>
        </div>
      </div>

      <div v-else class="palette-empty">
        <el-icon class="empty-icon"><Search /></el-icon>
        <p>未找到与 "{{ searchKeyword }}" 相关的指令或功能</p>
      </div>

      <div class="palette-footer">
        <span class="footer-tip"><kbd>↑</kbd> <kbd>↓</kbd> 切换选项</span>
        <span class="footer-tip"><kbd>Enter</kbd> 快速跳转</span>
        <span class="footer-tip"><kbd>Esc</kbd> 关闭面板</span>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
:deep(.el-dialog.command-palette-dialog) {
  border-radius: 12px;
  overflow: hidden;
  padding: 0;
  box-shadow: 0 20px 45px rgb(15 23 42 / 0.25);
  border: 1px solid rgb(226 232 240 / 0.8);
}

:deep(.el-dialog__header) {
  display: none;
}

:deep(.el-dialog__body) {
  padding: 0;
}

.palette-container {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.palette-search-bar {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
  gap: 12px;
}

.search-icon {
  font-size: 20px;
  color: #94a3b8;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  color: #0f172a;
  background: transparent;
}

.search-input::placeholder {
  color: #94a3b8;
}

.esc-kbd {
  font-size: 11px;
  padding: 3px 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  color: #64748b;
  font-family: monospace;
}

.palette-list {
  max-height: 420px;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.palette-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  gap: 14px;
}

.palette-item.is-selected {
  background: #eff6ff;
}

.item-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #475569;
  border: 1px solid #e2e8f0;
  transition: all 0.15s;
}

.palette-item.is-selected .item-icon-box {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-tag {
  font-size: 10px;
  padding: 1px 5px;
  background: #e2e8f0;
  color: #475569;
  border-radius: 3px;
  font-weight: normal;
}

.palette-item.is-selected .category-tag {
  background: #dbeafe;
  color: #1d4ed8;
}

.item-subtitle {
  font-size: 12px;
  color: #64748b;
}

.item-enter-hint kbd {
  font-size: 11px;
  padding: 2px 6px;
  background: #dbeafe;
  border-radius: 4px;
  color: #1d4ed8;
  font-family: monospace;
}

.palette-empty {
  padding: 40px 20px;
  text-align: center;
  color: #94a3b8;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.palette-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  background: #f8fafc;
  border-top: 1px solid #f1f5f9;
  font-size: 11px;
  color: #94a3b8;
}

.footer-tip kbd {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  padding: 1px 4px;
  font-family: monospace;
  color: #64748b;
}
</style>
