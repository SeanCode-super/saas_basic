<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { Document, Printer, Refresh, Stamp, Tickets } from "@element-plus/icons-vue";

type TemplateType = "PURCHASE_IN" | "REIMBURSE" | "SHIPPING_LABEL" | "VAT_INVOICE";

const selectedTemplate = ref<TemplateType>("PURCHASE_IN");
const showWatermark = ref(true);
const showOfficialStamp = ref(true);
const paperSize = ref<"A4" | "A5">("A4");

// 模拟表单数据
const vatInvoiceData = ref({
  invoiceCode: "031002300111",
  invoiceNo: "98236109",
  invoiceDate: "2026年10月26日",
  checkCode: "82910 39102 77481 02918",
  buyer: {
    name: "阿里云计算有限公司",
    taxId: "91330106673959654K",
    addrTel: "浙江省杭州市西湖区云谷园区 0571-82128888",
    bankAccount: "招商银行杭州高新支行 571906281010801"
  },
  seller: {
    name: "Antigravity 企业数字中台软件技术有限公司",
    taxId: "91320594MA27BXYZ88",
    addrTel: "江苏省苏州市工业园区纳米城 0512-68889999",
    bankAccount: "中国建设银行苏州独墅湖支行 3220198888005291"
  },
  items: [
    { name: "*信息技术服务* Antigravity PaaS 企业级平台授权", spec: "旗舰版 v2.0", unit: "套", qty: 1, price: 180000.0, amount: 180000.0, taxRate: "6%", taxAmount: 10800.0 },
    { name: "*技术咨询服务* 多租户微服务与 BPMN 工作流定制开发", spec: "实施周期 30人天", unit: "项", qty: 1, price: 45000.0, amount: 45000.0, taxRate: "6%", taxAmount: 2700.0 },
    { name: "*运维保障服务* 7x24 小时高可用容灾与安全加固", spec: "年度维保 SLA 99.99%", unit: "年", qty: 1, price: 20000.0, amount: 20000.0, taxRate: "6%", taxAmount: 1200.0 }
  ],
  payee: "陈静敏",
  checker: "王雪峰",
  drawer: "平台开票专员",
  remark: "对应合同编号: AGY-SaaS-2026-Q4-001。款项已全额结清。"
});

const totalVatGoodsAmount = computed(() => vatInvoiceData.value.items.reduce((s, i) => s + i.amount, 0));
const totalVatTaxAmount = computed(() => vatInvoiceData.value.items.reduce((s, i) => s + i.taxAmount, 0));
const totalVatSumAmount = computed(() => totalVatGoodsAmount.value + totalVatTaxAmount.value);

function digitToChinese(n: number): string {
  const fraction = ["角", "分"];
  const digit = ["零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"];
  const unit = [["元", "万", "亿"], ["", "拾", "佰", "仟"]];
  let num = Math.abs(n);
  let s = "";
  for (let i = 0; i < fraction.length; i++) {
    s += (digit[Math.floor(num * 10 * Math.pow(10, i)) % 10] + fraction[i]).replace(/零./, "");
  }
  s = s || "整";
  num = Math.floor(num);
  for (let i = 0; i < unit[0].length && num > 0; i++) {
    let p = "";
    for (let j = 0; j < unit[1].length && num > 0; j++) {
      p = digit[num % 10] + unit[1][j] + p;
      num = Math.floor(num / 10);
    }
    s = p.replace(/(零.)*零$/, "").replace(/^$/, "零") + unit[0][i] + s;
  }
  return s.replace(/(零.)*零元/, "元").replace(/(零.)+/g, "零").replace(/^整$/, "零元整");
}

const chineseVatSumAmount = computed(() => digitToChinese(totalVatSumAmount.value));

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
              <el-radio-button value="VAT_INVOICE">增值税专用发票</el-radio-button>
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

      <!-- 4. 增值税专用发票模板 -->
      <div v-if="selectedTemplate === 'VAT_INVOICE'" class="print-paper vat-invoice-paper" :class="`paper-${paperSize.toLowerCase()}`">
        <!-- 防伪水印 -->
        <div v-if="showWatermark" class="watermark-text vat-watermark">国家税务总局全国统一发票</div>

        <!-- 发票椭圆监制章 (税务局官方发票印章) -->
        <div class="national-tax-stamp">
          <div class="stamp-ellipse">
            <span class="stamp-top">全国统一发票监制章</span>
            <span class="stamp-mid">★</span>
            <span class="stamp-bot">国家税务总局监制</span>
          </div>
        </div>

        <!-- 发票抬头区 -->
        <div class="vat-header">
          <div class="vat-header-left">
            <div class="qr-placeholder">
              <div class="sim-qr-box">
                <div class="qr-dot"></div>
                <div class="qr-dot"></div>
                <div class="qr-dot"></div>
                <div class="qr-dot"></div>
              </div>
              <span class="qr-sub">防伪查验</span>
            </div>
            <div class="code-box">
              <div>机器编号: <strong>661928374921</strong></div>
            </div>
          </div>

          <div class="vat-title-box">
            <h1 class="vat-main-title">增值税专用发票</h1>
            <div class="vat-title-underline"></div>
          </div>

          <div class="vat-header-right">
            <div>发票代码: <strong class="vat-code">{{ vatInvoiceData.invoiceCode }}</strong></div>
            <div>发票号码: <strong class="vat-no">{{ vatInvoiceData.invoiceNo }}</strong></div>
            <div>开票日期: <span>{{ vatInvoiceData.invoiceDate }}</span></div>
            <div>校 验 码: <span class="vat-check-code">{{ vatInvoiceData.checkCode }}</span></div>
          </div>
        </div>

        <!-- 发票核心网格主体 -->
        <div class="vat-body-grid">
          <!-- 购买方 & 密码区 -->
          <div class="vat-row vat-row-party">
            <div class="party-col party-buyer">
              <div class="party-label">购<br>买<br>方</div>
              <div class="party-content">
                <div class="line"><span>名　　　　称：</span><strong>{{ vatInvoiceData.buyer.name }}</strong></div>
                <div class="line"><span>纳税人识别号：</span><strong>{{ vatInvoiceData.buyer.taxId }}</strong></div>
                <div class="line"><span>地 址、电 话：</span><span>{{ vatInvoiceData.buyer.addrTel }}</span></div>
                <div class="line"><span>开户行及账号：</span><span>{{ vatInvoiceData.buyer.bankAccount }}</span></div>
              </div>
            </div>

            <div class="party-col party-pwd">
              <div class="party-label">密<br>码<br>区</div>
              <div class="pwd-content">
                <code>&lt;&gt;148*&lt;78/4+82&gt;10-91823&gt;&lt;&gt;92*</code>
                <code>78+&lt;1920-819&lt;&gt;123+45910/&lt;&gt;82</code>
                <code>45/1829&lt;&gt;*+192-384910283&gt;&lt;&gt;</code>
                <code>9102+4859&lt;&gt;1028-49218293*&lt;&gt;</code>
              </div>
            </div>
          </div>

          <!-- 货物及应税劳务清单明细表 -->
          <div class="vat-table-wrapper">
            <table class="vat-goods-table">
              <thead>
                <tr>
                  <th style="width: 30%;">货物或应税劳务、服务名称</th>
                  <th style="width: 14%;">规格型号</th>
                  <th style="width: 6%;">单位</th>
                  <th style="width: 8%;">数量</th>
                  <th style="width: 10%;">单价</th>
                  <th style="width: 12%;">金 额</th>
                  <th style="width: 8%;">税率</th>
                  <th style="width: 12%;">税 额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(it, idx) in vatInvoiceData.items" :key="idx">
                  <td class="text-left font-mono">{{ it.name }}</td>
                  <td>{{ it.spec }}</td>
                  <td class="text-center">{{ it.unit }}</td>
                  <td class="text-right">{{ it.qty }}</td>
                  <td class="text-right">{{ it.price.toFixed(2) }}</td>
                  <td class="text-right font-mono">{{ it.amount.toFixed(2) }}</td>
                  <td class="text-center">{{ it.taxRate }}</td>
                  <td class="text-right font-mono">{{ it.taxAmount.toFixed(2) }}</td>
                </tr>
                <!-- 小计行 -->
                <tr class="vat-subtotal-tr">
                  <td colspan="5" class="text-center font-bold">合　　　　　计</td>
                  <td class="text-right font-bold font-mono">¥{{ totalVatGoodsAmount.toFixed(2) }}</td>
                  <td></td>
                  <td class="text-right font-bold font-mono">¥{{ totalVatTaxAmount.toFixed(2) }}</td>
                </tr>
                <!-- 价税合计行 -->
                <tr class="vat-total-sum-tr">
                  <td colspan="2" class="text-center font-bold">价税合计 (大写)</td>
                  <td colspan="4" class="text-left font-bold chinese-amount">
                    {{ chineseVatSumAmount }}
                  </td>
                  <td colspan="2" class="text-right font-bold font-mono">
                    (小写) ¥{{ totalVatSumAmount.toFixed(2) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 销售方 & 备注区 -->
          <div class="vat-row vat-row-party">
            <div class="party-col party-buyer">
              <div class="party-label">销<br>售<br>方</div>
              <div class="party-content">
                <div class="line"><span>名　　　　称：</span><strong>{{ vatInvoiceData.seller.name }}</strong></div>
                <div class="line"><span>纳税人识别号：</span><strong>{{ vatInvoiceData.seller.taxId }}</strong></div>
                <div class="line"><span>地 址、电 话：</span><span>{{ vatInvoiceData.seller.addrTel }}</span></div>
                <div class="line"><span>开户行及账号：</span><span>{{ vatInvoiceData.seller.bankAccount }}</span></div>
              </div>
            </div>

            <div class="party-col party-pwd">
              <div class="party-label">备<br><br>注</div>
              <div class="remark-content">
                <p>{{ vatInvoiceData.remark }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 发票底部签署区 -->
        <div class="vat-footer">
          <div>收款人: <strong>{{ vatInvoiceData.payee }}</strong></div>
          <div>复核: <strong>{{ vatInvoiceData.checker }}</strong></div>
          <div>开票人: <strong>{{ vatInvoiceData.drawer }}</strong></div>
          <div>销售方: (章)</div>
        </div>

        <!-- 销售方发票专用印章 (电子防伪红章) -->
        <div v-if="showOfficialStamp" class="vat-seller-stamp">
          <div class="vat-stamp-outer">
            <div class="vat-stamp-title">{{ vatInvoiceData.seller.name }}</div>
            <div class="vat-stamp-star">★</div>
            <div class="vat-stamp-type">发票专用章</div>
            <div class="vat-stamp-taxid">{{ vatInvoiceData.seller.taxId }}</div>
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

/* ==================== 增值税发票专有套打样式 ==================== */
.vat-invoice-paper {
  border: 1px solid #994d1a;
  background-color: #fffefb;
  color: #5c2d12;
}

.national-tax-stamp {
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  pointer-events: none;
}

.stamp-ellipse {
  width: 140px;
  height: 70px;
  border: 2px solid rgb(220 38 38 / 0.7);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: rgb(220 38 38 / 0.75);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  line-height: 1.2;
}

.stamp-mid {
  font-size: 16px;
  color: rgb(220 38 38 / 0.8);
}

.vat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 12px;
  padding: 0 4px;
}

.vat-header-left {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.sim-qr-box {
  width: 44px;
  height: 44px;
  border: 1px solid #994d1a;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  gap: 4px;
  background: #fff;
}

.qr-dot {
  background: #5c2d12;
}

.qr-sub {
  display: block;
  font-size: 10px;
  color: #78350f;
  text-align: center;
  margin-top: 2px;
}

.vat-title-box {
  text-align: center;
  flex: 1;
}

.vat-main-title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 6px;
  color: #78350f;
  margin: 0;
  font-family: SimSun, "Songti SC", serif;
}

.vat-title-underline {
  height: 3px;
  border-top: 1px solid #994d1a;
  border-bottom: 1px solid #994d1a;
  margin-top: 6px;
  width: 240px;
  margin-left: auto;
  margin-right: auto;
}

.vat-header-right {
  font-size: 12px;
  line-height: 1.6;
  text-align: right;
  font-family: var(--sb-font-mono, monospace);
  color: #5c2d12;
}

.vat-code, .vat-no {
  font-size: 14px;
  color: #b91c1c;
  letter-spacing: 1px;
}

.vat-body-grid {
  border: 1px solid #994d1a;
}

.vat-row-party {
  display: flex;
  border-bottom: 1px solid #994d1a;
}

.party-col {
  display: flex;
}

.party-buyer {
  width: 60%;
  border-right: 1px solid #994d1a;
}

.party-pwd {
  width: 40%;
}

.party-label {
  width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #994d1a;
  font-size: 12px;
  font-weight: 600;
  background: #fff9f0;
  text-align: center;
  color: #78350f;
  padding: 4px;
}

.party-content {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.7;
  flex: 1;
}

.party-content .line {
  display: flex;
}

.party-content .line span {
  color: #78350f;
  white-space: nowrap;
}

.party-content .line strong {
  color: #1e293b;
  font-weight: 600;
}

.pwd-content {
  padding: 8px 12px;
  font-size: 11px;
  font-family: var(--sb-font-mono, monospace);
  line-height: 1.5;
  color: #78350f;
  display: flex;
  flex-direction: column;
}

.remark-content {
  padding: 8px 12px;
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}

.vat-table-wrapper {
  border-bottom: 1px solid #994d1a;
}

.vat-goods-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.vat-goods-table th,
.vat-goods-table td {
  border: 1px solid #994d1a;
  padding: 6px 8px;
}

.vat-goods-table th {
  background: #fff9f0;
  color: #78350f;
  font-weight: 600;
  text-align: center;
}

.vat-subtotal-tr {
  background: #fff9f0;
}

.vat-total-sum-tr {
  background: #fffdf7;
}

.chinese-amount {
  letter-spacing: 2px;
  font-size: 13px;
  color: #991b1b;
}

.vat-footer {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px 4px;
  font-size: 12px;
  color: #78350f;
}

.vat-seller-stamp {
  position: absolute;
  right: 60px;
  bottom: 24px;
  pointer-events: none;
}

.vat-stamp-outer {
  width: 150px;
  height: 150px;
  border: 3px solid rgb(220 38 38 / 0.85);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: rgb(220 38 38 / 0.88);
  transform: rotate(-12deg);
  padding: 8px;
  box-sizing: border-box;
  text-align: center;
}

.vat-stamp-title {
  font-size: 10px;
  font-weight: 700;
  line-height: 1.1;
}

.vat-stamp-star {
  font-size: 22px;
  line-height: 1;
  margin: 2px 0;
}

.vat-stamp-type {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 2px;
}

.vat-stamp-taxid {
  font-size: 9px;
  font-family: var(--sb-font-mono, monospace);
  margin-top: 2px;
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
