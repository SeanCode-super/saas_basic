<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  MagicStick,
  Cpu,
  Promotion,
  Plus,
  Delete,
  Check,
  RefreshRight,
  View,
  Right,
  Connection,
  List,
  Setting
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import {
  designAiApp,
  publishAiApp,
  listAiModels,
  deleteAiModel
} from "@/api/modules/codegen/ai-builder";
import type {
  AiAppDesignResult,
  DynamicFieldItem,
  DynamicModelItem
} from "@/api/modules/codegen/ai-builder";

const router = useRouter();

const activeTab = ref<"builder" | "published">("builder");

// Prompt & Model
const promptText = ref("");
const selectedAiModel = ref("DeepSeek-V3");
const designing = ref(false);
const publishing = ref(false);
const seedMockData = ref(true);

// AI Design Result
const designResult = ref<AiAppDesignResult | null>(null);
const publishedApps = ref<DynamicModelItem[]>([]);
const loadingPublished = ref(false);

// Published Success Modal
const publishSuccessVisible = ref(false);
const publishedModel = ref<DynamicModelItem | null>(null);

// Preset scenarios for non-technical users
const quickScenarios = [
  {
    title: "会议室预订系统",
    desc: "会议室名称、预订人、使用部门、开始时间、结束时间、参会人数、预订状态、会议主题、配套设备",
    prompt: "需要一个企业会议室预约管理功能，包含会议室名称、预订人姓名、使用部门、预订开始时间、预订结束时间、参会人数、预订状态（待审批、已通过、已取消、使用中）、会议主题、配套设备需求（投影仪、白板、视频会议终端）。"
  },
  {
    title: "员工差旅报销申请",
    desc: "出差人、出差事由、目的地城市、出发日期、返回日期、报销总金额、发票张数、审批状态、备注说明",
    prompt: "做一个员工差旅与日常费用报销申请应用，包含申请人、报销类型（差旅费、业务招待、办公用品、交通打车）、出差城市、出发日期、返回日期、报销总金额、关联发票张数、审批状态（草稿、审批中、已通过、已打款、已驳回）、报销明细备注。"
  },
  {
    title: "企业IT与设备报修",
    desc: "报修设备、资产编号、报修人、联系电话、所在工位、故障紧急度、故障描述、处理状态",
    prompt: "帮我生成一个企业内部IT设备报修与工单系统，包含报修设备名称、固定资产编码、报修人员、联系电话、办公室/工位号、故障紧急度（普通、紧急、特急）、故障详细描述、处理状态（待派工、处理中、已修复、无法修复已报废）、处理人。"
  },
  {
    title: "客户拜访与商机跟进",
    desc: "客户名称、拜访联系人、拜访形式、拜访日期、沟通纪要、意向级别、商机金额、下一步计划",
    prompt: "设计一个销售拜访与客户跟进台账，包含客户全称、拜访联系人、拜访形式（现场拜访、腾讯会议、电话沟通）、拜访日期、沟通核心纪要、意向级别（初步接触、方案交流、商务谈判、签约赢单、输单流失）、预估合同金额、跟进负责人、下步计划。"
  },
  {
    title: "办公耗材领用登记",
    desc: "耗材品类、规格型号、申领数量、领用部门、领用人、发放状态、领用日期",
    prompt: "构建一个行政办公耗材领用管理应用，包括耗材名称、耗材分类（纸张文具、打印耗材、保洁劳保、茶水零食）、申领数量、单位、领用部门、申领人姓名、领用日期、发放状态（待发放、已发放、库存不足）。"
  },
  {
    title: "固定资产盘点清查",
    desc: "资产编码、资产名称、购置日期、原值金额、存放位置、使用责任人、盘点状态",
    prompt: "生成一个公司固定资产盘点台账，包含资产唯一编码、资产名称、类别（电子设备、办公家具、运输工具、网络器材）、购置日期、原值金额、存放地点、当前保管人、盘点状态（正常在用、闲置、故障待修、盘亏缺失）。"
  }
];

function applyScenario(scenario: typeof quickScenarios[0]) {
  promptText.value = scenario.prompt;
  ElMessage.info(`已载入「${scenario.title}」业务需求模板`);
}

// 1. AI Design Blueprint
async function handleGenerateBlueprint() {
  if (!promptText.value.trim()) {
    ElMessage.warning("请在需求输入框中描述您的业务需求，或点击上方快捷预设");
    return;
  }
  designing.value = true;
  try {
    const res = await designAiApp(promptText.value.trim(), selectedAiModel.value);
    if (res.data) {
      designResult.value = res.data;
      ElMessage.success("🎉 AI 架构设计完成！请核对下方字段与预览");
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "AI 蓝图生成失败");
  } finally {
    designing.value = false;
  }
}

// Add custom field
function handleAddField() {
  if (!designResult.value) return;
  const sort = designResult.value.fields.length + 1;
  designResult.value.fields.push({
    fieldCode: `custom_field_${sort}`,
    fieldName: `自定义字段${sort}`,
    columnType: "VARCHAR(255)",
    javaType: "String",
    componentType: "input",
    isRequired: false,
    isQuery: true,
    sortNo: sort * 10
  });
}

// Remove field
function handleRemoveField(idx: number) {
  if (!designResult.value) return;
  designResult.value.fields.splice(idx, 1);
}

// 2. Publish to MySQL and Mount Menu
async function handlePublishApp() {
  if (!designResult.value) return;
  publishing.value = true;
  try {
    const res = await publishAiApp({
      modelCode: designResult.value.modelCode,
      modelName: designResult.value.modelName,
      tableName: designResult.value.tableName,
      category: designResult.value.category,
      icon: designResult.value.icon,
      description: designResult.value.description,
      fields: designResult.value.fields,
      seedSampleData: seedMockData.value,
      sampleData: designResult.value.sampleData
    });

    if (res.data) {
      publishedModel.value = res.data;
      publishSuccessVisible.value = true;
      await loadPublishedApps();
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "发布应用失败");
  } finally {
    publishing.value = false;
  }
}

// Load published dynamic models
async function loadPublishedApps() {
  loadingPublished.value = true;
  try {
    const res = await listAiModels();
    if (res.data) {
      publishedApps.value = res.data;
    }
  } catch (err: unknown) {
    ElMessage.error(err instanceof Error ? err.message : "获取已发布应用失败");
  } finally {
    loadingPublished.value = false;
  }
}

// Navigate to the dynamic app
function goToApp(modelCode: string) {
  router.push(`/dynamic/${modelCode}`);
}

// Delete model
async function handleDeleteModel(app: DynamicModelItem) {
  try {
    await ElMessageBox.confirm(
      `确定下线并删除「${app.modelName} (${app.modelCode})」业务应用及关联菜单吗？`,
      "下线应用确认",
      {
        type: "warning",
        confirmButtonText: "确定下线",
        cancelButtonText: "取消"
      }
    );
    await deleteAiModel(app.id);
    ElMessage.success("应用已成功下线");
    await loadPublishedApps();
  } catch (err: unknown) {
    if (err !== "cancel") {
      ElMessage.error(err instanceof Error ? err.message : "下线应用失败");
    }
  }
}

onMounted(() => {
  loadPublishedApps();
});
</script>

<template>
  <div class="ai-builder-section">
    <!-- 顶栏选项卡 -->
    <div class="section-tabs-bar">
      <el-radio-group v-model="activeTab" size="large">
        <el-radio-button value="builder">
          <el-icon><MagicStick /></el-icon>
          AI 智能自然语言构建
        </el-radio-button>
        <el-radio-button value="published">
          <el-icon><List /></el-icon>
          已发布业务应用 ({{ publishedApps.length }})
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- TAB 1: AI 智能构建 -->
    <div v-show="activeTab === 'builder'" class="builder-view">
      <!-- 需求输入看板 -->
      <BaseCard class="ai-prompt-card">
        <div class="ai-header-zone">
          <div class="ai-title-wrap">
            <span class="ai-chip">✨ AI Agent 智能驱动</span>
            <h2 class="ai-main-title">自然语言需求一键生成企业应用</h2>
            <p class="ai-subtitle">
              不懂编程也能开发！用日常中文描述您的业务台账、审批申请或管理需求，AI
              自动完成物理表建表、多租户隔离、前端组件渲染与路由菜单挂载。
            </p>
          </div>

          <div class="ai-model-selector">
            <span class="selector-label">底座推理引擎：</span>
            <el-select v-model="selectedAiModel" style="width: 180px">
              <el-option label="DeepSeek-V3 (推荐)" value="DeepSeek-V3" />
              <el-option label="DeepSeek-R1 (深度思维)" value="DeepSeek-R1" />
              <el-option label="GPT-4o-mini (超快)" value="GPT-4o-mini" />
              <el-option label="Qwen-Max (通义千问)" value="Qwen-Max" />
            </el-select>
          </div>
        </div>

        <!-- 场景快捷预设 -->
        <div class="quick-scenarios-container">
          <span class="quick-title">⚡ 常用业务场景一键载入：</span>
          <div class="scenario-chips">
            <el-button
              v-for="sc in quickScenarios"
              :key="sc.title"
              size="small"
              class="scenario-btn"
              @click="applyScenario(sc)"
            >
              {{ sc.title }}
            </el-button>
          </div>
        </div>

        <!-- 自然语言文本框 -->
        <div class="prompt-input-wrapper">
          <el-input
            v-model="promptText"
            type="textarea"
            :rows="4"
            placeholder="请在此输入您的应用需求。例如：我们需要一个办公耗材申领系统，包含品类、申领数量、部门、申领人、领用日期、发放状态..."
            class="main-prompt-input"
          />
          <div class="prompt-action-bar">
            <span class="tip-text">支持包含字段名、下拉选项枚举、是否必填等详细业务描述</span>
            <el-button
              type="primary"
              size="large"
              :icon="MagicStick"
              :loading="designing"
              class="generate-btn"
              @click="handleGenerateBlueprint"
            >
              {{ designing ? "AI 正在深度解析业务实体..." : "开始 AI 智能推理设计" }}
            </el-button>
          </div>
        </div>
      </BaseCard>

      <!-- AI 设计蓝图展示区 -->
      <div v-if="designResult" class="design-result-zone">
        <!-- 基础元数据摘要卡片 -->
        <BaseCard class="model-meta-card">
          <template #header>
            <div class="flex-between">
              <div class="meta-card-title">
                <el-icon class="text-primary"><Cpu /></el-icon>
                <span>AI 推断应用模型蓝图</span>
              </div>
              <el-tag type="success" effect="dark">模型设计就绪</el-tag>
            </div>
          </template>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="应用名称">
              <el-input v-model="designResult.modelName" style="width: 200px" />
            </el-descriptions-item>
            <el-descriptions-item label="模型代码">
              <el-tag effect="plain" type="info">{{ designResult.modelCode }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="物理数据库表">
              <code>{{ designResult.tableName }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="业务分类">
              <el-input v-model="designResult.category" style="width: 150px" />
            </el-descriptions-item>
            <el-descriptions-item label="应用图标">
              <el-input v-model="designResult.icon" style="width: 150px" />
            </el-descriptions-item>
            <el-descriptions-item label="功能描述">
              <el-input v-model="designResult.description" style="width: 100%" />
            </el-descriptions-item>
          </el-descriptions>
        </BaseCard>

        <!-- 字段可视化编辑网格 -->
        <BaseCard class="fields-card">
          <template #header>
            <div class="flex-between">
              <div>
                <strong>业务字段与输入组件映射</strong>
                <span class="sub-tip">（AI 已自动配置字段物理存储类型、UI 输入控件与下拉字典选项）</span>
              </div>
              <el-button size="small" type="primary" :icon="Plus" @click="handleAddField">
                添加自定义字段
              </el-button>
            </div>
          </template>

          <el-table :data="designResult.fields" border stripe style="width: 100%">
            <el-table-column label="#" type="index" width="48" align="center" />
            <el-table-column label="字段中文名称" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.fieldName" placeholder="如：预订人" />
              </template>
            </el-table-column>
            <el-table-column label="字段代码 (列名)" min-width="160">
              <template #default="{ row }">
                <code>{{ row.fieldCode }}</code>
              </template>
            </el-table-column>
            <el-table-column label="UI 组件类型" width="160">
              <template #default="{ row }">
                <el-select v-model="row.componentType" style="width: 100%">
                  <el-option label="单行文本 (input)" value="input" />
                  <el-option label="多行文本 (textarea)" value="textarea" />
                  <el-option label="数值金额 (number)" value="number" />
                  <el-option label="下拉选项 (select)" value="select" />
                  <el-option label="日期选择 (date)" value="date" />
                  <el-option label="日期时间 (datetime)" value="datetime" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="必填约束" width="100" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isRequired" />
              </template>
            </el-table-column>
            <el-table-column label="支持查询筛选" width="120" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isQuery" />
              </template>
            </el-table-column>
            <el-table-column label="字典选项 (JSON 或逗号隔开)" min-width="220">
              <template #default="{ row }">
                <el-input
                  v-if="row.componentType === 'select'"
                  v-model="row.optionsJson"
                  placeholder='如: ["审批中", "已通过", "已驳回"]'
                />
                <span v-else class="text-muted">无需字典</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button
                  link
                  type="danger"
                  :icon="Delete"
                  @click="handleRemoveField($index)"
                />
              </template>
            </el-table-column>
          </el-table>
        </BaseCard>

        <!-- 界面实时模拟预览 -->
        <BaseCard class="preview-card">
          <template #header>
            <div class="flex-between">
              <div>
                <strong>✨ 实时界面交互预览 (Live Preview)</strong>
                <span class="sub-tip">（上线后的真实界面效果）</span>
              </div>
              <el-tag type="info" size="small">样例渲染</el-tag>
            </div>
          </template>

          <div class="live-preview-box">
            <!-- 模拟工具栏 -->
            <div class="mock-toolbar">
              <div class="mock-title">
                <strong>{{ designResult.modelName }} 台账</strong>
              </div>
              <div class="mock-actions">
                <el-button size="small" type="primary" :icon="Plus">新增记录</el-button>
              </div>
            </div>

            <!-- 模拟数据表格 -->
            <el-table :data="designResult.sampleData" border style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" align="center" />
              <el-table-column
                v-for="f in designResult.fields"
                :key="f.fieldCode"
                :prop="f.fieldCode"
                :label="f.fieldName"
                min-width="120"
              >
                <template #default="{ row }">
                  <el-tag v-if="f.componentType === 'select'" size="small">
                    {{ row[f.fieldCode] }}
                  </el-tag>
                  <span v-else>{{ row[f.fieldCode] }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="center">
                <template #default>
                  <el-button link type="primary" size="small">编辑</el-button>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </BaseCard>

        <!-- 一键发布上线控制栏 -->
        <div class="publish-action-panel">
          <div class="panel-left">
            <el-checkbox v-model="seedMockData" label="自动写入 3 条演示业务台账 (方便立即体验)" />
          </div>
          <div class="panel-right">
            <el-button
              type="success"
              size="large"
              :icon="Promotion"
              :loading="publishing"
              class="publish-btn"
              @click="handlePublishApp"
            >
              {{ publishing ? "正在执行 DDL 建表并挂载菜单..." : "🚀 确认无误，一键构建并发布上线" }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 2: 已发布应用列表 -->
    <div v-show="activeTab === 'published'" class="published-view">
      <BaseCard class="published-card">
        <template #header>
          <div class="flex-between">
            <div>
              <strong>已发布上线业务应用</strong>
              <span class="sub-tip">（所有由 AI 工坊构建的多租户真实业务应用，已挂载至侧边栏导航）</span>
            </div>
            <el-button :icon="RefreshRight" :loading="loadingPublished" @click="loadPublishedApps">
              刷新
            </el-button>
          </div>
        </template>

        <el-table
          v-loading="loadingPublished"
          :data="publishedApps"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="modelName" label="应用名称" min-width="160">
            <template #default="{ row }">
              <strong>{{ row.modelName }}</strong>
              <div class="text-muted text-xs">{{ row.description || "无业务描述" }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="modelCode" label="模型代码" width="160">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.modelCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tableName" label="物理数据库表" min-width="180">
            <template #default="{ row }">
              <code>{{ row.tableName }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="所属分类" width="120" align="center" />
          <el-table-column label="字段数量" width="100" align="center">
            <template #default="{ row }">
              <span class="field-count-pill">{{ row.fields?.length || 0 }} 项</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="构建时间" width="180" align="center" />
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" :icon="Right" @click="goToApp(row.modelCode)">
                进入应用
              </el-button>
              <el-button type="danger" size="small" :icon="Delete" @click="handleDeleteModel(row)">
                下线
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </BaseCard>
    </div>

    <!-- 发布成功弹窗 -->
    <el-dialog
      v-model="publishSuccessVisible"
      title="🎉 应用构建与发布成功！"
      width="560px"
      align-center
    >
      <div class="success-dialog-body">
        <div class="success-icon-wrap">
          <el-icon class="success-icon"><Check /></el-icon>
        </div>
        <h3 class="success-title">「{{ publishedModel?.modelName }}」已正式上线</h3>
        <p class="success-desc">
          AI 已在数据库成功创建物理表
          <code>{{ publishedModel?.tableName }}</code>
          并挂载多租户隔离约束，同时已将入口自动注入到您的左侧导航菜单中！
        </p>

        <div class="success-routes-box">
          <div class="route-item">
            <span class="label">访问路由：</span>
            <code>/dynamic/{{ publishedModel?.modelCode }}</code>
          </div>
          <div class="route-item">
            <span class="label">权限码：</span>
            <code>dyn:{{ publishedModel?.modelCode }}:query</code>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="publishSuccessVisible = false">留在本工坊</el-button>
          <el-button
            type="primary"
            size="large"
            :icon="Right"
            @click="
              publishSuccessVisible = false;
              goToApp(publishedModel?.modelCode || '');
            "
          >
            👉 立即前往新应用工作台
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-builder-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-tabs-bar {
  display: flex;
  justify-content: flex-start;
}

.ai-prompt-card {
  background: linear-gradient(135deg, #fff 0%, #f6f9fc 100%);
  border: 1px solid #e2e8f0;

  .ai-header-zone {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 20px;
    margin-bottom: 18px;

    .ai-chip {
      display: inline-block;
      padding: 4px 10px;
      font-size: 12px;
      font-weight: 600;
      border-radius: 20px;
      background: rgb(99 102 241 / 0.1);
      color: #6366f1;
      margin-bottom: 8px;
    }

    .ai-main-title {
      font-size: 20px;
      font-weight: 700;
      color: #1e293b;
      margin: 0 0 8px;
    }

    .ai-subtitle {
      font-size: 13px;
      color: #64748b;
      margin: 0;
      line-height: 1.6;
      max-width: 780px;
    }

    .ai-model-selector {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #fff;
      padding: 8px 14px;
      border-radius: 8px;
      border: 1px solid #e2e8f0;

      .selector-label {
        font-size: 13px;
        color: #475569;
        font-weight: 500;
        white-space: nowrap;
      }
    }
  }

  .quick-scenarios-container {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
    padding: 10px 14px;
    background: #f1f5f9;
    border-radius: 8px;

    .quick-title {
      font-size: 13px;
      font-weight: 600;
      color: #334155;
    }

    .scenario-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;

      .scenario-btn {
        border-color: #cbd5e1;

        &:hover {
          color: var(--el-color-primary);
          border-color: var(--el-color-primary);
        }
      }
    }
  }

  .prompt-input-wrapper {
    .main-prompt-input {
      :deep(.el-textarea__inner) {
        font-size: 14px;
        line-height: 1.6;
        padding: 12px 14px;
        border-radius: 8px;
        box-shadow: 0 1px 3px rgb(0 0 0 / 0.05);
      }
    }

    .prompt-action-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 14px;

      .tip-text {
        font-size: 12px;
        color: #94a3b8;
      }

      .generate-btn {
        padding: 12px 28px;
        font-weight: 600;
      }
    }
  }
}

.design-result-zone {
  display: flex;
  flex-direction: column;
  gap: 20px;

  .meta-card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
  }

  .sub-tip {
    font-size: 12px;
    color: #94a3b8;
    font-weight: normal;
    margin-left: 6px;
  }

  .live-preview-box {
    background: #fafbfc;
    border: 1px dashed #cbd5e1;
    border-radius: 8px;
    padding: 16px;

    .mock-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
  }

  .publish-action-panel {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 18px 24px;
    background: #fff;
    border-radius: 12px;
    border: 1px solid #e2e8f0;
    box-shadow: 0 4px 12px rgb(0 0 0 / 0.04);

    .publish-btn {
      padding: 14px 32px;
      font-size: 15px;
      font-weight: 700;
    }
  }
}

.field-count-pill {
  display: inline-block;
  padding: 2px 8px;
  background: #f1f5f9;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.success-dialog-body {
  text-align: center;
  padding: 10px 16px;

  .success-icon-wrap {
    width: 60px;
    height: 60px;
    border-radius: 50%;
    background: #dcfce7;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 16px;

    .success-icon {
      font-size: 32px;
      color: #16a34a;
    }
  }

  .success-title {
    font-size: 20px;
    font-weight: 700;
    color: #1e293b;
    margin: 0 0 10px;
  }

  .success-desc {
    font-size: 13px;
    color: #64748b;
    line-height: 1.6;
    margin: 0 0 20px;
  }

  .success-routes-box {
    background: #f8fafc;
    border-radius: 8px;
    padding: 12px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    text-align: left;
    font-size: 13px;

    .route-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .label {
        color: #64748b;
        width: 80px;
      }
    }
  }
}

.text-xs {
  font-size: 12px;
}
</style>
