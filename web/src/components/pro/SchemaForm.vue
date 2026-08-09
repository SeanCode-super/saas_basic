<script setup lang="ts">
import { computed, useSlots } from "vue";

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

const filterSummary = computed(() => `共 ${props.schema.length} 项筛选条件`);

function updateField(field: string, value: string | number | undefined) {
  emit("update:modelValue", {
    ...props.modelValue,
    [field]: value
  });
}

function handleInput(field: string, value: string) {
  updateField(field, value);
}

function handleSelect(field: string, value: string | number) {
  updateField(field, value);
}
</script>

<template>
  <section class="schema-form-shell">
    <header class="schema-form-shell__header">
      <div class="schema-form-shell__copy">
        <span class="schema-form-shell__eyebrow">QUERY STUDIO</span>
        <strong>查询条件</strong>
        <p>{{ filterSummary }}</p>
      </div>
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

    <el-form label-width="110px" class="schema-form">
      <el-row :gutter="16">
        <el-col v-for="item in schema" :key="item.field" :xs="24" :sm="12" :lg="8">
          <el-form-item :label="item.label">
            <el-input
              v-if="item.component === 'input'"
              :model-value="modelValue[item.field]"
              :placeholder="item.placeholder"
              @update:model-value="(value: string) => handleInput(item.field, value)"
            />
            <el-select
              v-else
              :model-value="modelValue[item.field]"
              :placeholder="item.placeholder"
              style="width: 100%"
              @update:model-value="(value: string | number) => handleSelect(item.field, value)"
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
  padding: 18px 20px 20px;
  border: 1px solid rgb(15 23 42 / 0.08);
  border-radius: 20px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(248 251 255 / 0.96)),
    var(--sb-surface-strong);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.82);
}

.schema-form-shell__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.schema-form-shell__copy {
  strong,
  span {
    display: block;
  }

  strong {
    font-size: 16px;
    color: var(--sb-text-primary);
  }

  p {
    margin: 8px 0 0;
    color: var(--sb-text-secondary);
    font-size: 13px;
  }
}

.schema-form-shell__eyebrow {
  margin-bottom: 8px;
  color: var(--sb-primary-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.schema-form-shell__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.schema-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.schema-form :deep(.el-form-item__label) {
  color: var(--sb-text-secondary);
  font-weight: 600;
}

.schema-form :deep(.el-input__wrapper),
.schema-form :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgb(15 23 42 / 0.06) inset;
}

@media (max-width: 960px) {
  .schema-form-shell__header {
    flex-direction: column;
  }

  .schema-form-shell__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
