<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  CircleCheck,
  DocumentAdd,
  Download,
  Plus,
  RefreshRight,
  Search,
  WarningFilled
} from "@element-plus/icons-vue";
import ControlSurface from "@/components/platform/ControlSurface.vue";
import ExportDialog from "@/components/platform/ExportDialog.vue";
import ModuleWorkbench from "@/components/platform/ModuleWorkbench.vue";
import ProTable, { type ProTableColumn } from "@/components/pro/ProTable.vue";
import type { DimensionAxisConfig } from "@/utils/dimension-slice";
import {
  createErpPurchaseOrder,
  deleteErpProduct,
  fetchErpInventory,
  fetchErpInventoryRecords,
  fetchErpProducts,
  fetchErpPurchaseOrders,
  saveErpProduct,
  stockInPurchaseOrder
} from "@/api/modules/erp";
import type {
  ErpInventoryRecordRow,
  ErpInventoryRow,
  ErpProductRow,
  ErpPurchaseOrderRow
} from "@/api/modules/erp";

type ErpSectionKey = "inventory" | "products" | "purchase" | "records";

const erpSectionItems: Array<{ key: ErpSectionKey; label: string; description: string }> = [
  { key: "inventory", label: "实时库存看板", description: "多仓实时可用库存、安全预警线与资产估值监控。" },
  { key: "products", label: "物料商品档案", description: "统一维护商品物料编码、分类规格与采销指导价。" },
  { key: "purchase", label: "采购履约管理", description: "采购订单下单、在途履约与一键入库核销台账。" },
  { key: "records", label: "出入库流水台账", description: "采购入库、销售出库及调拨操作全链路审计流水。" }
];

const route = useRoute();
const router = useRouter();

function parseErpTabToken(raw: unknown): ErpSectionKey | null {
  const value = String(raw || "").trim().toLowerCase();
  if (value === "product" || value === "products") return "products";
  if (value === "purchase" || value === "purchases") return "purchase";
  if (value === "record" || value === "records") return "records";
  if (value === "inventory") return "inventory";
  return null;
}

function resolveCurrentErpTab(): ErpSectionKey {
  const paramTab = Array.isArray(route.params.tab) ? route.params.tab[0] : route.params.tab;
  const fromParam = parseErpTabToken(paramTab);
  if (fromParam) return fromParam;

  const sectionParam = Array.isArray(route.params.section) ? route.params.section[0] : route.params.section;
  const fromSection = parseErpTabToken(sectionParam);
  if (fromSection) return fromSection;

  const pathSeg = route.path.replace(/^\/erp\/?/, "").split("/")[0];
  const fromPath = parseErpTabToken(pathSeg);
  if (fromPath) return fromPath;

  const fromQuery = parseErpTabToken(route.query.tab);
  if (fromQuery) return fromQuery;

  return "inventory";
}

const activeTab = ref<ErpSectionKey>(resolveCurrentErpTab());
const loading = ref(false);
const erpExportVisible = ref(false);

watch(
  () => route.fullPath,
  () => {
    const resolved = resolveCurrentErpTab();
    if (resolved !== activeTab.value) {
      activeTab.value = resolved;
    }
  },
  { immediate: true }
);

const activeSectionMeta = computed(
  () => erpSectionItems.find((item) => item.key === activeTab.value) ?? erpSectionItems[0]
);

const erpExportSource = computed(() => {
  if (activeTab.value === "products") return "erp_product";
  if (activeTab.value === "purchase") return "erp_purchase_order";
  return "erp_inventory";
});

const erpExportTitle = computed(() => {
  if (activeTab.value === "products") return "ERP 商品物料档案导出";
  if (activeTab.value === "purchase") return "ERP 采购订单履约台账导出";
  if (activeTab.value === "records") return "ERP 出入库台账流水导出";
  return "ERP 实时仓库库存明细导出";
});

// 1. Products
const products = ref<ErpProductRow[]>([]);
const productSearch = ref("");
const productDialogVisible = ref(false);
const productForm = reactive({
  productCode: "",
  productName: "",
  category: "IT设施",
  specification: "",
  unit: "台",
  purchasePrice: 0,
  salePrice: 0,
  minStock: 10,
  status: "ENABLED",
  remark: ""
});

// 2. Inventory
const inventory = ref<ErpInventoryRow[]>([]);
const inventorySearch = ref("");

// 3. Purchase Orders
const purchaseOrders = ref<ErpPurchaseOrderRow[]>([]);
const purchaseDialogVisible = ref(false);
const purchaseForm = reactive({
  supplierId: 1,
  supplierName: "华为数字能源技术有限公司",
  totalAmount: 48500,
  remark: "例行高压动力模块采购批次"
});

// 4. Inventory Records
const records = ref<ErpInventoryRecordRow[]>([]);

const erpRecordAdHocRows = computed(() =>
  activeTab.value === "records" ? (records.value as unknown as Array<Record<string, unknown>>) : []
);

const filteredProducts = computed(() => {
  if (!productSearch.value.trim()) return products.value;
  const kw = productSearch.value.toLowerCase();
  return products.value.filter(
    (p) =>
      p.productCode.toLowerCase().includes(kw) ||
      p.productName.toLowerCase().includes(kw) ||
      p.category.toLowerCase().includes(kw)
  );
});

const filteredInventory = computed(() => {
  if (!inventorySearch.value.trim()) return inventory.value;
  const kw = inventorySearch.value.toLowerCase();
  return inventory.value.filter(
    (i) =>
      i.productCode.toLowerCase().includes(kw) ||
      i.productName.toLowerCase().includes(kw) ||
      i.warehouseName.toLowerCase().includes(kw)
  );
});

const lowStockCount = computed(
  () => inventory.value.filter((item) => item.isLowStock).length
);

const pendingPurchaseCount = computed(
  () => purchaseOrders.value.filter((item) => item.status === "PENDING").length
);

const inventoryTotalValue = computed(() =>
  inventory.value.reduce((acc, item) => acc + Number(item.totalValue || 0), 0)
);

const erpSummary = computed(() => [
  { label: "实时在库物料", value: inventory.value.length },
  { label: "低库存预警项", value: lowStockCount.value },
  { label: "待入库采购单", value: pendingPurchaseCount.value },
  { label: "实时库存总估值", value: `¥ ${inventoryTotalValue.value.toLocaleString()}` }
]);

// Columns & 5D Slice Dimensions
const inventoryColumns: ProTableColumn[] = [
  { prop: "productCode", label: "物料编码", width: 128 },
  { prop: "productName", label: "物料全称", minWidth: 180, slot: "productName" },
  { prop: "warehouseName", label: "所属仓库", width: 156 },
  { prop: "stockQuantity", label: "当前总在库", width: 112, align: "center", numeric: true, slot: "stockQuantity" },
  { prop: "lockedQuantity", label: "锁定库存", width: 96, align: "center", numeric: true },
  { prop: "availableQuantity", label: "可用库存", width: 100, align: "center", numeric: true, slot: "availableQuantity" },
  { prop: "health", label: "库存健康度", width: 130, align: "center", slot: "health" },
  { prop: "totalValue", label: "库存估值", width: 132, numeric: true, slot: "totalValue" }
];

const inventoryDimensions: DimensionAxisConfig[] = [
  { key: "warehouseName", label: "所属仓库" },
  {
    key: "healthStatus",
    label: "库存健康度",
    accessor: (row) => (row.isLowStock ? "LOW" : "OK"),
    options: [
      { label: "低于预警线", value: "LOW" },
      { label: "库存充足", value: "OK" }
    ]
  }
];

const productColumns: ProTableColumn[] = [
  { prop: "productCode", label: "物料编码", width: 150 },
  { prop: "productName", label: "物料名称", minWidth: 180 },
  { prop: "category", label: "分类", width: 120 },
  { prop: "specification", label: "规格型号", minWidth: 150 },
  { prop: "unit", label: "单位", width: 72, align: "center" },
  { prop: "purchasePrice", label: "采购指导价", width: 124, numeric: true, slot: "purchasePrice" },
  { prop: "salePrice", label: "指导售价", width: 124, numeric: true, slot: "salePrice" },
  { prop: "minStock", label: "安全警戒值", width: 106, align: "center", numeric: true },
  { prop: "actions", label: "操作", width: 96, align: "center", slot: "actions" }
];

const productDimensions: DimensionAxisConfig[] = [
  { key: "category", label: "物料分类" },
  { key: "unit", label: "计量单位" }
];

const purchaseColumns: ProTableColumn[] = [
  { prop: "orderNo", label: "采购单号", width: 180 },
  { prop: "supplierName", label: "供货单位", minWidth: 180 },
  { prop: "totalAmount", label: "订单标的总额", width: 140, numeric: true, slot: "totalAmount" },
  { prop: "status", label: "履约状态", width: 126, align: "center", slot: "status" },
  { prop: "remark", label: "采购说明", minWidth: 160 },
  { prop: "createdAt", label: "制单时间", width: 170 },
  { prop: "actions", label: "操作", width: 126, align: "center", slot: "actions" }
];

const purchaseDimensions: DimensionAxisConfig[] = [
  {
    key: "status",
    label: "履约状态",
    options: [
      { label: "待入库验收", value: "PENDING" },
      { label: "已入库完成", value: "IN_STORAGE" }
    ]
  },
  { key: "supplierName", label: "供货单位" }
];

const recordColumns: ProTableColumn[] = [
  { prop: "recordNo", label: "流水单号", width: 180 },
  { prop: "recordType", label: "业务类型", width: 116, align: "center", slot: "recordType" },
  { prop: "productName", label: "物料名称", minWidth: 170 },
  { prop: "warehouseName", label: "执行仓库", width: 156 },
  { prop: "beforeQuantity", label: "变动前", width: 92, align: "center", numeric: true },
  { prop: "changeQuantity", label: "变动数量", width: 100, align: "center", numeric: true, slot: "changeQuantity" },
  { prop: "afterQuantity", label: "变动后", width: 92, align: "center", numeric: true },
  { prop: "operatorName", label: "操作人", width: 110 },
  { prop: "remark", label: "操作备注", minWidth: 160 },
  { prop: "createdAt", label: "记录时间", width: 170 }
];

const recordDimensions: DimensionAxisConfig[] = [
  {
    key: "recordType",
    label: "业务类型",
    options: [
      { label: "采购入库", value: "PURCHASE_IN" },
      { label: "销售出库", value: "SALE_OUT" },
      { label: "调拨调整", value: "TRANSFER" }
    ]
  },
  { key: "warehouseName", label: "执行仓库" }
];

function getErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "message" in err && typeof (err as { message?: unknown }).message === "string") {
    return (err as { message: string }).message || fallback;
  }
  return fallback;
}

async function loadAll() {
  loading.value = true;
  try {
    const [prods, invs, pos, recs] = await Promise.all([
      fetchErpProducts(),
      fetchErpInventory(),
      fetchErpPurchaseOrders(),
      fetchErpInventoryRecords()
    ]);
    products.value = prods;
    inventory.value = invs;
    purchaseOrders.value = pos;
    records.value = recs;
  } catch (err: unknown) {
    ElMessage.error(getErrorMessage(err, "加载 ERP 数据失败"));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadAll();
});

function openAddProductDialog() {
  productForm.productCode = `PRD-${Date.now().toString(36).toUpperCase()}`;
  productForm.productName = "";
  productForm.category = "IT设施";
  productForm.specification = "标准工业级规格";
  productForm.unit = "台";
  productForm.purchasePrice = 1200;
  productForm.salePrice = 1800;
  productForm.minStock = 10;
  productForm.status = "ENABLED";
  productForm.remark = "新增供应链物料";
  productDialogVisible.value = true;
}

async function handleSaveProduct() {
  if (!productForm.productCode || !productForm.productName) {
    ElMessage.warning("请填写物料编码与名称");
    return;
  }
  try {
    await saveErpProduct(productForm);
    ElMessage.success("物料商品已成功录入，并完成库存初始化！");
    productDialogVisible.value = false;
    await loadAll();
  } catch (err: unknown) {
    ElMessage.error(getErrorMessage(err, "保存物料失败"));
  }
}

async function handleDeleteProduct(id: number) {
  try {
    await ElMessageBox.confirm("确定删除该物料档案吗？", "删除确认", { type: "warning" });
    await deleteErpProduct(id);
    ElMessage.success("物料已安全删除");
    await loadAll();
  } catch (err: unknown) {
    if (err !== "cancel" && err !== "close") {
      ElMessage.error(getErrorMessage(err, "删除物料失败"));
    }
  }
}

function openAddPurchaseDialog() {
  purchaseForm.supplierId = 1;
  purchaseForm.supplierName = "华为数字能源技术有限公司";
  purchaseForm.totalAmount = 56000;
  purchaseForm.remark = "第四季度动力储备物料采购";
  purchaseDialogVisible.value = true;
}

async function handleSavePurchase() {
  try {
    await createErpPurchaseOrder(purchaseForm);
    ElMessage.success("采购订单已生成！");
    purchaseDialogVisible.value = false;
    await loadAll();
  } catch (err: unknown) {
    ElMessage.error(getErrorMessage(err, "创建采购单失败"));
  }
}

async function handleStockIn(order: ErpPurchaseOrderRow) {
  try {
    await ElMessageBox.confirm(
      `确认对采购订单【${order.orderNo}】执行一键核销入库吗？系统将自动扣减在途并增加总仓库存。`,
      "入库核销确认",
      { type: "info" }
    );
    await stockInPurchaseOrder(order.id, 1);
    ElMessage.success("采购订单入库成功！已自动更新库存并生成台账流水。");
    await loadAll();
  } catch (err: unknown) {
    if (err !== "cancel" && err !== "close") {
      ElMessage.error(getErrorMessage(err, "入库核销失败"));
    }
  }
}
</script>

<template>
  <div class="erp-container">
    <ControlSurface
      badge="ERP"
      :title="activeSectionMeta.label"
      :description="activeSectionMeta.description"
    >
      <template #actions>
        <el-button v-if="activeTab !== 'records'" @click="router.push('/erp/records')">出入库流水台账</el-button>
        <el-button v-else @click="router.push('/erp/inventory')">返回实时库存</el-button>
        <el-button :icon="Download" @click="erpExportVisible = true">安全导出</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadAll">刷新数据</el-button>
        <el-button
          v-if="activeTab === 'products'"
          type="primary"
          :icon="Plus"
          @click="openAddProductDialog"
        >
          新增物料档案
        </el-button>
        <el-button
          v-else-if="activeTab === 'purchase'"
          type="primary"
          :icon="DocumentAdd"
          @click="openAddPurchaseDialog"
        >
          新建采购订单
        </el-button>
      </template>

      <div class="module-section-stack">
        <ModuleWorkbench
          eyebrow="ERP"
          :title="activeSectionMeta.label"
          :description="activeSectionMeta.description"
          :summary="erpSummary"
        >
          <!-- 1. 实时多仓库存看板 -->
          <ProTable
            v-if="activeTab === 'inventory'"
            title="多仓实时库存明细"
            row-key="id"
            :columns="inventoryColumns"
            :data="filteredInventory"
            :loading="loading"
            :dimensions="inventoryDimensions"
            dimension-storage-key="erp-inventory"
            empty-title="暂无库存记录"
            empty-description="请调整多维切片或关键词筛选。"
          >
            <template #search>
              <el-input
                v-model="inventorySearch"
                placeholder="搜索物料编码、物料名称、仓库..."
                :prefix-icon="Search"
                clearable
                style="width: 320px;"
              />
            </template>
            <template #productName="{ row }">
              <strong>{{ row.productName }}</strong>
              <div class="text-muted">{{ row.specification }}</div>
            </template>
            <template #stockQuantity="{ row }">
              <span class="stock-badge">{{ row.stockQuantity }} {{ row.unit }}</span>
            </template>
            <template #availableQuantity="{ row }">
              <strong class="text-success">{{ row.availableQuantity }}</strong>
            </template>
            <template #health="{ row }">
              <el-tag v-if="row.isLowStock" type="danger" size="small" :icon="WarningFilled">
                低于预警线 ({{ row.minStock }})
              </el-tag>
              <el-tag v-else type="success" size="small" :icon="CircleCheck">充足</el-tag>
            </template>
            <template #totalValue="{ row }">
              ¥ {{ (row.totalValue || 0).toLocaleString() }}
            </template>
          </ProTable>

          <!-- 2. 商品物料档案 -->
          <ProTable
            v-else-if="activeTab === 'products'"
            title="物料商品主档案"
            row-key="id"
            :columns="productColumns"
            :data="filteredProducts"
            :loading="loading"
            :dimensions="productDimensions"
            dimension-storage-key="erp-products"
          >
            <template #search>
              <el-input
                v-model="productSearch"
                placeholder="搜索物料编码、名称、分类..."
                :prefix-icon="Search"
                clearable
                style="width: 320px;"
              />
            </template>
            <template #purchasePrice="{ row }">¥ {{ row.purchasePrice?.toFixed(2) }}</template>
            <template #salePrice="{ row }">¥ {{ row.salePrice?.toFixed(2) }}</template>
            <template #actions="{ row }">
              <el-button link type="danger" @click="handleDeleteProduct(row.id)">删除</el-button>
            </template>
          </ProTable>

          <!-- 3. 采购履约管理 -->
          <ProTable
            v-else-if="activeTab === 'purchase'"
            title="采购订单履约台账"
            row-key="id"
            :columns="purchaseColumns"
            :data="purchaseOrders"
            :loading="loading"
            :dimensions="purchaseDimensions"
            dimension-storage-key="erp-purchase"
          >
            <template #totalAmount="{ row }">¥ {{ row.totalAmount?.toLocaleString() }}</template>
            <template #status="{ row }">
              <el-tag v-if="row.status === 'IN_STORAGE'" type="success">已入库完成</el-tag>
              <el-tag v-else type="warning">待入库验收</el-tag>
            </template>
            <template #actions="{ row }">
              <el-button
                v-if="row.status === 'PENDING'"
                type="primary"
                size="small"
                @click="handleStockIn(row)"
              >
                一键入库
              </el-button>
              <span v-else class="text-muted">已核销</span>
            </template>
          </ProTable>

          <!-- 4. 出入库台账审计 -->
          <ProTable
            v-else
            title="出入库流水审计台账"
            row-key="id"
            :columns="recordColumns"
            :data="records"
            :loading="loading"
            :dimensions="recordDimensions"
            dimension-storage-key="erp-records"
          >
            <template #recordType="{ row }">
              <el-tag v-if="row.recordType === 'PURCHASE_IN'" type="success">采购入库</el-tag>
              <el-tag v-else-if="row.recordType === 'SALE_OUT'" type="warning">销售出库</el-tag>
              <el-tag v-else type="info">调拨调整</el-tag>
            </template>
            <template #changeQuantity="{ row }">
              <strong :class="row.changeQuantity > 0 ? 'text-success' : 'text-danger'">
                {{ row.changeQuantity > 0 ? `+${row.changeQuantity}` : row.changeQuantity }}
              </strong>
            </template>
          </ProTable>
        </ModuleWorkbench>
      </div>
    </ControlSurface>

    <!-- 新建物料 Dialog -->
    <el-dialog v-model="productDialogVisible" title="录入物料商品档案" width="560px">
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="物料编码" required>
              <el-input v-model="productForm.productCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" required>
              <el-input v-model="productForm.productName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="物料分类">
              <el-input v-model="productForm.category" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计量单位">
              <el-input v-model="productForm.unit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规格型号说明">
          <el-input v-model="productForm.specification" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="采购指导价">
              <el-input-number v-model="productForm.purchasePrice" :min="0" :precision="2" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售指导价">
              <el-input-number v-model="productForm.salePrice" :min="0" :precision="2" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安全预警值">
              <el-input-number v-model="productForm.minStock" :min="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注说明">
          <el-input v-model="productForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveProduct">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 新建采购订单 Dialog -->
    <el-dialog v-model="purchaseDialogVisible" title="创建供应链采购订单" width="500px">
      <el-form label-position="top">
        <el-form-item label="供货单位" required>
          <el-input v-model="purchaseForm.supplierName" />
        </el-form-item>
        <el-form-item label="订单预估标的额" required>
          <el-input-number v-model="purchaseForm.totalAmount" :min="1" :precision="2" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="采购事由与说明">
          <el-input v-model="purchaseForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="purchaseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePurchase">确认创建</el-button>
      </template>
    </el-dialog>

    <ExportDialog
      v-model:visible="erpExportVisible"
      :source-code="activeTab === 'records' ? '' : erpExportSource"
      :default-title="erpExportTitle"
      :ad-hoc-rows="erpRecordAdHocRows"
    />
  </div>
</template>

<style scoped>
.erp-container {
  padding: 0 0 16px;
  background: transparent;
  box-sizing: border-box;
}

.stock-badge {
  font-family: var(--sb-font-mono);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.text-muted {
  font-size: 12px;
  color: var(--sb-text-muted);
}

.text-success {
  color: var(--sb-success-color);
}

.text-danger {
  color: var(--sb-danger-color);
}
</style>
