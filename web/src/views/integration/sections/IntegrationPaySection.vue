<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  CreditCard,
  Check,
  Refresh,
  Money,
  CircleCheckFilled,
  WarningFilled,
  Promotion,
  ShoppingBag
} from "@element-plus/icons-vue";
import BaseCard from "@/components/base/BaseCard.vue";
import { createPayOrder, getPayOrderStatus, mockPaySuccess } from "@/api/modules/integration/service";
import type { PayOrderCreatePayload, PayOrderRow } from "@/api/modules/integration/types";

const loading = ref(false);
const creating = ref(false);
const simulating = ref(false);

// 支付下单表单
const orderForm = reactive<PayOrderCreatePayload>({
  orderNo: `PO_${Date.now().toString().slice(-8)}`,
  amount: 299.00,
  channel: "WECHAT",
  subject: "企业旗舰版按月订阅服务",
  description: "Antigravity PaaS 企业级平台技术授权与高可用服务"
});

// 当前展示中的支付收银台订单
const currentOrder = ref<PayOrderRow | null>(null);

// 历史支付流水台账（本地+沙箱模拟持久化）
const tradeHistory = ref<PayOrderRow[]>([
  {
    payTradeNo: "PAY-9810294810294812",
    orderNo: "PO_98210394",
    amount: 1999.00,
    channel: "WECHAT",
    status: "SUCCESS",
    payUrl: "weixin://wxpay/bizpayurl?pr=PAY-9810294810294812",
    qrCodeUrl: "https://api.qrserver.com/v1/create-qr-code/?data=weixin://wxpay/bizpayurl",
    expireAt: "2026-10-26 12:00:00",
    paidAt: "2026-10-26 11:32:15"
  },
  {
    payTradeNo: "PAY-7719283019284910",
    orderNo: "PO_88192031",
    amount: 8800.00,
    channel: "ALIPAY",
    status: "SUCCESS",
    payUrl: "https://openapi.alipay.com/gateway.do?trade_no=PAY-7719283019284910",
    qrCodeUrl: "https://api.qrserver.com/v1/create-qr-code/?data=alipay",
    expireAt: "2026-10-26 10:00:00",
    paidAt: "2026-10-26 09:41:02"
  }
]);

async function handleCreateOrder() {
  if (!orderForm.amount || orderForm.amount <= 0) {
    ElMessage.warning("请输入有效的支付金额");
    return;
  }
  creating.value = true;
  try {
    const res = await createPayOrder(orderForm);
    currentOrder.value = res;
    // 加入历史记录
    tradeHistory.value.unshift(res);
    ElMessage.success("收银台订单创建成功，请扫码支付！");
  } catch (err: any) {
    ElMessage.error(err?.message || "创建支付订单失败");
  } finally {
    creating.value = false;
  }
}

async function handleMockSuccess() {
  if (!currentOrder.value) return;
  simulating.value = true;
  try {
    const res = await mockPaySuccess(currentOrder.value.orderNo);
    currentOrder.value = res;
    // 更新历史台账状态
    const idx = tradeHistory.value.findIndex(item => item.orderNo === res.orderNo);
    if (idx !== -1) {
      tradeHistory.value[idx] = res;
    }
    ElMessage.success("模拟扫码支付成功！订单状态已更新为 SUCCESS");
  } catch (err: any) {
    ElMessage.error(err?.message || "模拟支付回调失败");
  } finally {
    simulating.value = false;
  }
}

async function handleRefreshCurrentStatus() {
  if (!currentOrder.value) return;
  loading.value = true;
  try {
    const res = await getPayOrderStatus(currentOrder.value.orderNo);
    currentOrder.value = res;
    ElMessage.info(`订单最新状态：${res.status}`);
  } catch (err: any) {
    ElMessage.error(err?.message || "查询状态失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  orderForm.orderNo = `PO_${Date.now().toString().slice(-8)}`;
  orderForm.amount = 299.00;
  currentOrder.value = null;
}
</script>

<template>
  <div class="pay-center-container">
    <!-- 顶部核心支付指标条 -->
    <div class="pay-metric-strip">
      <div class="metric-item">
        <el-icon class="icon-green"><CreditCard /></el-icon>
        <span class="label">微信支付 Native</span>
        <span class="value">商户直连模式</span>
      </div>
      <div class="divider"></div>
      <div class="metric-item">
        <el-icon class="icon-blue"><Money /></el-icon>
        <span class="label">支付宝当面付</span>
        <span class="value">扫码沙箱已就绪</span>
      </div>
      <div class="divider"></div>
      <div class="metric-item">
        <el-icon class="icon-purple"><ShoppingBag /></el-icon>
        <span class="label">聚合收银台</span>
        <span class="value">动态二合一码</span>
      </div>
      <div class="divider"></div>
      <div class="metric-item">
        <el-icon class="icon-orange"><CircleCheckFilled /></el-icon>
        <span class="label">交易回调通知</span>
        <span class="value">自动状态补偿</span>
      </div>
    </div>

    <!-- 支付测试主工作区 -->
    <div class="pay-workbench mt-4">
      <div class="pay-grid">
        <!-- 1. 下单收银台控制卡片 -->
        <BaseCard title="统一聚合收银台 (Cashier Desk)">
          <el-form label-position="top">
            <el-form-item label="支付渠道" required>
              <el-radio-group v-model="orderForm.channel">
                <el-radio-button value="WECHAT">微信支付 (Native 扫码)</el-radio-button>
                <el-radio-button value="ALIPAY">支付宝 (当面付)</el-radio-button>
                <el-radio-button value="MOCK_SANDBOX">聚合沙箱测试渠道</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="商户交易单号" required>
                  <el-input v-model="orderForm.orderNo" placeholder="业务系统生成的唯一订单号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="交易金额 (元)" required>
                  <el-input-number
                    v-model="orderForm.amount"
                    :min="0.01"
                    :precision="2"
                    :step="100"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="商品名称/订单标题" required>
              <el-input v-model="orderForm.subject" placeholder="例如：SaaS 平台企业年费订阅" />
            </el-form-item>

            <el-form-item label="商品附加描述">
              <el-input v-model="orderForm.description" placeholder="用于在支付回执中展示给用户的说明信息" />
            </el-form-item>

            <div class="form-actions-bar">
              <el-button type="primary" :icon="CreditCard" :loading="creating" @click="handleCreateOrder">
                生成预支付订单并唤起收银台
              </el-button>
              <el-button @click="resetForm">重置单号</el-button>
            </div>
          </el-form>
        </BaseCard>

        <!-- 2. 扫码与实时支付回执 -->
        <BaseCard title="收银台动态二维码与交易交互">
          <div v-if="currentOrder" class="cashier-box">
            <div class="cashier-status-bar">
              <span class="cashier-no">平台流水号: <strong>{{ currentOrder.payTradeNo }}</strong></span>
              <el-tag
                :type="currentOrder.status === 'SUCCESS' ? 'success' : currentOrder.status === 'PENDING' ? 'warning' : 'danger'"
                effect="dark"
                size="large"
              >
                {{ currentOrder.status === 'SUCCESS' ? '已支付成功' : currentOrder.status === 'PENDING' ? '待用户扫码支付' : currentOrder.status }}
              </el-tag>
            </div>

            <!-- 动态二维码展示 -->
            <div class="qr-canvas-box">
              <div class="qr-frame">
                <!-- 模拟二维码画板 -->
                <div class="mock-qr-code">
                  <div class="qr-corner top-left"></div>
                  <div class="qr-corner top-right"></div>
                  <div class="qr-corner bottom-left"></div>
                  <div class="qr-center-logo">
                    <span v-if="currentOrder.channel === 'WECHAT'">微信</span>
                    <span v-else-if="currentOrder.channel === 'ALIPAY'">支</span>
                    <span v-else>PAY</span>
                  </div>
                </div>
              </div>

              <div class="qr-tip-text">
                <span class="amount-large">¥{{ currentOrder.amount.toFixed(2) }}</span>
                <p>请使用手机扫描二维码完成支付</p>
                <p class="channel-hint">当前支付通道：<strong>{{ currentOrder.channel }}</strong></p>
              </div>
            </div>

            <!-- 模拟测试快捷操作条 -->
            <div class="sim-actions-box">
              <el-button
                v-if="currentOrder.status === 'PENDING'"
                type="success"
                :icon="Check"
                :loading="simulating"
                @click="handleMockSuccess"
              >
                一键模拟用户扫码支付成功 (Mock Callback)
              </el-button>
              <el-button
                v-else
                type="info"
                plain
                disabled
              >
                ✓ 交易已完成 (支付时间: {{ currentOrder.paidAt || '刚刚' }})
              </el-button>

              <el-button :icon="Refresh" :loading="loading" @click="handleRefreshCurrentStatus">
                查询订单状态
              </el-button>
            </div>
          </div>

          <div v-else class="empty-cashier">
            <el-icon class="empty-icon"><CreditCard /></el-icon>
            <p>请在左侧填写订单金额并点击“生成预支付订单”</p>
            <span class="sub">系统将调用统一支付网关生成包含商户凭据的聚合收银二维码</span>
          </div>
        </BaseCard>
      </div>

      <!-- 3. 支付流水与交易台账 -->
      <div class="trade-history-panel mt-4">
        <BaseCard title="支付交易流水与结算对账台账">
          <el-table :data="tradeHistory" stripe border style="width: 100%">
            <el-table-column type="index" label="#" width="60" align="center" />
            <el-table-column prop="payTradeNo" label="平台支付流水号" width="220" />
            <el-table-column prop="orderNo" label="商户业务单号" width="180" />
            <el-table-column prop="amount" label="金额 (元)" width="120" align="right">
              <template #default="{ row }">
                <strong class="text-primary font-mono">¥{{ row.amount.toFixed(2) }}</strong>
              </template>
            </el-table-column>
            <el-table-column prop="channel" label="支付渠道" width="130" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.channel === 'WECHAT' ? 'success' : 'primary'" effect="plain">
                  {{ row.channel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'warning'">
                  {{ row.status === 'SUCCESS' ? '支付成功' : '待付款' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expireAt" label="过期时间" width="180" />
            <el-table-column prop="paidAt" label="完成支付时间" min-width="180">
              <template #default="{ row }">
                <span>{{ row.paidAt || '--' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </BaseCard>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pay-center-container {
  display: flex;
  flex-direction: column;
}

.pay-metric-strip {
  display: flex;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: var(--sb-radius-md, 8px);
  padding: 10px 16px;
  gap: 16px;

  .metric-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .label {
      font-size: 13px;
      color: #64748b;
    }

    .value {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    .icon-green { color: #16a34a; font-size: 18px; }
    .icon-blue { color: #2563eb; font-size: 18px; }
    .icon-purple { color: #9333ea; font-size: 18px; }
    .icon-orange { color: #ea580c; font-size: 18px; }
  }

  .divider {
    width: 1px;
    height: 18px;
    background: #cbd5e1;
  }
}

.pay-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-actions-bar {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.cashier-box {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .cashier-status-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid #e2e8f0;

    .cashier-no {
      font-size: 13px;
      color: #64748b;
      font-family: var(--sb-font-mono, monospace);
    }
  }

  .qr-canvas-box {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 16px;
    background: #f8fafc;
    border-radius: 8px;
    border: 1px solid #e2e8f0;

    .qr-frame {
      width: 130px;
      height: 130px;
      background: #fff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05);

      .mock-qr-code {
        width: 110px;
        height: 110px;
        position: relative;
        background:
          radial-gradient(#0f172a 2px, transparent 2px) 0 0 / 8px 8px,
          radial-gradient(#0f172a 2px, transparent 2px) 4px 4px / 8px 8px;
        border: 2px solid #0f172a;

        .qr-corner {
          position: absolute;
          width: 24px;
          height: 24px;
          border: 4px solid #0f172a;
          background: #fff;

          &.top-left { top: 0; left: 0; }
          &.top-right { top: 0; right: 0; }
          &.bottom-left { bottom: 0; left: 0; }
        }

        .qr-center-logo {
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          background: #fff;
          border: 1px solid #cbd5e1;
          border-radius: 4px;
          padding: 2px 4px;
          font-size: 10px;
          font-weight: 700;
          color: #2563eb;
        }
      }
    }

    .qr-tip-text {
      .amount-large {
        font-size: 28px;
        font-weight: 800;
        color: #0f172a;
        font-family: var(--sb-font-mono, monospace);
      }

      p {
        margin: 4px 0 0;
        font-size: 13px;
        color: #64748b;
      }

      .channel-hint {
        margin-top: 8px;
        font-size: 12px;
        color: #334155;
      }
    }
  }

  .sim-actions-box {
    display: flex;
    gap: 12px;
    padding-top: 8px;
  }
}

.empty-cashier {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  color: #64748b;

  .empty-icon {
    font-size: 48px;
    color: #cbd5e1;
    margin-bottom: 12px;
  }

  p {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #334155;
  }

  .sub {
    font-size: 12px;
    color: #94a3b8;
    margin-top: 4px;
  }
}
</style>
