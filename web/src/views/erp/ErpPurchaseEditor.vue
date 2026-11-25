<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/modules/auth";
import WorkspacePanel from "@/components/platform/WorkspacePanel.vue";
import { confirmLegacyPurchaseOrder, createErpOption, createErpPurchaseOrder, fetchErpOptions } from "@/api/modules/erp";
import type { ErpMasterOption, ErpMasterType, ErpProductRow, ErpPurchaseOrderRow, ErpPurchaseRequest } from "@/api/modules/erp";

const visible = defineModel<boolean>({ required: true });
const props = defineProps<{ order?: ErpPurchaseOrderRow; products: ErpProductRow[] }>();
const emit = defineEmits<{ saved: [] }>();
const auth = useAuthStore();
const reviewing = auth.hasPermission("erp:purchase:confirm") && props.order?.status === "PENDING" && (props.order.warehouseId == null || !props.order.items.length);
const readonly = props.order != null && !reviewing;
const title = readonly ? "采购单明细" : reviewing ? "核对历史采购单" : "新建采购单";
const suppliers = ref<ErpMasterOption[]>([]);
const warehouses = ref<ErpMasterOption[]>([]);
const supplierId = ref(props.order?.supplierId);
const warehouseId = ref(props.order?.warehouseId ?? undefined);
const remark = ref(props.order?.remark ?? "");
const busy = ref(false);
const loading = ref(false);
const error = ref("");
const masterType = ref<ErpMasterType>();
const master = reactive({ code: "", name: "" });
const products = computed(() => props.products.filter(product => product.status === "ENABLED"));
interface EditingLine { key: number; productId?: number; quantity?: number; unitPrice?: number }
let nextKey = 0;
const lines = ref<EditingLine[]>((props.order?.items ?? []).map(line => ({ ...line, key: nextKey++ })));
function addLine() {
  lines.value.push({ key: nextKey++, quantity: 1 });
}
if (!readonly && !lines.value.length) addLine();

function subtotal(line: EditingLine) {
  return Math.round((line.unitPrice ?? 0) * 100) * (line.quantity ?? 0);
}
const total = computed(() => lines.value.reduce((sum, line) => sum + subtotal(line), 0) / 100);
function selectProduct(line: EditingLine) {
  line.unitPrice = products.value.find(p => p.id === line.productId)?.purchasePrice;
}
function errorText(cause: unknown) {
  return cause instanceof Error ? cause.message : "请求失败，请重试";
}
async function loadOptions() {
  loading.value = true;
  error.value = "";
  try {
    [suppliers.value, warehouses.value] = await Promise.all([fetchErpOptions("suppliers"), fetchErpOptions("warehouses")]);
  } catch (cause) {
    error.value = errorText(cause);
  } finally {
    loading.value = false;
  }
}
onMounted(() => { if (!readonly) void loadOptions(); });

function openMaster(type: ErpMasterType) {
  masterType.value = type;
  master.code = "";
  master.name = "";
}
async function saveMaster() {
  const type = masterType.value;
  if (!type || busy.value || !master.code.trim() || !master.name.trim()) return;
  busy.value = true;
  try {
    const option = await createErpOption(type, master.code.trim(), master.name.trim());
    if (type === "suppliers") {
      suppliers.value.push(option);
      supplierId.value = option.id;
    } else {
      warehouses.value.push(option);
      warehouseId.value = option.id;
    }
    masterType.value = undefined;
  } catch (cause) {
    ElMessage.error(errorText(cause));
  } finally {
    busy.value = false;
  }
}
async function save() {
  if (busy.value || masterType.value) return;
  if (!supplierId.value || !warehouseId.value || !lines.value.length) {
    ElMessage.warning("请选择供应商、收货仓库，并添加采购明细");
    return;
  }
  const items: ErpPurchaseRequest["items"] = [];
  for (const line of lines.value) {
    if (!products.value.some(p => p.id === line.productId) || !line.productId || !Number.isInteger(line.quantity) ||
        !line.quantity || line.quantity < 1 || line.unitPrice == null || !Number.isFinite(line.unitPrice) || line.unitPrice < 0) {
      ElMessage.warning("请逐行填写有效物料、数量和单价");
      return;
    }
    items.push({ productId: line.productId, quantity: line.quantity, unitPrice: line.unitPrice });
  }
  busy.value = true;
  try {
    const payload = { supplierId: supplierId.value, warehouseId: warehouseId.value, items, remark: remark.value };
    if (props.order) await confirmLegacyPurchaseOrder(props.order.id, payload);
    else await createErpPurchaseOrder(payload);
    ElMessage.success(props.order ? "采购明细已确认" : "采购单已创建");
    visible.value = false;
    emit("saved");
  } catch (cause) {
    ElMessage.error(errorText(cause));
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <WorkspacePanel v-model="visible" :title="title" width="760px">
    <template v-if="readonly && order">
      <p>{{ order.orderNo }} · {{ order.supplierName }} · {{ order.warehouseName ?? "历史仓库未记录" }}</p>
      <el-table :data="order.items" empty-text="历史单据未记录采购明细">
        <el-table-column prop="productName" label="物料" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="unitPrice" label="单价（元）" />
        <el-table-column prop="subtotal" label="小计（元）" />
      </el-table>
      <p>单据总额 ¥{{ Number(order.totalAmount).toFixed(2) }}</p>
      <p v-if="order.remark">{{ order.remark }}</p>
    </template>
    <el-form v-else label-position="top" v-loading="loading">
      <el-alert v-if="reviewing" type="warning" :closable="false" title="请按原始采购凭据核对每项物料、数量和价格，并确认实际收货仓库。" />
      <el-alert v-if="error" type="error" :closable="false" :title="error"><el-button @click="loadOptions">重试</el-button></el-alert>
      <el-form-item label="供应商" required>
        <div class="master-row">
          <el-select v-model="supplierId" filterable placeholder="选择供应商"><el-option v-for="o in suppliers" :key="o.id" :value="o.id" :label="o.name" /></el-select>
          <el-button v-if="auth.hasPermission('erp:supplier:create')" @click="openMaster('suppliers')">新增供应商</el-button>
        </div>
      </el-form-item>
      <el-form-item label="收货仓库" required>
        <div class="master-row">
          <el-select v-model="warehouseId" filterable placeholder="选择仓库"><el-option v-for="o in warehouses" :key="o.id" :value="o.id" :label="o.name" /></el-select>
          <el-button v-if="auth.hasPermission('erp:warehouse:create')" @click="openMaster('warehouses')">新增仓库</el-button>
        </div>
      </el-form-item>
      <fieldset v-if="masterType" class="master-form">
        <legend>{{ masterType === 'suppliers' ? '新增供应商' : '新增仓库' }}</legend>
        <el-input v-model="master.code" placeholder="编码" aria-label="编码" maxlength="64" />
        <el-input v-model="master.name" placeholder="名称" aria-label="名称" maxlength="128" />
        <el-button @click="masterType = undefined">取消</el-button>
        <el-button :loading="busy" :disabled="!master.code.trim() || !master.name.trim()" @click="saveMaster">保存</el-button>
      </fieldset>
      <el-form-item label="采购明细" required>
        <el-table :data="lines" row-key="key" empty-text="请添加采购物料">
          <el-table-column label="物料" min-width="160"><template #default="{ row }">
            <el-select v-model="row.productId" filterable placeholder="选择物料" @change="selectProduct(row)">
              <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.productName + ' · ' + p.productCode" />
            </el-select>
          </template></el-table-column>
          <el-table-column label="数量" width="94"><template #default="{ row }"><el-input-number v-model="row.quantity" aria-label="数量" :controls="false" :precision="0" :min="1" :max="2147483647" /></template></el-table-column>
          <el-table-column label="单价（元）" width="120"><template #default="{ row }"><el-input-number v-model="row.unitPrice" aria-label="单价" :controls="false" :precision="2" :min="0" :max="9999999999.99" /></template></el-table-column>
          <el-table-column label="小计" width="100"><template #default="{ row }">{{ (subtotal(row) / 100).toFixed(2) }}</template></el-table-column>
          <el-table-column width="60"><template #default="{ $index }"><el-button link type="danger" @click="lines.splice($index, 1)">移除</el-button></template></el-table-column>
        </el-table>
        <el-button :disabled="lines.length >= 200" @click="addLine">添加物料</el-button>
      </el-form-item>
      <p>合计 ¥{{ total.toFixed(2) }}</p>
      <el-form-item label="备注"><el-input v-model="remark" type="textarea" maxlength="500" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ readonly ? "关闭" : "取消" }}</el-button>
      <el-button v-if="!readonly" type="primary" :disabled="loading || !!error || !!masterType" :loading="busy" @click="save">{{ reviewing ? "确认明细" : "创建采购单" }}</el-button>
    </template>
  </WorkspacePanel>
</template>

<style scoped>
.master-row { display: flex; gap: 8px; width: 100%; }
.master-form { display: grid; grid-template-columns: 1fr 1fr auto auto; gap: 8px; margin: 0 0 16px; border: 1px solid var(--el-border-color); }
.el-input-number { width: 100%; }
</style>
