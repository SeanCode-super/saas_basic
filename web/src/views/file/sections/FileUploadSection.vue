<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
defineProps<{
  uploadPolicy: {
    maxFileMb: number;
    directUpload: boolean;
    chunkUpload: boolean;
    imageCompression: boolean;
    virusScan: boolean;
  };
  loading?: boolean;
  objectRows: Array<Record<string, unknown>>;
  uploadSessions: Array<Record<string, unknown>>;
  uploadForm: {
    objectKey: string;
    fileName: string;
    fileSize: number;
    uploadMode: string;
    partCount: number;
    contentType: string;
    visibility: string;
    bizType: string;
    remark: string;
  };
  createUpload: () => void | Promise<void>;
  completeUpload: (row: Record<string, unknown>) => void | Promise<void>;
}>();

const sessionColumns: ProTableColumn[] = [
  { prop: "sessionCode", label: "会话编码", minWidth: 200 },
  { prop: "fileName", label: "文件名", minWidth: 180 },
  { prop: "uploadMode", label: "上传模式", minWidth: 120 },
  { prop: "status", label: "状态", minWidth: 100 },
  { prop: "actions", label: "操作", minWidth: 160, slot: "actions" }
];

const objectColumns: ProTableColumn[] = [
  { prop: "objectKey", label: "对象 Key", minWidth: 240 },
  { prop: "fileName", label: "文件名", minWidth: 180 },
  { prop: "fileSize", label: "大小", minWidth: 120 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>上传治理</h3>
        <p>上传链路应该统一控制分片、直传、压缩和恶意文件扫描能力。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="单文件最大大小（MB）">
        <el-input-number v-model="uploadPolicy.maxFileMb" :min="1" :max="10240" style="width: 100%" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="uploadPolicy.directUpload" active-text="允许直传" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="uploadPolicy.chunkUpload" active-text="允许分片上传" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="uploadPolicy.imageCompression" active-text="图片自动压缩" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="uploadPolicy.virusScan" active-text="启用病毒扫描" />
      </el-form-item>
      <el-form-item label="对象 Key">
        <el-input v-model="uploadForm.objectKey" />
      </el-form-item>
      <el-form-item label="文件名">
        <el-input v-model="uploadForm.fileName" />
      </el-form-item>
      <el-form-item label="文件大小">
        <el-input-number v-model="uploadForm.fileSize" :min="1" :max="1073741824" style="width: 100%" />
      </el-form-item>
      <el-form-item label="上传模式">
        <el-select v-model="uploadForm.uploadMode">
          <el-option label="单次上传" value="SINGLE" />
          <el-option label="分片上传" value="CHUNK" />
        </el-select>
      </el-form-item>
      <el-form-item label="分片数">
        <el-input-number v-model="uploadForm.partCount" :min="1" :max="1000" style="width: 100%" />
      </el-form-item>
      <el-form-item label="内容类型">
        <el-input v-model="uploadForm.contentType" />
      </el-form-item>
      <el-form-item label="可见性">
        <el-select v-model="uploadForm.visibility">
          <el-option label="私有" value="PRIVATE" />
          <el-option label="公开" value="PUBLIC" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型">
        <el-input v-model="uploadForm.bizType" />
      </el-form-item>
      <el-form-item label="完成备注">
        <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
      </el-form-item>
      <el-button type="primary" @click="createUpload">创建上传会话</el-button>
    </el-form>

    <ProTable
      title="上传会话"
      subtitle="真实上传会话台账。"
      :loading="loading"
      :columns="sessionColumns"
      :data="uploadSessions"
      compact
      empty-title="当前没有上传会话"
      empty-description="点击上方按钮可创建新的上传会话。"
    >
      <template #actions="{ row }">
        <el-button
          link
          type="primary"
          :disabled="row.status === 'COMPLETED'"
          @click="completeUpload(row)"
        >
          {{ row.status === "COMPLETED" ? "已完成" : "完成上传" }}
        </el-button>
      </template>
    </ProTable>

    <ProTable
      title="文件对象"
      subtitle="上传完成后的对象沉淀区。"
      :loading="loading"
      :columns="objectColumns"
      :data="objectRows"
      compact
      empty-title="当前没有文件对象"
      empty-description="上传会话创建后，文件对象会逐步沉淀在这里。"
    />
  </el-card>
</template>
