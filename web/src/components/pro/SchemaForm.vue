<script setup lang="ts">
import { useSlots } from "vue";

export interface SchemaField {
  field: string;
  label: string;
  component: "input" | "select";
  placeholder?: string;
  options?: Array<{ label: string; value: string | number }>;
}

const props = defineProps<{
  modelValue: Record<string, string | number | undefined>;
  schema: SchemaField[];
}>();

const emit = defineEmits<{
  "update:modelValue": [Record<string, string | number | undefined>];
  submit: [];
  reset: [];
}>();

const slots = useSlots();

function updateField(field: string, value: string | number | undefined) {
  emit("update:modelValue", { ...props.modelValue, [field]: value });
}
</script>

<template>
  <section class="schema-form-shell">
    <header class="schema-form-shell__header">
      <strong>筛选</strong>
      <div class="schema-form-shell__actions">
        <slot
          v-if="slots.actions"
          name="actions"
          :submit="() => emit('submit')"
          :reset="() => emit('reset')"
        />
        <template v-else>
          <el-button @click="emit('reset')">重置</el-button>
          <el-button type="primary" @click="emit('submit')">查询</el-button>
        </template>
      </div>
    </header>

    <el-form label-position="top" class="schema-form">
      <el-row :gutter="12">
        <el-col v-for="item in schema" :key="item.field" :xs="24" :sm="12" :lg="8">
          <el-form-item :label="item.label">
            <el-input
              v-if="item.component === 'input'"
              :model-value="modelValue[item.field]"
              :placeholder="item.placeholder"
              @update:model-value="(value: string) => updateField(item.field, value)"
            />
            <el-select
              v-else
              :model-value="modelValue[item.field]"
              :placeholder="item.placeholder"
              style="width: 100%"
              @update:model-value="(value: string | number) => updateField(item.field, value)"
            >
              <el-option
                v-for="option in item.options ?? []"
                :key="String(option.value)"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </section>
</template>

<style scoped lang="scss">
.schema-form-shell {
  padding: 13px 14px 2px;
  border: 1px solid var(--sb-border-color);
  border-radius: var(--sb-radius-md);
  background: #fafbfc;
}

.schema-form-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  strong {
    font-size: 14px;
  }
}

.schema-form-shell__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.schema-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.schema-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 4px;
  color: var(--sb-text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.schema-form :deep(.el-input__wrapper),
.schema-form :deep(.el-select__wrapper) {
  min-height: 34px;
  border-radius: 5px;
}
</style>
