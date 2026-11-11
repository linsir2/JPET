<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { cartApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';

const store = useAppStore();

const checkoutVisible = ref(false);
const step = ref(1);
const orderSummary = reactive({
  count: 0,
  amount: 0
});
const draft = reactive({
  receiver: '小橘喵家长',
  phone: '13812345678',
  address: '上海市浦东新区爱宠大道 88 号 18 栋 602',
  paymentMethod: 'alipay' as 'alipay' | 'wechat' | 'bank'
});
const checkoutMessage = ref('');
const checkoutError = ref('');
const paymentStage = ref<'idle' | 'qr' | 'success'>('idle');
const paymentOrderNo = ref('');
const paymentCountdown = ref(3);
let paymentCountdownTimer: number | undefined;
let paymentAutoCloseTimer: number | undefined;

const items = computed(() => store.state.cart);
const selectedItems = computed(() => items.value.filter((item) => item.selected));
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0));
const selectedAmount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0));
const allSelected = computed(() => items.value.length > 0 && items.value.every((item) => item.selected));

function formatSelection(selection: Record<string, string | undefined>) {
  const entries = Object.entries(selection || {}).filter(([, value]) => Boolean(value));
  if (!entries.length) {
    return '默认规格';
  }
  return entries.map(([key, value]) => `${key}：${value}`).join(' / ');
}

async function updateQuantity(id: string, quantity: number) {
  await cartApi.updateQuantity(id, quantity);
  await store.reloadCart();
}

async function toggleSelection(id: string, value: boolean) {
  await cartApi.toggleSelection(id, value);
  await store.reloadCart();
}

async function toggleAll() {
  await cartApi.toggleAll(!allSelected.value);
  await store.reloadCart();
}

async function remove(id: string) {
  await cartApi.remove(id);
  await store.reloadCart();
}

async function clearCart() {
  await cartApi.clear();
  await store.reloadCart();
}

function openCheckout() {
  if (!selectedItems.value.length) {
    return;
  }
  orderSummary.count = selectedCount.value;
  orderSummary.amount = selectedAmount.value;
  checkoutMessage.value = '';
  checkoutError.value = '';
  paymentStage.value = 'idle';
  paymentOrderNo.value = '';
  paymentCountdown.value = 3;
  step.value = 1;
  checkoutVisible.value = true;
}

function clearPaymentTimers() {
  if (paymentCountdownTimer) {
    window.clearInterval(paymentCountdownTimer);
    paymentCountdownTimer = undefined;
  }
  if (paymentAutoCloseTimer) {
    window.clearTimeout(paymentAutoCloseTimer);
    paymentAutoCloseTimer = undefined;
  }
}

function closeCheckout() {
  clearPaymentTimers();
  checkoutVisible.value = false;
  paymentStage.value = 'idle';
  paymentOrderNo.value = '';
  paymentCountdown.value = 3;
}

async function submitCheckout() {
  checkoutError.value = '';
  try {
    const result = await cartApi.checkout(draft);
    paymentOrderNo.value = result.orderNo;
    paymentStage.value = 'qr';
    checkoutMessage.value = '';
  } catch (error) {
    checkoutError.value = error instanceof Error ? error.message : '下单失败，请稍后重试。';
  }
}

async function simulatePaymentSuccess() {
  clearPaymentTimers();
  await store.reloadCart();
  checkoutMessage.value = `支付成功，订单号 ${paymentOrderNo.value}`;
  paymentStage.value = 'success';
  paymentCountdown.value = 3;
  paymentCountdownTimer = window.setInterval(() => {
    paymentCountdown.value = Math.max(0, paymentCountdown.value - 1);
  }, 1000);
  paymentAutoCloseTimer = window.setTimeout(() => {
    closeCheckout();
  }, 3000);
}

function handleSelectionEvent(id: string, event: Event) {
  const target = event.target as HTMLInputElement | null;
  void toggleSelection(id, Boolean(target?.checked));
}

onUnmounted(() => {
  clearPaymentTimers();
});
</script>

<template>
  <AppShell active="cart" title="购物车" subtitle="完整保留原来购物车逻辑，并把步骤流程做成可维护的状态界面">
    <section class="cart-header">
      <div>
        <h2>购物车清单</h2>
        <p>可继续调节数量、勾选商品并进入三步下单流程。</p>
      </div>
      <button v-if="items.length" class="clear-btn" @click="clearCart">清空购物车</button>
    </section>

    <section v-if="items.length" class="cart-layout">
      <div class="cart-list">
        <article v-for="item in items" :key="item.id" class="cart-item">
          <label class="select-box">
            <input :checked="item.selected" type="checkbox" @change="handleSelectionEvent(item.id, $event)" />
          </label>
          <img :src="item.productImage" :alt="item.productName" />
          <div class="item-copy">
            <h3>{{ item.productName }}</h3>
            <p>{{ formatSelection(item.selection) }}</p>
            <strong>¥{{ item.price }}</strong>
          </div>
          <div class="stepper">
            <button @click="updateQuantity(item.id, item.quantity - 1)">-</button>
            <span>{{ item.quantity }}</span>
            <button @click="updateQuantity(item.id, item.quantity + 1)">+</button>
          </div>
          <button class="remove-btn" @click="remove(item.id)">删除</button>
        </article>
      </div>

      <aside class="summary-card">
        <label class="toggle-all">
          <input :checked="allSelected" type="checkbox" @change="toggleAll" />
          <span>全选商品</span>
        </label>
        <div class="summary-row">
          <span>已选件数</span>
          <strong>{{ selectedCount }}</strong>
        </div>
        <div class="summary-row">
          <span>商品总价</span>
          <strong>¥{{ selectedAmount }}</strong>
        </div>
        <button class="checkout-btn" @click="openCheckout">去结算</button>
      </aside>
    </section>

    <section v-else class="empty-state">
      <h3>购物车还是空的</h3>
      <p>先去分类页或者首页挑几件毛孩子喜欢的商品吧。</p>
    </section>

    <teleport to="body">
      <div v-if="checkoutVisible" class="modal-overlay" @click.self="paymentStage === 'idle' && closeCheckout()">
        <div class="checkout-panel">
          <div class="panel-head">
            <h3>订单确认</h3>
            <button v-if="paymentStage === 'idle'" @click="closeCheckout">✕</button>
          </div>
          <div class="steps">
            <button :class="{ active: step === 1 }" @click="step = 1">1 收货信息</button>
            <button :class="{ active: step === 2 }" @click="step = 2">2 支付方式</button>
            <button :class="{ active: step === 3 }" @click="step = 3">3 订单确认</button>
          </div>

          <div v-if="step === 1" class="step-pane">
            <label><span>收货人</span><input v-model="draft.receiver" type="text" /></label>
            <label><span>手机号</span><input v-model="draft.phone" type="tel" /></label>
            <label><span>收货地址</span><textarea v-model="draft.address" rows="4"></textarea></label>
            <button class="checkout-btn" @click="step = 2">下一步</button>
          </div>

          <div v-else-if="step === 2" class="step-pane">
            <label class="pay-option"><input v-model="draft.paymentMethod" value="alipay" type="radio" /> 支付宝</label>
            <label class="pay-option"><input v-model="draft.paymentMethod" value="wechat" type="radio" /> 微信支付</label>
            <label class="pay-option"><input v-model="draft.paymentMethod" value="bank" type="radio" /> 银行卡</label>
            <div class="row-actions">
              <button class="ghost-btn" @click="step = 1">上一步</button>
              <button class="checkout-btn" @click="step = 3">下一步</button>
            </div>
          </div>

          <div v-else class="step-pane">
            <p class="error-text" v-if="checkoutError">{{ checkoutError }}</p>
            <p class="success" v-if="paymentStage === 'success'">{{ checkoutMessage }}</p>

            <div v-if="paymentStage === 'idle'" class="confirm-grid">
              <div>
                <span>收货信息</span>
                <strong>{{ draft.receiver }} · {{ draft.phone }}</strong>
                <p>{{ draft.address }}</p>
              </div>
              <div>
                <span>支付方式</span>
                <strong>{{ draft.paymentMethod }}</strong>
                <p>已选商品 {{ orderSummary.count }} 件，共计 ¥{{ orderSummary.amount }}</p>
              </div>
            </div>

            <div v-else-if="paymentStage === 'qr'" class="payment-stage-card">
              <div class="qr-code" :class="`qr-code--${draft.paymentMethod}`">
                <span class="qr-corner qr-corner--tl"></span>
                <span class="qr-corner qr-corner--tr"></span>
                <span class="qr-corner qr-corner--bl"></span>
                <span class="qr-corner qr-corner--br"></span>
                <div class="qr-pattern"></div>
                <div class="qr-scan-line"></div>
              </div>
              <div class="payment-copy">
                <strong>{{ draft.paymentMethod === 'wechat' ? '请使用微信扫码支付' : draft.paymentMethod === 'bank' ? '请使用银行卡 App 扫码确认' : '请使用支付宝扫码支付' }}</strong>
                <p>模拟支付二维码已生成，订单号 {{ paymentOrderNo }}。</p>
                <p>点击下方按钮后，将进入支付成功页并在 3 秒后自动关闭。</p>
              </div>
              <button class="checkout-btn" @click="simulatePaymentSuccess">模拟已扫码支付</button>
            </div>

            <div v-else class="payment-stage-card payment-stage-card--success">
              <div class="payment-success-icon">✓</div>
              <strong>支付成功</strong>
              <p>订单 {{ paymentOrderNo }} 已提交给商家处理。</p>
              <p>{{ paymentCountdown }} 秒后自动返回购物车</p>
            </div>

            <div class="row-actions" v-if="paymentStage === 'idle' && !checkoutMessage">
              <button class="ghost-btn" @click="step = 2">上一步</button>
              <button class="checkout-btn" @click="submitCheckout">确认下单</button>
            </div>
          </div>
        </div>
      </div>
    </teleport>
  </AppShell>
</template>

<style scoped>
.cart-header,
.summary-card,
.empty-state,
.checkout-panel,
.cart-list {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.cart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
}

.cart-header h2,
.empty-state h3,
.panel-head h3 {
  margin: 0;
}

.cart-header p,
.empty-state p {
  color: var(--ink-soft);
}

.clear-btn,
.checkout-btn,
.ghost-btn {
  padding: 12px 18px;
  border-radius: 999px;
}

.clear-btn,
.checkout-btn {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.7);
}

.cart-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 18px;
  margin-top: 18px;
}

.cart-list {
  padding: 14px;
}

.cart-item {
  display: grid;
  grid-template-columns: auto 110px 1fr auto auto;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 22px;
}

.cart-item + .cart-item {
  margin-top: 12px;
  background: rgba(255, 248, 243, 0.85);
}

.cart-item img {
  width: 110px;
  height: 110px;
  border-radius: 18px;
  object-fit: cover;
}

.item-copy p {
  color: var(--ink-soft);
}

.item-copy strong {
  color: #ea5b90;
}

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
}

.stepper button,
.remove-btn {
  border-radius: 999px;
}

.stepper button {
  width: 32px;
  height: 32px;
  background: rgba(255, 145, 178, 0.14);
}

.remove-btn {
  padding: 10px 14px;
  background: rgba(255, 92, 125, 0.1);
  color: #d54e7a;
}

.summary-card {
  align-self: start;
  padding: 20px;
}

.toggle-all,
.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.summary-row {
  padding-top: 18px;
}

.summary-row strong {
  font-size: 24px;
}

.checkout-btn {
  width: 100%;
  margin-top: 20px;
}

.empty-state {
  margin-top: 18px;
  padding: 40px 20px;
  text-align: center;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(61, 43, 47, 0.38);
  backdrop-filter: blur(12px);
  z-index: 20;
}

.checkout-panel {
  width: min(720px, 100%);
  padding: 24px;
}

.panel-head,
.steps,
.row-actions,
.confirm-grid {
  display: flex;
  gap: 12px;
}

.panel-head,
.row-actions {
  align-items: center;
  justify-content: space-between;
}

.steps {
  margin-top: 18px;
}

.steps button {
  flex: 1;
  padding: 12px 14px;
  border-radius: 999px;
  background: rgba(255, 248, 243, 0.9);
}

.steps button.active {
  background: linear-gradient(135deg, #ff91b2, #ffcf85);
  color: white;
}

.step-pane {
  display: grid;
  gap: 14px;
  margin-top: 20px;
}

.step-pane label {
  display: grid;
  gap: 8px;
}

.step-pane input,
.step-pane textarea {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
}

.pay-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 249, 244, 0.92);
}

.confirm-grid {
  flex-direction: column;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 249, 244, 0.9);
}

.confirm-grid span,
.success {
  color: var(--ink-soft);
}

.error-text {
  color: #d44f79;
  background: rgba(255, 99, 132, 0.1);
  padding: 12px 14px;
  border-radius: 16px;
}

.payment-stage-card {
  display: grid;
  justify-items: center;
  gap: 16px;
  padding: 24px;
  border-radius: 22px;
  background: rgba(255, 249, 244, 0.92);
  text-align: center;
}

.payment-stage-card--success {
  background: linear-gradient(180deg, rgba(240, 255, 245, 0.94), rgba(255, 249, 244, 0.92));
}

.payment-copy {
  display: grid;
  gap: 8px;
}

.payment-copy p,
.payment-stage-card p {
  margin: 0;
  color: var(--ink-soft);
}

.qr-code {
  position: relative;
  width: 220px;
  height: 220px;
  padding: 18px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(28, 28, 28, 0.08), 0 16px 38px rgba(36, 24, 32, 0.12);
}

.qr-code--alipay {
  background: linear-gradient(180deg, #fefefe, #eef8ff);
}

.qr-code--wechat {
  background: linear-gradient(180deg, #fefefe, #effcf0);
}

.qr-code--bank {
  background: linear-gradient(180deg, #fefefe, #fff7ed);
}

.qr-pattern {
  width: 100%;
  height: 100%;
  border-radius: 18px;
  background:
    linear-gradient(90deg, #111 10px, transparent 10px) 0 0 / 22px 22px,
    linear-gradient(#111 10px, transparent 10px) 0 0 / 22px 22px,
    linear-gradient(90deg, transparent 11px, #111 11px, #111 14px, transparent 14px) 0 0 / 29px 29px,
    linear-gradient(transparent 11px, #111 11px, #111 14px, transparent 14px) 0 0 / 29px 29px,
    #fff;
  opacity: 0.94;
}

.qr-corner {
  position: absolute;
  width: 42px;
  height: 42px;
  border: 6px solid #111;
  border-radius: 10px;
  z-index: 1;
}

.qr-corner--tl { top: 16px; left: 16px; }
.qr-corner--tr { top: 16px; right: 16px; }
.qr-corner--bl { bottom: 16px; left: 16px; }
.qr-corner--br { bottom: 16px; right: 16px; }

.qr-scan-line {
  position: absolute;
  left: 18px;
  right: 18px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, rgba(255, 97, 138, 0.85), transparent);
  animation: qr-scan 1.8s ease-in-out infinite;
}

.payment-success-icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #69d98b, #94f0a7);
  color: white;
  font-size: 34px;
  font-weight: 800;
  box-shadow: 0 14px 28px rgba(68, 168, 93, 0.24);
}

@keyframes qr-scan {
  0% { top: 24px; opacity: 0.3; }
  50% { opacity: 1; }
  100% { top: calc(100% - 28px); opacity: 0.3; }
}

@media (max-width: 980px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .cart-item {
    grid-template-columns: auto 1fr;
  }

  .cart-item img {
    width: 90px;
    height: 90px;
  }

  .stepper,
  .remove-btn {
    grid-column: 2;
    justify-self: start;
  }

  .steps,
  .row-actions {
    flex-direction: column;
  }

  .steps button,
  .row-actions button {
    width: 100%;
  }
}
</style>