<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { cartApi, catalogApi, orderApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';
import type { BuyerOrder } from '@/buyer/types/models';

const router = useRouter();
const store = useAppStore();

const tabs = [
  { key: 'all', label: '全部订单' },
  { key: 'pending', label: '待发货' },
  { key: 'shipped', label: '运输中' },
  { key: 'completed', label: '已完成' },
  { key: 'cancelled', label: '已取消' },
] as const;

const activeTab = ref<(typeof tabs)[number]['key']>('all');
const loading = ref(false);
const orders = ref<BuyerOrder[]>([]);
const reviewTarget = ref<BuyerOrder | null>(null);
const reviewSubmitting = ref(false);
const reviewForm = reactive({
  rating: 5,
  content: '',
});

const activeTabLabel = computed(() => tabs.find((tab) => tab.key === activeTab.value)?.label ?? '订单');

const filteredOrders = computed(() => {
  if (activeTab.value === 'all') {
    return orders.value;
  }
  return orders.value.filter((order) => mapOrderStatus(order.status) === activeTab.value);
});

const emptyText = computed(() => {
  if (loading.value) {
    return '订单加载中...';
  }
  if (activeTab.value === 'all') {
    return '暂无订单';
  }
  return `暂无${activeTabLabel.value}`;
});

function mapOrderStatus(status?: string) {
  switch (status) {
    case 'pending':
    case '待发货':
      return 'pending';
    case 'shipped':
    case '已发货':
    case '运输中':
      return 'shipped';
    case 'completed':
    case '已完成':
    case '已签收':
      return 'completed';
    case 'cancelled':
    case '已取消':
      return 'cancelled';
    default:
      return 'all';
  }
}

function statusText(status: string) {
  switch (mapOrderStatus(status)) {
    case 'pending':
      return '待发货';
    case 'shipped':
      return '已发货';
    case 'completed':
      return '已完成';
    case 'cancelled':
      return '已取消';
    default:
      return status || '处理中';
  }
}

function paymentText(method?: string) {
  switch (method) {
    case 'alipay':
      return '支付宝';
    case 'wechat':
      return '微信支付';
    case 'bank':
      return '银行卡';
    default:
      return method || '在线支付';
  }
}

async function loadOrders() {
  loading.value = true;
  try {
    const result = await orderApi.list('all');
    orders.value = Array.isArray(result?.list) ? result.list : [];
  } finally {
    loading.value = false;
  }
}

async function logout() {
  await store.logout();
}

function updateOrder(updated: BuyerOrder) {
  orders.value = orders.value.map((item) => (item.id === updated.id ? updated : item));
}

async function cancelOrder(order: BuyerOrder) {
  if (!window.confirm(`确认取消订单 ${order.orderNo} 吗？`)) {
    return;
  }
  try {
    updateOrder(await orderApi.cancel(order.id));
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '取消订单失败，请稍后重试。');
  }
}

async function confirmOrder(order: BuyerOrder) {
  if (!window.confirm(`确认已收到商品“${order.name}”吗？`)) {
    return;
  }
  try {
    updateOrder(await orderApi.confirm(order.id));
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '确认收货失败，请稍后重试。');
  }
}

async function updateAddress(order: BuyerOrder) {
  const nextAddress = window.prompt('请输入新的收货地址', order.address || '');
  if (nextAddress == null) {
    return;
  }
  const trimmed = nextAddress.trim();
  if (!trimmed || trimmed === order.address) {
    return;
  }
  try {
    updateOrder(await orderApi.updateAddress(order.id, trimmed));
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '修改地址失败，请稍后重试。');
  }
}

async function requestReturn(order: BuyerOrder) {
  if (!window.confirm(`确认对订单 ${order.orderNo} 发起退货申请吗？`)) {
    return;
  }
  try {
    updateOrder(await orderApi.requestReturn(order.id));
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '退货申请失败，请稍后重试。');
  }
}

function contactMerchant(order: BuyerOrder) {
  router.push({
    path: '/buyer/chat',
    query: {
      mode: 'merchant',
      orderId: order.id,
      orderNo: order.orderNo,
      product: order.name,
    },
  });
}

async function buyAgain(order: BuyerOrder) {
  try {
    const product = await catalogApi.getProduct(order.productId);
    await cartApi.add(product, order.quantity, order.skuAttributes || {});
    await store.reloadCart();
    router.push('/buyer/cart');
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '再来一单失败，请稍后重试。');
  }
}

function openReview(order: BuyerOrder) {
  reviewTarget.value = order;
  reviewForm.rating = 5;
  reviewForm.content = '';
}

function closeReview() {
  reviewTarget.value = null;
  reviewSubmitting.value = false;
}

async function submitReview() {
  if (!reviewTarget.value) return;
  if (!reviewForm.content.trim()) {
    window.alert('请先填写评价内容。');
    return;
  }

  reviewSubmitting.value = true;
  try {
    const updated = await orderApi.submitReview(reviewTarget.value.id, {
      rating: reviewForm.rating,
      content: reviewForm.content.trim(),
    });
    orders.value = orders.value.map((item) => (item.id === updated.id ? updated : item));
    closeReview();
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '评价提交失败，请稍后重试。');
    reviewSubmitting.value = false;
  }
}

onMounted(() => {
  void loadOrders();
});
</script>

<template>
  <AppShell active="profile" title="我的订单" subtitle="查看订单状态、物流进度，并在完成后提交真实评价">
    <section class="orders-page">
      <div class="orders-head">
        <div class="orders-toolbar">
          <button type="button" class="ghost-btn" @click="router.push('/buyer/profile')">返回个人中心</button>
          <button type="button" class="submit-btn" @click="logout">退出登录</button>
        </div>
        <div class="orders-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="tab-btn"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <div v-if="filteredOrders.length" class="order-list">
        <article v-for="order in filteredOrders" :key="order.id" class="order-card">
          <img :src="order.image" :alt="order.name" class="order-image" />

          <div class="order-main">
            <div class="order-title-row">
              <div>
                <h3>{{ order.name }}</h3>
                <p class="order-no">订单号：{{ order.orderNo }}</p>
              </div>
              <span class="status-badge" :class="`status-${order.status}`">{{ statusText(order.status) }}</span>
            </div>

            <div class="order-meta-grid">
              <p><strong>规格：</strong>{{ order.skuSummary || '默认规格' }}</p>
              <p><strong>数量：</strong>{{ order.quantity }}</p>
              <p><strong>单价：</strong>¥{{ order.unitPrice.toFixed(2) }}</p>
              <p><strong>总价：</strong>¥{{ order.amount.toFixed(2) }}</p>
              <p><strong>支付方式：</strong>{{ paymentText(order.paymentMethod) }}</p>
              <p><strong>下单时间：</strong>{{ order.createdAt || '-' }}</p>
            </div>

            <div class="order-progress-box">
              <p><strong>物流进度：</strong>{{ order.progress || '处理中' }}</p>
              <p><strong>当前节点：</strong>{{ order.currentNode || '等待更新' }}</p>
              <p><strong>收货信息：</strong>{{ order.receiverName || '-' }} {{ order.receiverPhone || '' }}</p>
              <p><strong>地址：</strong>{{ order.address || '-' }}</p>
            </div>

            <div class="order-actions">
              <template v-if="mapOrderStatus(order.status) === 'pending'">
                <button type="button" class="action-btn danger" @click="cancelOrder(order)">取消订单</button>
                <button type="button" class="action-btn" @click="contactMerchant(order)">联系商家</button>
                <button type="button" class="action-btn" @click="updateAddress(order)">修改地址</button>
              </template>
              <template v-else-if="mapOrderStatus(order.status) === 'shipped'">
                <button type="button" class="action-btn primary" @click="confirmOrder(order)">已收货</button>
                <button type="button" class="action-btn" @click="contactMerchant(order)">联系商家</button>
              </template>
              <template v-else-if="mapOrderStatus(order.status) === 'completed'">
                <button
                  type="button"
                  class="action-btn primary"
                  :disabled="!order.canReview"
                  @click="order.canReview && openReview(order)"
                >
                  {{ order.canReview ? '评价' : '已评价' }}
                </button>
                <button type="button" class="action-btn danger" @click="requestReturn(order)">退货</button>
              </template>
              <template v-else-if="mapOrderStatus(order.status) === 'cancelled'">
                <button type="button" class="action-btn primary" @click="buyAgain(order)">再来一单</button>
              </template>
            </div>

            <div v-if="order.review" class="review-panel review-panel--filled">
              <div class="review-head">
                <strong>我的评价</strong>
                <span>{{ '★'.repeat(order.review.rating) }}{{ '☆'.repeat(5 - order.review.rating) }}</span>
              </div>
              <p>{{ order.review.content }}</p>
              <small>{{ order.review.createdAt }}</small>
            </div>

            <div v-else-if="order.canReview" class="review-panel">
              <p>订单已完成，可以提交本次购物评价，评价会同步显示到商家端。</p>
              <button type="button" class="review-btn" @click="openReview(order)">去评价</button>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">{{ emptyText }}</div>
    </section>

    <div v-if="reviewTarget" class="review-dialog-overlay" @click="closeReview">
      <div class="review-dialog" @click.stop>
        <h3>评价订单</h3>
        <p class="review-product">{{ reviewTarget.name }}</p>
        <div class="stars-row">
          <button
            v-for="star in 5"
            :key="star"
            type="button"
            class="star-btn"
            :class="{ active: star <= reviewForm.rating }"
            @click="reviewForm.rating = star"
          >
            ★
          </button>
        </div>
        <textarea
          v-model="reviewForm.content"
          rows="5"
          maxlength="500"
          placeholder="说说这次购物体验、商品质量和物流服务"
        ></textarea>
        <div class="dialog-actions">
          <button type="button" class="ghost-btn" @click="closeReview">取消</button>
          <button type="button" class="submit-btn" :disabled="reviewSubmitting" @click="submitReview">
            {{ reviewSubmitting ? '提交中...' : '提交评价' }}
          </button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.orders-page {
  display: grid;
  gap: 18px;
}

.orders-head,
.order-card,
.empty-state,
.review-dialog {
  background: rgba(255, 255, 255, 0.88);
  border-radius: 28px;
  box-shadow: var(--shadow-card);
}

.orders-head {
  padding: 18px;
}

.orders-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 14px;
}

.orders-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tab-btn,
.review-btn,
.ghost-btn,
.submit-btn {
  border-radius: 999px;
  padding: 11px 18px;
}

.tab-btn.active,
.review-btn,
.submit-btn {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: #fff;
}

.order-list {
  display: grid;
  gap: 16px;
}

.order-card {
  display: grid;
  grid-template-columns: 144px 1fr;
  gap: 18px;
  padding: 18px;
}

.order-image {
  width: 100%;
  height: 144px;
  border-radius: 22px;
  object-fit: cover;
}

.order-main {
  display: grid;
  gap: 14px;
}

.order-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.order-title-row h3,
.review-dialog h3 {
  margin: 0;
}

.order-no,
.review-product,
.review-panel small {
  color: var(--ink-soft);
}

.status-badge {
  align-self: flex-start;
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 700;
}

.status-pending {
  background: rgba(251, 191, 36, 0.16);
  color: #b45309;
}

.status-shipped {
  background: rgba(59, 130, 246, 0.14);
  color: #1d4ed8;
}

.status-completed {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.status-cancelled {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.order-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.order-meta-grid p,
.order-progress-box p,
.review-panel p {
  margin: 0;
}

.order-progress-box,
.review-panel {
  border-radius: 20px;
  padding: 14px 16px;
  background: rgba(255, 248, 243, 0.9);
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.action-btn {
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 184, 115, 0.32);
  color: var(--ink-strong);
}

.action-btn.primary {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: #fff;
  border-color: transparent;
}

.action-btn.danger {
  background: rgba(254, 242, 242, 0.92);
  border-color: rgba(248, 113, 113, 0.3);
  color: #dc2626;
}

.action-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.review-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-panel--filled {
  display: grid;
  justify-content: initial;
}

.review-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.empty-state {
  padding: 48px 24px;
  text-align: center;
  color: var(--ink-soft);
}

.review-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  padding: 20px;
}

.review-dialog {
  width: min(100%, 560px);
  padding: 22px;
}

.stars-row {
  display: flex;
  gap: 8px;
  margin: 16px 0;
}

.star-btn {
  background: transparent;
  border: none;
  font-size: 28px;
  color: #cbd5e1;
}

.star-btn.active {
  color: #f59e0b;
}

textarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 250, 246, 0.92);
  padding: 14px 16px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.74);
}

@media (max-width: 760px) {
  .order-card {
    grid-template-columns: 1fr;
  }

  .order-image {
    height: 200px;
  }

  .order-title-row,
  .order-actions,
  .review-panel,
  .review-head,
  .dialog-actions,
  .orders-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .orders-toolbar .ghost-btn,
  .orders-toolbar .submit-btn {
    width: 100%;
  }

  .order-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
