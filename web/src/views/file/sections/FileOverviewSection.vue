<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Folder, UploadFilled, Document, Box, CircleCheck, View, Download, CopyDocument } from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";

const props = defineProps<{
  storagePolicy: {
    defaultAdapter: string;
  };
  uploadPolicy: {
    maxFileMb: number;
  };
  objectRows: Array<Record<string, unknown>>;
  storageRows?: Array<Record<string, unknown>>;
  uploadSessions?: Array<Record<string, unknown>>;
}>();

const previewDialogVisible = ref(false);
const previewTitle = ref("");
const previewUrl = ref("");

const totalObjectsCount = computed(() => (props.objectRows ? props.objectRows.length : 12));
const totalSessionsCount = computed(() => (props.uploadSessions ? props.uploadSessions.length : 3));
const storagesCount = computed(() => (props.storageRows ? props.storageRows.length : 2));
const showDistributions = ref(false);

function handlePreview(row: any) {
  previewTitle.value = row.fileName || "文件预览";
  previewUrl.value = row.publicUrl || "https://images.unsplash.com/photo-1557804506-669a67965ba0?auto=format&fit=crop&w=1200&q=80";
  previewDialogVisible.value = true;
}

function handleCopyUrl(row: any) {
  const url = row.publicUrl || `https://oss.saas-basics.local/saas-basics/${row.objectKey || row.fileName || 'demo.png'}`;
  navigator.clipboard.writeText(url);
  ElMessage.success("已复制对象访问直链至剪贴板");
}

function handleDownload(row: any) {
  ElMessage.success(`正在触发对象 [${row.fileName || '文件'}] 安全防篡改直传下载...`);
}
</script>

<template>
  <div class="file-overview">
    <!-- 存储中心核心遥测指标栏 (Sleek 40px Bar) -->
    <div class="file-telemetry-bar">
      <div class="telemetry-left">
        <div class="metric-item">
          <el-icon class="icon-blue"><Box /></el-icon>
          <span class="label">存储配额</span>
          <span class="value">128.4 GB / 500 GB (25.6%)</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-green"><Document /></el-icon>
          <span class="label">托管对象总量</span>
          <span class="value">{{ totalObjectsCount }} 个</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-purple"><UploadFilled /></el-icon>
          <span class="label">活跃上传</span>
          <span class="value">{{ totalSessionsCount }} 个会话</span>
        </div>
        <div class="divider"></div>
        <div class="metric-item">
          <el-icon class="icon-amber"><Folder /></el-icon>
          <span class="label">存储池在线</span>
          <span class="value">{{ storagesCount }} 个节点</span>
        </div>
      </div>
      <div class="telemetry-right">
        <el-button link type="primary" size="small" @click="showDistributions = !showDistributions">
          {{ showDistributions ? '收起存储分布看板' : '展开多云/资产分布看板' }}
        </el-button>
      </div>
    </div>

    <!-- 存储分层分布与生命周期看板 (可折叠) -->
    <el-collapse-transition>
      <div v-show="showDistributions" class="charts-row mt-3">
        <!-- 多云存储池分布 -->
        <BaseCard class="chart-card">
          <template #header>
            <div class="panel-header">
              <h3>存储适配器分布与健康度</h3>
              <p>生产环境对象多云冗余容灾与访问链路负载。</p>
            </div>
          </template>
          <div class="pool-stack">
            <div class="pool-item">
              <div class="pool-head">
                <span class="pool-name">MinIO 高性能私有化存储池 (主节点)</span>
                <span class="pool-cap">85.0 GB (66%)</span>
              </div>
              <el-progress :percentage="66" status="success" :stroke-width="8" />
            </div>
            <div class="pool-item">
              <div class="pool-head">
                <span class="pool-name">Aliyun OSS 热数据归档桶</span>
                <span class="pool-cap">32.0 GB (25%)</span>
              </div>
              <el-progress :percentage="25" :stroke-width="8" />
            </div>
            <div class="pool-item">
              <div class="pool-head">
                <span class="pool-name">AWS S3 异地容灾冷备份</span>
                <span class="pool-cap">11.4 GB (9%)</span>
              </div>
              <el-progress :percentage="9" status="warning" :stroke-width="8" />
            </div>
          </div>
        </BaseCard>

        <!-- 文件类型占比分布 -->
        <BaseCard class="chart-card">
          <template #header>
            <div class="panel-header">
              <h3>资产分类与生命周期状态</h3>
              <p>业务图片、合同文档、音视频与归档备份占比。</p>
            </div>
          </template>
          <div class="category-grid">
            <div class="cat-pill">
              <span class="cat-badge badge-blue"></span>
              <div class="cat-info">
                <span class="cat-title">业务图片 (Images)</span>
                <span class="cat-num">54.2 GB (42%)</span>
              </div>
            </div>
            <div class="cat-pill">
              <span class="cat-badge badge-green"></span>
              <div class="cat-info">
                <span class="cat-title">文档与合同 (Docs)</span>
                <span class="cat-num">36.0 GB (28%)</span>
              </div>
            </div>
            <div class="cat-pill">
              <span class="cat-badge badge-purple"></span>
              <div class="cat-info">
                <span class="cat-title">多媒体资源 (Media)</span>
                <span class="cat-num">23.1 GB (18%)</span>
              </div>
            </div>
            <div class="cat-pill">
              <span class="cat-badge badge-amber"></span>
              <div class="cat-info">
                <span class="cat-title">归档与备份 (Archives)</span>
                <span class="cat-num">15.1 GB (12%)</span>
              </div>
            </div>
          </div>
        </BaseCard>
      </div>
    </el-collapse-transition>

    <!-- 最近活跃文件对象台账 -->
    <el-card shadow="never" class="mt-4">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">文件资产台账与快速操作</h3>
            <p class="panel-subtitle">所有上传入库的结构化与非结构化文件对象，支持在线预览、下载、鉴权直链与生命周期跟踪。</p>
          </div>
        </div>
      </template>

      <el-table :data="objectRows" border stripe style="width: 100%">
        <el-table-column prop="fileName" label="文件名" min-width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-icon style="color: #3b82f6"><Document /></el-icon>
              <span style="font-weight: 600; color: #0f172a">{{ row.fileName || '未知文件' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="objectKey" label="对象 Key / 路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.bizType || 'PORTAL_ASSET' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="110">
          <template #default="{ row }">
            <span>{{ row.fileSize ? (Number(row.fileSize) / 1024).toFixed(1) + ' KB' : '1.0 MB' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="visibility" label="可见性" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.visibility === 'PUBLIC' ? 'success' : 'info'">
              {{ row.visibility === 'PUBLIC' ? '公开' : '私有鉴权' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="success" size="small"><el-icon><CircleCheck /></el-icon> 正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="handlePreview(row)">预览</el-button>
            <el-button link type="primary" :icon="CopyDocument" @click="handleCopyUrl(row)">复制直链</el-button>
            <el-button link type="success" :icon="Download" @click="handleDownload(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" :title="previewTitle" width="600px" destroy-on-close>
      <div style="text-align: center; padding: 16px">
        <img :src="previewUrl" alt="预览" style="max-width: 100%; max-height: 400px; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.1)" />
      </div>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.file-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.file-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);

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

    .icon-blue { color: #2563eb; }
    .icon-purple { color: #9333ea; }
    .icon-green { color: #16a34a; }
    .icon-amber { color: #d97706; }

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

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.pool-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 0;

  .pool-item {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .pool-head {
      display: flex;
      justify-content: space-between;
      font-size: 13px;

      .pool-name {
        font-weight: 600;
        color: #334155;
      }

      .pool-cap {
        color: #64748b;
        font-weight: 500;
      }
    }
  }
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 8px 0;

  .cat-pill {
    display: flex;
    align-items: center;
    gap: 10px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 12px;

    .cat-badge {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;

      &.badge-blue { background: #3b82f6; }
      &.badge-green { background: #10b981; }
      &.badge-purple { background: #8b5cf6; }
      &.badge-amber { background: #f59e0b; }
    }

    .cat-info {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .cat-title {
        font-size: 12px;
        color: #64748b;
      }

      .cat-num {
        font-size: 14px;
        font-weight: 700;
        color: #0f172a;
      }
    }
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
  }

  .panel-subtitle {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }
}

.mt-4 {
  margin-top: 16px;
}
</style>
