<script setup lang="ts">
import BaseCard from "@/components/base/BaseCard.vue";

defineProps<{
  packagePolicy: {
    defaultPackage: string;
    seatQuota: number;
    storageQuotaGb: number;
    apiThrottle: number;
    featureReleaseMode: string;
  };
  savePolicy: (message: string) => void;
}>();
</script>

<template>
  <div class="policy-workbench">
    <BaseCard tone="soft">
      <template #header>
        <div class="panel-header">
          <h3>套餐配额</h3>
          <p>平台默认套餐、席位、流量和功能发布方式应该由底座统一设定。</p>
        </div>
      </template>

      <div class="policy-grid">
        <el-form label-position="top">
          <el-form-item label="默认套餐">
            <el-select v-model="packagePolicy.defaultPackage">
              <el-option label="至尊版" value="SUPREME" />
              <el-option label="企业版" value="ENTERPRISE" />
              <el-option label="专业版" value="PROFESSIONAL" />
              <el-option label="基础版" value="BASIC" />
            </el-select>
          </el-form-item>
          <div class="dialog-grid">
            <el-form-item label="席位配额">
              <el-input-number v-model="packagePolicy.seatQuota" :min="1" :max="100000" style="width: 100%" />
            </el-form-item>
            <el-form-item label="存储配额（GB）">
              <el-input-number v-model="packagePolicy.storageQuotaGb" :min="1" :max="100000" style="width: 100%" />
            </el-form-item>
            <el-form-item label="API 每日限流">
              <el-input-number v-model="packagePolicy.apiThrottle" :min="1000" :max="100000000" style="width: 100%" />
            </el-form-item>
            <el-form-item label="功能发布模式">
              <el-select v-model="packagePolicy.featureReleaseMode">
                <el-option label="渐进发布" value="GRADUAL" />
                <el-option label="全量发布" value="ALL" />
                <el-option label="按套餐开放" value="PLAN_BASED" />
              </el-select>
            </el-form-item>
          </div>
          <el-button type="primary" @click="savePolicy('租户套餐与配额基线已保存')">保存套餐基线</el-button>
        </el-form>
      </div>
    </BaseCard>

    <BaseCard tone="contrast">
      <template #header>
        <div class="panel-header">
          <h3>配额信号</h3>
          <p>默认套餐和资源上限会直接影响租户开通和后续升级路径。</p>
        </div>
      </template>
      <div class="signal-stack">
        <article class="signal-item">
          <span>默认套餐</span>
          <strong>{{ packagePolicy.defaultPackage }}</strong>
        </article>
        <article class="signal-item">
          <span>席位上限</span>
          <strong>{{ packagePolicy.seatQuota }}</strong>
        </article>
        <article class="signal-item">
          <span>存储上限</span>
          <strong>{{ packagePolicy.storageQuotaGb }} GB</strong>
        </article>
      </div>
    </BaseCard>
  </div>
</template>

<style scoped lang="scss">
.policy-workbench {
  display: grid;
  gap: 16px;
}
</style>
