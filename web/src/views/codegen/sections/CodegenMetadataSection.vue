<script setup lang="ts">
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>元数据建模</h3>
        <p>先定义模型边界，再决定要生成什么代码和 SQL 产物。</p>
      </div>
    </template>
    <ul class="output-list">
      <li>数据表、字段、索引、唯一约束</li>
      <li>字典绑定、表单组件、校验规则</li>
      <li>菜单骨架、权限码、数据权限占位</li>
      <li>API 契约、DTO、查询对象、分页对象</li>
    </ul>
  </el-card>
</template>

<style scoped lang="scss">
.output-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 10px;
}
</style>
