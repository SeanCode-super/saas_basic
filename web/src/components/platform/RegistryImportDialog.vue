<script setup lang="ts">
const visible = defineModel<boolean>("visible", { required: true });
const content = defineModel<string>("content", { required: true });

defineProps<{
  title: string;
  description: string;
  sample: string;
  loading?: boolean;
}>();

const emit = defineEmits<{
  confirm: [];
}>();

function fillSample(sample: string) {
  content.value = sample;
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="760px" class="registry-import-dialog">
    <div class="registry-import">
      <div class="registry-import__toolbar">
        <strong>JSON 数组</strong>
        <span class="registry-import__toolbar-spacer" />
        <el-button @click="fillSample(sample)">填充示例</el-button>
        <el-button @click="content = ''">清空</el-button>
      </div>

      <div class="registry-import__editor">
        <el-input
          v-model="content"
          type="textarea"
          :rows="16"
          placeholder="请粘贴 JSON 数组导入清单"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="emit('confirm')">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.registry-import {
  display: grid;
  gap: 16px;
}

.registry-import__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  strong {
    font-size: 13px;
  }
}

.registry-import__toolbar-spacer {
  flex: 1;
}

.registry-import__editor :deep(.el-textarea__inner) {
  min-height: 360px;
  border-radius: 5px;
  box-shadow: 0 0 0 1px rgb(15 27 45 / 0.08) inset;
  padding: 16px 18px;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
}

</style>
