<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowLeft, FullScreen, Refresh } from "@element-plus/icons-vue";

const router = useRouter();
const currentTime = ref("");
let timer: any = null;
const isFullscreen = ref(false);

function updateTime() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const date = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");
  currentTime.value = `${year}-${month}-${date} ${hours}:${minutes}:${seconds}`;
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen();
    isFullscreen.value = true;
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen();
      isFullscreen.value = false;
    }
  }
}

function goBack() {
  router.push("/dashboard");
}

// 统计核心数据
const stats = ref({
  totalGmv: "¥ 18,492,600",
  activeTenants: 128,
  throughputToday: "1,294,820",
  workflowCount: 384,
  uptimeDays: 246
});

const recentLogs = ref([
  { time: "15:08:12", tenant: "华为云生态", action: "采购入库核销", status: "SUCCESS" },
  { time: "15:06:45", tenant: "比亚迪供应链", action: "批量审批通过 (6笔)", status: "SUCCESS" },
  { time: "15:04:22", tenant: "顺丰速运", action: "高并发短信投递", status: "SUCCESS" },
  { time: "15:01:09", tenant: "微盟科技", action: "大额支付回执校验", status: "SUCCESS" },
  { time: "14:58:33", tenant: "阿里云计算", action: "租户套餐静默扩容", status: "SUCCESS" },
  { time: "14:55:01", tenant: "腾讯金融", action: "跨端会话安全校验", status: "SUCCESS" }
]);

onMounted(() => {
  updateTime();
  timer = setInterval(updateTime, 1000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="data-screen-shell">
    <!-- 科技动态星轨与网格底纹 -->
    <div class="screen-bg-grid" />
    <div class="screen-glow-spot" />

    <!-- 顶部科技感 Header -->
    <header class="screen-header">
      <div class="header-left">
        <el-button class="screen-back-btn" :icon="ArrowLeft" plain size="small" @click="goBack">
          返回控制台
        </el-button>
        <span class="header-clock">{{ currentTime }}</span>
      </div>

      <div class="header-center">
        <div class="header-title-glow">
          <span class="title-sub">ANTIGRAVITY INTELLIGENT PLATFORM</span>
          <h1 class="title-main">企业级多租户数字化实时指挥中心</h1>
        </div>
      </div>

      <div class="header-right">
        <el-button class="screen-back-btn" :icon="FullScreen" plain size="small" @click="toggleFullscreen">
          {{ isFullscreen ? "退出全屏" : "全屏沉浸" }}
        </el-button>
        <span class="status-badge">
          <span class="status-dot-pulse" />
          系统全链路健康 (UP)
        </span>
      </div>
    </header>

    <!-- 主展示面板三栏布局 -->
    <main class="screen-body">
      <!-- 左栏：指标仪表与排行 -->
      <section class="screen-column col-left">
        <div class="screen-card">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>实时多租户负荷 TOP5</h3>
          </div>
          <div class="card-content">
            <div class="tenant-rank-item">
              <span class="rank-badge rank-1">01</span>
              <span class="rank-name">华为云数字化生态</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" style="width: 92%;" /></div>
              <span class="rank-val">92%</span>
            </div>
            <div class="tenant-rank-item">
              <span class="rank-badge rank-2">02</span>
              <span class="rank-name">比亚迪新能源供应链</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" style="width: 85%;" /></div>
              <span class="rank-val">85%</span>
            </div>
            <div class="tenant-rank-item">
              <span class="rank-badge rank-3">03</span>
              <span class="rank-name">顺丰全球智慧物流</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" style="width: 78%;" /></div>
              <span class="rank-val">78%</span>
            </div>
            <div class="tenant-rank-item">
              <span class="rank-badge">04</span>
              <span class="rank-name">腾讯企业服务集群</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" style="width: 64%;" /></div>
              <span class="rank-val">64%</span>
            </div>
            <div class="tenant-rank-item">
              <span class="rank-badge">05</span>
              <span class="rank-name">阿里国际供应链</span>
              <div class="rank-bar-bg"><div class="rank-bar-fill" style="width: 53%;" /></div>
              <span class="rank-val">53%</span>
            </div>
          </div>
        </div>

        <div class="screen-card mt-3">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>集成网关吞吐速率 (QPS)</h3>
          </div>
          <div class="card-content">
            <div class="qps-grid">
              <div class="qps-cell">
                <span class="qps-label">消息短信网关</span>
                <span class="qps-num">4,820 <small>req/s</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">SMTP 邮件中心</span>
                <span class="qps-num">950 <small>req/s</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">聚合支付通道</span>
                <span class="qps-num">1,210 <small>req/s</small></span>
              </div>
              <div class="qps-cell">
                <span class="qps-label">AI 模型流式推理</span>
                <span class="qps-num">360 <small>tokens/s</small></span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 中栏：核心数字 KPI + 宏观全景地图 -->
      <section class="screen-column col-center">
        <!-- 核心统计大卡 -->
        <div class="kpi-banner">
          <div class="kpi-item">
            <span class="kpi-label">平台交易累计总额 (GMV)</span>
            <span class="kpi-value text-gold">{{ stats.totalGmv }}</span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">当前活跃多租户</span>
            <span class="kpi-value text-cyan">{{ stats.activeTenants }} <small>家</small></span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">今日网关总吞吐</span>
            <span class="kpi-value text-blue">{{ stats.throughputToday }}</span>
          </div>
          <div class="kpi-divider" />
          <div class="kpi-item">
            <span class="kpi-label">工作流流转审批</span>
            <span class="kpi-value text-emerald">{{ stats.workflowCount }} <small>单</small></span>
          </div>
        </div>

        <!-- 数字化拓扑与全国中心节点示意图 -->
        <div class="screen-map-container">
          <div class="map-radar-circle circle-1" />
          <div class="map-radar-circle circle-2" />
          <div class="map-radar-circle circle-3" />

          <!-- 城市主要节点呼吸灯 -->
          <div class="map-node node-shanghai">
            <span class="node-dot" />
            <span class="node-label">上海 (华东中心) · 98.9%</span>
          </div>
          <div class="map-node node-beijing">
            <span class="node-dot" />
            <span class="node-label">北京 (华北中心) · 99.2%</span>
          </div>
          <div class="map-node node-shenzhen">
            <span class="node-dot" />
            <span class="node-label">深圳 (华南中心) · 99.5%</span>
          </div>
          <div class="map-node node-chengdu">
            <span class="node-dot" />
            <span class="node-label">成都 (西南节点) · 97.8%</span>
          </div>

          <div class="map-status-overlay">
            <span>全国四大核心计算中心集群双活运行中</span>
          </div>
        </div>
      </section>

      <!-- 右栏：实时动态流水与 AI 模型负载 -->
      <section class="screen-column col-right">
        <div class="screen-card">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>AI 大模型调用分布 (实时)</h3>
          </div>
          <div class="card-content">
            <div class="ai-model-dist">
              <div class="ai-dist-row">
                <span class="ai-name">DeepSeek-V3</span>
                <el-progress :percentage="52" color="#00e5ff" :stroke-width="10" />
              </div>
              <div class="ai-dist-row">
                <span class="ai-name">DeepSeek-R1 (推理链)</span>
                <el-progress :percentage="28" color="#3b82f6" :stroke-width="10" />
              </div>
              <div class="ai-dist-row">
                <span class="ai-name">GPT-4o Mini</span>
                <el-progress :percentage="14" color="#10b981" :stroke-width="10" />
              </div>
              <div class="ai-dist-row">
                <span class="ai-name">通义千问 Qwen-Max</span>
                <el-progress :percentage="6" color="#f59e0b" :stroke-width="10" />
              </div>
            </div>
          </div>
        </div>

        <div class="screen-card mt-3 flex-1">
          <div class="card-header">
            <span class="card-title-icon">✦</span>
            <h3>系统关键业务执行流水</h3>
          </div>
          <div class="card-content scroll-logs">
            <div v-for="(log, idx) in recentLogs" :key="idx" class="screen-log-row">
              <span class="log-time">{{ log.time }}</span>
              <span class="log-tenant">{{ log.tenant }}</span>
              <span class="log-action">{{ log.action }}</span>
              <span class="log-status">{{ log.status }}</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.data-screen-shell {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  background-color: #030a16;
  color: #e2e8f0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  z-index: 9999;
}

.screen-bg-grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(0, 229, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 229, 255, 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

.screen-glow-spot {
  position: absolute;
  top: -100px;
  left: 50%;
  transform: translateX(-50%);
  width: 800px;
  height: 350px;
  background: radial-gradient(circle, rgba(0, 229, 255, 0.15) 0%, rgba(3, 10, 22, 0) 70%);
  pointer-events: none;
}

.screen-header {
  position: relative;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: linear-gradient(180deg, rgba(13, 27, 49, 0.8) 0%, rgba(3, 10, 22, 0.4) 100%);
  border-bottom: 1px solid rgba(0, 229, 255, 0.2);
  z-index: 10;
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 320px;
}

.header-right {
  justify-content: flex-end;
}

.screen-back-btn {
  background: rgba(0, 229, 255, 0.1) !important;
  border-color: rgba(0, 229, 255, 0.4) !important;
  color: #00e5ff !important;
}

.header-clock {
  font-family: monospace;
  font-size: 14px;
  color: #94a3b8;
  letter-spacing: 1px;
}

.header-center {
  text-align: center;
}

.title-sub {
  display: block;
  font-size: 11px;
  color: #00e5ff;
  letter-spacing: 3px;
  opacity: 0.8;
  margin-bottom: 2px;
}

.title-main {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(180deg, #ffffff 0%, #00e5ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #10b981;
  background: rgba(16, 185, 129, 0.1);
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.status-dot-pulse {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 8px #10b981;
}

.screen-body {
  flex: 1;
  display: grid;
  grid-template-columns: 360px 1fr 380px;
  gap: 20px;
  padding: 20px;
  z-index: 10;
  overflow: hidden;
}

.screen-column {
  display: flex;
  flex-direction: column;
}

.screen-card {
  background: rgba(13, 27, 49, 0.6);
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 12px;
  padding: 16px;
  backdrop-filter: blur(10px);
  box-shadow: inset 0 0 20px rgba(0, 229, 255, 0.05);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
  margin-bottom: 14px;
}

.card-title-icon {
  color: #00e5ff;
}

.card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.tenant-rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 13px;
}

.rank-badge {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.1);
  font-weight: bold;
  font-size: 11px;
}

.rank-1 { background: #eab308; color: #000; }
.rank-2 { background: #94a3b8; color: #000; }
.rank-3 { background: #d97706; color: #fff; }

.rank-name {
  width: 130px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #cbd5e1;
}

.rank-bar-bg {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.rank-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #0284c7 0%, #00e5ff 100%);
  border-radius: 3px;
}

.rank-val {
  font-family: monospace;
  color: #00e5ff;
  font-size: 12px;
  width: 32px;
  text-align: right;
}

.qps-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.qps-cell {
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.qps-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.qps-num {
  font-size: 18px;
  font-weight: bold;
  color: #00e5ff;
  font-family: monospace;
}

.qps-num small {
  font-size: 11px;
  color: #64748b;
  font-weight: normal;
}

.kpi-banner {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: rgba(13, 27, 49, 0.7);
  border: 1px solid rgba(0, 229, 255, 0.25);
  border-radius: 12px;
  padding: 18px 24px;
  margin-bottom: 20px;
}

.kpi-item {
  text-align: center;
}

.kpi-label {
  display: block;
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 26px;
  font-weight: 800;
  font-family: monospace;
}

.kpi-value small {
  font-size: 14px;
  font-weight: normal;
}

.text-gold { color: #facc15; text-shadow: 0 0 12px rgba(250, 204, 21, 0.4); }
.text-cyan { color: #00e5ff; text-shadow: 0 0 12px rgba(0, 229, 255, 0.4); }
.text-blue { color: #60a5fa; }
.text-emerald { color: #34d399; }

.kpi-divider {
  width: 1px;
  height: 36px;
  background: rgba(255, 255, 255, 0.1);
}

.screen-map-container {
  flex: 1;
  position: relative;
  background: radial-gradient(circle at center, rgba(14, 165, 233, 0.1) 0%, rgba(3, 10, 22, 0) 70%);
  border: 1px solid rgba(0, 229, 255, 0.15);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.map-radar-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px dashed rgba(0, 229, 255, 0.2);
  pointer-events: none;
}

.circle-1 { width: 220px; height: 220px; }
.circle-2 { width: 420px; height: 420px; }
.circle-3 { width: 620px; height: 620px; }

.map-node {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(3, 10, 22, 0.7);
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid rgba(0, 229, 255, 0.4);
  font-size: 12px;
  color: #f8fafc;
}

.node-dot {
  width: 8px;
  height: 8px;
  background: #00e5ff;
  border-radius: 50%;
  box-shadow: 0 0 10px #00e5ff;
}

.node-shanghai { top: 48%; left: 58%; }
.node-beijing { top: 32%; left: 52%; }
.node-shenzhen { top: 62%; left: 54%; }
.node-chengdu { top: 54%; left: 38%; }

.map-status-overlay {
  position: absolute;
  bottom: 16px;
  font-size: 12px;
  color: #64748b;
  letter-spacing: 1px;
}

.ai-dist-row {
  margin-bottom: 14px;
}

.ai-name {
  display: block;
  font-size: 12px;
  color: #cbd5e1;
  margin-bottom: 6px;
}

.scroll-logs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.screen-log-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 6px;
  border-left: 3px solid #00e5ff;
}

.log-time {
  color: #64748b;
  font-family: monospace;
}

.log-tenant {
  color: #93c5fd;
  font-weight: 500;
}

.log-action {
  flex: 1;
  color: #e2e8f0;
}

.log-status {
  color: #10b981;
  font-size: 10px;
  background: rgba(16, 185, 129, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}

.mt-3 {
  margin-top: 16px;
}

.flex-1 {
  flex: 1;
}
</style>
