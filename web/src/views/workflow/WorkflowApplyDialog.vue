<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { startWorkflowInstance } from "@/api/modules/workflow";
import type { FormFieldConfig, ProcessDefinitionRow } from "@/api/modules/workflow";

const props = defineProps<{
  visible: boolean;
  definition: ProcessDefinitionRow | null;
}>();

const emit = defineEmits<{
  "update:visible": [boolean];
  success: [];
}>();

const submitting = ref(false);
const titleModel = ref("");
const formFields = ref<FormFieldConfig[]>([]);
const formDataModel = reactive<Record<string, any>>({});

watch(
  () => props.definition,
  (def) => {
    if (!def) {
      formFields.value = [];
      titleModel.value = "";
      return;
    }

    titleModel.value = `申请: ${def.processName}`;
    Object.keys(formDataModel).forEach((key) => delete formDataModel[key]);

    if (def.formSchemaJson) {
      try {
        const parsed = JSON.parse(def.formSchemaJson) as FormFieldConfig[];
        formFields.value = Array.isArray(parsed) ? parsed : [];
        formFields.value.forEach((field) => {
          formDataModel[field.fieldCode] = field.defaultValue ?? (field.fieldType === "NUMBER" ? 0 : "");
        });
      } catch {
        formFields.value = [];
      }
    } else {
      formFields.value = [
        { fieldCode: "reason", fieldName: "申请事由与说明", fieldType: "TEXTAREA", required: true, placeholder: "请详细填写申请事项及背景" }
      ];
      formDataModel.reason = "";
    }
  },
  { immediate: true }
);

async function handleSubmit() {
  if (!props.definition) return;
  if (!titleModel.value.trim()) {
    ElMessage.warning("请填写审批单标题");
    return;
  }

  // 基础必填项校验
  for (const field of formFields.value) {
    if (field.required) {
      const val = formDataModel[field.fieldCode];
      if (val === undefined || val === null || val === "" || (field.fieldType === "NUMBER" && Number(val) <= 0)) {
        ElMessage.warning(`请填写必填项: 【${field.fieldName}】`);
        return;
      }
    }
  }

  submitting.value = true;
  try {
    await startWorkflowInstance({
      definitionId: props.definition.id,
      title: titleModel.value.trim(),
      formDataJson: JSON.stringify(formDataModel)
    });
    ElMessage.success("审批申请已成功发起，进入流转！");
    emit("update:visible", false);
    emit("success");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="definition ? `发起审批：${definition.processName}` : '发起审批申请'"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="definition" class="apply-header-banner">
      <div class="apply-icon-box" :style="{ backgroundColor: definition.color || '#2563eb' }">
        <el-icon><component :is="definition.icon || 'Document'" /></el-icon>
      </div>
      <div class="apply-meta">
        <strong>{{ definition.processName }}</strong>
        <p>{{ definition.description || '请根据业务实际情况如实填写以下审批单信息。' }}</p>
      </div>
    </div>

    <el-form label-position="top" class="apply-form-body">
      <el-form-item label="审批单标题" required>
        <el-input v-model="titleModel" placeholder="请输入直观明了的单据标题" />
      </el-form-item>

      <div class="apply-fields-grid">
        <el-form-item
          v-for="field in formFields"
          :key="field.fieldCode"
          :label="field.fieldName"
          :required="field.required"
          :class="{ 'full-width': field.fieldType === 'TEXTAREA' }"
        >
          <!-- 文本类型 -->
          <el-input
            v-if="field.fieldType === 'TEXT'"
            v-model="formDataModel[field.fieldCode] as string"
            :placeholder="field.placeholder || `请输入${field.fieldName}`"
          />

          <!-- 数字类型 -->
          <el-input-number
            v-else-if="field.fieldType === 'NUMBER'"
            v-model="formDataModel[field.fieldCode] as number"
            :min="0"
            :placeholder="field.placeholder || `请输入${field.fieldName}`"
            style="width: 100%"
          />

          <!-- 下拉单选 -->
          <el-select
            v-else-if="field.fieldType === 'SELECT'"
            v-model="formDataModel[field.fieldCode]"
            :placeholder="field.placeholder || `请选择${field.fieldName}`"
            style="width: 100%"
          >
            <el-option
              v-for="opt in field.options || []"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>

          <!-- 日期选择 -->
          <el-date-picker
            v-else-if="field.fieldType === 'DATE'"
            v-model="formDataModel[field.fieldCode] as string"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="field.placeholder || `请选择${field.fieldName}`"
            style="width: 100%"
          />

          <!-- 多行文本 -->
          <el-input
            v-else-if="field.fieldType === 'TEXTAREA'"
            v-model="formDataModel[field.fieldCode] as string"
            type="textarea"
            :rows="3"
            :placeholder="field.placeholder || `请详细填写${field.fieldName}`"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        立即提交审批
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.apply-header-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: var(--sb-radius-md);
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 20px;
}

.apply-icon-box {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 20px;
  flex-shrink: 0;
}

.apply-meta {
  strong {
    font-size: 15px;
    color: #0f172a;
    display: block;
    margin-bottom: 2px;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: #64748b;
    line-height: 1.4;
  }
}

.apply-form-body {
  margin-top: 4px;
}

.apply-fields-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;

  .full-width {
    grid-column: 1 / -1;
  }
}
</style>
