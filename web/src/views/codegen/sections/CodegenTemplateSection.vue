<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { DocumentCopy, Check, Cpu, Setting, ArrowUp, ArrowDown } from "@element-plus/icons-vue";

const props = defineProps<{
  templatePolicy: {
    generateSql: boolean;
    generatePermission: boolean;
    generateMenu: boolean;
    includeOptimisticLock: boolean;
    includeTenantColumn: boolean;
    includeDeletedColumn: boolean;
  };
  styleGuide: {
    packagePrefix: string;
    tablePrefix: string;
    routeStyle: string;
    formStyle: string;
    apiStyle: string;
  };
  savePolicy: (message: string) => void;
}>();

const configExpanded = ref(false);
const activeCodeTab = ref("controller");

const sampleCodes: Record<string, { title: string; lang: string; code: string }> = {
  controller: {
    title: "ContractController.java",
    lang: "java",
    code: `package com.saasbasics.platform.modules.contract.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.contract.dto.*;
import com.saasbasics.platform.modules.contract.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 电子合同综合治理控制层
 * 符合企业级多租户与 RBAC 权限规范
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    @RequirePermission("biz_contract:query")
    public ApiResponse<List<ContractResponse>> list(ContractQueryRequest request) {
        return ApiResponse.success(contractService.listContracts(request));
    }

    @GetMapping("/{id}")
    @RequirePermission("biz_contract:query")
    public ApiResponse<ContractResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(contractService.getContractById(id));
    }

    @PostMapping
    @RequirePermission("biz_contract:create")
    public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractSaveRequest request) {
        return ApiResponse.success(contractService.createContract(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("biz_contract:update")
    public ApiResponse<ContractResponse> update(@PathVariable Long id, @Valid @RequestBody ContractSaveRequest request) {
        return ApiResponse.success(contractService.updateContract(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("biz_contract:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ApiResponse.success(null);
    }
}`
  },
  service: {
    title: "ContractService.java",
    lang: "java",
    code: `package com.saasbasics.platform.modules.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.contract.entity.ContractEntity;
import com.saasbasics.platform.modules.contract.mapper.ContractMapper;
import com.saasbasics.platform.modules.contract.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ContractService {

    private final ContractMapper contractMapper;

    public ContractService(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    public List<ContractResponse> listContracts(ContractQueryRequest request) {
        LambdaQueryWrapper<ContractEntity> wrapper = new LambdaQueryWrapper<>();
        if (request.contractNo() != null) {
            wrapper.like(ContractEntity::getContractNo, request.contractNo());
        }
        if (request.contractStatus() != null) {
            wrapper.eq(ContractEntity::getContractStatus, request.contractStatus());
        }
        wrapper.orderByDesc(ContractEntity::getCreatedAt);
        return contractMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractResponse createContract(ContractSaveRequest request) {
        ContractEntity entity = new ContractEntity();
        apply(entity, request);
        contractMapper.insert(entity);
        return toResponse(entity);
    }

    private ContractResponse toResponse(ContractEntity entity) {
        return new ContractResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getContractNo(),
            entity.getContractTitle(),
            entity.getContractAmount(),
            entity.getContractStatus(),
            entity.getCreatedAt()
        );
    }
}`
  },
  entity: {
    title: "ContractEntity.java",
    lang: "java",
    code: `package com.saasbasics.platform.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("biz_contract")
public class ContractEntity extends BaseTenantEntity {

    @TableField("contract_no")
    private String contractNo;

    @TableField("contract_title")
    private String contractTitle;

    @TableField("customer_id")
    private Long customerId;

    @TableField("contract_amount")
    private BigDecimal contractAmount;

    @TableField("signing_date")
    private LocalDate signingDate;

    @TableField("contract_status")
    private String contractStatus;

    @Version
    @TableField("version")
    private Integer version;

    // Getters and Setters...
}`
  },
  vue: {
    title: "ContractView.vue",
    lang: "vue",
    code: [
      "<template>",
      '  <div class="contract-container">',
      "    <ProTable",
      '      title="电子合同台账"',
      '      subtitle="全生命周期电子合同履约治理"',
      "      :columns=\"columns\"",
      "      :data=\"rows\"",
      "      :loading=\"loading\"",
      "    >",
      '      <template #status="{ row }">',
      "        <el-tag :type=\"row.contractStatus === 'SIGNED' ? 'success' : 'warning'\">",
      "          {{ row.contractStatus }}",
      "        </el-tag>",
      "      </template>",
      "    </ProTable>",
      "  </div>",
      "</template>",
      "",
      "<" + "script setup lang=\"ts\">",
      'import { ref, onMounted } from "vue";',
      'import ProTable from "@/components/pro/ProTable.vue";',
      'import type { ProTableColumn } from "@/components/pro/ProTable.vue";',
      'import { fetchContractList, deleteContract } from "@/api/modules/contract";',
      "",
      "const loading = ref(false);",
      "const rows = ref([]);",
      "const columns = [",
      '  { prop: "contractNo", label: "合同编号", minWidth: 160 },',
      '  { prop: "contractTitle", label: "合同标题", minWidth: 200 },',
      '  { prop: "contractAmount", label: "标的金额", minWidth: 120 },',
      '  { prop: "contractStatus", label: "状态", minWidth: 100, slot: "status" },',
      '  { prop: "createdAt", label: "签署创建时间", minWidth: 170 }',
      "];",
      "",
      "async function loadData() {",
      "  loading.value = true;",
      "  try {",
      "    const res = await fetchContractList();",
      "    rows.value = res.data;",
      "  } finally {",
      "    loading.value = false;",
      "  }",
      "}",
      "",
      "onMounted(loadData);",
      "<" + "/script>"
    ].join("\n")
  },
  sql: {
    title: "V100__create_biz_contract.sql",
    lang: "sql",
    code: [
      "-- 企业级多租户表结构标准 DDL",
      "CREATE TABLE IF NOT EXISTS biz_contract (",
      "    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 ID',",
      "    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户隔离 ID',",
      "    contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',",
      "    contract_title VARCHAR(128) NOT NULL COMMENT '合同标题',",
      "    customer_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '客户企业 ID',",
      "    contract_amount DECIMAL(12,2) NOT NULL DEFAULT '0.00' COMMENT '合同标的金额',",
      "    signing_date DATE NULL DEFAULT NULL COMMENT '签署日期',",
      "    contract_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '合同状态',",
      "    remark VARCHAR(500) NULL DEFAULT NULL COMMENT '备注说明',",
      "    created_by BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人 ID',",
      "    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',",
      "    updated_by BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更新人 ID',",
      "    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',",
      "    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',",
      "    deleted_at DATETIME(3) NULL DEFAULT NULL COMMENT '删除时间',",
      "    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',",
      "    PRIMARY KEY (id),",
      "    KEY idx_tenant_contract_no (tenant_id, contract_no),",
      "    KEY idx_tenant_customer (tenant_id, customer_id)",
      ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业电子合同台账';"
    ].join("\n")
  }
};

function handleCopyCode() {
  const current = sampleCodes[activeCodeTab.value];
  if (current) {
    navigator.clipboard.writeText(current.code);
    ElMessage.success(`已复制 ${current.title} 模版源码`);
  }
}
</script>

<template>
  <div class="codegen-template">
    <!-- 模板基线策略配置 (极简紧凑工具条) -->
    <el-card shadow="never" class="template-header-card">
      <div class="template-toolbar-row">
        <div class="toolbar-left">
          <span class="toolbar-title">模板基线架构</span>
          <div class="policy-quick-chips">
            <el-tag size="small" :type="templatePolicy.includeTenantColumn ? 'success' : 'info'">租户隔离</el-tag>
            <el-tag size="small" :type="templatePolicy.includeDeletedColumn ? 'success' : 'info'">逻辑删除</el-tag>
            <el-tag size="small" :type="templatePolicy.includeOptimisticLock ? 'success' : 'info'">乐观锁</el-tag>
            <el-tag size="small" :type="templatePolicy.generateSql ? 'warning' : 'info'">Flyway DDL</el-tag>
            <el-tag size="small" :type="templatePolicy.generatePermission ? 'primary' : 'info'">RBAC 权限</el-tag>
          </div>
        </div>

        <div class="toolbar-actions">
          <el-button link type="primary" size="small" :icon="configExpanded ? ArrowUp : Setting" @click="configExpanded = !configExpanded">
            {{ configExpanded ? '收起配置' : '基线参数设置' }}
          </el-button>
          <el-button type="primary" :icon="Check" @click="savePolicy('代码生成模板策略已成功固化保存')">
            保存模板基线
          </el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="configExpanded" class="template-expanded-body">
          <el-form label-position="left" label-width="120px" size="small">
            <el-row :gutter="14">
              <el-col :span="8">
                <el-form-item label="统一基础包前缀">
                  <el-input v-model="styleGuide.packagePrefix" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="表名通用前缀">
                  <el-input v-model="styleGuide.tablePrefix" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="路由接口风格">
                  <el-select v-model="styleGuide.routeStyle" style="width: 100%">
                    <el-option label="RESTful 资源式 (/api/{resource})" value="resource" />
                    <el-option label="RPC 操作式 (/api/{module}/{action})" value="action" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <div class="policy-toggle-grid">
              <el-checkbox v-model="templatePolicy.includeTenantColumn" label="自动带出租户列 (tenant_id)" />
              <el-checkbox v-model="templatePolicy.includeDeletedColumn" label="启用逻辑删除列 (deleted)" />
              <el-checkbox v-model="templatePolicy.includeOptimisticLock" label="启用乐观锁版本 (version)" />
              <el-checkbox v-model="templatePolicy.generateSql" label="同步生成 Flyway DDL/DML" />
              <el-checkbox v-model="templatePolicy.generatePermission" label="同步生成 RBAC 权限枚举" />
              <el-checkbox v-model="templatePolicy.generateMenu" label="同步生成菜单注册 SQL" />
            </div>
          </el-form>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 模板骨架实时透视与代码预览 -->
    <el-card shadow="never" class="preview-card">
      <template #header>
        <div class="panel-header-row">
          <div class="preview-title-row">
            <span class="panel-title">骨架代码实时透视</span>
            <code class="active-file-tag">{{ sampleCodes[activeCodeTab]?.title }}</code>
          </div>
          <el-button :icon="DocumentCopy" plain size="small" @click="handleCopyCode">复制代码片段</el-button>
        </div>
      </template>

      <el-tabs v-model="activeCodeTab" type="card" class="code-tabs">
        <el-tab-pane label="Controller.java" name="controller" />
        <el-tab-pane label="Service.java" name="service" />
        <el-tab-pane label="Entity.java" name="entity" />
        <el-tab-pane label="View.vue" name="vue" />
        <el-tab-pane label="Schema.sql" name="sql" />
      </el-tabs>

      <div class="code-preview-box">
        <pre><code>{{ sampleCodes[activeCodeTab]?.code }}</code></pre>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.codegen-template {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.template-header-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.template-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .toolbar-title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
    }

    .policy-quick-chips {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.template-expanded-body {
  margin-top: 12px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.policy-toggle-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #cbd5e1;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.preview-card {
  :deep(.el-card__header) {
    padding: 10px 16px;
  }

  :deep(.el-card__body) {
    padding: 14px 16px;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .preview-title-row {
    display: flex;
    align-items: center;
    gap: 8px;

    .panel-title {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
    }

    .active-file-tag {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      color: #2563eb;
      background: #eff6ff;
      padding: 2px 8px;
      border-radius: 4px;
      border: 1px solid #bfdbfe;
    }
  }
}

.code-tabs {
  margin-bottom: 12px;
}

.code-preview-box {
  background: #0f172a;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
  max-height: 580px;
  min-height: 460px;

  pre {
    margin: 0;
    color: #e2e8f0;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12.5px;
    line-height: 1.6;
  }
}
</style>
