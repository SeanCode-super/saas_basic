<script setup lang="ts">
defineProps<{
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
</script>

<template>
  <div class="dual-grid">
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <h3>模板基线</h3>
          <p>决定生成物是否具备企业级底座的通用字段、权限和菜单骨架。</p>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item>
          <el-switch v-model="templatePolicy.generateSql" active-text="同步生成 MySQL DDL" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="templatePolicy.generatePermission" active-text="同步生成权限码" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="templatePolicy.generateMenu" active-text="同步生成菜单骨架" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="templatePolicy.includeTenantColumn" active-text="默认附带 tenant_id 字段" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="templatePolicy.includeDeletedColumn" active-text="默认附带 deleted 字段" />
        </el-form-item>
        <el-form-item>
          <el-switch v-model="templatePolicy.includeOptimisticLock" active-text="默认附带 version 字段" />
        </el-form-item>
        <el-button type="primary" @click="savePolicy('代码模板基线已保存')">保存模板基线</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <h3>统一风格规范</h3>
          <p>统一包前缀、表前缀、路由风格和表单形态，避免生成物风格漂移。</p>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="Java 包前缀">
          <el-input v-model="styleGuide.packagePrefix" />
        </el-form-item>
        <el-form-item label="业务表前缀">
          <el-input v-model="styleGuide.tablePrefix" />
        </el-form-item>
        <el-form-item label="路由风格">
          <el-select v-model="styleGuide.routeStyle">
            <el-option label="资源式" value="resource" />
            <el-option label="模块式" value="module" />
          </el-select>
        </el-form-item>
        <el-form-item label="表单交互风格">
          <el-select v-model="styleGuide.formStyle">
            <el-option label="抽屉" value="drawer" />
            <el-option label="弹窗" value="dialog" />
            <el-option label="独立页" value="page" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 风格">
          <el-select v-model="styleGuide.apiStyle">
            <el-option label="RESTful" value="restful" />
            <el-option label="命令式" value="command" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="savePolicy('统一风格规范已保存')">保存风格规范</el-button>
      </el-form>
    </el-card>
  </div>
</template>
