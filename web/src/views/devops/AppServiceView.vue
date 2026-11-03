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
  CaretRight,
  Tickets,
  Monitor,
  Lock,
  MagicStick
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
  checkAppServicePort,
  type AppServiceItem,
  type AppBuildRecordItem,
  type AppArtifactItem,
  type PortCheckResult
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

// ==================== 5. 架构模板与服务配置弹窗 ====================
interface ServiceTemplate {
  key: string;
  name: string;
  icon: string;
  desc: string;
  serviceType: "JAR" | "NODE" | "DOCKER" | "SCRIPT";
  driverType: "HOST_PROCESS" | "DOCKER";
  defaultPort: number;
  workDir: string;
  startCmdTpl: (code: string, port: number) => string;
  buildCmdTpl: (code: string) => string;
  healthUrlTpl: (code: string, port: number) => string;
  logPathTpl: (code: string) => string;
  artifactPathTpl: (code: string) => string;
  remark: string;
}

const SERVICE_TEMPLATES: ServiceTemplate[] = [
  {
    key: "spring_boot",
    name: "Spring Boot 微服务",
    icon: "☕",
    desc: "Java 17/21 独立后端微服务",
    serviceType: "JAR",
    driverType: "HOST_PROCESS",
    defaultPort: 8086,
    workDir: "/Users/shenzhidan/ideaProject/saas_basics/server",
    startCmdTpl: (code, port) => `java -Dfile.encoding=UTF-8 -Dserver.port=${port} -DDB_PASSWORD=19990518suo -jar ${code}/target/${code}-0.1.0-SNAPSHOT.jar`,
    buildCmdTpl: (code) => `mvn clean package -DskipTests -pl ${code} -am`,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}/actuator/health`,
    logPathTpl: (code) => `logs/${code}.log`,
    artifactPathTpl: (code) => `${code}/target/${code}-0.1.0-SNAPSHOT.jar`,
    remark: "Spring Boot 工业级后端微服务"
  },
  {
    key: "web_frontend",
    name: "Vue / Vite 前端工程",
    icon: "🌐",
    desc: "Vue 3 / React / Vite 响应式前端应用",
    serviceType: "NODE",
    driverType: "HOST_PROCESS",
    defaultPort: 9528,
    workDir: "/Users/shenzhidan/ideaProject/saas_basics/web",
    startCmdTpl: (code, port) => `npm run dev -- --port ${port}`,
    buildCmdTpl: () => `npm run build`,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}`,
    logPathTpl: (code) => `logs/${code}.log`,
    artifactPathTpl: () => `dist`,
    remark: "Vue 3 + Vite 现代化 Web 交互前端"
  },
  {
    key: "python_fastapi",
    name: "Python / AI 模型服务",
    icon: "🐍",
    desc: "FastAPI / PyTorch / LLM 推理服务",
    serviceType: "SCRIPT",
    driverType: "HOST_PROCESS",
    defaultPort: 8000,
    workDir: "/Users/shenzhidan/ideaProject/saas_basics",
    startCmdTpl: (code, port) => `python3 -m uvicorn app:app --host 0.0.0.0 --port ${port}`,
    buildCmdTpl: () => `pip install -r requirements.txt`,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}/docs`,
    logPathTpl: (code) => `logs/${code}.log`,
    artifactPathTpl: () => ``,
    remark: "Python / FastAPI 智能化模型服务"
  },
  {
    key: "golang_native",
    name: "Go 高性能微服务",
    icon: "🐹",
    desc: "Golang 单二进制高并发服务",
    serviceType: "SCRIPT",
    driverType: "HOST_PROCESS",
    defaultPort: 8090,
    workDir: "/Users/shenzhidan/ideaProject/saas_basics",
    startCmdTpl: (code, port) => `./${code} -port ${port}`,
    buildCmdTpl: (code) => `go build -o ${code} main.go`,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}/health`,
    logPathTpl: (code) => `logs/${code}.log`,
    artifactPathTpl: (code) => `${code}`,
    remark: "Golang 高并发微服务"
  },
  {
    key: "docker_container",
    name: "Docker 独立容器",
    icon: "🐳",
    desc: "Redis / Nginx / 任意 Docker 镜像",
    serviceType: "DOCKER",
    driverType: "DOCKER",
    defaultPort: 6380,
    workDir: "/tmp",
    startCmdTpl: (code, port) => `docker run -d --name ${code} -p ${port}:${port} redis:7-alpine`,
    buildCmdTpl: () => `docker pull redis:7-alpine`,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}`,
    logPathTpl: () => ``,
    artifactPathTpl: () => ``,
    remark: "Docker 容器化服务组件"
  },
  {
    key: "custom",
    name: "自由自定义配置",
    icon: "⚡",
    desc: "完全自由定制任意启动命令",
    serviceType: "SCRIPT",
    driverType: "HOST_PROCESS",
    defaultPort: 8088,
    workDir: "/Users/shenzhidan/ideaProject/saas_basics",
    startCmdTpl: () => ``,
    buildCmdTpl: () => ``,
    healthUrlTpl: (code, port) => `http://127.0.0.1:${port}`,
    logPathTpl: (code) => `logs/${code}.log`,
    artifactPathTpl: () => ``,
    remark: "自定义应用组件"
  }
];

const editDialogVisible = ref(false);
const activeTemplateKey = ref("spring_boot");
const portCheckResult = ref<PortCheckResult | null>(null);
const portChecking = ref(false);
let portCheckTimer: any = null;

const editForm = ref<Partial<AppServiceItem>>({
  serviceType: "JAR",
  driverType: "HOST_PROCESS",
  serviceGroup: "PLATFORM",
  autoRestart: 0,
  port: 8086
});

function applyTemplate(tpl: ServiceTemplate, force = false) {
  activeTemplateKey.value = tpl.key;
  editForm.value.serviceType = tpl.serviceType;
  editForm.value.driverType = tpl.driverType;
  editForm.value.workDir = tpl.workDir;
  if (!editForm.value.port || force) {
    editForm.value.port = tpl.defaultPort;
  }
  const code = editForm.value.serviceCode || "new-service";
  const port = editForm.value.port || tpl.defaultPort;
  editForm.value.startCmd = tpl.startCmdTpl(code, port);
  editForm.value.buildCmd = tpl.buildCmdTpl(code);
  editForm.value.healthUrl = tpl.healthUrlTpl(code, port);
  editForm.value.logPath = tpl.logPathTpl(code);
  editForm.value.artifactPath = tpl.artifactPathTpl(code);
  editForm.value.remark = tpl.remark;
  checkPortConflict(editForm.value.port);
}

function handleCodeOrPortChange() {
  if (editForm.value.id) {
    checkPortConflict(editForm.value.port);
    return;
  }
  const tpl = SERVICE_TEMPLATES.find((t) => t.key === activeTemplateKey.value);
  if (!tpl || tpl.key === "custom") {
    checkPortConflict(editForm.value.port);
    return;
  }
  const code = editForm.value.serviceCode || "new-service";
  const port = editForm.value.port || tpl.defaultPort;
  editForm.value.startCmd = tpl.startCmdTpl(code, port);
  editForm.value.healthUrl = tpl.healthUrlTpl(code, port);
  editForm.value.logPath = tpl.logPathTpl(code);
  editForm.value.artifactPath = tpl.artifactPathTpl(code);
  checkPortConflict(port);
}

function checkPortConflict(port?: number) {
  if (portCheckTimer) clearTimeout(portCheckTimer);
  if (!port || port <= 0 || port > 65535) {
    portCheckResult.value = null;
    return;
  }
  portCheckTimer = setTimeout(async () => {
    portChecking.value = true;
    try {
      portCheckResult.value = await checkAppServicePort(port, editForm.value.id);
    } catch (e) {
      portCheckResult.value = null;
    } finally {
      portChecking.value = false;
    }
  }, 250);
}

// ==================== 6. 核心业务操作 ====================
// 按钮级防抖加载态: actionLoading['1-start'], actionLoading['1-restart'] 等
const actionLoading = ref<Record<string, boolean>>({});

function setActionLoading(id: number | string, action: string, val: boolean) {
  if (val) {
    actionLoading.value[`${id}-${action}`] = true;
  } else {
    delete actionLoading.value[`${id}-${action}`];
  }
}

function isActionLoading(id: number | string, action: string): boolean {
  return !!actionLoading.value[`${id}-${action}`];
}

async function loadServices(silent = false) {
  if (!silent && serviceList.value.length === 0) loading.value = true;
  try {
    const fresh = await fetchAppServices();
    if (serviceList.value.length === 0) {
      serviceList.value = fresh;
    } else {
      // In-place merge to avoid DOM re-creation, preserve hover and active states
      const freshMap = new Map(fresh.map((s) => [s.id, s]));
      for (const cur of serviceList.value) {
        const item = freshMap.get(cur.id);
        if (item) {
          Object.assign(cur, item);
          freshMap.delete(cur.id);
        }
      }
      for (const [_, item] of freshMap) {
        serviceList.value.push(item);
      }
      const freshIds = new Set(fresh.map((s) => s.id));
      serviceList.value = serviceList.value.filter((s) => freshIds.has(s.id));
    }
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
  setActionLoading(service.id, "start", true);
  const prevStatus = service.status;
  service.status = "STARTING"; // 乐观UI更新，瞬间给用户反馈
  try {
    ElMessage.info(`正在下发启动指令 [${service.serviceName}]...`);
    const res = await startAppService(service.id);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
      service.status = prevStatus;
    }
  } catch (err: any) {
    service.status = prevStatus;
    ElMessage.error("启动失败: " + (err?.message || "未知错误"));
  } finally {
    setActionLoading(service.id, "start", false);
    await loadServices(true);
  }
}

async function handleStop(service: AppServiceItem, timeout = 15) {
  if (service.serviceCode === "backend-main") {
    ElMessageBox.alert(
      "核心主服务 (backend-main) 承载整个平台中台与控制面 API。为防止控制台连接断开，禁止直接在线停机！如需更新部署，请使用【安全重启】或服务器外部守护脚本。",
      "安全保护警告",
      { type: "error", confirmButtonText: "我知道了" }
    );
    return;
  }
  try {
    await ElMessageBox.confirm(`确认停止服务 [${service.serviceName}] 吗？`, "优雅停止确认", {
      type: "warning",
      confirmButtonText: "确认停止",
      cancelButtonText: "取消"
    });
    setActionLoading(service.id, "stop", true);
    ElMessage.info(`正在停止服务 [${service.serviceName}]...`);
    const res = await stopAppService(service.id, timeout);
    if (res.success) {
      ElMessage.success(res.message);
      service.status = "STOPPED";
      service.currentPid = undefined;
    } else {
      ElMessage.error(res.message);
    }
  } catch (err) {
  } finally {
    setActionLoading(service.id, "stop", false);
    await loadServices(true);
  }
}

async function handleRestart(service: AppServiceItem) {
  const isCore = service.serviceCode === "backend-main";
  const confirmMsg = isCore
    ? `⚠️ 您正在重启【平台核心主服务 (${service.serviceName})】！\n重启期间控制台将断开连接约 3~5 秒，并由守护进程自愈拉起。确认继续重启吗？`
    : `确认重启服务 [${service.serviceName}] 吗？`;

  try {
    await ElMessageBox.confirm(confirmMsg, isCore ? "核心服务重启高危确认" : "重启确认", {
      type: isCore ? "error" : "warning",
      confirmButtonText: isCore ? "确认重启核心主服务" : "确认重启",
      cancelButtonText: "取消"
    });
    setActionLoading(service.id, "restart", true);
    service.status = "STARTING";
    ElMessage.info(`正在下发重启指令 [${service.serviceName}]...`);
    const res = await restartAppService(service.id, 15);
    if (res.success) {
      ElMessage.success(res.message);
    } else {
      ElMessage.error(res.message);
    }
  } catch (err) {
  } finally {
    setActionLoading(service.id, "restart", false);
    await loadServices(true);
  }
}

async function handleKill(service: AppServiceItem) {
  if (service.serviceCode === "backend-main") {
    ElMessageBox.alert(
      "核心主服务 (backend-main) 承载控制面基础支撑，严禁在线强制强杀！如需强制处理请登录服务器操作系统执行操作。",
      "安全防护禁止",
      { type: "error", confirmButtonText: "我知道了" }
    );
    return;
  }
  try {
    await ElMessageBox.confirm(`强制杀死进程 (SIGKILL) 将立即释放端口，但可能中断进行中的事务。确认强杀 [${service.serviceName}] 吗？`, "强制终止警告", {
      type: "error",
      confirmButtonText: "立即强杀",
      cancelButtonText: "取消"
    });
    setActionLoading(service.id, "kill", true);
    const res = await killAppService(service.id);
    if (res.success) {
      ElMessage.warning(res.message);
      service.status = "STOPPED";
      service.currentPid = undefined;
    } else {
      ElMessage.error(res.message);
    }
  } catch (err) {
  } finally {
    setActionLoading(service.id, "kill", false);
    await loadServices(true);
  }
}

// ==================== 7. 终端日志流处理 ====================
let logBuffer: string[] = [];
let rafId: number | null = null;

async function openTerminal(service: AppServiceItem) {
  terminalService.value = service;
  terminalLogs.value = [];
  terminalVisible.value = true;
  terminalSearch.value = "";
  autoScroll.value = true;
  logBuffer = [];

  // 1. 先拉取最近 100 行日志
  try {
    const recent = await fetchAppRecentLogs(service.id, 100);
    terminalLogs.value = recent;
    scrollTerminalToBottom();
  } catch (e) {
    terminalLogs.value = ["[SYSTEM ERROR] 获取初始日志失败"];
  }

  // 2. 建立 SSE 实时推流（加入 requestAnimationFrame 帧级缓冲，避免刷屏卡顿）
  if (sseSource) {
    sseSource.close();
  }
  const sseUrl = `${import.meta.env.VITE_API_BASE_URL || "/api"}/infra/devops/services/${service.id}/logs/stream`;
  sseSource = new EventSource(sseUrl);

  const flushLogs = () => {
    if (logBuffer.length > 0) {
      terminalLogs.value.push(...logBuffer);
      logBuffer = [];
      if (terminalLogs.value.length > 600) {
        terminalLogs.value = terminalLogs.value.slice(-600);
      }
      if (autoScroll.value) {
        scrollTerminalToBottom();
      }
    }
    rafId = null;
  };

  sseSource.addEventListener("log", (evt) => {
    logBuffer.push(evt.data);
    if (!rafId) {
      rafId = requestAnimationFrame(flushLogs);
    }
  });

  sseSource.addEventListener("error", () => {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
  });
}

function closeTerminal() {
  if (sseSource) {
    sseSource.close();
    sseSource = null;
  }
  if (rafId) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  logBuffer = [];
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

const filteredLogs = computed(() => {
  if (!terminalSearch.value) return terminalLogs.value;
  const kw = terminalSearch.value.toLowerCase();
  return terminalLogs.value.filter(line => line.toLowerCase().includes(kw));
});

function getLogClass(line: string) {
  if (line.includes("ERROR") || line.includes("Exception") || line.includes("FAILED")) return "log-error";
  if (line.includes("WARN")) return "log-warn";
  if (line.includes("INFO")) return "log-info";
  if (line.includes("DEBUG")) return "log-debug";
  if (line.includes("[TIANSHU CI]")) return "log-ci";
  return "log-default";
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
  }
}

// ==================== 10. 服务配置添加/编辑 ====================
function openEditDialog(service?: AppServiceItem) {
  if (service) {
    editForm.value = { ...service };
    activeTemplateKey.value = "";
    checkPortConflict(service.port);
  } else {
    editForm.value = {
      serviceCode: "demo-service-" + Date.now().toString().slice(-4),
      serviceName: "新建业务微服务",
      serviceGroup: "PLATFORM",
      autoRestart: 0
    };
    applyTemplate(SERVICE_TEMPLATES[0], true);
  }
  editDialogVisible.value = true;
}

async function submitSaveService() {
  if (!editForm.value.serviceCode || !editForm.value.serviceName || !editForm.value.workDir || !editForm.value.startCmd) {
    ElMessage.warning("请完整填写服务标识、服务名称、工作目录与启动命令");
    return;
  }
  if (portCheckResult.value && !portCheckResult.value.available) {
    try {
      await ElMessageBox.confirm(
        `当前端口 [${editForm.value.port}] ${portCheckResult.value.message}。强制保存可能导致服务启动冲突，是否确认继续保存？`,
        "端口占用冲突提示",
        { type: "warning", confirmButtonText: "仍然保存", cancelButtonText: "返回修改" }
      );
    } catch {
      return;
    }
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
  <div class="devops-page">
    <!-- 1. 顶部 Header 与全景卡片 -->
    <el-card shadow="never" class="header-card mb-4">
      <div class="header-top-row">
        <div class="header-left">
          <div class="title-with-icon">
            <div class="header-badge-icon">
              <el-icon><Platform /></el-icon>
            </div>
            <div>
              <div class="title-line">
                <h2 class="main-title">服务编排与发布中台</h2>
                <el-tag type="primary" size="small" effect="plain" class="driver-tag">
                  双模驱动 · 敏捷 DevOps
                </el-tag>
              </div>
              <p class="sub-desc">
                全自动编译构建 · 制品版本热更与回滚 · 进程生命周期平滑启停 · 实时流式日志终端
              </p>
            </div>
          </div>
        </div>

        <div class="header-right">
          <div class="auto-refresh-box">
            <el-icon color="#409eff"><Timer /></el-icon>
            <span class="refresh-label">自动刷新:</span>
            <el-select v-model="autoRefreshSeconds" size="default" style="width: 105px;" @change="handleAutoRefreshChange">
              <el-option :value="0" label="关闭" />
              <el-option :value="3" label="3 秒" />
              <el-option :value="5" label="5 秒" />
              <el-option :value="10" label="10 秒" />
            </el-select>
          </div>

          <el-button type="primary" :icon="Plus" @click="openEditDialog()">
            新建纳管服务
          </el-button>

          <el-button :icon="Refresh" :loading="loading" @click="loadServices()">
            刷新状态
          </el-button>
        </div>
      </div>

      <!-- 4 大全景统计指标栏 -->
      <div class="stat-metrics-row">
        <div class="stat-metric-card">
          <div class="stat-label">
            <el-icon color="#409eff"><Box /></el-icon>
            <span>总纳管服务</span>
          </div>
          <div class="stat-val">
            {{ totalServices }} <span class="unit">个模块</span>
          </div>
        </div>

        <div class="stat-metric-card">
          <div class="stat-label">
            <span class="pulse-dot green"></span>
            <span>正常运行实例</span>
          </div>
          <div class="stat-val text-success">
            {{ runningServices }} <span class="unit">/ {{ totalServices }} 在线</span>
          </div>
        </div>

        <div class="stat-metric-card">
          <div class="stat-label">
            <el-icon color="#6366f1"><Cpu /></el-icon>
            <span>物理内存常驻 (RSS)</span>
          </div>
          <div class="stat-val text-indigo">
            {{ totalMemoryMb }} <span class="unit">MB</span>
          </div>
        </div>

        <div class="stat-metric-card">
          <div class="stat-label">
            <el-icon color="#eab308"><SetUp /></el-icon>
            <span>编排驱动引擎</span>
          </div>
          <div class="stat-val text-engine">
            ProcessTree + Docker
          </div>
        </div>
      </div>
    </el-card>

    <!-- 2. 服务卡片矩阵 (Service Card Grid) -->
    <el-row :gutter="16">
      <el-col
        v-for="service in serviceList"
        :key="service.id"
        :xs="24"
        :sm="24"
        :md="12"
        :lg="12"
        :xl="12"
        class="mb-4"
      >
        <el-card
          shadow="hover"
          class="service-box-card"
          :class="{
            'card-running': service.status === 'RUNNING',
            'card-compiling': service.status === 'COMPILING',
            'card-starting': service.status === 'STARTING',
            'card-error': service.status === 'ERROR',
            'card-stopped': service.status === 'STOPPED'
          }"
        >
          <!-- 卡片顶栏 -->
          <div class="service-card-top">
            <div class="service-title-area">
              <div
                class="service-icon-box"
                :class="{
                  'icon-running': service.status === 'RUNNING',
                  'icon-compiling': service.status === 'COMPILING',
                  'icon-error': service.status === 'ERROR',
                  'icon-stopped': service.status === 'STOPPED'
                }"
              >
                <span v-if="service.serviceType === 'JAR'">☕</span>
                <span v-else-if="service.serviceType === 'NODE'">🌐</span>
                <span v-else-if="service.serviceType === 'DOCKER'">🐳</span>
                <span v-else>⚡</span>
              </div>

              <div class="service-name-box">
                <div class="name-row">
                  <h3 class="service-name-text">{{ service.serviceName }}</h3>
                  <el-tag size="small" type="info" class="code-tag font-mono">{{ service.serviceCode }}</el-tag>
                  <el-tag size="small" type="primary" effect="plain" class="driver-tag">{{ service.driverType }}</el-tag>
                </div>
                <div class="service-remark-text" :title="service.remark">
                  {{ service.remark || '暂无描述' }}
                </div>
              </div>
            </div>

            <!-- 状态标签带脉冲灯 -->
            <div class="service-status-area">
              <el-tag
                :type="service.status === 'RUNNING' ? 'success' :
                       service.status === 'COMPILING' ? 'primary' :
                       service.status === 'STARTING' ? 'warning' :
                       service.status === 'ERROR' ? 'danger' : 'info'"
                size="default"
                effect="light"
                class="status-tag"
              >
                <span
                  class="pulse-dot"
                  :class="{
                    'green': service.status === 'RUNNING',
                    'blue': service.status === 'COMPILING',
                    'amber': service.status === 'STARTING',
                    'red': service.status === 'ERROR',
                    'gray': service.status === 'STOPPED'
                  }"
                ></span>
                {{ service.status === 'RUNNING' ? '运行中' :
                   service.status === 'COMPILING' ? '编译中' :
                   service.status === 'STARTING' ? '启动中' :
                   service.status === 'ERROR' ? '异常' : '已停止' }}
              </el-tag>
            </div>
          </div>

          <!-- 卡片中间：4 宫格遥测数据 -->
          <div class="service-telemetry-grid">
            <div class="telemetry-cell">
              <span class="cell-label">端口 / 探针</span>
              <div class="cell-val">
                <span class="font-mono font-bold">{{ service.port ? `:${service.port}` : '无' }}</span>
                <el-tag v-if="service.portListening" type="success" size="small" effect="dark" class="port-tag">LISTENING</el-tag>
                <el-tag v-else type="info" size="small" effect="plain" class="port-tag">STOPPED</el-tag>
              </div>
            </div>

            <div class="telemetry-cell">
              <span class="cell-label">系统 PID</span>
              <div class="cell-val font-mono font-bold">
                {{ service.currentPid ? `#${service.currentPid}` : '-' }}
              </div>
            </div>

            <div class="telemetry-cell">
              <span class="cell-label">本次运行已持续</span>
              <div class="cell-val font-mono">
                {{ formatUptime(service.uptimeSeconds) }}
              </div>
            </div>

            <div class="telemetry-cell">
              <span class="cell-label">物理内存 / CPU</span>
              <div class="cell-val font-mono text-indigo font-bold">
                {{ formatBytes(service.memoryRssBytes) }}
                <span class="cpu-text" v-if="service.cpuUsagePercent">({{ service.cpuUsagePercent }}%)</span>
              </div>
            </div>
          </div>

          <!-- 工作路径与制品名 -->
          <div class="service-meta-strip">
            <div class="strip-left" :title="service.workDir">
              <el-icon class="mr-1 text-slate-400"><Folder /></el-icon>
              <span class="font-mono strip-text">{{ service.workDir }}</span>
            </div>
            <div class="strip-right font-mono" v-if="service.artifactPath">
              {{ service.artifactPath.split('/').pop() }}
            </div>
          </div>

          <!-- 卡片底部操作栏 -->
          <div class="service-card-actions">
            <div class="action-btn-left">
              <el-button
                type="success"
                size="default"
                :icon="VideoPlay"
                :loading="isActionLoading(service.id, 'start')"
                :disabled="service.status === 'RUNNING' || service.status === 'COMPILING' || isActionLoading(service.id, 'start')"
                @click="handleStart(service)"
              >
                启动
              </el-button>

              <el-button
                type="primary"
                size="default"
                :icon="RefreshRight"
                :loading="isActionLoading(service.id, 'restart')"
                :disabled="service.status === 'COMPILING' || isActionLoading(service.id, 'restart')"
                @click="handleRestart(service)"
              >
                重启
              </el-button>

              <el-tooltip
                v-if="service.serviceCode === 'backend-main'"
                content="平台核心控制面，禁止在线停机以防控制台失联"
                placement="top"
              >
                <el-button
                  type="info"
                  size="default"
                  :icon="Lock"
                  disabled
                >
                  受保护
                </el-button>
              </el-tooltip>
              <el-dropdown v-else trigger="click">
                <el-button
                  type="danger"
                  size="default"
                  :icon="VideoPause"
                  :loading="isActionLoading(service.id, 'stop') || isActionLoading(service.id, 'kill')"
                  :disabled="service.status === 'STOPPED' || isActionLoading(service.id, 'stop') || isActionLoading(service.id, 'kill')"
                >
                  停止
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleStop(service, 15)">
                      <el-icon><VideoPause /></el-icon> 优雅停止 (SIGTERM)
                    </el-dropdown-item>
                    <el-dropdown-item divided @click="handleKill(service)" class="text-danger">
                      <el-icon><WarningFilled /></el-icon> 强制杀死 (SIGKILL)
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <div class="action-btn-right">
              <el-tooltip content="一键触发自动化构建打包" placement="top">
                <el-button
                  size="default"
                  :loading="service.status === 'COMPILING'"
                  @click="openBuildDrawer(service)"
                >
                  <el-icon><SetUp /></el-icon> 编译
                </el-button>
              </el-tooltip>

              <el-tooltip content="查看实时运行日志 (tail -f)" placement="top">
                <el-button size="default" @click="openTerminal(service)">
                  <el-icon><Monitor /></el-icon> 日志
                </el-button>
              </el-tooltip>

              <el-tooltip content="上传制品与历史版本回滚" placement="top">
                <el-button size="default" @click="openArtifactModal(service)">
                  <el-icon><Upload /></el-icon> 制品
                </el-button>
              </el-tooltip>

              <el-dropdown trigger="click">
                <el-button size="default">···</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openEditDialog(service)">
                      <el-icon><Edit /></el-icon> 编辑服务配置
                    </el-dropdown-item>
                    <el-dropdown-item divided @click="handleDeleteService(service)" class="text-danger">
                      <el-icon><Delete /></el-icon> 删除该服务
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 实时终端弹窗 (macOS 极简暗黑终端风格) -->
    <el-dialog
      v-model="terminalVisible"
      :show-close="false"
      width="82%"
      top="4vh"
      class="terminal-dialog-custom"
      :before-close="closeTerminal"
    >
      <div class="terminal-shell">
        <div class="terminal-topbar">
          <div class="traffic-lights">
            <span class="traffic-btn close-btn" @click="closeTerminal"></span>
            <span class="traffic-btn min-btn"></span>
            <span class="traffic-btn max-btn"></span>
            <span class="term-title font-mono">
              tianshu@service: ~ / {{ terminalService?.serviceCode }} / live.log
            </span>
          </div>

          <div class="term-actions">
            <el-input
              v-model="terminalSearch"
              placeholder="搜索日志关键字..."
              size="small"
              style="width: 180px;"
              clearable
              :prefix-icon="Search"
            />

            <el-button
              size="small"
              :type="autoScroll ? 'primary' : 'info'"
              plain
              @click="autoScroll = !autoScroll"
            >
              {{ autoScroll ? '滚动锁定: 开' : '滚动锁定: 关' }}
            </el-button>

            <el-button size="small" @click="clearTerminal">清屏</el-button>
            <el-button size="small" :icon="Download" @click="downloadTerminalLog">下载日志</el-button>
            <el-button size="small" :icon="Close" circle @click="closeTerminal" />
          </div>
        </div>

        <div ref="terminalBodyRef" class="terminal-screen font-mono">
          <div v-if="filteredLogs.length === 0" class="empty-term-tip">
            暂无日志输出或无匹配项
          </div>
          <div
            v-for="(line, index) in filteredLogs"
            :key="index"
            class="term-line"
            :class="getLogClass(line)"
          >
            {{ line }}
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 4. 自动化构建抽屉 -->
    <el-drawer
      v-model="buildDrawerVisible"
      title="全自动编译构建流水线 (CI/CD Pipeline)"
      size="65%"
    >
      <div v-if="buildService" class="build-drawer-content">
        <el-card shadow="never" class="mb-4 build-cmd-card">
          <div class="build-cmd-header">
            <div>
              <h4 class="service-h4">{{ buildService.serviceName }}</h4>
              <p class="service-p font-mono">工作目录: {{ buildService.workDir }}</p>
            </div>
            <el-button
              type="primary"
              :icon="CaretRight"
              :loading="buildExecuting"
              @click="executeBuild"
            >
              立即执行编译构建
            </el-button>
          </div>

          <div class="cmd-input-row">
            <span class="cmd-label">执行编译命令 (可按需微调):</span>
            <el-input
              v-model="customBuildCmd"
              placeholder="如: mvn clean package -DskipTests"
              size="default"
              class="font-mono"
            />
          </div>
        </el-card>

        <div class="build-body-grid">
          <!-- 左侧历史 -->
          <div class="build-history-col">
            <div class="history-title">历史构建记录</div>
            <div v-if="buildRecords.length === 0" class="empty-history">暂无记录</div>
            <div
              v-for="rec in buildRecords"
              :key="rec.id"
              class="history-item"
              :class="{ 'item-active': activeBuildRecord?.id === rec.id }"
              @click="selectBuildRecord(rec)"
            >
              <div class="rec-row1">
                <span class="font-mono rec-no">{{ rec.buildNo }}</span>
                <el-tag
                  size="small"
                  :type="rec.status === 'SUCCESS' ? 'success' : rec.status === 'FAILED' ? 'danger' : 'primary'"
                >
                  {{ rec.status }}
                </el-tag>
              </div>
              <div class="rec-row2">
                <span>{{ rec.createdAt ? rec.createdAt.replace('T', ' ').slice(5, 16) : '-' }}</span>
                <span v-if="rec.durationMs">{{ (rec.durationMs / 1000).toFixed(1) }}s</span>
              </div>
            </div>
          </div>

          <!-- 右侧控制台 -->
          <div class="build-console-col">
            <div class="console-header">
              <span class="font-mono">Console Output: {{ activeBuildRecord?.buildNo || 'None' }}</span>
              <span v-if="buildExecuting" class="building-indicator">
                <el-icon class="is-loading"><Loading /></el-icon> 正在编译打包中...
              </span>
            </div>
            <div class="console-body font-mono">
              <div v-for="(line, idx) in liveBuildLogs" :key="idx" class="term-line" :class="getLogClass(line)">
                {{ line }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 5. 制品中心与版本回滚弹窗 -->
    <el-dialog
      v-model="artifactModalVisible"
      title="制品中心与多版本发布 (Artifact Hub & Rollback)"
      width="60%"
    >
      <div v-if="artifactService" class="artifact-content">
        <!-- 上传新制品 -->
        <el-card shadow="never" class="mb-4 upload-card">
          <div class="upload-title">
            <el-icon color="#409eff"><Upload /></el-icon>
            <span>手动上传新制品部署 (Jar / Zip)</span>
          </div>
          <div class="upload-row">
            <input type="file" @change="handleFileChange" class="file-picker" />
            <el-input v-model="uploadRemark" placeholder="发布备注 (如: 修复审批流回退漏洞)" size="default" style="flex: 1;" />
            <el-button type="primary" :loading="uploadSubmitting" @click="submitUploadArtifact">
              确认部署
            </el-button>
          </div>
        </el-card>

        <!-- 历史版本 -->
        <div class="artifact-history-section">
          <h4 class="history-h4">版本历史归档 (支持一键秒级回滚)</h4>
          <div v-if="artifactList.length === 0" class="empty-history">暂无归档制品</div>
          <div
            v-for="art in artifactList"
            :key="art.id"
            class="artifact-item"
            :class="{ 'current-art': art.isCurrent }"
          >
            <div class="art-info">
              <div class="art-top">
                <span class="art-tag font-mono">{{ art.versionTag }}</span>
                <el-tag v-if="art.isCurrent" size="small" type="success" effect="dark">当前生产版本</el-tag>
                <span class="art-file font-mono">{{ art.fileName }}</span>
              </div>
              <div class="art-meta font-mono">
                <span>大小: {{ (art.fileSizeBytes / (1024 * 1024)).toFixed(2) }} MB</span>
                <span>MD5: {{ art.fileMd5.slice(0, 8) }}...</span>
                <span>时间: {{ art.createdAt ? art.createdAt.replace('T', ' ').slice(0, 19) : '-' }}</span>
                <span v-if="art.remark" class="text-secondary">备注: {{ art.remark }}</span>
              </div>
            </div>

            <div class="art-action">
              <el-button
                v-if="!art.isCurrent"
                size="small"
                type="warning"
                plain
                @click="handleRollback(art)"
              >
                一键回滚至此版本
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 6. 服务配置添加/编辑弹窗 (支持模板化与端口冲突检测) -->
    <el-dialog
      v-model="editDialogVisible"
      :title="editForm.id ? '编辑纳管服务' : '新建纳管服务'"
      width="58%"
      class="service-edit-dialog"
      destroy-on-close
    >
      <!-- 智能模板选择器 (仅在新建服务时展示) -->
      <div v-if="!editForm.id" class="template-selector-box mb-4">
        <div class="template-header">
          <div class="tpl-title">
            <el-icon color="#409eff"><MagicStick /></el-icon>
            <span>推荐应用架构模板（点击即可一键预填标准指令与探针）</span>
          </div>
          <el-tag size="small" type="success" effect="light">标准契约对齐</el-tag>
        </div>
        <div class="template-cards-grid">
          <div
            v-for="tpl in SERVICE_TEMPLATES"
            :key="tpl.key"
            class="tpl-card"
            :class="{ active: activeTemplateKey === tpl.key }"
            @click="applyTemplate(tpl, true)"
          >
            <div class="tpl-card-icon">{{ tpl.icon }}</div>
            <div class="tpl-card-info">
              <div class="tpl-card-name">{{ tpl.name }}</div>
              <div class="tpl-card-desc">{{ tpl.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <el-form label-width="120px" class="edit-form-content">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="服务代码 (唯一)" required>
              <el-input
                v-model="editForm.serviceCode"
                placeholder="如: crm-service"
                :disabled="!!editForm.id"
                @input="handleCodeOrPortChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务显示名称" required>
              <el-input v-model="editForm.serviceName" placeholder="如: 天枢云基 · CRM 业务模块" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="服务架构类型">
              <el-select v-model="editForm.serviceType" style="width: 100%;">
                <el-option value="JAR" label="☕ Java Jar 应用" />
                <el-option value="NODE" label="🌐 Node.js / 前端应用" />
                <el-option value="DOCKER" label="🐳 Docker 容器镜像" />
                <el-option value="SCRIPT" label="⚡ 原生程序 / 脚本 (Python/Go/Shell)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编排驱动引擎">
              <el-select v-model="editForm.driverType" style="width: 100%;">
                <el-option value="HOST_PROCESS" label="Host 宿主机进程树 (Java 21 ProcessTree)" />
                <el-option value="DOCKER" label="Docker Engine (Socket / CLI)" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="服务工作目录" required>
          <el-input v-model="editForm.workDir" placeholder="服务的根目录绝对路径，如: /Users/xxx/project" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="服务监听端口" required>
              <div class="port-input-wrapper">
                <el-input-number
                  v-model="editForm.port"
                  :min="1"
                  :max="65535"
                  style="width: 130px;"
                  @change="handleCodeOrPortChange"
                />
                <div class="port-status-inline">
                  <span v-if="portChecking" class="status-checking">
                    <el-icon class="is-loading"><Loading /></el-icon> 检查端口...
                  </span>
                  <span v-else-if="portCheckResult?.available" class="status-available text-success">
                    <el-icon><CircleCheck /></el-icon> 端口可用
                  </span>
                  <el-tooltip
                    v-else-if="portCheckResult && !portCheckResult.available"
                    :content="portCheckResult.message"
                    placement="top"
                  >
                    <span class="status-occupied text-danger">
                      <el-icon><CircleClose /></el-icon> 冲突: {{ portCheckResult.occupiedByRegisteredService ? '已分配' : '被占用' }}
                    </span>
                  </el-tooltip>
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="健康探测 URL">
              <el-input v-model="editForm.healthUrl" placeholder="如: http://127.0.0.1:8080/actuator/health" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="启动运行命令" required>
          <el-input
            v-model="editForm.startCmd"
            type="textarea"
            :rows="2"
            placeholder="如: java -Dfile.encoding=UTF-8 -jar app.jar 或 npm run dev"
          />
        </el-form-item>

        <el-form-item label="自动编译命令">
          <el-input v-model="editForm.buildCmd" placeholder="如: mvn clean package -DskipTests 或 npm run build" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="制品产物相对路径">
              <el-input v-model="editForm.artifactPath" placeholder="如: target/app.jar 或 dist" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日志输出路径">
              <el-input v-model="editForm.logPath" placeholder="留空默认: workDir/logs/{serviceCode}.log" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="业务用途说明">
          <el-input v-model="editForm.remark" placeholder="服务的用途说明或技术栈说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer-actions">
          <el-button v-if="!editForm.id && activeTemplateKey" size="default" @click="handleCodeOrPortChange">
            <el-icon><Refresh /></el-icon> 重新依模板填充
          </el-button>
          <div class="right-buttons">
            <el-button @click="editDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitSaveService">保存并纳管服务</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.devops-page {
  padding: 16px;
}

.mb-4 {
  margin-bottom: 16px;
}

.mr-1 {
  margin-right: 4px;
}

.font-mono {
  font-family: var(--sb-font-mono, monospace);
}

.font-bold {
  font-weight: 700;
}

/* 顶部卡片 */
.header-card {
  border-radius: 12px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-light, #e2e8f0);
}

.header-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left .title-with-icon {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-badge-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9 0%, #6366f1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3);
}

.title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: var(--el-text-color-primary, #0f172a);
}

.sub-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
  margin: 4px 0 0 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.auto-refresh-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #64748b);
  background: var(--el-fill-color-light, #f8fafc);
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
}

/* 4 大全景指标栏 */
.stat-metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter, #f1f5f9);
}

.stat-metric-card {
  background: var(--el-fill-color-light, #f8fafc);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  border-radius: 8px;
  padding: 10px 14px;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-val {
  font-size: 20px;
  font-weight: 700;
  margin-top: 4px;
  color: var(--el-text-color-primary, #0f172a);
}

.stat-val .unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary, #94a3b8);
}

.text-success {
  color: #10b981 !important;
}

.text-indigo {
  color: #6366f1 !important;
}

.text-engine {
  font-size: 14px;
  color: #f59e0b;
}

/* 服务卡片 */
.service-box-card {
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  background: var(--el-bg-color, #ffffff);
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;
}

.service-box-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light);
}

.card-running {
  border-left: 4px solid #10b981;
}

.card-compiling {
  border-left: 4px solid #0ea5e9;
}

.card-starting {
  border-left: 4px solid #f59e0b;
}

.card-error {
  border-left: 4px solid #ef4444;
}

.card-stopped {
  border-left: 4px solid #94a3b8;
}

.service-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.service-title-area {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.service-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background: var(--el-fill-color-light, #f1f5f9);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
}

.icon-running {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.3);
}

.icon-compiling {
  background: rgba(14, 165, 233, 0.1);
  border-color: rgba(14, 165, 233, 0.3);
}

.service-name-box {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.service-name-text {
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--el-text-color-primary, #0f172a);
}

.code-tag {
  font-size: 11px;
}

.service-remark-text {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 脉冲灯 */
.pulse-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 5px;
}

.pulse-dot.green {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.pulse-dot.blue {
  background: #0ea5e9;
  box-shadow: 0 0 8px #0ea5e9;
}

.pulse-dot.amber {
  background: #f59e0b;
  box-shadow: 0 0 8px #f59e0b;
}

.pulse-dot.red {
  background: #ef4444;
  box-shadow: 0 0 8px #ef4444;
}

.pulse-dot.gray {
  background: #94a3b8;
}

/* 遥测数据 4 宫格 */
.service-telemetry-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-light, #f8fafc);
  border-radius: 6px;
  margin-bottom: 10px;
}

.telemetry-cell {
  display: flex;
  flex-direction: column;
}

.cell-label {
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
}

.cell-val {
  font-size: 13px;
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.port-tag {
  padding: 0 4px;
  font-size: 10px;
}

.cpu-text {
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
  font-weight: 400;
}

/* 文件路径条 */
.service-meta-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
  padding: 6px 10px;
  background: var(--el-fill-color-lighter, #f1f5f9);
  border-radius: 4px;
  margin-bottom: 12px;
}

.strip-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.strip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strip-right {
  flex-shrink: 0;
  margin-left: 8px;
  color: #0ea5e9;
}

/* 卡片操作栏 */
.service-card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.action-btn-left,
.action-btn-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.text-danger {
  color: #ef4444 !important;
}

/* 终端样式 */
.terminal-shell {
  background: #0a0f1d;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.4);
}

.terminal-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: #111827;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.traffic-lights {
  display: flex;
  align-items: center;
  gap: 8px;
}

.traffic-btn {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  cursor: pointer;
}

.close-btn { background: #ef4444; }
.min-btn { background: #f59e0b; }
.max-btn { background: #10b981; }

.term-title {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}

.term-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.terminal-screen {
  padding: 14px 16px;
  height: 60vh;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #cbd5e1;
  background: #090d16;
}

.term-line {
  white-space: pre-wrap;
  word-break: break-all;
}

.empty-term-tip {
  text-align: center;
  color: #64748b;
  padding: 40px 0;
}

.log-error { color: #f87171 !important; }
.log-warn { color: #fbbf24 !important; }
.log-info { color: #34d399 !important; }
.log-debug { color: #38bdf8 !important; }
.log-ci { color: #67e8f9 !important; font-weight: 700; }
.log-default { color: #cbd5e1; }

/* 抽屉样式 */
.build-cmd-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.service-h4 {
  font-size: 15px;
  font-weight: 700;
  margin: 0;
}

.service-p {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
  margin: 4px 0 0 0;
}

.cmd-input-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cmd-label {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
}

.build-body-grid {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 14px;
  height: calc(100vh - 280px);
}

.build-history-col {
  background: var(--el-fill-color-light, #f8fafc);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  border-radius: 8px;
  padding: 10px;
  overflow-y: auto;
}

.history-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 10px;
}

.history-item {
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  margin-bottom: 8px;
  cursor: pointer;
  background: var(--el-bg-color, #ffffff);
}

.history-item.item-active {
  border-color: #0ea5e9;
  background: rgba(14, 165, 233, 0.08);
}

.rec-row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rec-no {
  font-size: 12px;
  font-weight: 700;
}

.rec-row2 {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
  margin-top: 4px;
}

.build-console-col {
  background: #090d16;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.console-header {
  padding: 8px 12px;
  background: #111827;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  justify-content: space-between;
}

.console-body {
  padding: 12px;
  flex: 1;
  overflow-y: auto;
  font-size: 12px;
  line-height: 1.6;
}

/* 制品模态框 */
.upload-card {
  background: var(--el-fill-color-light, #f8fafc);
  border-radius: 8px;
}

.upload-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 10px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-picker {
  font-size: 12px;
}

.history-h4 {
  font-size: 14px;
  font-weight: 700;
  margin: 16px 0 10px 0;
}

.artifact-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  background: var(--el-bg-color, #ffffff);
  margin-bottom: 8px;
}

.artifact-item.current-art {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.05);
}

.art-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.art-tag {
  font-size: 13px;
  font-weight: 700;
}

.art-file {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
}

.art-meta {
  display: flex;
  gap: 14px;
  font-size: 11px;
  color: var(--el-text-color-secondary, #94a3b8);
  margin-top: 4px;
}

/* 智能应用架构模板选择器 */
.template-selector-box {
  background: var(--el-fill-color-light, #f8fafc);
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  border-radius: 10px;
  padding: 12px 14px;
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.tpl-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #0f172a);
}

.template-cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.tpl-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tpl-card:hover {
  border-color: var(--el-color-primary, #409eff);
  transform: translateY(-1px);
}

.tpl-card.active {
  border-color: var(--el-color-primary, #409eff);
  background: rgba(64, 158, 255, 0.08);
  box-shadow: 0 0 0 1px var(--el-color-primary, #409eff);
}

.tpl-card-icon {
  font-size: 20px;
  line-height: 1;
}

.tpl-card-info {
  overflow: hidden;
}

.tpl-card-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary, #0f172a);
}

.tpl-card-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary, #64748b);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 130px;
}

/* 端口检测指示器 */
.port-input-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.port-status-inline {
  display: flex;
  align-items: center;
  font-size: 12px;
}

.status-checking {
  color: var(--el-text-color-secondary, #64748b);
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-available {
  color: #10b981;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-occupied {
  color: #ef4444;
  font-weight: 600;
  cursor: help;
  display: flex;
  align-items: center;
  gap: 4px;
}

.dialog-footer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.dialog-footer-actions .right-buttons {
  display: flex;
  gap: 10px;
}
</style>
