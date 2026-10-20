<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Calendar,
  Check,
  Close,
  Document,
  Lock,
  Money,
  Phone,
  Plus,
  RefreshRight,
  Share,
  TrendCharts,
  Unlock,
  UserFilled,
  Wallet
} from "@element-plus/icons-vue";
import {
  addCrmFollowUpRecord,
  approveCrmContract,
  approveCrmReceivable,
  claimCrmCustomer,
  convertClueToCustomer,
  createCrmBusiness,
  createCrmClue,
  createCrmContract,
  createCrmCustomer,
  createCrmReceivable,
  getCrmStatistics,
  listCrmBusinesses,
  listCrmClues,
  listCrmContracts,
  listCrmCustomers,
  listCrmFollowUpRecords,
  listCrmReceivables,
  returnCustomerToPool,
  toggleCustomerLock,
  updateCrmBusinessStage
} from "@/api/modules/crm";
import type {
  CrmBusinessRow,
  CrmClueRow,
  CrmContractRow,
  CrmCustomerRow,
  CrmFollowUpRecordRow,
  CrmReceivableRow,
  CrmStatistics
} from "@/api/modules/crm";

const route = useRoute();
const activeTab = ref<"customers" | "clues" | "businesses" | "contracts" | "receivables">("customers");
const loading = ref(false);

function syncTabFromRoute() {
  const p = route.path.toLowerCase();
  if (p.includes("clue")) activeTab.value = "clues";
  else if (p.includes("business")) activeTab.value = "businesses";
  else if (p.includes("contract")) activeTab.value = "contracts";
  else if (p.includes("receivable")) activeTab.value = "receivables";
  else activeTab.value = "customers";
}

watch(() => route.path, () => {
  syncTabFromRoute();
  refreshAll();
});

// 统计指标
const stats = ref<CrmStatistics>({
  totalCustomerCount: 0,
  poolCustomerCount: 0,
  totalClueCount: 0,
  totalBusinessAmount: 0,
  contractWonAmount: 0,
  receivableReceivedAmount: 0,
  receivablePendingAmount: 0
});

// 1. 客户与公海池
const customers = ref<CrmCustomerRow[]>([]);
const customerPoolFilter = ref<number>(0); // 0: 我的客户, 1: 公海池
const customerSearch = ref("");
const customerLevelFilter = ref("");
const customerDialogVisible = ref(false);
const customerForm = reactive({
  name: "",
  industry: "互联网高科技",
  level: "B",
  source: "ONLINE",
  mobile: "",
  telephone: "",
  email: "",
  address: "",
  remark: ""
});

// 2. 线索
const clues = ref<CrmClueRow[]>([]);
const clueStatusFilter = ref("");
const clueSearch = ref("");
const clueDialogVisible = ref(false);
const clueForm = reactive({
  name: "",
  contactName: "",
  mobile: "",
  telephone: "",
  email: "",
  address: "",
  source: "ONLINE",
  remark: ""
});

// 3. 商机
const businesses = ref<CrmBusinessRow[]>([]);
const businessStageFilter = ref("");
const businessSearch = ref("");
const businessDialogVisible = ref(false);
const businessForm = reactive({
  name: "",
  customerId: 1,
  totalPrice: 100000,
  dealDate: "",
  stage: "INITIAL",
  winRate: 20,
  remark: ""
});

const stageDialogVisible = ref(false);
const currentBusiness = ref<CrmBusinessRow | null>(null);
const stageForm = reactive({
  stage: "PROPOSAL",
  winRate: 60,
  lossReason: ""
});

// 4. 合同
const contracts = ref<CrmContractRow[]>([]);
const contractStatusFilter = ref("");
const contractSearch = ref("");
const contractDialogVisible = ref(false);
const contractForm = reactive({
  name: "",
  customerId: 1,
  businessId: 1,
  totalPrice: 200000,
  startTime: "",
  endTime: "",
  remark: ""
});

// 5. 回款
const receivables = ref<CrmReceivableRow[]>([]);
const receivableStatusFilter = ref("");
const receivableDialogVisible = ref(false);
const receivableForm = reactive({
  contractId: 1,
  price: 50000,
  returnTime: "",
  payType: "BANK_TRANSFER",
  remark: ""
});

// 6. 跟进记录抽屉
const followUpDrawerVisible = ref(false);
const currentFollowUpTarget = ref<{ bizType: string; bizId: number; title: string }>({
  bizType: "CUSTOMER",
  bizId: 0,
  title: ""
});
const followUpRecords = ref<CrmFollowUpRecordRow[]>([]);
const followUpForm = reactive({
  followType: "CALL",
  content: "",
  nextContactTime: ""
});

// 加载统计
async function loadStats() {
  try {
    const res = await getCrmStatistics();
    if (res.data) stats.value = res.data;
  } catch (err: any) {
    console.error("Failed to load CRM stats:", err);
  }
}

// 加载客户列表
async function loadCustomers() {
  loading.value = true;
  try {
    const res = await listCrmCustomers({
      isPool: customerPoolFilter.value,
      keyword: customerSearch.value,
      level: customerLevelFilter.value
    });
    customers.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "加载客户列表失败");
  } finally {
    loading.value = false;
  }
}

// 加载线索列表
async function loadClues() {
  loading.value = true;
  try {
    const res = await listCrmClues({
      status: clueStatusFilter.value,
      keyword: clueSearch.value
    });
    clues.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "加载线索失败");
  } finally {
    loading.value = false;
  }
}

// 加载商机列表
async function loadBusinesses() {
  loading.value = true;
  try {
    const res = await listCrmBusinesses({
      stage: businessStageFilter.value,
      keyword: businessSearch.value
    });
    businesses.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "加载商机失败");
  } finally {
    loading.value = false;
  }
}

// 加载合同列表
async function loadContracts() {
  loading.value = true;
  try {
    const res = await listCrmContracts({
      status: contractStatusFilter.value,
      keyword: contractSearch.value
    });
    contracts.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "加载合同失败");
  } finally {
    loading.value = false;
  }
}

// 加载回款列表
async function loadReceivables() {
  loading.value = true;
  try {
    const res = await listCrmReceivables({
      status: receivableStatusFilter.value
    });
    receivables.value = res.data || [];
  } catch (err: any) {
    ElMessage.error(err.message || "加载回款失败");
  } finally {
    loading.value = false;
  }
}

function refreshAll() {
  loadStats();
  if (activeTab.value === "customers") loadCustomers();
  else if (activeTab.value === "clues") loadClues();
  else if (activeTab.value === "businesses") loadBusinesses();
  else if (activeTab.value === "contracts") loadContracts();
  else if (activeTab.value === "receivables") loadReceivables();
}

// 客户操作
async function handleCreateCustomer() {
  if (!customerForm.name.trim()) {
    ElMessage.warning("请填写客户名称");
    return;
  }
  try {
    await createCrmCustomer(customerForm);
    ElMessage.success("客户建档成功");
    customerDialogVisible.value = false;
    loadCustomers();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "创建客户失败");
  }
}

async function handleClaim(row: CrmCustomerRow) {
  try {
    await claimCrmCustomer(row.id);
    ElMessage.success(`抢单成功！客户 [${row.name}] 已转入您的名下`);
    loadCustomers();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "抢单失败");
  }
}

async function handleReturnToPool(row: CrmCustomerRow) {
  try {
    await ElMessageBox.confirm(`确认将客户 [${row.name}] 退回公海池吗？公海池内所有销售人员均可重新认领抢单。`, "退回公海确认", {
      confirmButtonText: "确认退回",
      cancelButtonText: "取消",
      type: "warning"
    });
    await returnCustomerToPool(row.id);
    ElMessage.success("已退回公海池");
    loadCustomers();
    loadStats();
  } catch (err: any) {
    if (err !== "cancel") ElMessage.error(err.message || "操作失败");
  }
}

async function handleToggleLock(row: CrmCustomerRow) {
  try {
    await toggleCustomerLock(row.id);
    ElMessage.success(row.lockStatus === 1 ? "已解除客户锁定" : "已锁定客户（锁定后不可退入公海）");
    loadCustomers();
  } catch (err: any) {
    ElMessage.error(err.message || "切换锁定状态失败");
  }
}

// 线索操作
async function handleCreateClue() {
  if (!clueForm.name.trim() || !clueForm.contactName.trim()) {
    ElMessage.warning("请填写线索名称与联系人");
    return;
  }
  try {
    await createCrmClue(clueForm);
    ElMessage.success("线索录入成功");
    clueDialogVisible.value = false;
    loadClues();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "创建线索失败");
  }
}

async function handleConvertClue(row: CrmClueRow) {
  try {
    await ElMessageBox.confirm(`确认将线索 [${row.name}] 一键转为正式企业客户吗？`, "线索转化", {
      confirmButtonText: "确认转化",
      cancelButtonText: "取消",
      type: "info"
    });
    await convertClueToCustomer(row.id);
    ElMessage.success("转化成功！已自动建档为正式客户并建立跟踪记录");
    loadClues();
    loadStats();
  } catch (err: any) {
    if (err !== "cancel") ElMessage.error(err.message || "转化失败");
  }
}

// 商机操作
async function handleCreateBusiness() {
  if (!businessForm.name.trim()) {
    ElMessage.warning("请填写商机名称");
    return;
  }
  try {
    await createCrmBusiness(businessForm);
    ElMessage.success("商机创建成功");
    businessDialogVisible.value = false;
    loadBusinesses();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "创建商机失败");
  }
}

function openStageDialog(row: CrmBusinessRow) {
  currentBusiness.value = row;
  stageForm.stage = row.stage;
  stageForm.winRate = row.winRate;
  stageForm.lossReason = row.lossReason || "";
  stageDialogVisible.value = true;
}

async function handleUpdateStage() {
  if (!currentBusiness.value) return;
  try {
    await updateCrmBusinessStage(currentBusiness.value.id, stageForm);
    ElMessage.success("商机阶段流转更新成功");
    stageDialogVisible.value = false;
    loadBusinesses();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "更新商机阶段失败");
  }
}

// 合同操作
async function handleCreateContract() {
  if (!contractForm.name.trim()) {
    ElMessage.warning("请填写合同名称");
    return;
  }
  try {
    await createCrmContract(contractForm);
    ElMessage.success("合同拟定成功，已发起审批流");
    contractDialogVisible.value = false;
    loadContracts();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "创建合同失败");
  }
}

async function handleApproveContract(row: CrmContractRow, approve: boolean) {
  try {
    await approveCrmContract(row.id, approve);
    ElMessage.success(approve ? "合同审批通过并已正式生效" : "合同已驳回");
    loadContracts();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "审批失败");
  }
}

// 回款操作
async function handleCreateReceivable() {
  try {
    await createCrmReceivable(receivableForm);
    ElMessage.success("回款单据已提交，等待财务复核");
    receivableDialogVisible.value = false;
    loadReceivables();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "提交回款失败");
  }
}

async function handleApproveReceivable(row: CrmReceivableRow) {
  try {
    await approveCrmReceivable(row.id);
    ElMessage.success("财务到账复核完成，已完成该笔回款核销");
    loadReceivables();
    loadStats();
  } catch (err: any) {
    ElMessage.error(err.message || "核销失败");
  }
}

// 跟进记录抽屉
async function openFollowUpDrawer(bizType: string, bizId: number, title: string) {
  currentFollowUpTarget.value = { bizType, bizId, title };
  followUpForm.content = "";
  followUpForm.nextContactTime = "";
  followUpDrawerVisible.value = true;
  await loadFollowUpRecords();
}

async function loadFollowUpRecords() {
  try {
    const res = await listCrmFollowUpRecords({
      bizType: currentFollowUpTarget.value.bizType,
      bizId: currentFollowUpTarget.value.bizId
    });
    followUpRecords.value = res.data || [];
  } catch (err: any) {
    ElMessage.error("加载跟进记录失败");
  }
}

async function handleAddFollowUp() {
  if (!followUpForm.content.trim()) {
    ElMessage.warning("请填写本次沟通跟进内容");
    return;
  }
  try {
    await addCrmFollowUpRecord({
      bizType: currentFollowUpTarget.value.bizType,
      bizId: currentFollowUpTarget.value.bizId,
      followType: followUpForm.followType,
      content: followUpForm.content,
      nextContactTime: followUpForm.nextContactTime || undefined
    });
    ElMessage.success("跟进记录写入成功");
    followUpForm.content = "";
    await loadFollowUpRecords();
    refreshAll();
  } catch (err: any) {
    ElMessage.error(err.message || "添加跟进失败");
  }
}

onMounted(() => {
  syncTabFromRoute();
  refreshAll();
});
</script>

<template>
  <div class="crm-container">
    <!-- 顶部 KPI 指标总览栏 (Sleek 40px Bar) -->
    <div class="crm-telemetry-bar">
      <div class="telemetry-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><UserFilled /></el-icon>
          <span class="label">客户/公海</span>
          <span class="value">{{ stats.totalCustomerCount }} / {{ stats.poolCustomerCount }}</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-amber"><Phone /></el-icon>
          <span class="label">活跃线索</span>
          <span class="value">{{ stats.totalClueCount }} 条</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-emerald"><Money /></el-icon>
          <span class="label">商机管线</span>
          <span class="value">¥{{ (stats.totalBusinessAmount || 0).toLocaleString() }}</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-indigo"><Document /></el-icon>
          <span class="label">已签合同</span>
          <span class="value">¥{{ (stats.contractWonAmount || 0).toLocaleString() }}</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><Wallet /></el-icon>
          <span class="label">已回款额</span>
          <span class="value">¥{{ (stats.receivableReceivedAmount || 0).toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <!-- 核心业务多标签控制台 -->
    <el-card shadow="never" class="main-card">
      <el-tabs v-model="activeTab" @tab-change="refreshAll">
        <!-- 标签 1：客户档案与公海池 -->
        <el-tab-pane label="客户档案与公海池" name="customers">
          <div class="tab-toolbar">
            <div class="left-actions">
              <el-radio-group v-model="customerPoolFilter" @change="loadCustomers" size="default">
                <el-radio-button :value="0">我的客户</el-radio-button>
                <el-radio-button :value="1">公海池客户 (待抢单)</el-radio-button>
              </el-radio-group>
              <el-select v-model="customerLevelFilter" placeholder="客户等级" clearable style="width: 130px; margin-left: 12px" @change="loadCustomers">
                <el-option label="A 级核心" value="A" />
                <el-option label="B 级重要" value="B" />
                <el-option label="C 级普通" value="C" />
                <el-option label="D 级意向" value="D" />
              </el-select>
              <el-input v-model="customerSearch" placeholder="搜索客户名/编码/手机" clearable style="width: 220px; margin-left: 12px" @keyup.enter="loadCustomers" />
              <el-button type="primary" :icon="RefreshRight" @click="loadCustomers" style="margin-left: 8px">查询</el-button>
            </div>
            <div class="right-actions">
              <el-button type="primary" :icon="Plus" @click="customerDialogVisible = true">新建客户</el-button>
            </div>
          </div>

          <el-table :data="customers" v-loading="loading" stripe border style="width: 100%">
            <el-table-column prop="customerCode" label="客户编号" width="160" />
            <el-table-column prop="name" label="企业客户名称" min-width="200">
              <template #default="{ row }">
                <span class="customer-name-link" @click="openFollowUpDrawer('CUSTOMER', row.id, row.name)">{{ row.name }}</span>
                <el-tag size="small" type="danger" v-if="row.lockStatus === 1" style="margin-left: 6px">锁定</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="level" label="客户级别" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.level === 'A' ? 'danger' : row.level === 'B' ? 'warning' : 'info'" effect="dark">
                  {{ row.level }} 级
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="industry" label="所属行业" width="130" />
            <el-table-column prop="mobile" label="联系电话" width="130" />
            <el-table-column prop="dealStatus" label="成交状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.dealStatus === 1 ? 'success' : 'info'">
                  {{ row.dealStatus === 1 ? "已成交" : "未成交" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ownerUserName" label="当前归属" width="120">
              <template #default="{ row }">
                <span :class="row.isPool === 1 ? 'pool-owner' : 'active-owner'">{{ row.ownerUserName || '公海池' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastContactTime" label="最后跟进时间" width="160" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.isPool === 1" link type="success" size="small" :icon="TrendCharts" @click="handleClaim(row)">抢单认领</el-button>
                <template v-else>
                  <el-button link type="primary" size="small" :icon="Calendar" @click="openFollowUpDrawer('CUSTOMER', row.id, row.name)">跟进记录</el-button>
                  <el-button link type="warning" size="small" :icon="row.lockStatus === 1 ? Unlock : Lock" @click="handleToggleLock(row)">
                    {{ row.lockStatus === 1 ? '解锁' : '锁定' }}
                  </el-button>
                  <el-button link type="danger" size="small" :icon="Share" @click="handleReturnToPool(row)">退回公海</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 标签 2：销售线索跟进 -->
        <el-tab-pane label="销售线索跟进" name="clues">
          <div class="tab-toolbar">
            <div class="left-actions">
              <el-select v-model="clueStatusFilter" placeholder="线索状态" clearable style="width: 140px" @change="loadClues">
                <el-option label="未跟进" value="UNFOLLOWED" />
                <el-option label="跟进中" value="FOLLOWING" />
                <el-option label="已转化为客户" value="CONVERTED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
              <el-input v-model="clueSearch" placeholder="搜索线索名称/联系人/手机" clearable style="width: 240px; margin-left: 12px" @keyup.enter="loadClues" />
              <el-button type="primary" :icon="RefreshRight" @click="loadClues" style="margin-left: 8px">查询</el-button>
            </div>
            <div class="right-actions">
              <el-button type="primary" :icon="Plus" @click="clueDialogVisible = true">录入线索</el-button>
            </div>
          </div>

          <el-table :data="clues" v-loading="loading" stripe border style="width: 100%">
            <el-table-column prop="name" label="线索名称/项目意向" min-width="180" />
            <el-table-column prop="contactName" label="联系人" width="120" />
            <el-table-column prop="mobile" label="手机号码" width="130" />
            <el-table-column prop="source" label="渠道来源" width="120" />
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'CONVERTED' ? 'success' : row.status === 'FOLLOWING' ? 'warning' : 'info'">
                  {{ row.status === 'CONVERTED' ? '已转化客户' : row.status === 'FOLLOWING' ? '持续跟进中' : row.status === 'UNFOLLOWED' ? '未跟进' : '已关闭' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ownerUserName" label="跟进责任人" width="120" />
            <el-table-column prop="nextContactTime" label="预约下次跟进" width="160" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="Calendar" @click="openFollowUpDrawer('CLUE', row.id, row.name)">写跟进</el-button>
                <el-button v-if="row.status !== 'CONVERTED'" link type="success" size="small" :icon="Check" @click="handleConvertClue(row)">一键转客户</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 标签 3：商机阶段漏斗 -->
        <el-tab-pane label="商机阶段漏斗" name="businesses">
          <div class="tab-toolbar">
            <div class="left-actions">
              <el-select v-model="businessStageFilter" placeholder="阶段筛选" clearable style="width: 140px" @change="loadBusinesses">
                <el-option label="初步接洽 (10%)" value="INITIAL" />
                <el-option label="方案报价 (60%)" value="PROPOSAL" />
                <el-option label="商务谈判 (80%)" value="NEGOTIATION" />
                <el-option label="赢单成交 (100%)" value="WON" />
                <el-option label="输单归档 (0%)" value="LOST" />
              </el-select>
              <el-input v-model="businessSearch" placeholder="搜索商机/客户" clearable style="width: 220px; margin-left: 12px" @keyup.enter="loadBusinesses" />
              <el-button type="primary" :icon="RefreshRight" @click="loadBusinesses" style="margin-left: 8px">查询</el-button>
            </div>
            <div class="right-actions">
              <el-button type="primary" :icon="Plus" @click="businessDialogVisible = true">新建商机</el-button>
            </div>
          </div>

          <el-table :data="businesses" v-loading="loading" stripe border style="width: 100%">
            <el-table-column prop="businessCode" label="商机编号" width="160" />
            <el-table-column prop="name" label="商机名称" min-width="180" />
            <el-table-column prop="customerName" label="关联企业客户" min-width="160" />
            <el-table-column prop="totalPrice" label="预计金额 (元)" width="150" align="right">
              <template #default="{ row }">
                <span class="money-text">¥{{ Number(row.totalPrice).toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stage" label="当前所处阶段" width="140" align="center">
              <template #default="{ row }">
                <el-tag :type="row.stage === 'WON' ? 'success' : row.stage === 'LOST' ? 'danger' : 'primary'" effect="plain">
                  {{ row.stage === 'INITIAL' ? '初步接洽' : row.stage === 'PROPOSAL' ? '方案报价' : row.stage === 'NEGOTIATION' ? '商务谈判' : row.stage === 'WON' ? '赢单成交' : '输单' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="winRate" label="预测赢率" width="140">
              <template #default="{ row }">
                <el-progress :percentage="row.winRate || 0" :status="row.stage === 'WON' ? 'success' : row.stage === 'LOST' ? 'exception' : ''" />
              </template>
            </el-table-column>
            <el-table-column prop="dealDate" label="预计结单日" width="120" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :icon="TrendCharts" @click="openStageDialog(row)">推进阶段</el-button>
                <el-button link type="info" size="small" :icon="Calendar" @click="openFollowUpDrawer('BUSINESS', row.id, row.name)">跟进</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 标签 4：商务合同审批 -->
        <el-tab-pane label="商务合同管控" name="contracts">
          <div class="tab-toolbar">
            <div class="left-actions">
              <el-select v-model="contractStatusFilter" placeholder="合同状态" clearable style="width: 140px" @change="loadContracts">
                <el-option label="审批中" value="APPROVING" />
                <el-option label="已生效" value="APPROVED" />
                <el-option label="已驳回" value="REJECTED" />
                <el-option label="草稿" value="DRAFT" />
              </el-select>
              <el-input v-model="contractSearch" placeholder="搜索合同编号/名称/客户" clearable style="width: 240px; margin-left: 12px" @keyup.enter="loadContracts" />
              <el-button type="primary" :icon="RefreshRight" @click="loadContracts" style="margin-left: 8px">查询</el-button>
            </div>
            <div class="right-actions">
              <el-button type="primary" :icon="Plus" @click="contractDialogVisible = true">拟定新合同</el-button>
            </div>
          </div>

          <el-table :data="contracts" v-loading="loading" stripe border style="width: 100%">
            <el-table-column prop="contractNo" label="合同编号" width="160" />
            <el-table-column prop="name" label="合同名称" min-width="200" />
            <el-table-column prop="customerName" label="客户名录" min-width="160" />
            <el-table-column prop="totalPrice" label="合同总金额 (元)" width="150" align="right">
              <template #default="{ row }">
                <span class="money-text">¥{{ Number(row.totalPrice).toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="合同周期" width="200">
              <template #default="{ row }">
                {{ row.startTime }} ~ {{ row.endTime }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="审批状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'APPROVING' ? 'warning' : 'danger'">
                  {{ row.status === 'APPROVED' ? '已审批生效' : row.status === 'APPROVING' ? '审批流中' : row.status === 'REJECTED' ? '已驳回' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'APPROVING'">
                  <el-button link type="success" size="small" :icon="Check" @click="handleApproveContract(row, true)">通过</el-button>
                  <el-button link type="danger" size="small" :icon="Close" @click="handleApproveContract(row, false)">驳回</el-button>
                </template>
                <el-button link type="primary" size="small" :icon="Calendar" @click="openFollowUpDrawer('CONTRACT', row.id, row.name)">跟进</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 标签 5：财务回款结算 -->
        <el-tab-pane label="财务回款结算" name="receivables">
          <div class="tab-toolbar">
            <div class="left-actions">
              <el-select v-model="receivableStatusFilter" placeholder="核销状态" clearable style="width: 140px" @change="loadReceivables">
                <el-option label="已核销到账" value="APPROVED" />
                <el-option label="待财务复核" value="PENDING" />
              </el-select>
              <el-button type="primary" :icon="RefreshRight" @click="loadReceivables" style="margin-left: 12px">刷新</el-button>
            </div>
            <div class="right-actions">
              <el-button type="primary" :icon="Plus" @click="receivableDialogVisible = true">录入回款</el-button>
            </div>
          </div>

          <el-table :data="receivables" v-loading="loading" stripe border style="width: 100%">
            <el-table-column prop="receivableNo" label="回款单号" width="160" />
            <el-table-column prop="contractName" label="对应商务合同" min-width="180" />
            <el-table-column prop="customerName" label="付款方企业" min-width="160" />
            <el-table-column prop="price" label="回款金额 (元)" width="150" align="right">
              <template #default="{ row }">
                <span class="money-text green">¥{{ Number(row.price).toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="payType" label="结算通道" width="120" align="center">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.payType === 'BANK_TRANSFER' ? '对公银行转账' : row.payType === 'WECHAT' ? '微信支付' : '支付宝' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="returnTime" label="到账日期" width="130" />
            <el-table-column prop="status" label="核销状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : 'warning'">
                  {{ row.status === 'APPROVED' ? '已核销' : '待财务审核' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" link type="success" size="small" :icon="Check" @click="handleApproveReceivable(row)">确认到账</el-button>
                <span v-else style="color: #909399; font-size: 13px">已到账归档</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 弹窗 1：新建客户 -->
    <el-dialog v-model="customerDialogVisible" title="录入企业客户" width="560px">
      <el-form :model="customerForm" label-width="100px">
        <el-form-item label="企业全称" required>
          <el-input v-model="customerForm.name" placeholder="请输入企业法定全称" />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-select v-model="customerForm.industry" style="width: 100%">
            <el-option label="互联网高科技" value="互联网高科技" />
            <el-option label="智能高端制造" value="智能高端制造" />
            <el-option label="央国企数字化" value="央国企数字化" />
            <el-option label="新能源与电力" value="新能源与电力" />
            <el-option label="现代智慧物流" value="现代智慧物流" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户等级">
          <el-radio-group v-model="customerForm.level">
            <el-radio value="A">A级 (重点客户)</el-radio>
            <el-radio value="B">B级 (重要客户)</el-radio>
            <el-radio value="C">C级 (普通意向)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="customerForm.mobile" placeholder="负责人或对接人联系电话" />
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="customerForm.address" placeholder="公司经营或发票寄送地址" />
        </el-form-item>
        <el-form-item label="客户备注">
          <el-input v-model="customerForm.remark" type="textarea" :rows="2" placeholder="客户背景与核心诉求描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateCustomer">确认建档</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：新建线索 -->
    <el-dialog v-model="clueDialogVisible" title="录入销售线索" width="540px">
      <el-form :model="clueForm" label-width="100px">
        <el-form-item label="意向项目名" required>
          <el-input v-model="clueForm.name" placeholder="例如：某某智慧物流平台采购" />
        </el-form-item>
        <el-form-item label="联系人姓名" required>
          <el-input v-model="clueForm.contactName" placeholder="对接人姓名" />
        </el-form-item>
        <el-form-item label="联系手机" required>
          <el-input v-model="clueForm.mobile" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="渠道来源">
          <el-select v-model="clueForm.source" style="width: 100%">
            <el-option label="官网在线咨询" value="ONLINE" />
            <el-option label="合作伙伴转介绍" value="REFERRAL" />
            <el-option label="行业展会峰会" value="EXHIBITION" />
            <el-option label="电话主动陌拜" value="COLD_CALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="沟通备忘">
          <el-input v-model="clueForm.remark" type="textarea" :rows="2" placeholder="初次沟通意向与痛点记录" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clueDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateClue">录入线索</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：新建商机 -->
    <el-dialog v-model="businessDialogVisible" title="新建销售商机" width="540px">
      <el-form :model="businessForm" label-width="100px">
        <el-form-item label="商机名称" required>
          <el-input v-model="businessForm.name" placeholder="例如：华润二期智能数字看板" />
        </el-form-item>
        <el-form-item label="关联客户" required>
          <el-select v-model="businessForm.customerId" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计金额">
          <el-input-number v-model="businessForm.totalPrice" :min="1000" :step="10000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="businessForm.stage" style="width: 100%">
            <el-option label="初步接洽 (10%)" value="INITIAL" />
            <el-option label="方案报价 (60%)" value="PROPOSAL" />
            <el-option label="商务谈判 (80%)" value="NEGOTIATION" />
            <el-option label="赢单成交 (100%)" value="WON" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计结单日">
          <el-date-picker v-model="businessForm.dealDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="businessDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateBusiness">保存商机</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 4：商机阶段推进 -->
    <el-dialog v-model="stageDialogVisible" title="推进商机销售阶段" width="500px">
      <el-form :model="stageForm" label-width="90px">
        <el-form-item label="推进至阶段">
          <el-select v-model="stageForm.stage" style="width: 100%">
            <el-option label="初步接洽 (10%)" value="INITIAL" />
            <el-option label="方案报价 (60%)" value="PROPOSAL" />
            <el-option label="商务谈判 (80%)" value="NEGOTIATION" />
            <el-option label="赢单成交 (100%)" value="WON" />
            <el-option label="输单归档 (0%)" value="LOST" />
          </el-select>
        </el-form-item>
        <el-form-item label="预测赢率 %">
          <el-slider v-model="stageForm.winRate" :step="5" :max="100" show-input />
        </el-form-item>
        <el-form-item v-if="stageForm.stage === 'LOST'" label="输单原因">
          <el-input v-model="stageForm.lossReason" placeholder="例如价格过高、竞品抢单等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateStage">更新推进</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 5：拟定合同 -->
    <el-dialog v-model="contractDialogVisible" title="拟定商务合同" width="560px">
      <el-form :model="contractForm" label-width="100px">
        <el-form-item label="合同名称" required>
          <el-input v-model="contractForm.name" placeholder="请输入合同标准命名" />
        </el-form-item>
        <el-form-item label="签约客户" required>
          <el-select v-model="contractForm.customerId" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联商机">
          <el-select v-model="contractForm.businessId" style="width: 100%">
            <el-option v-for="b in businesses" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同总金额">
          <el-input-number v-model="contractForm.totalPrice" :min="1000" :step="10000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="contractForm.startTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="contractForm.endTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contractDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateContract">发起审批并拟定</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 6：录入回款 -->
    <el-dialog v-model="receivableDialogVisible" title="录入财务回款" width="500px">
      <el-form :model="receivableForm" label-width="90px">
        <el-form-item label="关联合同" required>
          <el-select v-model="receivableForm.contractId" style="width: 100%">
            <el-option v-for="ct in contracts" :key="ct.id" :label="ct.name" :value="ct.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="回款金额">
          <el-input-number v-model="receivableForm.price" :min="100" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结算通道">
          <el-select v-model="receivableForm.payType" style="width: 100%">
            <el-option label="公账银行转账" value="BANK_TRANSFER" />
            <el-option label="企业微信支付" value="WECHAT" />
            <el-option label="支付宝结算" value="ALIPAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="到账日期">
          <el-date-picker v-model="receivableForm.returnTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receivableDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateReceivable">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 抽屉：全生命周期跟进记录与时间轴 -->
    <el-drawer v-model="followUpDrawerVisible" :title="'跟进轨迹时间轴 - ' + currentFollowUpTarget.title" size="460px">
      <div class="follow-up-drawer-content">
        <!-- 新增跟进输入框 -->
        <el-card shadow="never" class="add-follow-up-box">
          <div class="follow-up-type-select">
            <el-radio-group v-model="followUpForm.followType" size="small">
              <el-radio-button value="CALL">电话</el-radio-button>
              <el-radio-button value="VISIT">上门拜访</el-radio-button>
              <el-radio-button value="WECHAT">微信</el-radio-button>
              <el-radio-button value="EMAIL">邮件</el-radio-button>
            </el-radio-group>
          </div>
          <el-input
            v-model="followUpForm.content"
            type="textarea"
            :rows="3"
            placeholder="记录本次沟通纪要、客户痛点、方案报价反馈等..."
            style="margin-top: 10px"
          />
          <div class="follow-up-footer">
            <el-date-picker
              v-model="followUpForm.nextContactTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="预约下次联系时间"
              size="small"
              style="width: 200px"
            />
            <el-button type="primary" size="small" @click="handleAddFollowUp">提交记录</el-button>
          </div>
        </el-card>

        <!-- 历史时间轴 -->
        <div class="timeline-wrapper">
          <el-timeline v-if="followUpRecords.length > 0">
            <el-timeline-item
              v-for="item in followUpRecords"
              :key="item.id"
              :timestamp="item.createdAt"
              placement="top"
              type="primary"
            >
              <el-card shadow="hover" class="timeline-card">
                <div class="timeline-header">
                  <el-tag size="small" effect="plain">{{ item.followType }}</el-tag>
                  <span class="timeline-operator">跟进人: {{ item.contactUserName }}</span>
                </div>
                <div class="timeline-content">{{ item.content }}</div>
                <div v-if="item.nextContactTime" class="timeline-next-time">
                  下次联系: {{ item.nextContactTime }}
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无历史跟进记录" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.crm-container {
  padding: 16px;
  background-color: var(--el-bg-color-page, #f5f7fa);
  min-height: calc(100vh - 84px);
}

.crm-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.03);
  margin-bottom: 16px;

  .telemetry-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .icon-blue { color: #0284c7; }
    .icon-amber { color: #d97706; }
    .icon-emerald { color: #16a34a; }
    .icon-indigo { color: #4f46e5; }
    .icon-purple { color: #9333ea; }

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }
  }

  .divider {
    width: 1px;
    height: 14px;
    background: #e2e8f0;
  }
}

.main-card {
  border-radius: 8px;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.left-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.customer-name-link {
  color: #0284c7;
  font-weight: 600;
  cursor: pointer;
}

.customer-name-link:hover {
  text-decoration: underline;
}

.pool-owner {
  color: #e11d48;
  font-weight: 500;
}

.active-owner {
  color: #475569;
}

.money-text {
  font-weight: 700;
  color: #0284c7;
  font-variant-numeric: tabular-nums;
}

.money-text.green {
  color: #16a34a;
}

.add-follow-up-box {
  margin-bottom: 20px;
  background: #f8fafc;
}

.follow-up-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.timeline-wrapper {
  margin-top: 10px;
  max-height: calc(100vh - 330px);
  overflow-y: auto;
  padding-right: 6px;
}

.timeline-card {
  margin-bottom: 8px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.timeline-operator {
  font-size: 12px;
  color: #64748b;
}

.timeline-content {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}

.timeline-next-time {
  margin-top: 8px;
  font-size: 12px;
  color: #0284c7;
  font-weight: 500;
}
</style>
