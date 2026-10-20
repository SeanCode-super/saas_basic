<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Document, Printer, Refresh, Stamp, Tickets } from "@element-plus/icons-vue";

type TemplateType = "PURCHASE_IN" | "REIMBURSE" | "SHIPPING_LABEL";

const selectedTemplate = ref<TemplateType>("PURCHASE_IN");
const showWatermark = ref(true);
const showOfficialStamp = ref(true);
const paperSize = ref<"A4" | "A5">("A4");

// 模拟表单数据
const purchaseData = ref({
  orderNo: "PO-20261019-8802",
  supplierName: "华为数字能源技术有限公司",
  warehouseName: "华东一号中心立体总仓",
  inDate: "2026-10-19",
  inspector: "李建国",
  manager: "张明华",
  items: [
    { code: "HW-PWR-01", name: "工业级高频UPS电源模块 50kVA", spec: "50kVA/380V 三相", unit: "台", qty: 4, price: 18500.00, amount: 74000.00 },
    { code: "HW-CAB-08", name: "微模块一体化服务器机柜", spec: "42U 600*1200mm", unit: "套", qty: 8, price: 6200.00, amount: 49600.00 },
    { code: "HW-BAT-20", name: "磷酸铁锂高压储能电池组", spec: "51.2V 100Ah", unit: "组", qty: 16, price: 4200.00, amount: 67200.00 }
  ]
});

const reimburseData = ref({
  docNo: "EXP-20261019-3011",
  applicant: "平台管理员",
  dept: "数字研发部",
  applyDate: "2026-10-19",
  reason: "Q3 华东大客户私有化部署实施差旅与现场验收",
  totalAmount: 9850.00,
  items: [
    { category: "城际交通", desc: "高铁车票 (上海虹桥 - 深圳北)", qty: 2, amount: 1780.00, invoiceCount: 2 },
    { category: "住宿费", desc: "深圳维景国际大酒店 4晚", qty: 4, amount: 3200.00, invoiceCount: 1 },
    { category: "业务招待", desc: "实施验收商务工作餐", qty: 1, amount: 2680.00, invoiceCount: 1 },
    { category: "市内交通", desc: "现场往返出租车合规发票", qty: 6, amount: 2190.00, invoiceCount: 6 }
  ]
});

const shippingData = ref({
  trackingNo: "SF1892837492801",
  sender: "Antigravity 智能制造总仓 (李经理 13800138000)",
  senderAddr: "江苏省苏州市工业园区金鸡湖大道 88 号高新智造谷",
  receiver: "比亚迪汽车工业有限公司 (王主管 13911122233)",
  receiverAddr: "广东省深圳市坪山区比亚迪路 3009 号研发大楼 B 栋",
  productName: "精密智控网关主板及配套高精度传感器套件",
  weight: "4.8 kg",
  pieces: 1
});

const totalPurchaseAmount = computed(() => {
  return purchaseData.value.items.reduce((sum, item) => sum + item.amount, 0);
});

function handlePrint() {
  window.print();
}
</script>

<template>
  <div class="print-center-container">
    <!-- 顶部非打印控制台卡片 -->
    <div class="no-print">
      <el-card shadow="never" class="mb-4">
        <template #header>
          <div class="flex-between">
            <div>
              <h3 class="panel-title">企业级高保真套打与报表中心</h3>
              <p class="panel-subtitle">支持中国式复杂单据（采购入库单、报销凭证、仓储面单）的高保真套打、条形码打印与印章渲染。</p>
            </div>
            <div class="flex-actions">
              <el-button type="primary" :icon="Printer" size="large" @click="handlePrint">
                立即唤起浏览器套打 (Print)
              </el-button>
            </div>
          </div>
        </template>

        <div class="template-selector-bar">
          <div class="ctrl-group">
            <span class="ctrl-label">单据模板切换：</span>
            <el-radio-group v-model="selectedTemplate">
              <el-radio-button value="PURCHASE_IN">采购入库验收单</el-radio-button>
              <el-radio-button value="REIMBURSE">费用报销凭证单</el-radio-button>
              <el-radio-button value="SHIPPING_LABEL">仓储快递发货面单</el-radio-button>
            </el-radio-group>
          </div>

          <div class="ctrl-group">
            <span class="ctrl-label">打印样式控制：</span>
            <el-checkbox v-model="showWatermark">防伪安全水印</el-checkbox>
            <el-checkbox v-model="showOfficialStamp">电子业务公章</el-checkbox>
            <el-radio-group v-model="paperSize" size="small" style="margin-left: 12px;">
              <el-radio-button value="A4">A4 纸张</el-radio-button>
              <el-radio-button value="A5">A5 票据</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 打印预览纸张视图 (该区域响应 window.print()) -->
    <div class="print-sheet-wrapper">
      <!-- 1. 采购入库验收单模板 -->
      <div v-if="selectedTemplate === 'PURCHASE_IN'" class="print-paper" :class="`paper-${paperSize.toLowerCase()}`">
        <!-- 防伪水印 -->
        <div v-if="showWatermark" class="watermark-text">ANTIGRAVITY ORIGINAL DOC</div>

        <div class="sheet-header">
          <div class="company-name">ANTIGRAVITY ENTERPRISE CLOUD FOUNDATION</div>
          <h2 class="sheet-title">物料采购入库综合验收凭证</h2>
          <div class="barcode-box">
            <div class="sim-barcode">|| | ||| | |||| || ||| || |||| | |||</div>
            <span class="barcode-no">{{ purchaseData.orderNo }}</span>
          </div>
        </div>

        <div class="sheet-meta-grid">
          <div><strong>供货单位：</strong>{{ purchaseData.supplierName }}</div>
          <div><strong>验收仓库：</strong>{{ purchaseData.warehouseName }}</div>
          <div><strong>入库日期：</strong>{{ purchaseData.inDate }}</div>
          <div><strong>单据类别：</strong>标准供应链采购入库</div>
        </div>

        <table class="sheet-table">
          <thead>
            <tr>
              <th width="40">序号</th>
              <th width="120">物料编码</th>
              <th>物料名称</th>
              <th width="140">规格型号</th>
              <th width="50">单位</th>
              <th width="60">实收数</th>
              <th width="80">单价(元)</th>
              <th width="100">金额(元)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in purchaseData.items" :key="idx">
              <td class="text-center">{{ idx + 1 }}</td>
              <td>{{ item.code }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.spec }}</td>
              <td class="text-center">{{ item.unit }}</td>
              <td class="text-center font-bold">{{ item.qty }}</td>
              <td class="text-right">¥ {{ item.price.toFixed(2) }}</td>
              <td class="text-right font-bold">¥ {{ item.amount.toFixed(2) }}</td>
            </tr>
            <tr class="table-total-row">
              <td colspan="7" class="text-right"><strong>合计金额（大写：人民币壹拾玖万零捌佰元整）：</strong></td>
              <td class="text-right font-bold text-red">¥ {{ totalPurchaseAmount.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>

        <!-- 印章与签名栏 -->
        <div class="sheet-footer">
          <div class="sign-block">
            <span>仓储验收人：{{ purchaseData.inspector }}</span>
            <span>库房主管：{{ purchaseData.manager }}</span>
            <span>经手制单：系统管理员</span>
          </div>
          <!-- 电子印章 -->
          <div v-if="showOfficialStamp" class="official-stamp-seal">
            <div class="seal-inner">
              <span class="seal-star">★</span>
              <span class="seal-text">供应链物料验收专用章</span>
              <span class="seal-date">2026.10.19</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. 费用报销凭证模板 -->
      <div v-else-if="selectedTemplate === 'REIMBURSE'" class="print-paper" :class="`paper-${paperSize.toLowerCase()}`">
        <div v-if="showWatermark" class="watermark-text">ANTIGRAVITY ORIGINAL DOC</div>
        <div class="sheet-header">
          <div class="company-name">ANTIGRAVITY ENTERPRISE CLOUD FOUNDATION</div>
          <h2 class="sheet-title">企业通用支出与费用报销单</h2>
          <div class="qrcode-box">
            <span class="qrcode-icon">▦</span>
            <span class="barcode-no">{{ reimburseData.docNo }}</span>
          </div>
        </div>

        <div class="sheet-meta-grid">
          <div><strong>报销人员：</strong>{{ reimburseData.applicant }}</div>
          <div><strong>报销部门：</strong>{{ reimburseData.dept }}</div>
          <div><strong>申请时间：</strong>{{ reimburseData.applyDate }}</div>
          <div><strong>币种：</strong>人民币 (CNY)</div>
        </div>

        <div class="reimburse-reason-box">
          <strong>费用事由：</strong>{{ reimburseData.reason }}
        </div>

        <table class="sheet-table mt-2">
          <thead>
            <tr>
              <th width="40">序号</th>
              <th width="120">支出类别</th>
              <th>费用明细说明</th>
              <th width="60">单据张数</th>
              <th width="80">数量/天数</th>
              <th width="100">报销金额(元)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in reimburseData.items" :key="idx">
              <td class="text-center">{{ idx + 1 }}</td>
              <td>{{ item.category }}</td>
              <td>{{ item.desc }}</td>
              <td class="text-center">{{ item.invoiceCount }} 张</td>
              <td class="text-center">{{ item.qty }}</td>
              <td class="text-right font-bold">¥ {{ item.amount.toFixed(2) }}</td>
            </tr>
            <tr class="table-total-row">
              <td colspan="5" class="text-right"><strong>报销总额（大写：人民币玖仟捌佰伍拾元整）：</strong></td>
              <td class="text-right font-bold text-red">¥ {{ reimburseData.totalAmount.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="sheet-footer">
          <div class="sign-block">
            <span>报销人：{{ reimburseData.applicant }}</span>
            <span>部门主管审批：已通过 (电子签)</span>
            <span>财务复核：张敏</span>
            <span>出纳打款：已受理</span>
          </div>
          <div v-if="showOfficialStamp" class="official-stamp-seal seal-finance">
            <div class="seal-inner">
              <span class="seal-star">★</span>
              <span class="seal-text">财务结算核销专用章</span>
              <span class="seal-date">2026.10.19</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 仓储发货面单模板 -->
      <div v-else class="print-paper paper-shipping">
        <div class="shipping-card">
          <div class="shipping-header">
            <span class="shipping-logo">⚡ 顺丰速运 特快特惠</span>
            <div class="shipping-barcode">
              <div class="sim-barcode-lg">||| | |||| || ||| |||| | ||| || |||</div>
              <strong>{{ shippingData.trackingNo }}</strong>
            </div>
          </div>

          <div class="shipping-addr-box">
            <div class="addr-row">
              <span class="addr-tag">寄件方</span>
              <div class="addr-content">
                <strong>{{ shippingData.sender }}</strong>
                <p>{{ shippingData.senderAddr }}</p>
              </div>
            </div>
            <div class="addr-divider" />
            <div class="addr-row">
              <span class="addr-tag tag-dest">收件方</span>
              <div class="addr-content">
                <strong class="text-lg">{{ shippingData.receiver }}</strong>
                <p class="text-lg">{{ shippingData.receiverAddr }}</p>
              </div>
            </div>
          </div>

          <div class="shipping-meta-box">
            <span><strong>托寄物品：</strong>{{ shippingData.productName }}</span>
            <span><strong>实际计费重：</strong>{{ shippingData.weight }}</span>
            <span><strong>件数：</strong>{{ shippingData.pieces }} 件</span>
          </div>

          <div class="shipping-footer">
            <span>经办发货人：系统出库自动化</span>
            <span>签收人签字：_______________ 日期：____月____日</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.print-center-container {
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

.template-selector-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.ctrl-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ctrl-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

/* 打印纸张画布样式 */
.print-sheet-wrapper {
  display: flex;
  justify-content: center;
  padding: 30px 0;
  background: var(--el-fill-color-darker);
  border-radius: 12px;
}

.print-paper {
  position: relative;
  background: #fff;
  color: #1e293b;
  box-shadow: 0 10px 25px rgb(0 0 0 / 0.15);
  box-sizing: border-box;
  font-family: SimSun, "Songti SC", serif;
  overflow: hidden;
}

.paper-a4 {
  width: 210mm;
  min-height: 297mm;
  padding: 20mm;
}

.paper-a5 {
  width: 210mm;
  min-height: 148mm;
  padding: 12mm;
}

.paper-shipping {
  width: 130mm;
  min-height: 180mm;
  padding: 10mm;
}

.watermark-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotate(-30deg);
  font-size: 42px;
  color: rgb(203 213 225 / 0.25);
  font-weight: 900;
  letter-spacing: 6px;
  pointer-events: none;
  white-space: nowrap;
}

.sheet-header {
  position: relative;
  text-align: center;
  margin-bottom: 20px;
  border-bottom: 2px solid #000;
  padding-bottom: 12px;
}

.company-name {
  font-size: 12px;
  letter-spacing: 2px;
  color: #64748b;
  font-family: sans-serif;
}

.sheet-title {
  font-size: 24px;
  font-weight: bold;
  letter-spacing: 3px;
  margin: 6px 0 10px;
}

.barcode-box {
  position: absolute;
  right: 0;
  bottom: 8px;
  text-align: right;
  font-family: monospace;
}

.sim-barcode {
  letter-spacing: 2px;
  font-size: 16px;
  font-weight: bold;
}

.barcode-no {
  font-size: 11px;
  color: #475569;
}

.qrcode-box {
  position: absolute;
  right: 0;
  bottom: 8px;
  text-align: right;
}

.qrcode-icon {
  font-size: 26px;
  display: block;
}

.sheet-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  row-gap: 8px;
  font-size: 13px;
  margin-bottom: 14px;
}

.reimburse-reason-box {
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  margin-bottom: 12px;
}

.sheet-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.sheet-table th, .sheet-table td {
  border: 1px solid #334155;
  padding: 8px 6px;
}

.sheet-table th {
  background: #f1f5f9;
  font-weight: bold;
}

.text-center { text-align: center; }
.text-right { text-align: right; }
.font-bold { font-weight: bold; }
.text-red { color: #dc2626; }

.table-total-row td {
  background: #f8fafc;
  padding: 10px 6px;
}

.sheet-footer {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 30px;
}

.sign-block {
  display: flex;
  gap: 30px;
  font-size: 13px;
}

.official-stamp-seal {
  position: absolute;
  right: 20px;
  bottom: -15px;
  width: 120px;
  height: 120px;
  border: 3px solid #dc2626;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #dc2626;
  opacity: 0.85;
  transform: rotate(-15deg);
  pointer-events: none;
}

.seal-inner {
  text-align: center;
}

.seal-star {
  font-size: 22px;
  display: block;
  line-height: 1;
}

.seal-text {
  font-size: 11px;
  font-weight: bold;
  letter-spacing: 1px;
  display: block;
  margin: 4px 0;
}

.seal-date {
  font-size: 10px;
  font-family: monospace;
}

/* 发货面单特化 */
.shipping-card {
  border: 2px solid #000;
  padding: 12px;
}

.shipping-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 2px solid #000;
  padding-bottom: 10px;
}

.shipping-logo {
  font-size: 20px;
  font-weight: 900;
  color: #000;
}

.shipping-barcode {
  text-align: right;
  font-family: monospace;
}

.sim-barcode-lg {
  font-size: 22px;
  letter-spacing: 2px;
}

.shipping-addr-box {
  padding: 12px 0;
  border-bottom: 2px solid #000;
}

.addr-row {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.addr-tag {
  background: #000;
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  height: fit-content;
  border-radius: 2px;
}

.tag-dest {
  background: #dc2626;
}

.addr-content strong {
  display: block;
  font-size: 14px;
}

.addr-content p {
  margin: 2px 0 0;
  font-size: 13px;
}

.text-lg {
  font-size: 16px !important;
  font-weight: 800;
}

.shipping-meta-box {
  display: flex;
  gap: 20px;
  padding: 10px 0;
  border-bottom: 1px solid #cbd5e1;
  font-size: 12px;
}

.shipping-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding-top: 10px;
}

.mb-4 {
  margin-bottom: 16px;
}

/* 浏览器原生高保真打印适配 */
@media print {
  .no-print {
    display: none !important;
  }

  .print-center-container {
    padding: 0 !important;
  }

  .print-sheet-wrapper {
    background: transparent !important;
    padding: 0 !important;
  }

  .print-paper {
    box-shadow: none !important;
    width: 100% !important;
    min-height: auto !important;
    padding: 10mm !important;
  }
}
</style>
