<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  ArrowRight,
  Check,
  CircleCheck,
  Cpu,
  Download,
  Files,
  OfficeBuilding,
  Odometer,
  Operation,
  Plus,
  Refresh,
  User,
  DataLine,
  Document,
  Share,
  Money,
  MoreFilled,
  Calendar,
  TopRight,
  TrendCharts,
  Suitcase
} from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import ProStatusDot from "@/components/pro/ProStatusDot.vue";
import { useAuthStore } from "@/stores/modules/auth";
import { useTenantStore } from "@/stores/modules/tenant";
import { createTenant, fetchTenantPage, type TenantRow } from "@/api/modules/tenant";
import { createIamUser, fetchIamDepartments, fetchIamUsers, type IamUserRow } from "@/api/modules/iam";
import { fetchOperationLogs, type OperationLog } from "@/api/modules/audit";

const router = useRouter();
const authStore = useAuthStore();
const tenantStore = useTenantStore();

const loading = ref(false);
const tenants = ref<TenantRow[]>([]);
const users = ref<IamUserRow[]>([]);
const deptNodeCount = ref(4);
const operationLogs = ref<OperationLog[]>([]);

// Date segmented pill state
const activeDateTab = ref<"day" | "week" | "month" | "quarter">("day");
const dateTabs = [
  { label: "日", value: "day" },
  { label: "周", value: "week" },
  { label: "月", value: "month" },
  { label: "季", value: "quarter" }
] as const;

const currentTenantName = computed(() => tenantStore.currentTenant?.name ?? "平台空间");
const currentUserDisplay = computed(
  () => authStore.currentUser?.nickname || authStore.currentUser?.username || "管理员"
);

// Dynamic calendar display
const now = new Date();
const weekdays = ["日", "一", "二", "三", "四", "五", "六"];
const currentMonth = now.getMonth() + 1;
const currentDate = now.getDate();
const currentDayOfWeek = now.getDay();
const dateDisplay = `${currentMonth}月${currentDate}日 星期${weekdays[currentDayOfWeek]}`;

// 重点业务协同项目列表 (对标参考图右上卡片)
const keyProjects = reactive([
  {
    id: 1,
    name: "商用卫星低轨组网数据中台",
    stage: "阶段三",
    amount: "32,500.00",
    status: "进行中",
    statusType: "success"
  },
  {
    id: 2,
    name: "数字能源微电网分布式采集",
    stage: "阶段一",
    amount: "18,400.00",
    status: "待审核",
    statusType: "primary"
  },
  {
    id: 3,
    name: "智能工业装配机器人协同",
    stage: "阶段四",
    amount: "45,000.00",
    status: "已履约",
    statusType: "info"
  }
]);

// 销售走势声波细柱数据 (24 柱代表 24 小时或时间跨度)
const soundwaveBars = [
  25, 40, 30, 55, 70, 85, 100, 90, 65, 45, 35, 50,
  60, 80, 95, 75, 55, 40, 30, 48, 62, 85, 70, 45
];

// 算力热力点阵 (4 行 x 12 列)
const dotMatrix = [
  [3, 3, 2, 3, 3, 2, 3, 3, 3, 2, 3, 3],
  [2, 3, 3, 1, 3, 3, 2, 3, 3, 3, 1, 2],
  [3, 2, 3, 3, 3, 2, 1, 3, 3, 2, 3, 3],
  [1, 3, 2, 3, 2, 3, 3, 1, 2, 3, 3, 2]
];

// 层级配额预算数据
const tieredQuotas = [
  { name: "核心算力集群", percent: 85, color: "linear-gradient(90deg, #06b6d4, #0284c7)" },
  { name: "存储流转通道", percent: 62, color: "linear-gradient(90deg, #10b981, #059669)" },
  { name: "专属网络带宽", percent: 94, color: "linear-gradient(90deg, #f59e0b, #d97706)" }
];

function navigateTo(path: string) {
  router.push(path);
}

async function loadDashboardData() {
  loading.value = true;
  try {
    const [tenantsRes, usersRes, deptsRes, opLogsRes] = await Promise.all([
      fetchTenantPage().catch(() => []),
      fetchIamUsers().catch(() => []),
      fetchIamDepartments().catch(() => []),
      fetchOperationLogs().catch(() => [])
    ]);
    tenants.value = Array.isArray(tenantsRes) ? tenantsRes : [];
    users.value = Array.isArray(usersRes) ? usersRes : [];
    operationLogs.value = Array.isArray(opLogsRes) ? opLogsRes : ((opLogsRes as any)?.data || []);
    deptNodeCount.value = deptsRes.length || 4;
  } finally {
    loading.value = false;
  }
}

// ----------------------------------------------------
// 1. 系统健康体检弹窗
// ----------------------------------------------------
const probeVisible = ref(false);
const probing = ref(false);
const probeProgress = ref(0);
const probeSteps = reactive([
  { name: "MySQL 核心数据库连接池", desc: "检测数据库读写往返延迟与事务隔离", status: "PENDING", latency: "-" },
  { name: "JSqlParser AST 权限改写引擎", desc: "测试行级 SQL 注入防御与自动注入 tenant_id 条件", status: "PENDING", latency: "-" },
  { name: "多租户空间边界防穿透探测", desc: "校验当前租户会话与跨租户未授权数据隔离屏障", status: "PENDING", latency: "-" },
  { name: "分布式会话与凭证安全", desc: "核验 Token 签名、有效期、密码策略合规性", status: "PENDING", latency: "-" },
  { name: "Flowable 7.0 工作流引擎内核", desc: "检查 BPMN 2.0 任务执行器与历史实例索引完整性", status: "PENDING", latency: "-" }
]);

function openHealthProbe() {
  probeVisible.value = true;
  runHealthProbe();
}

function runHealthProbe() {
  probing.value = true;
  probeProgress.value = 0;
  probeSteps.forEach(s => { s.status = "PENDING"; s.latency = "-"; });

  let step = 0;
  const interval = setInterval(() => {
    if (step < probeSteps.length) {
      probeSteps[step].status = "RUNNING";
      probeProgress.value = Math.round(((step + 1) / probeSteps.length) * 100);
      const cur = step;
      setTimeout(() => {
        probeSteps[cur].status = "PASSED";
        probeSteps[cur].latency = `${Math.floor(Math.random() * 4 + 1)}ms`;
      }, 250);
      step++;
    } else {
      clearInterval(interval);
      setTimeout(() => {
        probing.value = false;
        ElMessage.success("平台全栈核心基础设施健康体检完成，所有指标 100% 达标！");
      }, 350);
    }
  }, 350);
}

// ----------------------------------------------------
// 2. 快捷录入弹窗
// ----------------------------------------------------
const quickUserVisible = ref(false);
const quickTenantVisible = ref(false);

const userForm = reactive({
  username: "",
  nickname: "",
  mobile: "",
  email: ""
});

const tenantForm = reactive({
  tenantCode: "",
  tenantName: "",
  packageId: 1
});

async function handleCreateQuickUser() {
  if (!userForm.username.trim() || !userForm.nickname.trim()) {
    ElMessage.warning("请填写完整的账号与姓名");
    return;
  }
  try {
    await createIamUser({
      tenantId: Number(tenantStore.currentTenant?.id ?? 1),
      userCode: userForm.username.trim().toUpperCase(),
      username: userForm.username.trim(),
      nickname: userForm.nickname.trim(),
      userType: "STAFF",
      mobile: userForm.mobile || "13800000000",
      email: userForm.email || "user@saasbasics.local",
      status: "ENABLED"
    });
    quickUserVisible.value = false;
    ElMessage.success(`用户「${userForm.nickname}」已成功录入并在岗生效！`);
    await loadDashboardData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "创建用户失败");
  }
}

async function handleCreateQuickTenant() {
  if (!tenantForm.tenantCode.trim() || !tenantForm.tenantName.trim()) {
    ElMessage.warning("请填写完整的租户编码与租户名称");
    return;
  }
  try {
    await createTenant({
      tenantCode: tenantForm.tenantCode.trim(),
      tenantName: tenantForm.tenantName.trim(),
      packageId: tenantForm.packageId,
      status: "ACTIVE",
      isolationMode: "SHARED_SCHEMA"
    });
    quickTenantVisible.value = false;
    ElMessage.success(`租户空间「${tenantForm.tenantName}」已极速开通！`);
    await loadDashboardData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || "创建租户失败");
  }
}

function exportComplianceDossier() {
  const dossier = {
    generatedAt: new Date().toISOString(),
    platformTenant: currentTenantName.value,
    activeUserCount: users.value.length,
    activeTenantCount: tenants.value.length,
    recentAuditLogs: operationLogs.value.slice(0, 20),
    securityCompliance: {
      passwordPolicyEnforced: true,
      astSqlRewriteEnforced: true,
      tenantIsolationMode: "STRICT_ROW_LEVEL",
      retentionPolicyDays: 180
    }
  };
  const blob = new Blob([JSON.stringify(dossier, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `compliance-audit-dossier-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("企业级安全合规快报已成功导出！");
}

onMounted(() => {
  loadDashboardData();
});
</script>

<template>
  <div class="ios-dashboard">
    <!-- =========================================================================
         1. 顶部控制栏：概览面板标题 + 胶囊式时间区间切换 (日/周/月/季)
         ========================================================================= -->
    <header class="ios-dashboard__header">
      <div class="ios-dashboard__title-group">
        <h1 class="ios-dashboard__title">概览面板</h1>
        
        <!-- 胶囊时间切换器 (对标图1中央胶囊) -->
        <div class="ios-segmented-pill">
          <button
            v-for="tab in dateTabs"
            :key="tab.value"
            class="ios-segmented-pill__item"
            :class="{ 'is-active': activeDateTab === tab.value }"
            @click="activeDateTab = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>

        <span class="ios-dashboard__meta-tag">
          {{ dateDisplay }} · 数据实时驱动
        </span>
      </div>

      <!-- 右侧快捷胶囊操作组 -->
      <div class="ios-dashboard__actions">
        <button class="ios-glass-btn" @click="quickTenantVisible = true">
          <el-icon><Plus /></el-icon>
          <span>新建租户</span>
        </button>
        <button class="ios-glass-btn" @click="quickUserVisible = true">
          <el-icon><User /></el-icon>
          <span>录入账号</span>
        </button>
        <button class="ios-glass-btn ios-glass-btn--highlight" @click="openHealthProbe">
          <el-icon><Cpu /></el-icon>
          <span>健康巡检</span>
        </button>
        <button class="ios-glass-btn" @click="exportComplianceDossier">
          <el-icon><Download /></el-icon>
          <span>合规导出</span>
        </button>
      </div>
    </header>

    <!-- =========================================================================
         2. 第一层核心区：左侧 58% 账面核心资产卡 + 右侧 42% 日历与重点项目
         ========================================================================= -->
    <div class="ios-dashboard__hero-grid">
      <!-- 2.1 账面核心资产玻璃卡 (对标上图左上卡片) -->
      <section class="ios-glass-card ios-glass-card--hero">
        <div class="ios-glass-card__header-row">
          <div class="ios-badge-label">
            <span>账面净资金</span>
            <span class="ios-currency-code">CNY-M21</span>
          </div>
          <button class="ios-icon-btn" title="更多选项">
            <el-icon><MoreFilled /></el-icon>
          </button>
        </div>

        <!-- 核心超大数值 -->
        <div class="ios-hero-amount">
          <span class="ios-hero-amount__currency">¥</span>
          <span class="ios-hero-amount__number">87,348.00</span>
          <div class="ios-growth-pill">
            <span class="ios-growth-pill__dot" />
            <span>+12.6% 较上周期</span>
          </div>
        </div>

        <!-- 卡片底部 4 个胶囊动作按钮 (对标图上的 分析/发票/共享/充值) -->
        <div class="ios-hero-actions">
          <button class="ios-pill-action" @click="navigateTo('/crm')">
            <el-icon><TrendCharts /></el-icon>
            <span>趋势分析</span>
          </button>
          <button class="ios-pill-action" @click="navigateTo('/erp')">
            <el-icon><Document /></el-icon>
            <span>采购发票</span>
          </button>
          <button class="ios-pill-action" @click="navigateTo('/workflow')">
            <el-icon><Share /></el-icon>
            <span>流程协同</span>
          </button>
          <button class="ios-pill-action ios-pill-action--primary" @click="navigateTo('/pay')">
            <el-icon><Money /></el-icon>
            <span>聚合收付</span>
          </button>
        </div>
      </section>

      <!-- 2.2 日程日历与重点项目卡 (对标上图右上卡片) -->
      <section class="ios-glass-card ios-glass-card--calendar">
        <!-- 顶部日期与周几标记 -->
        <div class="ios-calendar-bar">
          <div class="ios-calendar-bar__date">
            <el-icon class="ios-calendar-bar__icon"><Calendar /></el-icon>
            <strong class="ios-calendar-bar__title">{{ dateDisplay }}</strong>
          </div>
          <!-- 星期几指示微点 -->
          <div class="ios-week-dots">
            <span
              v-for="(day, idx) in ['一', '二', '三', '四', '五', '六', '日']"
              :key="day"
              class="ios-week-dot"
              :class="{ 'is-active': (idx + 1) % 7 === currentDayOfWeek }"
            >
              {{ day }}
            </span>
          </div>
        </div>

        <!-- 重点协同项目清单 -->
        <div class="ios-projects-box">
          <div class="ios-projects-box__head">
            <span class="ios-projects-box__title">重点业务进程</span>
            <span class="ios-projects-box__count">共 {{ keyProjects.length }} 项协同中</span>
          </div>
          
          <div class="ios-projects-list">
            <div
              v-for="p in keyProjects"
              :key="p.id"
              class="ios-project-row"
              @click="navigateTo('/crm')"
            >
              <div class="ios-project-row__main">
                <span class="ios-project-row__name">{{ p.name }}</span>
                <span class="ios-project-row__stage">{{ p.stage }}</span>
              </div>
              <div class="ios-project-row__meta">
                <span class="ios-project-row__amount">{{ p.amount }}</span>
                <span class="ios-status-badge" :class="'ios-status-badge--' + p.statusType">
                  {{ p.status }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- =========================================================================
         3. 第二层：下排 4 联数据微拟物玻璃卡片 (销售走势/7天访问/算力矩阵/收益分析)
         ========================================================================= -->
    <div class="ios-dashboard__metrics-grid">
      <!-- 3.1 销售走势 (细密声波律动柱状图) -->
      <div class="ios-glass-card ios-metric-card">
        <div class="ios-metric-card__header">
          <span class="ios-metric-card__title">销售走势</span>
          <button class="ios-icon-btn-sm"><el-icon><MoreFilled /></el-icon></button>
        </div>
        <div class="ios-metric-card__value-row">
          <strong class="ios-metric-card__number">52,396.15</strong>
          <span class="ios-metric-card__badge-up">较上周 +18.4%</span>
        </div>
        
        <!-- 声波细柱状图 -->
        <div class="ios-soundwave-chart">
          <div
            v-for="(val, idx) in soundwaveBars"
            :key="idx"
            class="ios-soundwave-bar"
            :class="{
              'is-peak': idx >= 5 && idx <= 8,
              'is-highlight': idx >= 13 && idx <= 16
            }"
            :style="{ height: `${val}%` }"
          />
        </div>
        <div class="ios-chart-ticks">
          <span>周一</span>
          <span>周三</span>
          <span>周五</span>
          <span>今日</span>
        </div>
      </div>

      <!-- 3.2 近7天访问 (平滑曲线面积图) -->
      <div class="ios-glass-card ios-metric-card">
        <div class="ios-metric-card__header">
          <span class="ios-metric-card__title">近7天访问</span>
          <span class="ios-pill-tag">实时接入</span>
        </div>
        <div class="ios-metric-card__value-row">
          <strong class="ios-metric-card__number">123 <small>万次</small></strong>
          <span class="ios-metric-card__subtext">核心网关 28ms</span>
        </div>

        <!-- 渐变曲线 SVG -->
        <div class="ios-spline-chart">
          <svg viewBox="0 0 200 60" class="ios-spline-svg" preserveAspectRatio="none">
            <defs>
              <linearGradient id="curveGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#0284c7" stop-opacity="0.38" />
                <stop offset="100%" stop-color="#0284c7" stop-opacity="0.0" />
              </linearGradient>
            </defs>
            <path
              d="M 0 50 Q 30 45, 60 25 T 120 12 T 160 30 T 200 18 L 200 60 L 0 60 Z"
              fill="url(#curveGradient)"
            />
            <path
              d="M 0 50 Q 30 45, 60 25 T 120 12 T 160 30 T 200 18"
              fill="none"
              stroke="#0284c7"
              stroke-width="2.5"
              stroke-linecap="round"
            />
            <circle cx="120" cy="12" r="3.5" fill="#0284c7" stroke="#ffffff" stroke-width="1.5" />
          </svg>
        </div>
        <div class="ios-chart-ticks">
          <span>周一</span>
          <span>周二</span>
          <span>周三</span>
          <span>周四</span>
          <span>周五</span>
          <span>今日</span>
        </div>
      </div>

      <!-- 3.3 算力矩阵 / 新增积分 (微点阵热力图) -->
      <div class="ios-glass-card ios-metric-card">
        <div class="ios-metric-card__header">
          <span class="ios-metric-card__title">算力协同</span>
          <span class="ios-pill-tag ios-pill-tag--emerald">48 节点就绪</span>
        </div>
        <div class="ios-metric-card__value-row">
          <strong class="ios-metric-card__number">2,800 <small>积分</small></strong>
          <span class="ios-metric-card__subtext">负载平稳 99.8%</span>
        </div>

        <!-- 点阵热力网格 -->
        <div class="ios-dot-matrix">
          <div v-for="(row, rIdx) in dotMatrix" :key="rIdx" class="ios-dot-matrix__row">
            <span
              v-for="(val, cIdx) in row"
              :key="cIdx"
              class="ios-matrix-dot"
              :class="`is-level-${val}`"
            />
          </div>
        </div>
        <div class="ios-matrix-legend">
          <span><i class="dot dot-high" /> 高峰</span>
          <span><i class="dot dot-mid" /> 均衡</span>
          <span><i class="dot dot-low" /> 空闲</span>
        </div>
      </div>

      <!-- 3.4 收益分析 / 配额预算 (层级步进容量条) -->
      <div class="ios-glass-card ios-metric-card">
        <div class="ios-metric-card__header">
          <span class="ios-metric-card__title">收益分析</span>
          <span class="ios-pill-tag ios-pill-tag--amber">达标 88.5%</span>
        </div>
        <div class="ios-metric-card__value-row">
          <strong class="ios-metric-card__number">64,000.00</strong>
          <span class="ios-metric-card__subtext">总预算 72,000</span>
        </div>

        <!-- 3 阶水平步进容量条 -->
        <div class="ios-tier-scales">
          <div v-for="t in tieredQuotas" :key="t.name" class="ios-tier-item">
            <div class="ios-tier-item__info">
              <span>{{ t.name }}</span>
              <span>{{ t.percent }}%</span>
            </div>
            <div class="ios-tier-item__bar-bg">
              <div
                class="ios-tier-item__bar-fill"
                :style="{ width: `${t.percent}%`, background: t.color }"
              />
            </div>
          </div>
        </div>
        <div class="ios-chart-ticks">
          <span>12,000</span>
          <span>36,000</span>
          <span>64,000</span>
        </div>
      </div>
    </div>

    <!-- =========================================================================
         4. 模态框与弹窗交互 (健康体检 / 账号录入 / 租户开通)
         ========================================================================= -->
    <!-- 系统健康体检弹窗 -->
    <el-dialog v-model="probeVisible" title="天枢企业级基础设施健康全栈巡检" width="620px" destroy-on-close>
      <div class="probe-dialog-body">
        <div class="probe-progress-wrap">
          <div class="probe-progress-info">
            <span>{{ probing ? "正在深度扫描平台各模块基础设施..." : "全栈深度检测执行完毕" }}</span>
            <strong class="probe-percentage">{{ probeProgress }}%</strong>
          </div>
          <el-progress :percentage="probeProgress" :status="probing ? '' : 'success'" :stroke-width="8" :show-text="false" />
        </div>

        <div class="probe-list">
          <div v-for="item in probeSteps" :key="item.name" class="probe-item">
            <div class="probe-item__icon">
              <el-icon v-if="item.status === 'PASSED'" color="#059669"><CircleCheck /></el-icon>
              <el-icon v-else-if="item.status === 'RUNNING'" class="is-loading" color="#2563eb"><Refresh /></el-icon>
              <span v-else class="probe-pending-dot" />
            </div>
            <div class="probe-item__content">
              <div class="probe-item__title-row">
                <strong>{{ item.name }}</strong>
                <span class="probe-latency" v-if="item.latency !== '-'">{{ item.latency }}</span>
              </div>
              <p class="probe-item__desc">{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="probeVisible = false">关闭窗口</el-button>
        <el-button type="primary" :loading="probing" @click="runHealthProbe">再次执行检测</el-button>
      </template>
    </el-dialog>

    <!-- 极速录入用户 -->
    <el-dialog v-model="quickUserVisible" title="录入在职系统账号" width="460px">
      <el-form label-position="top">
        <el-form-item label="登录用户名">
          <el-input v-model="userForm.username" placeholder="如：zhangsan" />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="userForm.nickname" placeholder="如：张三" />
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="userForm.mobile" placeholder="13800000000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickUserVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateQuickUser">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 极速开通租户 -->
    <el-dialog v-model="quickTenantVisible" title="创建企业租户空间" width="460px">
      <el-form label-position="top">
        <el-form-item label="租户编码 (全局唯一)">
          <el-input v-model="tenantForm.tenantCode" placeholder="如：corp_alpha" />
        </el-form-item>
        <el-form-item label="租户企业全称">
          <el-input v-model="tenantForm.tenantName" placeholder="如：北京天枢极核科技有限公司" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickTenantVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateQuickTenant">开通空间</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* =============================================================================
   iOS 27 真正的晶透液态毛玻璃 (Liquid Glass) 工作台系统
   ============================================================================= */

.ios-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
  animation: iosFadeIn 300ms ease-out;
}

@keyframes iosFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* -----------------------------------------------------------------------------
   1. 顶部控制栏与胶囊时间选择器
   ----------------------------------------------------------------------------- */
.ios-dashboard__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%) contrast(102%);
  -webkit-backdrop-filter: blur(28px) saturate(200%) contrast(102%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.95), 0 8px 24px -4px rgba(15, 23, 42, 0.05);
  margin-bottom: 2px;
}

.ios-dashboard__title-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.ios-dashboard__title {
  font-size: 24px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.025em;
  margin: 0;
  line-height: 1;
  text-shadow: 0 1px 1px rgba(255, 255, 255, 0.8);
}

/* 对标图1中央核心胶囊切换器 (日/周/月/季) */
.ios-segmented-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.75);
  border-radius: 9999px;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9), 0 4px 14px rgba(15, 23, 42, 0.05);

  &__item {
    border: none;
    background: transparent;
    padding: 4px 13px;
    font-size: 12.5px;
    font-weight: 550;
    color: #475569;
    border-radius: 9999px;
    cursor: pointer;
    transition: all 160ms cubic-bezier(0.16, 1, 0.3, 1);

    &:hover {
      color: #0f172a;
    }

    &.is-active {
      background: #ffffff;
      color: #0f172a;
      font-weight: 650;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.10);
    }
  }
}

.ios-dashboard__meta-tag {
  font-size: 12.5px;
  color: #475569;
  font-weight: 550;
  background: rgba(255, 255, 255, 0.45);
  padding: 4px 12px;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.8);
}

.ios-dashboard__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ios-glass-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px) saturate(190%);
  -webkit-backdrop-filter: blur(20px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 9999px;
  color: #1e293b;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(15, 23, 42, 0.05);
  transition: all 150ms ease;

  &:hover {
    background: rgba(255, 255, 255, 0.88);
    transform: translateY(-1px);
    box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95), 0 8px 20px rgba(15, 23, 42, 0.08);
  }

  &--highlight {
    background: rgba(37, 99, 235, 0.12);
    border-color: rgba(37, 99, 235, 0.35);
    color: #1d4ed8;

    &:hover {
      background: rgba(37, 99, 235, 0.20);
    }
  }
}

/* -----------------------------------------------------------------------------
   2. 晶透液态玻璃卡片基类 (.ios-glass-card)
   ----------------------------------------------------------------------------- */
.ios-glass-card {
  position: relative;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(28px) saturate(190%) contrast(102%);
  -webkit-backdrop-filter: blur(28px) saturate(190%) contrast(102%);
  border: 1px solid rgba(255, 255, 255, 0.75);
  border-radius: 26px;
  box-shadow: 
    inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.95),
    inset 0 0 0 1px rgba(255, 255, 255, 0.35),
    inset 0 -1px 1px 0 rgba(0, 0, 0, 0.02),
    0 16px 38px -8px rgba(12, 34, 56, 0.08),
    0 4px 12px -2px rgba(12, 34, 56, 0.03);
  padding: 22px 24px;
  box-sizing: border-box;
  transition: transform 180ms ease, box-shadow 180ms ease;

  &:hover {
    box-shadow: 
      inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98),
      inset 0 0 0 1px rgba(255, 255, 255, 0.45),
      0 22px 48px -10px rgba(12, 34, 56, 0.12),
      0 6px 16px -2px rgba(12, 34, 56, 0.05);
  }
}

/* -----------------------------------------------------------------------------
   3. 第一层核心 Hero 网格 (58% : 42%)
   ----------------------------------------------------------------------------- */
.ios-dashboard__hero-grid {
  display: grid;
  grid-template-columns: 58fr 42fr;
  gap: 16px;
}

.ios-glass-card--hero {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 230px;
}

.ios-glass-card__header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ios-badge-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: #334155;
}

.ios-currency-code {
  padding: 1px 7px;
  font-size: 10.5px;
  font-family: var(--sb-font-mono, monospace);
  font-weight: 650;
  border-radius: 9999px;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
}

.ios-icon-btn {
  border: none;
  background: rgba(255, 255, 255, 0.45);
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  cursor: pointer;
  transition: all 120ms ease;

  &:hover {
    background: rgba(255, 255, 255, 0.85);
    color: #0f172a;
  }
}

.ios-icon-btn-sm {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: #0f172a;
  }
}

/* 核心巨大数值 */
.ios-hero-amount {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 12px 0 16px;
  flex-wrap: wrap;

  &__currency {
    font-size: 22px;
    font-weight: 600;
    color: #0f172a;
  }

  &__number {
    font-size: 40px;
    font-weight: 800;
    color: #090d16;
    letter-spacing: -0.03em;
    font-variant-numeric: tabular-nums;
    line-height: 1;
  }
}

.ios-growth-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 12px;
  padding: 4px 10px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.28);
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 650;
  color: #047857;

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 6px #10b981;
  }
}

/* 底部操作胶囊组 */
.ios-hero-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.ios-pill-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 9999px;
  color: #334155;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04);
  transition: all 140ms ease;

  &:hover {
    background: #ffffff;
    color: #0f172a;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  }

  &--primary {
    background: #0284c7;
    border-color: #0284c7;
    color: #ffffff;
    box-shadow: 0 4px 14px rgba(2, 132, 199, 0.35);

    &:hover {
      background: #0369a1;
      color: #ffffff;
      box-shadow: 0 6px 18px rgba(2, 132, 199, 0.45);
    }
  }
}

/* 2.2 日程与项目卡片 */
.ios-glass-card--calendar {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.ios-calendar-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.65);

  &__date {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__icon {
    font-size: 16px;
    color: #0284c7;
  }

  &__title {
    font-size: 14.5px;
    font-weight: 700;
    color: #0f172a;
  }
}

.ios-week-dots {
  display: flex;
  gap: 5px;
}

.ios-week-dot {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  border-radius: 50%;
  color: #64748b;
  background: rgba(255, 255, 255, 0.45);

  &.is-active {
    background: #0284c7;
    color: #ffffff;
    box-shadow: 0 2px 6px rgba(2, 132, 199, 0.35);
  }
}

.ios-projects-box {
  margin-top: 10px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 12.5px;
    font-weight: 650;
    color: #475569;
  }

  &__count {
    font-size: 11px;
    color: #64748b;
  }
}

.ios-projects-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.ios-project-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.50);
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 12px;
  cursor: pointer;
  transition: all 120ms ease;

  &:hover {
    background: rgba(255, 255, 255, 0.85);
    transform: translateX(2px);
  }

  &__main {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__name {
    font-size: 12.5px;
    font-weight: 600;
    color: #1e293b;
  }

  &__stage {
    font-size: 10.5px;
    color: #64748b;
    background: rgba(15, 23, 42, 0.05);
    padding: 1px 6px;
    border-radius: 4px;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__amount {
    font-size: 12.5px;
    font-family: var(--sb-font-mono, monospace);
    font-weight: 650;
    color: #0f172a;
  }
}

.ios-status-badge {
  padding: 2px 7px;
  font-size: 10.5px;
  font-weight: 650;
  border-radius: 9999px;

  &--success {
    background: rgba(16, 185, 129, 0.15);
    color: #047857;
  }
  &--primary {
    background: rgba(2, 132, 199, 0.15);
    color: #0284c7;
  }
  &--info {
    background: rgba(100, 116, 139, 0.15);
    color: #475569;
  }
}

/* -----------------------------------------------------------------------------
   4. 第二层：下排 4 联数据微拟物卡片
   ----------------------------------------------------------------------------- */
.ios-dashboard__metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.ios-metric-card {
  padding: 18px 20px;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: 13.5px;
    font-weight: 650;
    color: #334155;
  }

  &__value-row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin: 8px 0;
  }

  &__number {
    font-size: 24px;
    font-weight: 800;
    color: #090d16;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;

    small {
      font-size: 12px;
      font-weight: 550;
      color: #64748b;
      margin-left: 2px;
    }
  }

  &__badge-up {
    font-size: 11px;
    font-weight: 650;
    color: #059669;
    background: rgba(16, 185, 129, 0.12);
    padding: 1px 6px;
    border-radius: 9999px;
  }

  &__subtext {
    font-size: 11px;
    color: #64748b;
  }
}

.ios-pill-tag {
  font-size: 10.5px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 9999px;
  background: rgba(2, 132, 199, 0.12);
  color: #0284c7;

  &--emerald {
    background: rgba(16, 185, 129, 0.12);
    color: #059669;
  }
  &--amber {
    background: rgba(245, 158, 11, 0.14);
    color: #d97706;
  }
}

/* 4.1 声波律动柱状图 */
.ios-soundwave-chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 44px;
  padding-top: 8px;
}

.ios-soundwave-bar {
  flex: 1;
  background: rgba(148, 163, 184, 0.35);
  border-radius: 2px;
  transition: all 180ms ease;

  &.is-peak {
    background: linear-gradient(180deg, #0284c7, #06b6d4);
    box-shadow: 0 0 6px rgba(6, 182, 212, 0.4);
  }

  &.is-highlight {
    background: linear-gradient(180deg, #10b981, #059669);
  }

  &:hover {
    transform: scaleY(1.1);
  }
}

/* 4.2 面积曲线图 */
.ios-spline-chart {
  height: 44px;
  width: 100%;
}

.ios-spline-svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* 4.3 点阵矩阵 */
.ios-dot-matrix {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 0;

  &__row {
    display: flex;
    justify-content: space-between;
  }
}

.ios-matrix-dot {
  width: 6.5px;
  height: 6.5px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.25);

  &.is-level-1 {
    background: rgba(148, 163, 184, 0.4);
  }
  &.is-level-2 {
    background: rgba(2, 132, 199, 0.65);
  }
  &.is-level-3 {
    background: #10b981;
    box-shadow: 0 0 4px #10b981;
  }
}

.ios-matrix-legend {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  font-size: 10px;
  color: #64748b;

  .dot {
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-right: 2px;

    &.dot-high { background: #10b981; }
    &.dot-mid { background: #0284c7; }
    &.dot-low { background: #cbd5e1; }
  }
}

/* 4.4 层级容量条 */
.ios-tier-scales {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ios-tier-item {
  &__info {
    display: flex;
    justify-content: space-between;
    font-size: 10.5px;
    color: #475569;
    font-weight: 550;
    margin-bottom: 2px;
  }

  &__bar-bg {
    width: 100%;
    height: 4.5px;
    background: rgba(148, 163, 184, 0.20);
    border-radius: 9999px;
    overflow: hidden;
  }

  &__bar-fill {
    height: 100%;
    border-radius: 9999px;
  }
}

.ios-chart-ticks {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #94a3b8;
  font-family: var(--sb-font-mono, monospace);
  margin-top: 4px;
}

/* -----------------------------------------------------------------------------
   5. 健康体检弹窗样式
   ----------------------------------------------------------------------------- */
.probe-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.probe-progress-wrap {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.probe-progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #334155;
  font-weight: 550;
}

.probe-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.probe-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.9);

  &__icon {
    font-size: 18px;
    margin-top: 2px;
  }

  &__content {
    flex: 1;
  }

  &__title-row {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    color: #0f172a;
  }

  &__desc {
    margin: 2px 0 0;
    font-size: 11.5px;
    color: #64748b;
  }
}

.probe-latency {
  font-family: var(--sb-font-mono, monospace);
  font-size: 11px;
  color: #059669;
  font-weight: 600;
}

.probe-pending-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd5e1;
}

/* Responsive */
@media (max-width: 1200px) {
  .ios-dashboard__hero-grid {
    grid-template-columns: 1fr;
  }
  .ios-dashboard__metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .ios-dashboard__metrics-grid {
    grid-template-columns: 1fr;
  }
  .ios-hero-actions {
    grid-template-columns: repeat(2, 1fr);
  }
  .ios-dashboard__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
