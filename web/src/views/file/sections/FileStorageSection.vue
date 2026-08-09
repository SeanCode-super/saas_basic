<script setup lang="ts">
import ProTable from "@/components/pro/ProTable.vue";
import type { ProTableColumn } from "@/components/pro/ProTable.vue";
defineProps<{
  storagePolicy: {
    defaultAdapter: string;
    retentionDays: number;
    versioning: boolean;
    deduplicateByHash: boolean;
    privateBucketOnly: boolean;
  };
  loading?: boolean;
  storageRows: Array<Record<string, unknown>>;
  storageForm: {
    storageCode: string;
    storageName: string;
    storageType: string;
    endpoint: string;
    bucketDefault: string;
    publicBaseUrl: string;
    status: string;
    remark: string;
  };
  saveStorage: () => void | Promise<void>;
  editStorage: (row: any) => void;
}>();

const storageColumns: ProTableColumn[] = [
  { prop: "storageCode", label: "存储编码", minWidth: 160 },
  { prop: "storageName", label: "存储名称", minWidth: 160 },
  { prop: "storageType", label: "类型", minWidth: 120 },
  { prop: "bucketDefault", label: "默认桶", minWidth: 140 },
  { prop: "status", label: "状态", minWidth: 100 }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="panel-header">
        <h3>存储策略</h3>
        <p>对象存储适配、保留时长、版本能力和桶默认可见性在这里统一收口。</p>
      </div>
    </template>

    <el-form label-position="top">
      <el-form-item label="默认存储适配器">
        <el-select v-model="storagePolicy.defaultAdapter">
          <el-option label="MinIO" value="MINIO" />
          <el-option label="OSS" value="OSS" />
          <el-option label="S3" value="S3" />
        </el-select>
      </el-form-item>
      <el-form-item label="文件保留天数">
        <el-input-number v-model="storagePolicy.retentionDays" :min="1" :max="3650" style="width: 100%" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="storagePolicy.versioning" active-text="启用对象版本管理" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="storagePolicy.deduplicateByHash" active-text="启用哈希去重" />
      </el-form-item>
      <el-form-item>
        <el-switch v-model="storagePolicy.privateBucketOnly" active-text="默认使用私有桶" />
      </el-form-item>
      <el-form-item label="存储编码">
        <el-input v-model="storageForm.storageCode" />
      </el-form-item>
      <el-form-item label="存储名称">
        <el-input v-model="storageForm.storageName" />
      </el-form-item>
      <el-form-item label="Endpoint">
        <el-input v-model="storageForm.endpoint" />
      </el-form-item>
      <el-form-item label="默认桶">
        <el-input v-model="storageForm.bucketDefault" />
      </el-form-item>
      <el-form-item label="公开访问地址">
        <el-input v-model="storageForm.publicBaseUrl" />
      </el-form-item>
      <el-button type="primary" @click="saveStorage">保存存储器</el-button>
    </el-form>

    <ProTable
      title="存储器列表"
      subtitle="真实存储器台账。"
      :loading="loading"
      :columns="storageColumns"
      :data="storageRows"
      compact
      empty-title="当前没有存储器"
      empty-description="保存上方表单即可创建第一条存储器。"
    >
      <template #actions="{ row }">
        <el-button link type="primary" @click="editStorage(row)">编辑</el-button>
      </template>
    </ProTable>
  </el-card>
</template>
