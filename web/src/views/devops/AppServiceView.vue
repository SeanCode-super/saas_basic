<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Platform,
  Refresh,
  Timer,
  VideoPlay,
  VideoPause,
  RefreshRight,
  WarningFilled,
  CircleCheck,
  CircleClose,
  Cpu,
  Coin,
  SetUp,
  Upload,
  Download,
  Document,
  Search,
  Delete,
  Edit,
  Close,
  Plus,
  Loading,
  Connection,
  Folder,
  Box,
  CaretRight
} from "@element-plus/icons-vue";
import {
  fetchAppServices,
  startAppService,
  stopAppService,
  restartAppService,
  killAppService,
  triggerAppBuild,
  fetchAppBuildRecords,
  fetchAppBuildLogs,
  fetchAppRecentLogs,
  fetchAppArtifacts,
  uploadAppArtifact,
  rollbackAppArtifact,
  saveAppService,
  deleteAppService,
  type AppServiceItem,
  type AppBuildRecordItem,
  type AppArtifactItem
} from "@/api/modules/devops";

// ==================== 1. 数据状态 ====================
const loading = ref(false);
const serviceList = ref<AppServiceItem[]>([]);
const autoRefreshSeconds = ref(5);
let timerId: any = null;

// 指标聚合
const totalServices = computed(() => serviceList.value.length);
const runningServices = computed(() => serviceList.value.filter(s => s.status === 'RUNNING').length);
const totalMemoryMb = computed(() => {
  const bytes = serviceList.value.reduce((acc, cur) => acc + (cur.memoryRssBytes || 0), 0);
  return (bytes / (1024 * 1024)).toFixed(1);
});

// ==================== 2. 终端日志流视窗 ====================
const terminalVisible = ref(false);
const terminalService = ref<AppServiceItem | null>(null);
const terminalLogs = ref<string[]>([]);
const terminalSearch = ref("");
const autoScroll = ref(true);
const terminalBodyRef = ref<HTMLElement | null>(null);
let sseSource: EventSource | null = null;

// ==================== 3. 自动化构建抽屉 ====================
const buildDrawerVisible = ref(false);
const buildService = ref<AppServiceItem | null>(null);
const buildRecords = ref<AppBuildRecordItem[]>([]);
const activeBuildRecord = ref<AppBuildRecordItem | null>(null);
const liveBuildLogs = ref<string[]>([]);
const customBuildCmd = ref("");
const buildExecuting = ref(false);
let buildLogTimer: any = null;

// ==================== 4. 制品归档与版本回滚弹窗 ====================
const artifactModalVisible = ref(false);
const artifactService = ref<AppServiceItem | null>(null);
const artifactList = ref<AppArtifactItem[]>([]);
const artifactLoading = ref(false);
const uploadRemark = ref("");
const selectedFile = ref<File | null>(null);
const uploadSubmitting = ref(false);

// ==================== 5. 服务配置编辑弹窗 ====================
const editDialogVisible = ref(false);
const editForm = ref<Partial<AppServiceItem>>({
  serviceType: "JAR",
  driverType: "HOST_PROCESS",
  serviceGroup: "PLATFORM",
  autoRestart: 0,
  port: 8080
});

// ==================== 6. 核心业务操作 ====================
async function loadServices(silent = false) {
  if (!silent) loading.value = true;
  try {
    serviceList.value = await fetchAppServices();
  } catch (err: any) {
    if (!silent) ElMessage.error("获取服务列表失败: " + (err?.message || "网络错误"));
  } finally {
    if (!silent) loading.value = false;
  }
}

function handleAutoRefreshChange(val: number) {
  if (timerId) clearInterval(timerId);
  if (val > 0) {
    timerId = setInterval(() => {
      loadServices(true);
    }, val * 1000);
  }
}

// 启停控制
async function handleStart(service: AppServiceItem) {
  try {
    ElMessage.info(`正在下发启动指令 [${service.serviceName}]...`);
    const res = await startAppService(service.id);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
    }
    await loadServices(true);
  } catch (err: any) {
    ElMessage.error("启动失败: " + (err?.message || "未知错误"));
  }
}

async function handleStop(service: AppServiceItem, timeout = 15) {
  try {
    await ElMessageBox.confirm(`确认停止服务 [${service.serviceName}] 吗？`, "优雅停止确认", {
      type: "warning",
      confirmButtonText: "确认停止",
      cancelButtonText: "取消"
    });
    ElMessage.info(`正在停止服务 [${service.serviceName}]...`);
    const res = await stopAppService(service.id, timeout);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
    }
    await loadServices(true);
  } catch (err) {
    // cancelled
  }
}

async function handleRestart(service: AppServiceItem) {
  try {
    await ElMessageBox.confirm(`确认重启服务 [${service.serviceName}] 吗？`, "重启确认", {
      type: "warning",
      confirmButtonText: "确认重启",
      cancelButtonText: "取消"
    });
    ElMessage.info(`正在重启服务 [${service.serviceName}]...`);
    const res = await restartAppService(service.id, 15);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
    }
    await loadServices(true);
  } catch (err) {
    // cancelled
  }
}

async function handleKill(service: AppServiceItem) {
  try {
    await ElMessageBox.confirm(`强制杀死进程 (SIGKILL) 将立即释放端口，但可能中断进行中的事务。确认强杀 [${service.serviceName}] 吗？`, "强制终止警告", {
      type: "error",
      confirmButtonText: "立即强杀",
      cancelButtonText: "取消"
    });
    const res = await killAppService(service.id);
    if (res.success) {
      ElMessage.warning(res.message);
    } else {
      ElMessage.error(res.message);
    }
    await loadServices(true);
  } catch (err) {
    // cancelled
  }
}

// ==================== 7. 终端日志流处理 ====================
async function openTerminal(service: AppServiceItem) {
  terminalService.value = service;
  terminalLogs.value = [];
  terminalVisible.value = true;
  terminalSearch.value = "";
  autoScroll.value = true;

  // 1. 先拉取最近 200 行日志
  try {
    const recent = await fetchAppRecentLogs(service.id, 200);
    terminalLogs.value = recent;
    scrollTerminalToBottom();
  } catch (e) {
    terminalLogs.value = ["[SYSTEM ERROR] 获取初始日志失败"];
  }

  // 2. 建立 SSE 实时推流
  if (sseSource) {
    sseSource.close();
  }
  const sseUrl = `${import.meta.env.VITE_API_BASE_URL || "/api"}/infra/devops/services/${service.id}/logs/stream`;
  sseSource = new EventSource(sseUrl);

  sseSource.addEventListener("log", (evt) => {
    terminalLogs.value.push(evt.data);
    if (terminalLogs.value.length > 3000) {
      terminalLogs.value.shift();
    }
    if (autoScroll.value) {
      scrollTerminalToBottom();
    }
  });

  sseSource.addEventListener("error", () => {
    // Retry or idle
  });
}

function closeTerminal() {
  if (sseSource) {
    sseSource.close();
    sseSource = null;
  }
  terminalVisible.value = false;
  terminalService.value = null;
}

function scrollTerminalToBottom() {
  nextTick(() => {
    if (terminalBodyRef.value) {
      terminalBodyRef.value.scrollTop = terminalBodyRef.value.scrollHeight;
    }
  });
}

function clearTerminal() {
  terminalLogs.value = [];
}

function downloadTerminalLog() {
  if (!terminalService.value) return;
  const content = terminalLogs.value.join("\n");
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${terminalService.value.serviceCode}-${Date.now()}.log`;
  a.click();
  URL.revokeObjectURL(url);
}

// 日志颜色过滤渲染
const filteredLogs = computed(() => {
  if (!terminalSearch.value) return terminalLogs.value;
  const kw = terminalSearch.value.toLowerCase();
  return terminalLogs.value.filter(line => line.toLowerCase().includes(kw));
});

function getLogClass(line: string) {
  if (line.includes("ERROR") || line.includes("Exception") || line.includes("FAILED")) return "text-red-400";
  if (line.includes("WARN")) return "text-amber-300";
  if (line.includes("INFO")) return "text-emerald-300";
  if (line.includes("DEBUG")) return "text-sky-300";
  if (line.includes("[TIANSHU CI]")) return "text-cyan-300 font-bold";
  return "text-slate-300";
}

// ==================== 8. 自动化编译中心 ====================
async function openBuildDrawer(service: AppServiceItem) {
  buildService.value = service;
  customBuildCmd.value = service.buildCmd || "";
  buildDrawerVisible.value = true;
  await loadBuildRecords(service.id);
}

async function loadBuildRecords(serviceId: number) {
  try {
    buildRecords.value = await fetchAppBuildRecords(serviceId);
    if (buildRecords.value.length > 0) {
      selectBuildRecord(buildRecords.value[0]);
    }
  } catch (e) {
    ElMessage.error("获取构建记录失败");
  }
}

async function selectBuildRecord(record: AppBuildRecordItem) {
  activeBuildRecord.value = record;
  try {
    liveBuildLogs.value = await fetchAppBuildLogs(record.id);
  } catch (e) {
    liveBuildLogs.value = ["[SYSTEM] 获取构建日志失败"];
  }
}

async function executeBuild() {
  if (!buildService.value) return;
  try {
    buildExecuting.value = true;
    ElMessage.info(`正在触发 [${buildService.value.serviceName}] 全自动编译打包...`);
    const newRecord = await triggerAppBuild(buildService.value.id, customBuildCmd.value);
    activeBuildRecord.value = newRecord;
    liveBuildLogs.value = [`[TIANSHU CI] 已下发构建任务: ${newRecord.buildNo}，实时输出追踪中...`];

    // 启动轮询检查构建日志
    if (buildLogTimer) clearInterval(buildLogTimer);
    buildLogTimer = setInterval(async () => {
      if (activeBuildRecord.value) {
        liveBuildLogs.value = await fetchAppBuildLogs(activeBuildRecord.value.id);
        const updatedList = await fetchAppBuildRecords(buildService.value!.id);
        buildRecords.value = updatedList;
        const current = updatedList.find(r => r.id === activeBuildRecord.value!.id);
        if (current && (current.status === "SUCCESS" || current.status === "FAILED")) {
          clearInterval(buildLogTimer);
          buildExecuting.value = false;
          if (current.status === "SUCCESS") {
            ElMessage.success("🎉 全自动编译打包成功！已自动归档新制品。");
          } else {
            ElMessage.error("❌ 编译打包失败，请检查控制台错误信息。");
          }
          await loadServices(true);
        }
      }
    }, 1500);

  } catch (err: any) {
    buildExecuting.value = false;
    ElMessage.error("触发构建失败: " + (err?.message || "未知错误"));
  }
}

// ==================== 9. 制品管理与版本回滚 ====================
async function openArtifactModal(service: AppServiceItem) {
  artifactService.value = service;
  artifactModalVisible.value = true;
  selectedFile.value = null;
  uploadRemark.value = "";
  await loadArtifacts(service.id);
}

async function loadArtifacts(serviceId: number) {
  artifactLoading.value = true;
  try {
    artifactList.value = await fetchAppArtifacts(serviceId);
  } finally {
    artifactLoading.value = false;
  }
}

function handleFileChange(event: any) {
  const file = event.target.files?.[0];
  if (file) {
    selectedFile.value = file;
  }
}

async function submitUploadArtifact() {
  if (!artifactService.value || !selectedFile.value) {
    ElMessage.warning("请选择要上传的制品文件 (Jar / Zip)");
    return;
  }
  uploadSubmitting.value = true;
  try {
    ElMessage.info("正在上传并热更制品...");
    await uploadAppArtifact(artifactService.value.id, selectedFile.value, uploadRemark.value);
    ElMessage.success("制品上传成功！已完成版本快照归档并更新当前部署路径。");
    selectedFile.value = null;
    uploadRemark.value = "";
    await loadArtifacts(artifactService.value.id);
    await loadServices(true);
  } catch (err: any) {
    ElMessage.error("上传失败: " + (err?.message || "未知错误"));
  } finally {
    uploadSubmitting.value = false;
  }
}

async function handleRollback(artifact: AppArtifactItem) {
  if (!artifactService.value) return;
  try {
    await ElMessageBox.confirm(`确认将服务一键回滚至版本 [${artifact.versionTag}] 并自动平滑重启吗？`, "版本回滚警告", {
      type: "warning",
      confirmButtonText: "确认回滚",
      cancelButtonText: "取消"
    });
    ElMessage.info(`正在回滚至版本 ${artifact.versionTag}...`);
    const res = await rollbackAppArtifact(artifactService.value.id, artifact.id);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
    }
    await loadArtifacts(artifactService.value.id);
    await loadServices(true);
  } catch (err) {
    // cancelled
  }
}

// ==================== 10. 服务配置添加/编辑 ====================
function openEditDialog(service?: AppServiceItem) {
  if (service) {
    editForm.value = { ...service };
  } else {
    editForm.value = {
      serviceCode: "custom-service-" + Date.now().toString().slice(-4),
      serviceName: "自定义扩展微服务",
      serviceType: "JAR",
      serviceGroup: "PLATFORM",
      driverType: "HOST_PROCESS",
      workDir: "/Users/shenzhidan/ideaProject/saas_basics",
      startCmd: "java -jar your-app.jar",
      buildCmd: "mvn clean package -DskipTests",
      port: 8090,
      autoRestart: 0
    };
  }
  editDialogVisible.value = true;
}

async function submitSaveService() {
  if (!editForm.value.serviceCode || !editForm.value.serviceName || !editForm.value.workDir || !editForm.value.startCmd) {
    ElMessage.warning("请完整填写服务标识、服务名称、工作目录与启动命令");
    return;
  }
  try {
    await saveAppService(editForm.value, editForm.value.id);
    ElMessage.success("服务配置保存成功");
    editDialogVisible.value = false;
    await loadServices();
  } catch (err: any) {
    ElMessage.error("保存失败: " + (err?.message || "未知错误"));
  }
}

async function handleDeleteService(service: AppServiceItem) {
  try {
    await ElMessageBox.confirm(`确认删除服务配置 [${service.serviceName}] 吗？如正在运行将被先安全终止。`, "删除确认", {
      type: "error",
      confirmButtonText: "确认删除",
      cancelButtonText: "取消"
    });
    await deleteAppService(service.id);
    ElMessage.success("服务已删除");
    await loadServices();
  } catch (err) {
  }
}

// 工具函数
function formatUptime(seconds?: number): string {
  if (!seconds || seconds <= 0) return "-";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function formatBytes(bytes?: number): string {
  if (!bytes || bytes <= 0) return "0 B";
  const mb = bytes / (1024 * 1024);
  if (mb >= 1024) return (mb / 1024).toFixed(2) + " GB";
  return mb.toFixed(1) + " MB";
}

onMounted(() => {
  loadServices();
  handleAutoRefreshChange(autoRefreshSeconds.value);
});

onUnmounted(() => {
  if (timerId) clearInterval(timerId);
  if (buildLogTimer) clearInterval(buildLogTimer);
  if (sseSource) sseSource.close();
});
</script>

<template>
  <div class="devops-container">
    <!-- 1. 顶部 Header 与全景流光指标 -->
    <div class="glass-header-card mb-6">
      <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-sky-500/30">
              <el-icon class="text-white text-xl"><Platform /></el-icon>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <h1 class="text-2xl font-bold text-white tracking-wide">服务编排与发布中台</h1>
                <span class="px-2 py-0.5 text-xs rounded-full bg-sky-500/20 text-sky-300 border border-sky-400/30 font-medium">
                  双模驱动 · 敏捷 DevOps
                </span>
              </div>
              <p class="text-sm text-slate-400 mt-0.5">
                全自动编译构建 · 制品版本热更与回滚 · 进程生命周期平滑启停 · 实时流式日志终端
              </p>
            </div>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <div class="flex items-center gap-2 bg-slate-800/60 px-3 py-1.5 rounded-xl border border-white/10 text-xs text-slate-300">
            <el-icon class="text-sky-400"><Timer /></el-icon>
            <span>自动刷新:</span>
            <el-select v-model="autoRefreshSeconds" size="small" class="w-20" @change="handleAutoRefreshChange">
              <el-option :value="0" label="关闭" />
              <el-option :value="3" label="3s" />
              <el-option :value="5" label="5s" />
              <el-option :value="10" label="10s" />
            </el-select>
          </div>

          <el-button type="primary" :icon="Plus" class="glass-btn-primary" @click="openEditDialog()">
            新建纳管服务
          </el-button>

          <el-button :icon="Refresh" circle class="glass-btn-circle" :loading="loading" @click="loadServices()" />
        </div>
      </div>

      <!-- 4 大全景指标卡片 -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
        <div class="metric-pill">
          <div class="text-xs text-slate-400 flex items-center gap-1.5">
            <el-icon class="text-sky-400"><Box /></el-icon> 总纳管服务
          </div>
          <div class="text-2xl font-bold text-white mt-1">{{ totalServices }} <span class="text-xs text-slate-400 font-normal">个模块</span></div>
        </div>

        <div class="metric-pill">
          <div class="text-xs text-slate-400 flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> 正常运行实例
          </div>
          <div class="text-2xl font-bold text-emerald-400 mt-1">{{ runningServices }} <span class="text-xs text-slate-400 font-normal">/ {{ totalServices }} 在线</span></div>
        </div>

        <div class="metric-pill">
          <div class="text-xs text-slate-400 flex items-center gap-1.5">
            <el-icon class="text-indigo-400"><Cpu /></el-icon> 物理内存常驻 (RSS)
          </div>
          <div class="text-2xl font-bold text-indigo-300 mt-1">{{ totalMemoryMb }} <span class="text-xs text-slate-400 font-normal">MB</span></div>
        </div>

        <div class="metric-pill">
          <div class="text-xs text-slate-400 flex items-center gap-1.5">
            <el-icon class="text-amber-400"><SetUp /></el-icon> 编排驱动架构
          </div>
          <div class="text-sm font-semibold text-amber-300 mt-2 flex items-center gap-1">
            <span>ProcessTree + Docker</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 服务卡片矩阵 (Service Card Grid) -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <div
        v-for="service in serviceList"
        :key="service.id"
        class="service-card group"
        :class="{
          'border-emerald-500/40 shadow-emerald-500/10': service.status === 'RUNNING',
          'border-sky-500/40 shadow-sky-500/10': service.status === 'COMPILING',
          'border-amber-500/40 shadow-amber-500/10': service.status === 'STARTING',
          'border-rose-500/40 shadow-rose-500/10': service.status === 'ERROR',
          'border-slate-800/80': service.status === 'STOPPED'
        }"
      >
        <!-- 卡片头部 -->
        <div class="flex items-start justify-between p-5 border-b border-white/5">
          <div class="flex items-start gap-3.5">
            <!-- 模块图标 -->
            <div
              class="w-12 h-12 rounded-xl flex items-center justify-center text-xl shrink-0 transition-transform group-hover:scale-105"
              :class="{
                'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30': service.status === 'RUNNING',
                'bg-sky-500/20 text-sky-400 border border-sky-500/30': service.status === 'COMPILING',
                'bg-amber-500/20 text-amber-400 border border-amber-500/30': service.status === 'STARTING',
                'bg-rose-500/20 text-rose-400 border border-rose-500/30': service.status === 'ERROR',
                'bg-slate-800 text-slate-400 border border-white/10': service.status === 'STOPPED'
              }"
            >
              <span v-if="service.serviceType === 'JAR'">☕</span>
              <span v-else-if="service.serviceType === 'NODE'">🌐</span>
              <span v-else-if="service.serviceType === 'DOCKER'">🐳</span>
              <span v-else>⚡</span>
            </div>

            <div>
              <div class="flex items-center gap-2 flex-wrap">
                <h3 class="text-base font-bold text-white group-hover:text-sky-300 transition-colors">
                  {{ service.serviceName }}
                </h3>
                <span class="px-2 py-0.5 text-xs rounded-md bg-slate-800/90 text-slate-300 border border-white/10 font-mono">
                  {{ service.serviceCode }}
                </span>
                <span class="px-2 py-0.5 text-xs rounded-md bg-indigo-950/60 text-indigo-300 border border-indigo-500/30">
                  {{ service.driverType }}
                </span>
              </div>
              <p class="text-xs text-slate-400 mt-1 line-clamp-1">
                {{ service.remark || '暂无描述' }}
              </p>
            </div>
          </div>

          <!-- 状态呼吸徽标 -->
          <div class="shrink-0">
            <div
              class="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border"
              :class="{
                'bg-emerald-950/60 text-emerald-300 border-emerald-500/40': service.status === 'RUNNING',
                'bg-sky-950/60 text-sky-300 border-sky-500/40': service.status === 'COMPILING',
                'bg-amber-950/60 text-amber-300 border-amber-500/40': service.status === 'STARTING',
                'bg-rose-950/60 text-rose-300 border-rose-500/40': service.status === 'ERROR',
                'bg-slate-800/80 text-slate-400 border-slate-700': service.status === 'STOPPED'
              }"
            >
              <span
                class="w-2 h-2 rounded-full"
                :class="{
                  'bg-emerald-400 animate-ping': service.status === 'RUNNING',
                  'bg-sky-400 animate-spin': service.status === 'COMPILING',
                  'bg-amber-400 animate-pulse': service.status === 'STARTING',
                  'bg-rose-500': service.status === 'ERROR',
                  'bg-slate-500': service.status === 'STOPPED'
                }"
              ></span>
              <span>
                {{ service.status === 'RUNNING' ? '运行中' :
                   service.status === 'COMPILING' ? '编译中' :
                   service.status === 'STARTING' ? '启动中' :
                   service.status === 'ERROR' ? '异常' : '已停止' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 卡片中间：指标矩阵与探针状态 -->
        <div class="p-5 grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-950/20 text-xs">
          <div class="metric-cell">
            <span class="text-slate-400">端口 / 监听</span>
            <div class="flex items-center gap-1.5 mt-0.5">
              <span class="font-mono font-bold text-white">{{ service.port ? `:${service.port}` : '无' }}</span>
              <el-tag v-if="service.portListening" type="success" size="small" effect="dark" class="px-1 scale-90">OK</el-tag>
              <el-tag v-else type="info" size="small" effect="plain" class="px-1 scale-90">未监听</el-tag>
            </div>
          </div>

          <div class="metric-cell">
            <span class="text-slate-400">系统 PID</span>
            <div class="font-mono font-bold text-white mt-0.5">
              {{ service.currentPid ? `#${service.currentPid}` : '-' }}
            </div>
          </div>

          <div class="metric-cell">
            <span class="text-slate-400">运行时长</span>
            <div class="font-mono text-white mt-0.5">
              {{ formatUptime(service.uptimeSeconds) }}
            </div>
          </div>

          <div class="metric-cell">
            <span class="text-slate-400">内存 / CPU</span>
            <div class="font-mono text-indigo-300 font-bold mt-0.5">
              {{ formatBytes(service.memoryRssBytes) }}
              <span class="text-slate-400 text-[10px] font-normal" v-if="service.cpuUsagePercent">({{ service.cpuUsagePercent }}%)</span>
            </div>
          </div>
        </div>

        <!-- 部署命令与制品摘要 -->
        <div class="px-5 py-2.5 bg-slate-900/40 border-t border-b border-white/5 flex items-center justify-between text-xs text-slate-400">
          <div class="flex items-center gap-2 truncate pr-2">
            <el-icon class="text-slate-500 shrink-0"><Folder /></el-icon>
            <span class="truncate font-mono text-slate-400" :title="service.workDir">{{ service.workDir }}</span>
          </div>
          <span class="text-slate-500 font-mono shrink-0" v-if="service.artifactPath">
            {{ service.artifactPath.split('/').pop() }}
          </span>
        </div>

        <!-- 卡片底部操作栏 -->
        <div class="p-4 flex items-center justify-between gap-2 flex-wrap bg-slate-950/40">
          <div class="flex items-center gap-2">
            <!-- 启动按钮 -->
            <el-button
              type="success"
              size="small"
              :icon="VideoPlay"
              class="glass-btn-success"
              :disabled="service.status === 'RUNNING' || service.status === 'COMPILING'"
              @click="handleStart(service)"
            >
              启动
            </el-button>

            <!-- 重启按钮 -->
            <el-button
              type="primary"
              size="small"
              :icon="RefreshRight"
              class="glass-btn-primary"
              :disabled="service.status === 'COMPILING'"
              @click="handleRestart(service)"
            >
              重启
            </el-button>

            <!-- 停止下拉按钮 -->
            <el-dropdown trigger="click">
              <el-button
                type="danger"
                size="small"
                :icon="VideoPause"
                class="glass-btn-danger"
                :disabled="service.status === 'STOPPED'"
              >
                停止
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleStop(service, 15)">
                    <el-icon><VideoPause /></el-icon> 优雅停止 (SIGTERM)
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleKill(service)" class="text-red-500">
                    <el-icon><WarningFilled /></el-icon> 强制杀死 (SIGKILL)
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="flex items-center gap-1.5">
            <!-- 编译打包 -->
            <el-tooltip content="一键触发自动化编译打包流水线" placement="top">
              <el-button
                size="small"
                class="glass-action-btn"
                :loading="service.status === 'COMPILING'"
                @click="openBuildDrawer(service)"
              >
                <el-icon><SetUp /></el-icon> 编译
              </el-button>
            </el-tooltip>

            <!-- 实时日志 -->
            <el-tooltip content="查看实时流式运行日志 (tail -f)" placement="top">
              <el-button size="small" class="glass-action-btn" @click="openTerminal(service)">
                <el-icon><Monitor /></el-icon> 日志
              </el-button>
            </el-tooltip>

            <!-- 制品发布与回滚 -->
            <el-tooltip content="制品上传与历史版本一键回滚" placement="top">
              <el-button size="small" class="glass-action-btn" @click="openArtifactModal(service)">
                <el-icon><Upload /></el-icon> 制品
              </el-button>
            </el-tooltip>

            <!-- 更多操作 -->
            <el-dropdown trigger="click">
              <el-button size="small" class="glass-action-btn">
                ...
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEditDialog(service)">
                    <el-icon><Edit /></el-icon> 编辑服务配置
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDeleteService(service)" class="text-red-500">
                    <el-icon><Delete /></el-icon> 删除该服务
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 实时终端弹窗 (Apple Terminal Fluid Glass) -->
    <el-dialog
      v-model="terminalVisible"
      :show-close="false"
      width="82%"
      top="5vh"
      class="terminal-dialog-glass"
      :before-close="closeTerminal"
    >
      <div class="terminal-window">
        <!-- 终端顶栏 -->
        <div class="terminal-header flex items-center justify-between px-4 py-3 bg-slate-900/90 border-b border-white/10">
          <div class="flex items-center gap-2">
            <span class="w-3 h-3 rounded-full bg-rose-500 cursor-pointer" @click="closeTerminal"></span>
            <span class="w-3 h-3 rounded-full bg-amber-500"></span>
            <span class="w-3 h-3 rounded-full bg-emerald-500"></span>
            <span class="ml-3 text-xs font-mono text-slate-300 font-bold">
              tianshu@console: ~ / {{ terminalService?.serviceCode }} / live.log
            </span>
          </div>

          <div class="flex items-center gap-3">
            <!-- 关键字搜索 -->
            <el-input
              v-model="terminalSearch"
              placeholder="搜索日志关键字..."
              size="small"
              class="w-48 terminal-search-input"
              clearable
              :prefix-icon="Search"
            />

            <!-- 自动滚屏开关 -->
            <div
              class="flex items-center gap-1.5 px-2.5 py-1 rounded text-xs cursor-pointer select-none border"
              :class="autoScroll ? 'bg-sky-500/20 text-sky-300 border-sky-400/40' : 'bg-slate-800 text-slate-400 border-slate-700'"
              @click="autoScroll = !autoScroll"
            >
              <span>贴底自动滚屏</span>
            </div>

            <!-- 清屏 -->
            <el-button size="small" class="glass-action-btn" @click="clearTerminal">清屏</el-button>

            <!-- 下载 -->
            <el-button size="small" class="glass-action-btn" :icon="Download" @click="downloadTerminalLog">下载</el-button>

            <!-- 关闭 -->
            <el-button size="small" circle :icon="Close" class="glass-action-btn" @click="closeTerminal" />
          </div>
        </div>

        <!-- 终端主体内容 -->
        <div ref="terminalBodyRef" class="terminal-body p-4 overflow-y-auto font-mono text-xs leading-relaxed max-h-[70vh]">
          <div v-if="filteredLogs.length === 0" class="text-slate-500 py-10 text-center">
            暂无日志输出或无匹配项
          </div>
          <div
            v-for="(line, index) in filteredLogs"
            :key="index"
            class="py-0.5 select-text break-all"
            :class="getLogClass(line)"
          >
            {{ line }}
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 4. 自动化编译构建抽屉 -->
    <el-drawer
      v-model="buildDrawerVisible"
      title="全自动编译构建流水线 (CI/CD Pipeline)"
      size="65%"
      class="build-drawer-glass"
    >
      <div v-if="buildService" class="flex flex-col h-full">
        <!-- 构建控制台 -->
        <div class="p-4 bg-slate-900/60 rounded-xl border border-white/10 mb-4">
          <div class="flex items-center justify-between mb-3">
            <div>
              <h4 class="text-sm font-bold text-white">{{ buildService.serviceName }}</h4>
              <p class="text-xs text-slate-400 mt-0.5">工作目录: {{ buildService.workDir }}</p>
            </div>
            <el-button
              type="primary"
              :icon="CaretRight"
              class="glass-btn-primary"
              :loading="buildExecuting"
              @click="executeBuild"
            >
              立即执行编译构建
            </el-button>
          </div>

          <div class="text-xs text-slate-400 mb-1">执行编译命令 (可按需微调):</div>
          <el-input
            v-model="customBuildCmd"
            placeholder="如: mvn clean package -DskipTests"
            size="small"
            class="font-mono"
          />
        </div>

        <!-- 分割为左右两列：左侧历史记录，右侧构建控制台日志 -->
        <div class="grid grid-cols-12 gap-4 flex-1 overflow-hidden">
          <!-- 构建历史 -->
          <div class="col-span-4 bg-slate-950/40 rounded-xl p-3 border border-white/5 overflow-y-auto">
            <h5 class="text-xs font-bold text-slate-300 mb-2">历史构建记录</h5>
            <div v-if="buildRecords.length === 0" class="text-xs text-slate-500 py-6 text-center">
              暂无历史记录
            </div>
            <div
              v-for="rec in buildRecords"
              :key="rec.id"
              class="p-2.5 rounded-lg mb-2 cursor-pointer border transition-all text-xs"
              :class="activeBuildRecord?.id === rec.id ? 'bg-sky-950/40 border-sky-500/50' : 'bg-slate-900/30 border-white/5 hover:border-white/20'"
              @click="selectBuildRecord(rec)"
            >
              <div class="flex items-center justify-between">
                <span class="font-mono font-bold text-white">{{ rec.buildNo }}</span>
                <span
                  class="px-1.5 py-0.5 rounded text-[10px]"
                  :class="{
                    'bg-emerald-500/20 text-emerald-300': rec.status === 'SUCCESS',
                    'bg-rose-500/20 text-rose-300': rec.status === 'FAILED',
                    'bg-sky-500/20 text-sky-300 animate-pulse': rec.status === 'RUNNING'
                  }"
                >
                  {{ rec.status }}
                </span>
              </div>
              <div class="text-[11px] text-slate-400 mt-1 flex items-center justify-between">
                <span>{{ rec.createdAt ? rec.createdAt.replace('T', ' ').slice(5, 16) : '-' }}</span>
                <span v-if="rec.durationMs">{{ (rec.durationMs / 1000).toFixed(1) }}s</span>
              </div>
            </div>
          </div>

          <!-- 构建控制台 -->
          <div class="col-span-8 bg-slate-950 rounded-xl p-4 border border-white/10 flex flex-col overflow-hidden">
            <div class="flex items-center justify-between pb-2 mb-2 border-b border-white/10 text-xs text-slate-400">
              <span class="font-mono">Console Output: {{ activeBuildRecord?.buildNo || 'None' }}</span>
              <span v-if="buildExecuting" class="flex items-center gap-1 text-sky-400 animate-pulse">
                <el-icon class="animate-spin"><Loading /></el-icon> 正在编译打包中...
              </span>
            </div>

            <div class="flex-1 overflow-y-auto font-mono text-xs leading-relaxed text-slate-300">
              <div v-for="(line, idx) in liveBuildLogs" :key="idx" class="py-0.5" :class="getLogClass(line)">
                {{ line }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 5. 制品管理与版本回滚弹窗 -->
    <el-dialog
      v-model="artifactModalVisible"
      title="制品中心与多版本发布 (Artifact Hub & Rollback)"
      width="60%"
      class="artifact-dialog-glass"
    >
      <div v-if="artifactService" class="space-y-4">
        <!-- 上传新制品 -->
        <div class="p-4 bg-slate-900/60 rounded-xl border border-white/10">
          <h4 class="text-sm font-bold text-white mb-2 flex items-center gap-2">
            <el-icon class="text-sky-400"><Upload /></el-icon> 手动上传新制品发布 (Jar / Zip)
          </h4>
          <div class="flex items-center gap-3">
            <input type="file" @change="handleFileChange" class="text-xs text-slate-300 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-sky-500 file:text-white hover:file:bg-sky-600 cursor-pointer" />
            <el-input v-model="uploadRemark" placeholder="发布备注 (如: 修复审批流回退漏洞)" size="small" class="flex-1" />
            <el-button type="primary" size="small" class="glass-btn-primary" :loading="uploadSubmitting" @click="submitUploadArtifact">
              确认部署
            </el-button>
          </div>
        </div>

        <!-- 历史制品版本列表 -->
        <div>
          <h4 class="text-sm font-bold text-slate-200 mb-2">版本历史归档 (支持一键秒级回滚)</h4>
          <div v-if="artifactList.length === 0" class="text-xs text-slate-500 py-6 text-center">
            暂无历史归档制品
          </div>
          <div
            v-for="art in artifactList"
            :key="art.id"
            class="p-3.5 rounded-xl mb-2.5 border transition-all flex items-center justify-between text-xs"
            :class="art.isCurrent ? 'bg-emerald-950/30 border-emerald-500/40' : 'bg-slate-900/40 border-white/5'"
          >
            <div>
              <div class="flex items-center gap-2">
                <span class="font-mono font-bold text-white text-sm">{{ art.versionTag }}</span>
                <span v-if="art.isCurrent" class="px-2 py-0.5 rounded-full text-[10px] bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-medium">
                  当前生产版本
                </span>
                <span class="text-slate-400 font-mono">{{ art.fileName }}</span>
              </div>
              <div class="text-slate-400 mt-1 flex items-center gap-4 text-[11px]">
                <span>大小: {{ (art.fileSizeBytes / (1024 * 1024)).toFixed(2) }} MB</span>
                <span>MD5: {{ art.fileMd5.slice(0, 8) }}...</span>
                <span>时间: {{ art.createdAt ? art.createdAt.replace('T', ' ').slice(0, 19) : '-' }}</span>
                <span class="text-slate-300" v-if="art.remark">备注: {{ art.remark }}</span>
              </div>
            </div>

            <div>
              <el-button
                v-if="!art.isCurrent"
                size="small"
                type="warning"
                class="glass-btn-warning"
                @click="handleRollback(art)"
              >
                一键回滚至此版本
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 6. 服务配置添加/编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="editForm.id ? '编辑纳管服务' : '新建纳管服务'"
      width="50%"
      class="edit-dialog-glass"
    >
      <el-form label-width="110px" class="space-y-3">
        <el-form-item label="服务标识代码" required>
          <el-input v-model="editForm.serviceCode" placeholder="如: saas-basics-crm" :disabled="!!editForm.id" />
        </el-form-item>

        <el-form-item label="服务显示名称" required>
          <el-input v-model="editForm.serviceName" placeholder="如: 天枢云基 · CRM 业务模块" />
        </el-form-item>

        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="服务类型">
            <el-select v-model="editForm.serviceType" class="w-full">
              <el-option value="JAR" label="Java Jar 应用" />
              <el-option value="NODE" label="Node.js / 前端应用" />
              <el-option value="DOCKER" label="Docker 容器" />
              <el-option value="SCRIPT" label="自定义 Shell 脚本" />
            </el-select>
          </el-form-item>

          <el-form-item label="编排驱动">
            <el-select v-model="editForm.driverType" class="w-full">
              <el-option value="HOST_PROCESS" label="Host 宿主机进程 (ProcessTree)" />
              <el-option value="DOCKER" label="Docker Engine (Socket)" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="工作目录 (WorkDir)" required>
          <el-input v-model="editForm.workDir" placeholder="服务的根目录绝对路径" />
        </el-form-item>

        <el-form-item label="启动命令 (Start)" required>
          <el-input v-model="editForm.startCmd" type="textarea" :rows="2" placeholder="如: java -jar app.jar 或 npm run dev" />
        </el-form-item>

        <el-form-item label="编译命令 (Build)">
          <el-input v-model="editForm.buildCmd" placeholder="如: mvn clean package -DskipTests 或 npm run build" />
        </el-form-item>

        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="监听端口">
            <el-input-number v-model="editForm.port" :min="1" :max="65535" class="w-full" />
          </el-form-item>

          <el-form-item label="健康探测 URL">
            <el-input v-model="editForm.healthUrl" placeholder="如: http://127.0.0.1:8080/actuator/health" />
          </el-form-item>
        </div>

        <el-form-item label="日志输出路径">
          <el-input v-model="editForm.logPath" placeholder="留空时自动输出至 workDir/logs/xxx.log" />
        </el-form-item>

        <el-form-item label="备注说明">
          <el-input v-model="editForm.remark" placeholder="服务的用途说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" class="glass-btn-primary" @click="submitSaveService">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.devops-container {
  padding: 1.5rem;
  min-height: calc(100vh - 4rem);
}

.glass-header-card {
  background: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 1.25rem;
  padding: 1.5rem;
  box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.5);
}

.metric-pill {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 1rem;
  padding: 1rem 1.25rem;
}

.service-card {
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 1.25rem;
  overflow: hidden;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
}

.service-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 35px -10px rgba(0, 0, 0, 0.6);
}

.metric-cell {
  display: flex;
  flex-direction: column;
}

.glass-btn-primary {
  background: linear-gradient(135deg, #0284c7 0%, #4f46e5 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  border-radius: 0.6rem !important;
}

.glass-btn-success {
  background: linear-gradient(135deg, #059669 0%, #10b981 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  border-radius: 0.6rem !important;
}

.glass-btn-danger {
  background: linear-gradient(135deg, #dc2626 0%, #ef4444 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  border-radius: 0.6rem !important;
}

.glass-btn-warning {
  background: linear-gradient(135deg, #d97706 0%, #f59e0b 100%) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 500;
  border-radius: 0.6rem !important;
}

.glass-btn-circle {
  background: rgba(30, 41, 59, 0.6) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: #e2e8f0 !important;
}

.glass-action-btn {
  background: rgba(30, 41, 59, 0.6) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: #cbd5e1 !important;
  border-radius: 0.5rem !important;
}

.glass-action-btn:hover {
  background: rgba(51, 65, 85, 0.8) !important;
  color: #38bdf8 !important;
  border-color: rgba(56, 189, 248, 0.4) !important;
}

/* 终端样式 */
.terminal-window {
  background: #090d16;
  border-radius: 1rem;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.8);
}

.terminal-body {
  background: #090d16;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
}
</style>
