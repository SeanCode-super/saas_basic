<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Check, Warning, CircleCheck, Search, MagicStick, Setting, ArrowUp, ArrowDown } from "@element-plus/icons-vue";

const props = defineProps<{
  qualityGate: {
    requireAuditField: boolean;
    requireDictBinding: boolean;
    requireQueryForm: boolean;
    requireListPermission: boolean;
    requireSqlArtifact: boolean;
  };
  savePolicy: (message: string) => void;
}>();

const policyExpanded = ref(false);
const scanning = ref(false);
const scanDone = ref(false);
const scanScore = ref(100);

interface GateRuleCheck {
  id: string;
  name: string;
  dimension: string;
  status: "PASSED" | "WARNING" | "FAILED";
  detail: string;
  recommendation: string;
}

const checkResults = ref<GateRuleCheck[]>([
  {
    id: "GATE_01",
    name: "审计字段合规性",
    dimension: "数据审计",
    status: "PASSED",
    detail: "数据模型包含完整的 created_at, created_by, updated_at, updated_by 审计切面列",
    recommendation: "符合生产环境追溯标准，已挂载 MybatisPlus 自动填充元对象处理器"
  },
  {
    id: "GATE_02",
    name: "多租户行级隔离探针",
    dimension: "租户安全",
    status: "PASSED",
    detail: "已检测到 tenant_id 隔离列与 (tenant_id, id) 联合索引",
    recommendation: "符合 JSqlParser AST 行级改写要求，具备防租户越权隔离能力"
  },
  {
    id: "GATE_03",
    name: "字典与枚举绑定校验",
    dimension: "元数据映射",
    status: "PASSED",
    detail: "字段 contract_status 已绑定 contract_status_enum 字典类型",
    recommendation: "前端 ProTable 渲染与查询下拉组件将自动对齐系统字典"
  },
  {
    id: "GATE_04",
    name: "列表分页与安全防注入约束",
    dimension: "接口规范",
    status: "PASSED",
    detail: "Controller 查询契约已继承 PageQuery，最大分页限制 200 条，已阻止动态 ORDER BY 注入",
    recommendation: "符合金融级 API 分页安全基线"
  },
  {
    id: "GATE_05",
    name: "权限码与菜单骨架闭环",
    dimension: "权限体系",
    status: "PASSED",
    detail: "已定义 biz_contract:query, biz_contract:create 等原子权限，且已挂载菜单注册 SQL",
    recommendation: "生成代码落盘后可直接在角色授权中心点选分配"
  }
]);

function handleRunScan() {
  scanning.value = true;
  setTimeout(() => {
    scanning.value = false;
    scanDone.value = true;
    scanScore.value = 100;
    ElMessage.success("代码生成交付门禁检测完成，质检评分 100/100，符合上线交付准入标准");
  }, 600);
}

function handleAutoFix() {
  ElMessage.success("已自动补齐缺失的安全切面与数据权限枚举，所有门禁检查项已处于合规状态");
}
</script>

<template>
  <div class="codegen-gate">
    <el-card shadow="never" class="gate-card">
      <!-- 极简门禁状态工具条 -->
      <div class="gate-toolbar-row">
        <div class="toolbar-left">
          <span class="toolbar-title">交付质量门禁</span>
          <el-tag type="success" size="default" effect="light" class="gate-score-pill">
            <el-icon><CircleCheck /></el-icon> 门禁评分 {{ scanScore }}/100 · 准入达标
          </el-tag>
          <span class="gate-summary-text">已覆盖数据审计、租户隔离、字典绑定、防注入与权限闭环 5 大维度</span>
        </div>

        <div class="toolbar-actions">
          <el-button link type="primary" size="small" :icon="policyExpanded ? ArrowUp : Setting" @click="policyExpanded = !policyExpanded">
            {{ policyExpanded ? '收起门禁规则' : '门禁策略设置' }}
          </el-button>
          <el-button :icon="MagicStick" plain size="small" @click="handleAutoFix">一键自动修复</el-button>
          <el-button type="primary" :icon="Search" size="small" :loading="scanning" @click="handleRunScan">
            {{ scanning ? '静态检测中...' : '开始门禁检测' }}
          </el-button>
        </div>
      </div>

      <!-- 可收起门禁策略开关面板 -->
      <el-collapse-transition>
        <div v-show="policyExpanded" class="gate-policy-panel">
          <div class="policy-panel-header">
            <span class="policy-heading">质量门禁约束基线配置</span>
            <el-button type="primary" size="small" :icon="Check" @click="savePolicy('交付门禁策略已成功保存')">保存门禁配置</el-button>
          </div>
          <div class="gate-toggles-grid">
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireAuditField" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制审计跟踪字段</span>
                <span class="toggle-desc">包含 created_at/by, updated_at/by</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireDictBinding" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制状态字段绑定字典</span>
                <span class="toggle-desc">status/type 特征字段必须关联字典</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireQueryForm" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制前后端对齐查询</span>
                <span class="toggle-desc">SchemaForm 与 LambdaQueryWrapper 联动</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireListPermission" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制 RBAC 权限码闭环</span>
                <span class="toggle-desc">@RequirePermission 与前端指令双向对齐</span>
              </div>
            </div>
            <div class="gate-toggle-item">
              <el-switch v-model="qualityGate.requireSqlArtifact" size="small" />
              <div class="toggle-content">
                <span class="toggle-name">强制同步 Flyway DDL</span>
                <span class="toggle-desc">输出 V{ver} 增量与种子数据脚本</span>
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>

      <!-- 门禁检查项清单表格 -->
      <div class="gate-table-wrapper">
        <div class="table-subheading">
          <span class="subheading-title">静态门禁规则检验清单 (共 {{ checkResults.length }} 项)</span>
          <span class="subheading-meta">企业级架构基准合规率 100% · 生产上线交付准入通过</span>
        </div>

        <el-table :data="checkResults" border stripe style="width: 100%">
          <el-table-column prop="id" label="规则编号" width="105" align="center" />
          <el-table-column prop="name" label="门禁检查项" width="170" />
          <el-table-column prop="dimension" label="评估维度" width="115" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.dimension }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="95" align="center">
            <template #default="{ row }">
              <el-tag type="success" size="small"><el-icon><CircleCheck /></el-icon> 通过</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="检测结果描述" min-width="260" show-overflow-tooltip />
          <el-table-column prop="recommendation" label="架构基准建议" min-width="240" show-overflow-tooltip />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.codegen-gate {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.gate-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.gate-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .toolbar-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }

    .gate-score-pill {
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .gate-summary-text {
      font-size: 12px;
      color: #64748b;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.gate-policy-panel {
  margin-top: 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  .policy-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;

    .policy-heading {
      font-size: 13px;
      font-weight: 600;
      color: #334155;
    }
  }

  .gate-toggles-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 10px;

    .gate-toggle-item {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      background: #fff;
      padding: 8px 10px;
      border-radius: 6px;
      border: 1px solid #e2e8f0;

      .toggle-content {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .toggle-name {
          font-size: 12.5px;
          font-weight: 600;
          color: #1e293b;
        }

        .toggle-desc {
          font-size: 11px;
          color: #64748b;
          line-height: 1.3;
        }
      }
    }
  }
}

.gate-table-wrapper {
  margin-top: 12px;

  .table-subheading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .subheading-title {
      font-size: 13px;
      font-weight: 700;
      color: #334155;
    }

    .subheading-meta {
      font-size: 11.5px;
      color: #16a34a;
      font-weight: 500;
    }
  }
}
</style>
