<template>
  <view class="mobile-erp-page">
    <view class="search-bar-box">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="输入物料名称或编码检索库存..."
      />
    </view>

    <view class="inventory-list">
      <view v-for="item in filteredList" :key="item.id" class="inv-card">
        <view class="inv-header">
          <text class="inv-code">{{ item.productCode }}</text>
          <text class="wh-tag">{{ item.warehouseName }}</text>
        </view>

        <text class="inv-name">{{ item.productName }}</text>
        <text class="inv-spec">规格：{{ item.specification }}</text>

        <view class="inv-stats-grid">
          <view class="stat-col">
            <text class="stat-val font-bold">{{ item.stockQuantity }} {{ item.unit }}</text>
            <text class="stat-label">当前总库存</text>
          </view>
          <view class="stat-col">
            <text class="stat-val text-green font-bold">{{ item.availableQuantity }}</text>
            <text class="stat-label">可用库存</text>
          </view>
          <view class="stat-col">
            <text class="stat-val text-blue">¥ {{ item.totalValue?.toLocaleString() }}</text>
            <text class="stat-label">估算货值</text>
          </view>
        </view>

        <view v-if="item.isLowStock" class="warning-bar">
          <text class="warning-text">⚠️ 库存低于安全警戒线 ({{ item.minStock }})，请及时补货</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { apiGetInventory } from "../../api/erp";

const keyword = ref("");
const list = ref<any[]>([]);

const filteredList = computed(() => {
  if (!keyword.value.trim()) return list.value;
  const kw = keyword.value.toLowerCase();
  return list.value.filter(
    (i) =>
      i.productName.toLowerCase().includes(kw) ||
      i.productCode.toLowerCase().includes(kw)
  );
});

async function loadInventory() {
  try {
    list.value = await apiGetInventory();
  } catch {}
}

onMounted(() => {
  loadInventory();
});
</script>

<style scoped>
.mobile-erp-page {
  min-height: 100vh;
  background-color: #f1f5f9;
  padding: 14px;
  box-sizing: border-box;
}

.search-bar-box {
  margin-bottom: 14px;
}

.search-input {
  background: #ffffff;
  height: 40px;
  border-radius: 8px;
  padding: 0 14px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
}

.inventory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inv-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.inv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.inv-code {
  font-family: monospace;
  font-size: 11px;
  color: #64748b;
}

.wh-tag {
  background: #f1f5f9;
  color: #475569;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.inv-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}

.inv-spec {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 12px;
}

.inv-stats-grid {
  display: flex;
  background: #f8fafc;
  padding: 10px;
  border-radius: 8px;
  justify-content: space-around;
  text-align: center;
}

.stat-col {
  display: flex;
  flex-direction: column;
}

.stat-val {
  font-size: 14px;
  font-family: monospace;
}

.stat-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}

.font-bold { font-weight: bold; }
.text-green { color: #10b981; }
.text-blue { color: #0284c7; }

.warning-bar {
  margin-top: 10px;
  background: #fef2f2;
  border: 1px solid #fee2e2;
  border-radius: 6px;
  padding: 6px 10px;
}

.warning-text {
  font-size: 11px;
  color: #ef4444;
}
</style>
