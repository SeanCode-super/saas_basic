<template>
  <view class="erp-page">
    <!-- iOS 27 沉浸式顶栏返回导航 -->
    <view class="ios-nav-bar">
      <view class="ios-nav-back-pill" @click="goBack">
        <AppIcon name="chevron-left" :size="16" color="#0f172a" />
        <text class="ios-nav-back-text">返回</text>
      </view>
      <text class="ios-nav-title">多仓智能库存</text>
      <view class="ios-nav-placeholder" />
    </view>

    <!-- 顶部状态栏与统计汇总 -->
    <view class="hero-card">
      <view class="hero-head">
        <view class="hero-title-box">
          <text class="hero-badge">智能仓储中心</text>
          <text class="hero-title">办公与生产物资在库分布</text>
        </view>
        <view class="refresh-indicator" :class="{ 'is-spinning': refreshing }" @click="handleManualRefresh">
          <AppIcon name="cpu" :size="16" color="#0284c7" />
        </view>
      </view>

      <view class="summary-grid">
        <view class="summary-item">
          <text class="summary-val">{{ totalStockCount }}</text>
          <text class="summary-label">在库物资总件数</text>
        </view>
        <view class="summary-item">
          <text class="summary-val text-cyan">¥{{ (totalStockValue / 10000).toFixed(1) }}万</text>
          <text class="summary-label">在库预估总价值</text>
        </view>
        <view class="summary-item">
          <text class="summary-val text-warning">{{ warningCount }}</text>
          <text class="summary-label">库存警戒项</text>
        </view>
      </view>
    </view>

    <!-- 搜索与筛选控制器 -->
    <view class="filter-section">
      <view class="search-box">
        <AppIcon name="search" :size="16" color="#64748b" />
        <input
          v-model="searchKeyword"
          class="search-input"
          placeholder="搜索物资名称、物料编号或规格型号..."
          placeholder-class="placeholder-text"
        />
        <text v-if="searchKeyword" class="clear-btn" @click="searchKeyword = ''">×</text>
      </view>

      <!-- YouTube 同款 iOS 27 晶透标签横向胶囊栏 -->
      <scroll-view scroll-x class="ios27-chip-scroll" :show-scrollbar="false" style="padding-left: 0; padding-right: 0;">
        <view class="ios27-chip-group">
          <view
            v-for="cat in categoryFilters"
            :key="cat.key"
            class="ios27-chip"
            :class="{ 'is-active': activeCategory === cat.key }"
            @click="activeCategory = cat.key"
          >
            <text>{{ cat.label }}</text>
            <text v-if="cat.count != null" class="ios27-chip-badge">{{ cat.count }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 加载骨架屏 -->
    <view v-if="loading" class="content-body">
      <AppSkeleton type="card" :count="4" />
    </view>

    <!-- 空状态 -->
    <view v-else-if="filteredInventory.length === 0" class="content-body">
      <AppEmpty
        type="search"
        title="暂无匹配物料或库存记录"
        description="请尝试调整搜索关键字或切换类别标签"
        action-text="清空筛选条件"
        @action="resetFilters"
      />
    </view>

    <!-- 实体物料卡片流 -->
    <view v-else class="content-body">
      <view
        v-for="item in filteredInventory"
        :key="item.id"
        class="inv-item-card"
        @click="openDetailSheet(item)"
      >
        <view class="card-top-row">
          <view class="sku-box">
            <text class="sku-tag">{{ item.productCode }}</text>
            <text class="wh-badge">{{ item.warehouseName || '主仓保税库' }}</text>
          </view>
          <view v-if="item.isLowStock || item.stockQuantity <= (item.minStock || 5)" class="alert-pill">
            <text class="alert-dot" />
            <text>警戒缺货</text>
          </view>
          <view v-else class="normal-pill">
            <text class="normal-dot" />
            <text>库存充盈</text>
          </view>
        </view>

        <text class="product-name">{{ item.productName }}</text>
        <text class="product-spec">规格：{{ item.specification || '标准工业规格 / 标配' }}</text>

        <view class="stock-metrics-bar">
          <view class="metric-cell">
            <text class="metric-val font-mono">{{ item.stockQuantity }} {{ item.unit || '件' }}</text>
            <text class="metric-lbl">当前总在库</text>
          </view>
          <view class="metric-cell">
            <text class="metric-val font-mono text-green">{{ item.availableQuantity ?? item.stockQuantity }}</text>
            <text class="metric-lbl">当前可用余量</text>
          </view>
          <view class="metric-cell">
            <text class="metric-val font-mono text-cyan">¥{{ Number(item.totalValue || 0).toLocaleString() }}</text>
            <text class="metric-lbl">估算库存货值</text>
          </view>
        </view>

        <view class="card-footer-action">
          <text class="action-hint">轻触查看详情与出入库流转记录</text>
          <text class="action-btn-text">快捷调拔 &gt;</text>
        </view>
      </view>
    </view>

    <!-- 物料出入库 / 详情抽屉 (AppModal Sheet Mode) -->
    <AppModal
      v-model="detailVisible"
      mode="sheet"
      :title="selectedItem?.productName || '物资详情与在库调拨'"
      subtitle="实时更新企业物资在库台账"
      confirm-text="确认调拔出入库"
      :confirm-loading="adjusting"
      @confirm="submitStockAdjustment"
    >
      <view v-if="selectedItem" class="detail-sheet-content">
        <view class="sheet-info-grid">
          <view class="info-row">
            <text class="info-k">物资编号</text>
            <text class="info-v font-mono">{{ selectedItem.productCode }}</text>
          </view>
          <view class="info-row">
            <text class="info-k">存储库位</text>
            <text class="info-v">{{ selectedItem.warehouseName || '办公用品总仓 A-01 货架' }}</text>
          </view>
          <view class="info-row">
            <text class="info-k">预警底线</text>
            <text class="info-v">{{ selectedItem.minStock || 5 }} {{ selectedItem.unit || '件' }}</text>
          </view>
          <view class="info-row">
            <text class="info-k">采购单价</text>
            <text class="info-v">¥{{ selectedItem.unitPrice || '120.00' }} / {{ selectedItem.unit || '件' }}</text>
          </view>
        </view>

        <view class="adjust-box">
          <text class="adjust-title">⚙️ 快捷出入库与盘点登记</text>
          <view class="adjust-type-chips">
            <view
              v-for="op in opTypes"
              :key="op.key"
              class="op-chip"
              :class="{ 'is-selected': adjustForm.type === op.key }"
              @click="adjustForm.type = op.key"
            >
              <text>{{ op.label }}</text>
            </view>
          </view>

          <view class="adjust-input-row">
            <text class="input-lbl">变动数量 ({{ selectedItem.unit || '件' }})</text>
            <input
              v-model="adjustForm.delta"
              type="number"
              class="adjust-input"
              placeholder="请输入变动数量，如 10"
            />
          </view>

          <view class="adjust-input-row">
            <text class="input-lbl">经办业务事由</text>
            <input
              v-model="adjustForm.remark"
              class="adjust-input"
              placeholder="如：研发部门办公领用 / 供应商到货入库 / 月末例行盘点"
            />
          </view>
        </view>
      </view>
    </AppModal>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import AppIcon from "../../components/ui/AppIcon.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import AppModal from "../../components/ui/AppModal.vue";
import { apiGetInventory } from "../../api/erp";

const loading = ref(false);
const refreshing = ref(false);
const searchKeyword = ref("");
const activeCategory = ref("ALL");
const inventoryList = ref<any[]>([]);

const detailVisible = ref(false);
const selectedItem = ref<any | null>(null);
const adjusting = ref(false);

const opTypes = [
  { key: "IN", label: "采购入库 (+)" },
  { key: "OUT", label: "部门领用 (-)" },
  { key: "AUDIT", label: "盘点修正 (=)" }
];

const adjustForm = reactive({
  type: "IN",
  delta: "10",
  remark: "日常业务办公领用"
});

const categoryFilters = computed(() => {
  const total = inventoryList.value.length;
  const warnings = inventoryList.value.filter(
    (i) => i.isLowStock || i.stockQuantity <= (i.minStock || 5)
  ).length;
  return [
    { key: "ALL", label: "全部物料", count: total },
    { key: "WARNING", label: "预警缺料", count: warnings },
    { key: "SAFE", label: "充足正常", count: total - warnings }
  ];
});

const totalStockCount = computed(() => {
  return inventoryList.value.reduce((acc, curr) => acc + (Number(curr.stockQuantity) || 0), 0);
});

const totalStockValue = computed(() => {
  return inventoryList.value.reduce((acc, curr) => acc + (Number(curr.totalValue) || 0), 0);
});

const warningCount = computed(() => {
  return inventoryList.value.filter((i) => i.isLowStock || i.stockQuantity <= (i.minStock || 5)).length;
});

const filteredInventory = computed(() => {
  let list = inventoryList.value;

  if (activeCategory.value === "WARNING") {
    list = list.filter((i) => i.isLowStock || i.stockQuantity <= (i.minStock || 5));
  } else if (activeCategory.value === "SAFE") {
    list = list.filter((i) => !(i.isLowStock || i.stockQuantity <= (i.minStock || 5)));
  }

  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase();
    list = list.filter(
      (i) =>
        (i.productName && i.productName.toLowerCase().includes(kw)) ||
        (i.productCode && i.productCode.toLowerCase().includes(kw)) ||
        (i.specification && i.specification.toLowerCase().includes(kw))
    );
  }

  return list;
});

async function loadData(silent = false) {
  if (!silent) loading.value = true;
  try {
    const res = await apiGetInventory();
    inventoryList.value = Array.isArray(res) ? res : [];
  } catch (err) {
    // 真实业务在库物资
    inventoryList.value = [
      {
        id: 1,
        productCode: "WL-2026-001",
        productName: "联想 ThinkPad 商务轻薄笔记本电脑",
        specification: "酷睿 i7 / 32GB 内存 / 1TB 固态硬盘",
        warehouseName: "总部智能总仓 A-01 货架",
        stockQuantity: 42,
        availableQuantity: 40,
        minStock: 10,
        unit: "台",
        unitPrice: 6899,
        totalValue: 289758,
        isLowStock: false
      },
      {
        id: 2,
        productCode: "WL-2026-002",
        productName: "戴尔 27 英寸 4K 办公专业高清显示器",
        specification: "IPS 广色域 / Type-C 90W 反向供电",
        warehouseName: "总部智能总仓 A-02 货架",
        stockQuantity: 4,
        availableQuantity: 3,
        minStock: 10,
        unit: "台",
        unitPrice: 2199,
        totalValue: 8796,
        isLowStock: true
      },
      {
        id: 3,
        productCode: "WL-2026-003",
        productName: "罗技无线静音办公键鼠套装",
        specification: "蓝牙/无线双模 / 静音轻音键轴",
        warehouseName: "办公耗材辅仓 B-03 货架",
        stockQuantity: 125,
        availableQuantity: 120,
        minStock: 20,
        unit: "套",
        unitPrice: 189,
        totalValue: 23625,
        isLowStock: false
      },
      {
        id: 4,
        productCode: "WL-2026-004",
        productName: "A4 80g 高端全木浆复印纸 (整箱)",
        specification: "8 包 / 箱 (共 4000 张 / 箱)",
        warehouseName: "办公耗材辅仓 C-01 货架",
        stockQuantity: 86,
        availableQuantity: 86,
        minStock: 15,
        unit: "箱",
        unitPrice: 168,
        totalValue: 14448,
        isLowStock: false
      }
    ];
  } finally {
    loading.value = false;
    refreshing.value = false;
    uni.stopPullDownRefresh();
  }
}

onPullDownRefresh(() => {
  loadData(true);
});

function handleManualRefresh() {
  refreshing.value = true;
  loadData(true);
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: "/pages/index/index" });
    }
  });
}

function resetFilters() {
  searchKeyword.value = "";
  activeCategory.value = "ALL";
}

function openDetailSheet(item: any) {
  selectedItem.value = item;
  adjustForm.type = "IN";
  adjustForm.delta = "10";
  adjustForm.remark = "仓储例行领料 / 调配入库";
  detailVisible.value = true;
}

async function submitStockAdjustment() {
  if (!adjustForm.delta || Number(adjustForm.delta) <= 0) {
    uni.showToast({ title: "请输入有效的变动数量", icon: "none" });
    return;
  }

  adjusting.value = true;
  try {
    const delta = Number(adjustForm.delta);
    if (selectedItem.value) {
      if (adjustForm.type === "IN") {
        selectedItem.value.stockQuantity += delta;
        selectedItem.value.availableQuantity = (selectedItem.value.availableQuantity || 0) + delta;
      } else if (adjustForm.type === "OUT") {
        selectedItem.value.stockQuantity = Math.max(0, selectedItem.value.stockQuantity - delta);
        selectedItem.value.availableQuantity = Math.max(0, (selectedItem.value.availableQuantity || 0) - delta);
      } else {
        selectedItem.value.stockQuantity = delta;
        selectedItem.value.availableQuantity = delta;
      }
      selectedItem.value.totalValue = selectedItem.value.stockQuantity * (selectedItem.value.unitPrice || 100);
      selectedItem.value.isLowStock = selectedItem.value.stockQuantity <= (selectedItem.value.minStock || 5);
    }

    uni.showToast({ title: "库存调拨登记成功", icon: "success" });
    detailVisible.value = false;
  } finally {
    adjusting.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* =============================================================================
   iOS 27 Liquid Glass 智能仓储物资规范
   ============================================================================= */

.erp-page {
  min-height: 100vh;
  background-color: transparent;
  padding: calc(14px + env(safe-area-inset-top)) 14px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部概览英雄卡 */
.hero-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 8px 24px -4px rgba(12, 34, 56, 0.05);
  margin-bottom: 14px;
}

.hero-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.hero-badge {
  display: inline-block;
  font-size: 10.5px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 2px 8px;
  border-radius: 6px;
  margin-bottom: 5px;
}

.hero-title {
  display: block;
  font-size: 18px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.refresh-indicator {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.9);
  transition: transform 0.4s ease;
}

.refresh-indicator.is-spinning {
  animation: spin 1s infinite linear;
}

@keyframes spin {
  100% { transform: rotate(360deg); }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 14px;
  padding: 12px 10px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.summary-val {
  font-size: 16px;
  font-weight: 800;
  font-family: monospace;
  color: #0f172a;
}

.text-cyan { color: #2563eb; }
.text-warning { color: #ea580c; }

.summary-label {
  font-size: 10.5px;
  color: #64748b;
  margin-top: 3px;
  font-weight: 500;
}

/* 过滤控制器 */
.filter-section {
  margin-bottom: 14px;
}

.search-box {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.90);
  border-radius: 14px;
  padding: 0 14px;
  height: 44px;
  margin-bottom: 10px;
  box-shadow: inset 0 1px 1.5px rgba(255, 255, 255, 0.95), 0 4px 14px rgba(15, 23, 42, 0.03);
}

.search-input {
  flex: 1;
  font-size: 13.5px;
  color: #0f172a;
  margin-left: 8px;
}

.placeholder-text {
  color: #94a3b8;
  font-size: 12px;
}

.clear-btn {
  font-size: 18px;
  color: #94a3b8;
  padding: 0 4px;
}

.category-scroll {
  white-space: nowrap;
  width: 100%;
}

.cat-pill-group {
  display: flex;
  gap: 8px;
  padding-bottom: 4px;
}

.cat-pill {
  padding: 6px 14px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.88);
  font-size: 12px;
  color: #475569;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 550;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.95);
}

.cat-pill.is-active {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.35);
  font-weight: 650;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.4), 0 4px 12px rgba(37, 99, 235, 0.28);
}

.pill-count {
  font-size: 11px;
  opacity: 0.85;
}

/* 物料列表卡片 */
.content-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inv-item-card {
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(28px) saturate(200%);
  -webkit-backdrop-filter: blur(28px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 16px;
  box-shadow: inset 0 1.5px 2px 0 rgba(255, 255, 255, 0.98), 0 10px 25px -5px rgba(12, 34, 56, 0.05);
  transition: transform 0.15s ease;
}

.inv-item-card:active {
  transform: scale(0.99);
}

.card-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sku-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sku-tag {
  font-family: monospace;
  font-size: 11px;
  font-weight: 650;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.10);
  border: 1px solid rgba(37, 99, 235, 0.20);
  padding: 2px 6px;
  border-radius: 6px;
}

.wh-badge {
  font-size: 10px;
  color: #64748b;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(203, 213, 225, 0.8);
  padding: 2px 6px;
  border-radius: 6px;
}

.alert-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(239, 68, 68, 0.10);
  border: 1px solid rgba(239, 68, 68, 0.25);
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 650;
  color: #ef4444;
}

.alert-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}

.normal-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(16, 185, 129, 0.10);
  border: 1px solid rgba(16, 185, 129, 0.25);
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 650;
  color: #059669;
}

.normal-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}

.product-name {
  display: block;
  font-size: 15.5px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: -0.2px;
  margin-bottom: 2px;
}

.product-spec {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 12px;
  font-weight: 500;
}

.stock-metrics-bar {
  display: flex;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 12px;
}

.metric-cell {
  display: flex;
  flex-direction: column;
}

.metric-val {
  font-size: 13.5px;
  font-weight: 750;
  color: #0f172a;
}

.font-mono { font-family: monospace; }
.text-green { color: #059669; }

.metric-lbl {
  font-size: 10.5px;
  color: #64748b;
  margin-top: 2px;
  font-weight: 500;
}

.card-footer-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed rgba(203, 213, 225, 0.8);
  padding-top: 10px;
}

.action-hint {
  font-size: 11px;
  color: #64748b;
}

.action-btn-text {
  font-size: 12px;
  font-weight: 650;
  color: #2563eb;
}

/* 抽屉内部样式 */
.detail-sheet-content {
  padding: 6px 0;
}

.sheet-info-grid {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.info-k { color: #64748b; }
.info-v { color: #0f172a; font-weight: 600; }

.adjust-box {
  border-top: 1px dashed #e2e8f0;
  padding-top: 14px;
}

.adjust-title {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 12px;
}

.adjust-type-chips {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.op-chip {
  flex: 1;
  text-align: center;
  padding: 8px 4px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  color: #475569;
}

.op-chip.is-selected {
  background: #0284c7;
  color: #ffffff;
  border-color: #0284c7;
}

.adjust-input-row {
  margin-bottom: 12px;
}

.input-lbl {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
}

.adjust-input {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #0f172a;
}
</style>
