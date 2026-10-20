<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Box,
  CircleCheck,
  DocumentAdd,
  Goods,
  Plus,
  RefreshRight,
  ShoppingCart,
  WarningFilled
} from "@element-plus/icons-vue";
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

const activeTab = ref<"products" | "inventory" | "purchase" | "records">("inventory");
const loading = ref(false);

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

// Filtered lists
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
  } catch (err: any) {
    ElMessage.error(err?.message || "加载 ERP 数据失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadAll();
});

// Product Actions
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
    loadAll();
  } catch (err: any) {
    ElMessage.error(err?.message || "保存物料失败");
  }
}

async function handleDeleteProduct(id: number) {
  try {
    await ElMessageBox.confirm("确定删除该物料档案吗？", "删除确认", { type: "warning" });
    await deleteErpProduct(id);
    ElMessage.success("物料已安全删除");
    loadAll();
  } catch {}
}

// Purchase Actions
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
    loadAll();
  } catch (err: any) {
    ElMessage.error(err?.message || "创建采购单失败");
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
    loadAll();
  } catch {}
}
</script>

<template>
  <div class="erp-container">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="flex-between">
          <div>
            <h2 class="panel-title">企业级进销存 (ERP) 供应链中枢</h2>
            <p class="panel-subtitle">支持商品物料档案、多仓实时库存、采购订单全生命周期履约与出入库台账审计。</p>
          </div>
          <div class="flex-actions">
            <el-button :icon="RefreshRight" :loading="loading" @click="loadAll">刷新数据</el-button>
          </div>
        </div>
      </template>

      <!-- 选项卡切换 -->
      <el-tabs v-model="activeTab" class="erp-tabs">
        <!-- 1. 实时多仓库存看板 -->
        <el-tab-pane label="实时库存看板" name="inventory">
          <div class="tab-toolbar">
            <el-input
              v-model="inventorySearch"
              placeholder="搜索物料编码、物料名称、仓库..."
              prefix-icon="Search"
              clearable
              style="width: 320px;"
            />
          </div>

          <el-table :data="filteredInventory" stripe border style="width: 100%;">
            <el-table-column prop="productCode" label="物料编码" width="150" />
            <el-table-column prop="productName" label="物料全称" min-width="180">
              <template #default="{ row }">
                <strong>{{ row.productName }}</strong>
                <div class="text-muted">{{ row.specification }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="所属仓库" width="180" />
            <el-table-column prop="stockQuantity" label="当前总在库" width="110" align="center">
              <template #default="{ row }">
                <span class="stock-badge">{{ row.stockQuantity }} {{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lockedQuantity" label="锁定库存" width="100" align="center" />
            <el-table-column prop="availableQuantity" label="可用库存" width="110" align="center">
              <template #default="{ row }">
                <strong class="text-success">{{ row.availableQuantity }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="库存健康度" width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isLowStock" type="danger" size="small" :icon="WarningFilled">
                  低于预警线 ({{ row.minStock }})
                </el-tag>
                <el-tag v-else type="success" size="small" :icon="CircleCheck">充足</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalValue" label="库存估值" width="130" align="right">
              <template #default="{ row }">
                ¥ {{ (row.totalValue || 0).toLocaleString() }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 2. 商品物料档案 -->
        <el-tab-pane label="物料商品档案" name="products">
          <div class="tab-toolbar flex-between">
            <el-input
              v-model="productSearch"
              placeholder="搜索物料编码、名称、分类..."
              prefix-icon="Search"
              clearable
              style="width: 320px;"
            />
            <el-button type="primary" :icon="Plus" @click="openAddProductDialog">新增物料档案</el-button>
          </div>

          <el-table :data="filteredProducts" stripe border style="width: 100%;">
            <el-table-column prop="productCode" label="物料编码" width="150" />
            <el-table-column prop="productName" label="物料名称" min-width="180" />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="specification" label="规格型号" min-width="150" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="purchasePrice" label="采购指导价" width="120" align="right">
              <template #default="{ row }">¥ {{ row.purchasePrice?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="salePrice" label="指导售价" width="120" align="right">
              <template #default="{ row }">¥ {{ row.salePrice?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="minStock" label="安全警戒值" width="100" align="center" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button link type="danger" @click="handleDeleteProduct(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 3. 采购履约管理 -->
        <el-tab-pane label="采购履约管理" name="purchase">
          <div class="tab-toolbar flex-between">
            <span class="text-muted">已开通与工作流审批与出入库全链路联动</span>
            <el-button type="primary" :icon="DocumentAdd" @click="openAddPurchaseDialog">新建采购订单</el-button>
          </div>

          <el-table :data="purchaseOrders" stripe border style="width: 100%;">
            <el-table-column prop="orderNo" label="采购单号" width="180" />
            <el-table-column prop="supplierName" label="供货单位" min-width="180" />
            <el-table-column prop="totalAmount" label="订单标的总额" width="140" align="right">
              <template #default="{ row }">¥ {{ row.totalAmount?.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="status" label="履约状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'IN_STORAGE'" type="success">已入库完成</el-tag>
                <el-tag v-else type="warning">待入库验收</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="采购说明" min-width="160" />
            <el-table-column prop="createdAt" label="制单时间" width="180" />
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  type="primary"
                  size="small"
                  @click="handleStockIn(row)"
                >
                  一键入库
                </el-button>
                <span v-else class="text-muted">无需操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 4. 出入库台账审计 -->
        <el-tab-pane label="出入库流水台账" name="records">
          <el-table :data="records" stripe border style="width: 100%;">
            <el-table-column prop="recordNo" label="流水单号" width="180" />
            <el-table-column prop="recordType" label="业务类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.recordType === 'PURCHASE_IN'" type="success">采购入库</el-tag>
                <el-tag v-else-if="row.recordType === 'SALE_OUT'" type="warning">销售出库</el-tag>
                <el-tag v-else type="info">调拨调整</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="物料名称" min-width="180" />
            <el-table-column prop="warehouseName" label="执行仓库" width="180" />
            <el-table-column prop="beforeQuantity" label="变动前库存" width="110" align="center" />
            <el-table-column prop="changeQuantity" label="变动数量" width="110" align="center">
              <template #default="{ row }">
                <strong :class="row.changeQuantity > 0 ? 'text-success' : 'text-danger'">
                  {{ row.changeQuantity > 0 ? `+${row.changeQuantity}` : row.changeQuantity }}
                </strong>
              </template>
            </el-table-column>
            <el-table-column prop="afterQuantity" label="变动后库存" width="110" align="center" />
            <el-table-column prop="operatorName" label="操作人" width="120" />
            <el-table-column prop="remark" label="操作备注" min-width="180" />
            <el-table-column prop="createdAt" label="记录时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

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
  </div>
</template>

<style scoped>
.erp-container {
  padding: 20px;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
}

.panel-subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 4px 0 0;
}

.tab-toolbar {
  margin-bottom: 16px;
}

.stock-badge {
  font-family: monospace;
  font-weight: bold;
}

.text-muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.text-success {
  color: var(--el-color-success);
}

.text-danger {
  color: var(--el-color-danger);
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
