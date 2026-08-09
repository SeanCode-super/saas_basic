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
      <div class="registry-import__intro">
        <div class="registry-import__copy">
          <span class="registry-import__eyebrow">IMPORT MANIFEST</span>
          <strong>批量导入清单</strong>
          <p>{{ description }}</p>
        </div>
        <div class="registry-import__guide">
          <span>输入规范</span>
          <strong>JSON 数组</strong>
          <p>每个对象代表一条待创建记录。建议先填充示例，再按字段逐项修改。</p>
        </div>
      </div>

      <div class="registry-import__toolbar">
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

.registry-import__intro {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 220px;
  gap: 14px;
}

.registry-import__copy {
  padding: 18px 20px;
  border: 1px solid var(--sb-border-color);
  border-radius: 18px;
  background:
    radial-gradient(circle at top right, rgb(30 94 255 / 0.08), transparent 24%),
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(246 250 255 / 0.94));

  strong {
    display: block;
    font-size: 18px;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
  }
}

.registry-import__guide {
  padding: 18px;
  border: 1px solid var(--sb-border-subtle);
  border-radius: 18px;
  background: linear-gradient(180deg, rgb(248 250 255 / 0.96), rgb(244 248 255 / 0.9));

  span,
  strong {
    display: block;
  }

  span {
    color: var(--sb-text-secondary);
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    font-size: 18px;
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    line-height: 1.7;
    font-size: 12px;
  }
}

.registry-import__eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.registry-import__toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.registry-import__editor :deep(.el-textarea__inner) {
  min-height: 360px;
  border-radius: 18px;
  box-shadow: 0 0 0 1px rgb(15 27 45 / 0.08) inset;
  padding: 16px 18px;
  font-family: ui-monospace, "SFMono-Regular", "SF Mono", Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .registry-import__intro {
    grid-template-columns: 1fr;
  }
}
</style>
