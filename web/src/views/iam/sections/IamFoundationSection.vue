<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import {
  Cpu,
  Key,
  Right,
  User,
  UserFilled,
  OfficeBuilding,
  CollectionTag,
  Lock,
  DataAnalysis,
  Grid,
  CircleCheckFilled,
  Aim
} from "@element-plus/icons-vue";
import type { IamOverview } from "@/api/modules/iam";

const router = useRouter();

defineProps<{
  overview: IamOverview | null;
  policyRows: Array<{
    title: string;
    owner: string;
    baseline: string;
    source: string;
  }>;
}>();

const emit = defineEmits<{
  (e: "openInspector"): void;
}>();

const activeTab = ref("baseline");
</script>

<template>
  <div class="foundation-container">
    <!-- Sleek 40px IAM Foundation Telemetry Strip -->
    <div class="iam-telemetry-bar">
      <div class="telemetry-items">
        <div class="telemetry-item">
          <span class="telemetry-icon blue"><el-icon><User /></el-icon></span>
          <span class="telemetry-label">系统账号</span>
          <strong class="telemetry-val">{{ overview?.counters.accounts ?? 0 }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon emerald"><el-icon><UserFilled /></el-icon></span>
          <span class="telemetry-label">组织员工</span>
          <strong class="telemetry-val">{{ overview?.counters.employees ?? 0 }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon purple"><el-icon><OfficeBuilding /></el-icon></span>
          <span class="telemetry-label">部门架构</span>
          <strong class="telemetry-val">{{ overview?.counters.departments ?? 0 }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon amber"><el-icon><CollectionTag /></el-icon></span>
          <span class="telemetry-label">标准岗位</span>
          <strong class="telemetry-val">{{ overview?.counters.positions ?? 0 }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon indigo"><el-icon><Key /></el-icon></span>
          <span class="telemetry-label">业务角色</span>
          <strong class="telemetry-val">{{ overview?.counters.roles ?? 0 }}</strong>
        </div>
        <div class="telemetry-divider" />
        <div class="telemetry-item">
          <span class="telemetry-icon rose"><el-icon><Lock /></el-icon></span>
          <span class="telemetry-label">API 资源</span>
          <strong class="telemetry-val">{{ overview?.counters.apiResources ?? 0 }}</strong>
        </div>
      </div>

      <div class="telemetry-actions">
        <el-button type="primary" plain size="small" :icon="Key" @click="router.push('/iam/role')">
          角色矩阵
        </el-button>
        <el-button type="success" plain size="small" :icon="Cpu" @click="emit('openInspector')">
          权限透视诊断器
        </el-button>
      </div>
    </div>

    <!-- Structured Foundation Tabs -->
    <el-tabs v-model="activeTab" type="border-card" class="foundation-tabs">
      <!-- Tab 1: Governance Baseline & Policy Metrics -->
      <el-tab-pane name="baseline">
        <template #label>
          <span class="tab-label">
            <el-icon><DataAnalysis /></el-icon>
            <span>身份治理基线与指标</span>
          </span>
        </template>

        <div class="tab-pane-content">

          <!-- Policy Rows Matrix -->
          <div class="policy-section">
            <div class="section-sub-title">
              <h4>身份与安全治理策略矩阵</h4>
              <p>各安全维度统一由底座策略引擎驱动，杜绝分散硬编码与权限逃逸。</p>
            </div>

            <div class="policy-table-card">
              <div v-for="item in policyRows" :key="item.title" class="policy-matrix-item">
                <div class="policy-info">
                  <div class="policy-header">
                    <strong>{{ item.title }}</strong>
                    <el-tag size="small" type="info" effect="plain">{{ item.owner }}</el-tag>
                  </div>
                  <p class="policy-desc">{{ item.baseline }}</p>
                </div>
                <div class="policy-source-chip">
                  <span class="source-label">实现标识</span>
                  <code>{{ item.source }}</code>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Domain Model & Runtime Capabilities -->
      <el-tab-pane name="models">
        <template #label>
          <span class="tab-label">
            <el-icon><Grid /></el-icon>
            <span>运行态领域模型与能力矩阵</span>
          </span>
        </template>

        <div class="tab-pane-content">
          <!-- 3-Tier Layer Architecture -->
          <div class="section-sub-title">
            <h4>领域模型分层架构 (Domain Model Hierarchy)</h4>
            <p>基于云原生三层模型解耦：账号凭据层、组织人事主数据层与统一 RBAC 授权治理层。</p>
          </div>

          <div class="model-topology-grid">
            <div class="topology-card">
              <div class="card-badge blue">主体层 · Identity Core</div>
              <h5>Account & Identity</h5>
              <p>系统登录凭证与自然人身份分离，支持一人多账号、统一实名档案与跨租户身份关联。</p>
              <div class="entity-pills">
                <span class="entity-pill">Account 账号</span>
                <span class="entity-pill">Identity 自然人</span>
                <span class="entity-pill">Multi-Tenant 租户绑定</span>
              </div>
            </div>

            <div class="topology-card">
              <div class="card-badge emerald">主数据层 · Organization</div>
              <h5>Department & Employee</h5>
              <p>组织部门树状层级、职务岗位序列与员工入职主数据统一沉淀，作为数据权限的划分基准。</p>
              <div class="entity-pills">
                <span class="entity-pill">Department 部门树</span>
                <span class="entity-pill">Position 职务岗位</span>
                <span class="entity-pill">Employee 员工主数据</span>
              </div>
            </div>

            <div class="topology-card">
              <div class="card-badge purple">控制层 · Authorization</div>
              <h5>Role & DataScope</h5>
              <p>功能权限 (RBAC) 与行级数据权限 (DataScope) 强强联合，细化到按钮级与 SQL 自动拦截注入。</p>
              <div class="entity-pills">
                <span class="entity-pill">Role 复合角色</span>
                <span class="entity-pill">ApiResource 权限点</span>
                <span class="entity-pill">DataScope 行级规则</span>
              </div>
            </div>
          </div>

          <!-- Runtime Capabilities Grid -->
          <div class="capabilities-section">
            <div class="section-sub-title">
              <h4>运行态核心能力状态 (Runtime Capabilities)</h4>
              <p>经由 Spring Boot 框架、MyBatis-Plus 动态拦截器与缓存层检验的权威能力闭环。</p>
            </div>

            <div class="capabilities-matrix-grid">
              <div v-for="cap in overview?.capabilities ?? []" :key="cap" class="cap-card">
                <div class="cap-header">
                  <el-icon class="cap-check-icon"><CircleCheckFilled /></el-icon>
                  <strong>{{ cap }}</strong>
                  <span class="cap-status-dot">已就绪</span>
                </div>
                <p class="cap-desc">已深度挂载在当前运行态上下文，全自动执行跨租户隔离、策略鉴权与合规审计。</p>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Inspection & Simulation Entrance -->
      <el-tab-pane name="simulation">
        <template #label>
          <span class="tab-label">
            <el-icon><Aim /></el-icon>
            <span>权限模拟与策略诊断</span>
          </span>
        </template>

        <div class="tab-pane-content simulation-intro-box">
          <div class="sim-card">
            <div class="sim-icon-box">
              <el-icon :size="36"><Cpu /></el-icon>
            </div>
            <h3>成员权限透视诊断器</h3>
            <p>
              采用零运行时侵入的权限模拟引擎，支持直接指定员工、目标操作接口与上下文租户，
              穿透输出角色匹配链条、最终决策判定（PASS/DENY）以及自动生成的行级 SQL 数据过滤规则。
            </p>
            <el-button type="success" size="large" @click="emit('openInspector')">
              立即启动权限模拟器
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.foundation-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

/* Sleek IAM Telemetry Bar */
.iam-telemetry-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--sb-border-color, #e2e8f0);
  border-radius: 8px;
  flex-wrap: wrap;
  gap: 12px;

  .telemetry-items {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
  }

  .telemetry-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;

    .telemetry-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 4px;
      font-size: 13px;

      &.blue { background: #eff6ff; color: #2563eb; }
      &.emerald { background: #ecfdf5; color: #059669; }
      &.purple { background: #faf5ff; color: #7c3aed; }
      &.amber { background: #fffbeb; color: #d97706; }
      &.indigo { background: #e0e7ff; color: #4338ca; }
      &.rose { background: #fff1f2; color: #e11d48; }
    }

    .telemetry-label {
      color: #64748b;
      font-size: 12px;
    }

    .telemetry-val {
      color: #0f172a;
      font-weight: 600;
    }
  }

  .telemetry-divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
  }

  .telemetry-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
}

/* Tabs */
.foundation-tabs {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--sb-border-color, #e2e8f0);
  background: #fff;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.tab-pane-content {
  padding: 16px 4px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Counters Grid */
.iam-counters-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.iam-counter-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;

  &:hover {
    border-color: #cbd5e1;
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgb(0 0 0 / 0.04);
  }

  .counter-icon {
    width: 38px;
    height: 38px;
    border-radius: 8px;
    display: grid;
    place-items: center;
    font-size: 18px;
    flex-shrink: 0;

    &.blue { background: #eff6ff; color: #2563eb; }
    &.emerald { background: #ecfdf5; color: #059669; }
    &.purple { background: #faf5ff; color: #7c3aed; }
    &.amber { background: #fffbeb; color: #d97706; }
    &.indigo { background: #e0e7ff; color: #4338ca; }
    &.rose { background: #fff1f2; color: #e11d48; }
  }

  .counter-body {
    display: flex;
    flex-direction: column;

    .counter-label {
      font-size: 11px;
      color: #64748b;
    }

    .counter-value {
      font-size: 20px;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.2;
      margin: 1px 0;
    }

    .counter-sub {
      font-size: 10px;
      color: #94a3b8;
    }
  }
}

/* Policy Section */
.section-sub-title {
  margin-bottom: 12px;

  h4 {
    margin: 0 0 4px;
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }
}

.policy-table-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.policy-matrix-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #f8fafc;
  }

  .policy-info {
    flex: 1;

    .policy-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      strong {
        font-size: 14px;
        color: #1e293b;
      }
    }

    .policy-desc {
      margin: 0;
      font-size: 12px;
      color: #64748b;
    }
  }

  .policy-source-chip {
    text-align: right;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;

    .source-label {
      font-size: 11px;
      color: #94a3b8;
    }

    code {
      font-size: 12px;
      background: #f1f5f9;
      padding: 2px 6px;
      border-radius: 4px;
      color: #0284c7;
    }
  }
}

/* Domain Model Topology */
.model-topology-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 8px;
}

.topology-card {
  position: relative;
  padding: 22px 20px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .card-badge {
    align-self: flex-start;
    padding: 3px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 600;

    &.blue { background: #eff6ff; color: #2563eb; }
    &.emerald { background: #ecfdf5; color: #059669; }
    &.purple { background: #faf5ff; color: #7c3aed; }
  }

  h5 {
    margin: 4px 0 0;
    font-size: 16px;
    color: #0f172a;
    font-weight: 700;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #64748b;
    line-height: 1.5;
    flex: 1;
  }

  .entity-pills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;

    .entity-pill {
      font-size: 11px;
      padding: 3px 8px;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      color: #334155;
    }
  }
}

/* Capabilities Matrix */
.capabilities-matrix-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.cap-card {
  padding: 14px 18px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;

  .cap-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;

    .cap-check-icon {
      color: #10b981;
      font-size: 16px;
    }

    strong {
      font-size: 13px;
      color: #0f172a;
      flex: 1;
      text-transform: capitalize;
    }

    .cap-status-dot {
      font-size: 11px;
      color: #059669;
      background: #ecfdf5;
      padding: 1px 6px;
      border-radius: 10px;
      font-weight: 500;
    }
  }

  .cap-desc {
    margin: 0;
    font-size: 12px;
    color: #64748b;
  }
}

/* Simulation Tab */
.simulation-intro-box {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 380px;

  .sim-card {
    max-width: 520px;
    text-align: center;
    padding: 36px 32px;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    background: #f8fafc;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;

    .sim-icon-box {
      width: 64px;
      height: 64px;
      border-radius: 16px;
      background: #ecfdf5;
      color: #10b981;
      display: grid;
      place-items: center;
    }

    h3 {
      margin: 0;
      font-size: 20px;
      color: #0f172a;
    }

    p {
      margin: 0 0 10px;
      font-size: 13px;
      color: #64748b;
      line-height: 1.6;
    }
  }
}

@media (max-width: 1200px) {
  .iam-counters-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .model-topology-grid {
    grid-template-columns: 1fr;
  }

  .capabilities-matrix-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .iam-quick-banners-grid {
    grid-template-columns: 1fr;
  }

  .iam-counters-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
