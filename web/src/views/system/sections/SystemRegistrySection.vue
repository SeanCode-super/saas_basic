<script setup lang="ts">
import { computed, ref } from "vue";
import { Setting, Grid, Monitor, Plus, Upload } from "@element-plus/icons-vue";
import type { SystemConfigRow } from "@/api/modules/system";
import type { PortalClientRow, PortalTerminalRow } from "@/api/modules/portal";
import BaseStatusTag from "@/components/base/BaseStatusTag.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import SchemaForm, { type SchemaField } from "@/components/pro/SchemaForm.vue";

const props = defineProps<{
  query: Record<string, string>;
  schema: SchemaField[];
  columns: ProTableColumn[];
  rows: SystemConfigRow[];
  portalClientColumns: ProTableColumn[];
  portalClients: PortalClientRow[];
  portalTerminalColumns: ProTableColumn[];
  portalTerminals: PortalTerminalRow[];
  loading: boolean;
  resetQuery: () => void;
  loadData: () => Promise<void> | void;
  openCreateDialog: () => void;
  openImportDialog: () => void;
  openCreatePortalClient: () => void;
  openCreatePortalTerminal: () => void;
  editPortalClient: (row: PortalClientRow) => void;
  editPortalTerminal: (row: PortalTerminalRow) => void;
  previewPortalClient: (row: PortalClientRow) => void;
  previewPortalTerminal: (row: PortalTerminalRow) => void;
}>();

const activeTab = ref("configs");

const queryModel = computed({
  get: () => props.query,
  set: (value) => Object.assign(props.query, value)
});
</script>

<template>
  <div class="system-registry-container">
    <el-tabs v-model="activeTab" type="border-card" class="registry-tabs">
      <!-- Tab 1: System Config Registry -->
      <el-tab-pane name="configs">
        <template #label>
          <span class="tab-label">
            <el-icon><Setting /></el-icon>
            <span>系统参数配置</span>
            <el-badge :value="rows.length" type="primary" class="tab-badge" />
          </span>
        </template>

        <div class="tab-content-wrap">
          <div class="query-box">
            <SchemaForm v-model="queryModel" :schema="schema">
              <template #actions>
                <el-button @click="resetQuery">重置</el-button>
                <el-button type="primary" @click="loadData">查询</el-button>
              </template>
            </SchemaForm>
          </div>

          <ProTable
            title=""
            subtitle="统一纳入全平台参数、功能开关与动态运行配置，支持安全审计与历史追溯。"
            :loading="loading"
            :columns="columns"
            :data="rows"
            selectable
            empty-title="当前暂无系统配置项"
            empty-description="点击上方「新增配置」添加参数，或通过「批量导入」快速初始化配置清单。"
          >
            <template #toolbar>
              <el-button :icon="Upload" @click="openImportDialog">批量导入</el-button>
              <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增配置</el-button>
            </template>
            <template #status="{ row }">
              <BaseStatusTag :status="row.status" />
            </template>
          </ProTable>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Portal Clients Registry -->
      <el-tab-pane name="clients">
        <template #label>
          <span class="tab-label">
            <el-icon><Grid /></el-icon>
            <span>门户应用 Client</span>
            <el-badge :value="portalClients.length" type="success" class="tab-badge" />
          </span>
        </template>

        <div class="tab-content-wrap">
          <ProTable
            title=""
            subtitle="管理前台门户、移动端及开放网关的 Client 身份凭证、个性化主题与认证策略绑定。"
            :loading="loading"
            :columns="portalClientColumns"
            :data="portalClients"
            empty-title="当前暂无门户 Client 注册信息"
            empty-description="点击上方「新增 Client」接入第一个门户应用。"
          >
            <template #toolbar>
              <el-button type="primary" :icon="Plus" @click="openCreatePortalClient">新增 Client</el-button>
            </template>
            <template #status="{ row }">
              <BaseStatusTag :status="row.status" />
            </template>
            <template #defaultClient="{ row }">
              <el-tag :type="row.isDefault ? 'success' : 'info'" size="small">
                {{ row.isDefault ? "是（主门户）" : "否" }}
              </el-tag>
            </template>
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="editPortalClient(row)">编辑</el-button>
              <el-button link type="success" size="small" @click="previewPortalClient(row)">预览登录页</el-button>
            </template>
          </ProTable>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Terminal Registry -->
      <el-tab-pane name="terminals">
        <template #label>
          <span class="tab-label">
            <el-icon><Monitor /></el-icon>
            <span>接入终端 Terminal</span>
            <el-badge :value="portalTerminals.length" type="warning" class="tab-badge" />
          </span>
        </template>

        <div class="tab-content-wrap">
          <ProTable
            title=""
            subtitle="定义浏览器、移动 App、小程序及开放终端类型的接入策略、验证码模式与默认入口。"
            :loading="loading"
            :columns="portalTerminalColumns"
            :data="portalTerminals"
            empty-title="当前暂无接入终端注册"
            empty-description="点击上方「新增终端」配置终端类型与安全规则。"
          >
            <template #toolbar>
              <el-button type="primary" :icon="Plus" @click="openCreatePortalTerminal">新增终端</el-button>
            </template>
            <template #status="{ row }">
              <BaseStatusTag :status="row.status" />
            </template>
            <template #actions="{ row }">
              <el-button link type="primary" size="small" @click="editPortalTerminal(row)">编辑</el-button>
              <el-button link type="success" size="small" @click="previewPortalTerminal(row)">预览终端入口</el-button>
            </template>
          </ProTable>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.system-registry-container {
  width: 100%;
}

.registry-tabs {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--sb-border-color, #e2e8f0);
  background: #ffffff;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.tab-badge {
  margin-left: 4px;
}

.tab-content-wrap {
  padding: 12px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-box {
  padding: 16px 20px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}
</style>
