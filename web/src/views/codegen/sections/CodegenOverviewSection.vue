<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus, RefreshRight, Document, Cpu, Check, Box, ArrowRight } from "@element-plus/icons-vue";
import { fetchCodegenProjects, createCodegenProject, updateCodegenProject } from "@/api/modules/codegen";
import type { CodegenProjectRow, CodegenProjectSavePayload } from "@/api/modules/codegen";
import { useAuthStore } from "@/stores/modules/auth";

defineProps<{
  templatePolicy: any;
  styleGuide: any;
  qualityGate: any;
  outputMatrix: string[];
  savePolicy: (message: string) => void;
}>();

const router = useRouter();
const authStore = useAuthStore();
const currentTenantId = () => authStore.currentUser?.tenantId ?? 1;

const loading = ref(false);
const projects = ref<CodegenProjectRow[]>([]);
const dialogVisible = ref(false);
const submitting = ref(false);
const editingId = ref<number | null>(null);

const form = reactive<CodegenProjectSavePayload>({
  tenantId: 1,
  code: "biz_supply_chain",
  name: "供应链中台业务域",
  basePackage: "com.saasbasics.platform.modules.supplychain",
  modulePrefix: "supply_chain",
  outputMode: "ZIP",
  status: "ENABLED",
  remark: "企业级多租户供应链基础业务中心"
});

async function loadProjects() {
  loading.value = true;
  try {
    const res: any = await fetchCodegenProjects();
    const list = Array.isArray(res?.data)
      ? res.data
      : (Array.isArray(res) ? res : (Array.isArray(res?.data?.data) ? res.data.data : []));
    projects.value = list;
    if (projects.value.length === 0) {
      // Seed initial display if empty
      projects.value = [
        {
          id: 1,
          tenantId: 1,
          code: "biz_mall_core",
          name: "电商商城履约域",
          basePackage: "com.saasbasics.platform.modules.mall",
          modulePrefix: "mall",
          outputMode: "ZIP",
          status: "ENABLED",
          remark: "包含商品、订单、履约配送与支付网关模型"
        },
        {
          id: 2,
          tenantId: 1,
          code: "biz_crm_lead",
          name: "企业客户与线索域",
          basePackage: "com.saasbasics.platform.modules.crm",
          modulePrefix: "crm",
          outputMode: "ZIP",
          status: "ENABLED",
          remark: "商机跟踪、客户 360 画像与拜访签到管理"
        }
      ];
    }
  } catch (err: any) {
    ElMessage.warning("加载工程列表失败，已呈现本地推荐基线");
    projects.value = [
      {
        id: 1,
        tenantId: 1,
        code: "biz_mall_core",
        name: "电商商城履约域",
        basePackage: "com.saasbasics.platform.modules.mall",
        modulePrefix: "mall",
        outputMode: "ZIP",
        status: "ENABLED",
        remark: "包含商品、订单、履约配送与支付网关模型"
      },
      {
        id: 2,
        tenantId: 1,
        code: "biz_crm_lead",
        name: "企业客户与线索域",
        basePackage: "com.saasbasics.platform.modules.crm",
        modulePrefix: "crm",
        outputMode: "ZIP",
        status: "ENABLED",
        remark: "商机跟踪、客户 360 画像与拜访签到管理"
      }
    ];
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  editingId.value = null;
  form.tenantId = currentTenantId();
  form.code = `biz_module_${Date.now().toString().slice(-4)}`;
  form.name = "新零售中台业务域";
  form.basePackage = "com.saasbasics.platform.modules.retail";
  form.modulePrefix = "retail";
  form.outputMode = "ZIP";
  form.status = "ENABLED";
  form.remark = "企业级标准多租户脚手架工程";
  dialogVisible.value = true;
}

function openEditDialog(row: CodegenProjectRow) {
  editingId.value = row.id;
  form.tenantId = row.tenantId;
  form.code = row.code;
  form.name = row.name;
  form.basePackage = row.basePackage;
  form.modulePrefix = row.modulePrefix;
  form.outputMode = row.outputMode;
  form.status = row.status;
  form.remark = row.remark || "";
  dialogVisible.value = true;
}

async function handleSaveProject() {
  if (!form.code || !form.name) {
    ElMessage.warning("请填写完整的工程编码与名称");
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value) {
      await updateCodegenProject(editingId.value, form);
      ElMessage.success("代码生成工程已更新");
    } else {
      await createCodegenProject(form);
      ElMessage.success("代码生成工程创建成功");
    }
    dialogVisible.value = false;
    await loadProjects();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? "保存工程失败");
  } finally {
    submitting.value = false;
  }
}

function jumpToSection(section: string) {
  router.push(`/codegen/${section}`);
}

onMounted(() => {
  loadProjects();
});
</script>

<template>
  <div class="codegen-overview">
    <!-- 顶部轻量快捷入口条 (紧凑导航，节省纵向空间) -->
    <div class="quick-nav-strip">
      <div class="quick-nav-pill" @click="jumpToSection('metadata')">
        <el-icon class="pill-icon text-blue"><Document /></el-icon>
        <span class="pill-label">元数据建模</span>
        <span class="pill-badge">8 张表</span>
        <el-icon class="pill-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="quick-nav-pill" @click="jumpToSection('template')">
        <el-icon class="pill-icon text-purple"><Cpu /></el-icon>
        <span class="pill-label">模板基线</span>
        <span class="pill-badge">7 层骨架</span>
        <el-icon class="pill-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="quick-nav-pill" @click="jumpToSection('gate')">
        <el-icon class="pill-icon text-green"><Check /></el-icon>
        <span class="pill-label">交付门禁</span>
        <span class="pill-badge">5 维约束</span>
        <el-icon class="pill-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="quick-nav-pill" @click="jumpToSection('artifact')">
        <el-icon class="pill-icon text-amber"><Box /></el-icon>
        <span class="pill-label">产物矩阵</span>
        <span class="pill-badge">一键打包</span>
        <el-icon class="pill-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 工程台账区域 -->
    <el-card shadow="never" class="overview-table-card">
      <template #header>
        <div class="panel-header-row">
          <div>
            <h3 class="panel-title">代码生成工程台账</h3>
            <p class="panel-subtitle">集中管理业务域基础根包、模块前缀与产物交付策略</p>
          </div>
          <div class="panel-actions">
            <el-button :icon="RefreshRight" :loading="loading" @click="loadProjects">刷新工程</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建工程域</el-button>
          </div>
        </div>
      </template>

      <el-table :data="projects" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="code" label="工程编码" min-width="140" />
        <el-table-column prop="name" label="工程名称" min-width="160">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #0f172a">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="basePackage" label="根包路径" min-width="230" show-overflow-tooltip />
        <el-table-column prop="modulePrefix" label="模块前缀" width="110" />
        <el-table-column prop="outputMode" label="输出模式" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.outputMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '已就绪' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="jumpToSection('metadata')">元数据建模</el-button>
              <el-button link type="success" size="small" @click="jumpToSection('artifact')">执行生成</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑工程弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑代码生成工程' : '新建代码生成工程域'" width="580px" destroy-on-close>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工程标识 (Code)" required>
              <el-input v-model="form.code" placeholder="如 biz_supply_chain" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工程名称" required>
              <el-input v-model="form.name" placeholder="如 供应链中台业务域" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Java 基础根包 (Base Package)" required>
          <el-input v-model="form.basePackage" placeholder="如 com.saasbasics.platform.modules.supplychain" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="表/模块前缀 (Module Prefix)">
              <el-input v-model="form.modulePrefix" placeholder="如 supply_chain" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产物交付模式">
              <el-select v-model="form.outputMode" style="width: 100%">
                <el-option label="打包下载 ZIP 源码包" value="ZIP" />
                <el-option label="直接写入工程对应目录" value="DIRECT_WRITE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="描述该业务域的核心功能与产物交付边界..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveProject">确认并保存工程</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.codegen-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-nav-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.quick-nav-pill {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--sb-border-color, #cbd5e1);
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
  transition: all 120ms ease;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.03);

  &:hover {
    border-color: #3b82f6;
    background: #eff6ff;

    .pill-arrow {
      color: #2563eb;
      transform: translateX(2px);
    }
  }

  .pill-icon {
    font-size: 15px;
    flex-shrink: 0;

    &.text-blue { color: #2563eb; }
    &.text-purple { color: #9333ea; }
    &.text-green { color: #16a34a; }
    &.text-amber { color: #d97706; }
  }

  .pill-label {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
    white-space: nowrap;
  }

  .pill-badge {
    margin-left: auto;
    font-size: 11px;
    font-weight: 500;
    color: #64748b;
    background: #f1f5f9;
    padding: 1px 7px;
    border-radius: 9999px;
    white-space: nowrap;
  }

  .pill-arrow {
    font-size: 11px;
    color: #94a3b8;
    flex-shrink: 0;
    transition: all 120ms ease;
  }
}

.overview-table-card {
  border-radius: 8px;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  white-space: nowrap;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .panel-title {
    margin: 0 0 2px;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }

  .panel-subtitle {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }

  .panel-actions {
    display: flex;
    gap: 8px;
  }
}
</style>
