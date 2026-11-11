<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { catalogApi, favoriteApi, profileApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';
import type { Product, UserProfile } from '@/buyer/types/models';

const route = useRoute();
const router = useRouter();
const store = useAppStore();

const favoriteProducts = ref<Product[]>([]);
const favoriteIds = ref<string[]>([]);
const profile = ref<UserProfile | null>(null);
const addresses = ref<Array<{ id: string; name: string; phone: string; detail: string; isDefault: boolean }>>([]);
const addressForm = reactive({ name: '', phone: '', detail: '' });
const couponIds = ref<string[]>([]);
const settings = reactive({
  orderNotice: true,
  promotionNotice: true,
  autoPlayVideo: false,
  privacyMode: true,
});

const couponTemplates = [
  { id: 'coupon-15', title: '满99减15', desc: '宠物粮与零食通用券', expiresAt: '2026-05-31' },
  { id: 'coupon-30', title: '满199减30', desc: '用品专区可用', expiresAt: '2026-06-15' },
  { id: 'coupon-free', title: '全场包邮券', desc: '限本月首单使用', expiresAt: '2026-04-30' },
];

const faqList = [
  { q: '订单发货后多久能收到？', a: '通常 48 小时内发出，偏远地区会根据物流情况顺延。' },
  { q: '如何修改收货地址？', a: '进入收货地址页面新增或设为默认地址，后续下单会自动带入。' },
  { q: '评价为什么会同步到商家？', a: '订单评价会直接写入订单记录，商家在物流历史中能看到对应买家的反馈。' },
];

const serviceMeta = computed(() => {
  switch (route.name) {
    case 'buyer-favorites':
      return { title: '我的收藏', subtitle: '管理你感兴趣的商品，支持随时移入或移出收藏列表。' };
    case 'buyer-addresses':
      return { title: '收货地址', subtitle: '维护常用收货信息，并同步默认地址到下单资料。' };
    case 'buyer-coupons':
      return { title: '优惠券', subtitle: '领取并查看当前账号下可用的优惠券。' };
    case 'buyer-settings':
      return { title: '设置', subtitle: '保存消息提醒与隐私偏好，保持你的使用习惯。' };
    default:
      return { title: '帮助中心', subtitle: '常见问题、自助说明以及人工客服入口。' };
  }
});

function scopedKey(name: string) {
  const userId = store.state.user?.id || 'guest';
  return `jpet-store:buyer-services:${userId}:${name}`;
}

function readStorage<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) as T : fallback;
  } catch {
    return fallback;
  }
}

function writeStorage<T>(key: string, value: T) {
  window.localStorage.setItem(key, JSON.stringify(value));
}

const claimedCoupons = computed(() => couponTemplates.filter((item) => couponIds.value.includes(item.id)));
const unclaimedCoupons = computed(() => couponTemplates.filter((item) => !couponIds.value.includes(item.id)));
const collectedProducts = computed(() => favoriteProducts.value.filter((product) => favoriteIds.value.includes(product.id)));

function openFavoriteProduct(product: Product) {
  router.push({
    path: '/buyer/category',
    query: {
      search: product.name,
      detail: String(product.id),
    },
  });
}

function toggleFavorite(productId: string) {
  void favoriteApi.toggle(productId).then((next) => {
    favoriteIds.value = next;
  });
}

function claimCoupon(couponId: string) {
  if (couponIds.value.includes(couponId)) return;
  couponIds.value = [...couponIds.value, couponId];
  writeStorage(scopedKey('coupons'), couponIds.value);
}

async function saveAddress() {
  if (!addressForm.name.trim() || !addressForm.phone.trim() || !addressForm.detail.trim()) {
    window.alert('请先填写完整的收货信息。');
    return;
  }

  const next = {
    id: `addr-${Date.now()}`,
    name: addressForm.name.trim(),
    phone: addressForm.phone.trim(),
    detail: addressForm.detail.trim(),
    isDefault: addresses.value.length === 0,
  };
  addresses.value = [...addresses.value, next];
  writeStorage(scopedKey('addresses'), addresses.value);
  addressForm.name = '';
  addressForm.phone = '';
  addressForm.detail = '';

  if (next.isDefault) {
    profile.value = await profileApi.updateProfile({ address: next.detail });
  }
}

async function setDefaultAddress(id: string) {
  addresses.value = addresses.value.map((item) => ({ ...item, isDefault: item.id === id }));
  writeStorage(scopedKey('addresses'), addresses.value);
  const current = addresses.value.find((item) => item.id === id);
  if (current) {
    profile.value = await profileApi.updateProfile({ address: current.detail });
  }
}

function removeAddress(id: string) {
  addresses.value = addresses.value.filter((item) => item.id !== id);
  if (addresses.value.length && !addresses.value.some((item) => item.isDefault)) {
    addresses.value[0].isDefault = true;
  }
  writeStorage(scopedKey('addresses'), addresses.value);
}

function saveSettings() {
  writeStorage(scopedKey('settings'), { ...settings });
}

async function logout() {
  await store.logout();
}

async function loadPageData() {
  favoriteIds.value = await favoriteApi.listIds();
  couponIds.value = readStorage<string[]>(scopedKey('coupons'), []);
  Object.assign(settings, readStorage(scopedKey('settings'), settings));
  addresses.value = readStorage(scopedKey('addresses'), []);
  profile.value = await profileApi.getProfile();

  if (!addresses.value.length && profile.value?.address) {
    addresses.value = [{
      id: 'addr-profile-default',
      name: profile.value.username,
      phone: profile.value.phone,
      detail: profile.value.address,
      isDefault: true,
    }];
    writeStorage(scopedKey('addresses'), addresses.value);
  }

  if (route.name === 'buyer-favorites') {
    const products = await catalogApi.listProducts({ sort: 'heat-desc' });
    favoriteProducts.value = products;
  }
}

watch(
  () => route.name,
  () => {
    void loadPageData();
  },
  { immediate: true }
);
</script>

<template>
  <AppShell active="profile" :title="serviceMeta.title" :subtitle="serviceMeta.subtitle">
    <section class="service-toolbar">
      <button type="button" class="ghost-btn" @click="router.push('/buyer/profile')">返回个人中心</button>
      <button type="button" class="primary-btn" @click="logout">退出登录</button>
    </section>

    <section v-if="route.name === 'buyer-favorites'" class="service-page">
      <div class="service-card">
        <h3>收藏商品</h3>
        <div v-if="collectedProducts.length" class="favorite-grid">
          <article v-for="product in collectedProducts" :key="product.id" class="favorite-item" @click="openFavoriteProduct(product)">
            <img :src="product.image" :alt="product.name" />
            <div>
              <strong>{{ product.name }}</strong>
              <p>{{ product.description }}</p>
              <span>¥{{ product.price.toFixed(2) }}</span>
            </div>
            <button type="button" class="primary-btn" @click.stop="toggleFavorite(product.id)">
              取消收藏
            </button>
          </article>
        </div>
        <div v-else class="empty-state">先去商品分类或首页把喜欢的商品加入收藏，这里会自动显示。</div>
      </div>
    </section>

    <section v-else-if="route.name === 'buyer-addresses'" class="service-page service-page--split">
      <div class="service-card">
        <h3>新增地址</h3>
        <div class="form-grid">
          <input v-model="addressForm.name" type="text" placeholder="收件人姓名" />
          <input v-model="addressForm.phone" type="tel" placeholder="联系电话" />
          <textarea v-model="addressForm.detail" rows="4" placeholder="详细收货地址"></textarea>
        </div>
        <button type="button" class="primary-btn" @click="saveAddress">保存地址</button>
      </div>
      <div class="service-card">
        <h3>地址列表</h3>
        <div v-if="addresses.length" class="address-list">
          <article v-for="item in addresses" :key="item.id" class="address-item">
            <div>
              <strong>{{ item.name }} {{ item.phone }}</strong>
              <p>{{ item.detail }}</p>
              <small v-if="item.isDefault">默认地址</small>
            </div>
            <div class="inline-actions">
              <button type="button" class="ghost-btn" @click="setDefaultAddress(item.id)">设为默认</button>
              <button type="button" class="ghost-btn danger-btn" @click="removeAddress(item.id)">删除</button>
            </div>
          </article>
        </div>
        <div v-else class="empty-state">暂无收货地址。</div>
      </div>
    </section>

    <section v-else-if="route.name === 'buyer-coupons'" class="service-page service-page--split">
      <div class="service-card">
        <h3>可领取优惠券</h3>
        <div class="coupon-list">
          <article v-for="coupon in unclaimedCoupons" :key="coupon.id" class="coupon-item">
            <div>
              <strong>{{ coupon.title }}</strong>
              <p>{{ coupon.desc }}</p>
              <small>有效期至 {{ coupon.expiresAt }}</small>
            </div>
            <button type="button" class="primary-btn" @click="claimCoupon(coupon.id)">领取</button>
          </article>
        </div>
      </div>
      <div class="service-card">
        <h3>我的优惠券</h3>
        <div v-if="claimedCoupons.length" class="coupon-list">
          <article v-for="coupon in claimedCoupons" :key="coupon.id" class="coupon-item claimed">
            <div>
              <strong>{{ coupon.title }}</strong>
              <p>{{ coupon.desc }}</p>
              <small>有效期至 {{ coupon.expiresAt }}</small>
            </div>
            <span class="claimed-tag">已领取</span>
          </article>
        </div>
        <div v-else class="empty-state">你还没有领取优惠券。</div>
      </div>
    </section>

    <section v-else-if="route.name === 'buyer-settings'" class="service-page">
      <div class="service-card settings-card">
        <h3>消息与隐私设置</h3>
        <div class="settings-grid">
          <label class="setting-row"><span>订单状态提醒</span><input v-model="settings.orderNotice" type="checkbox" /></label>
          <label class="setting-row"><span>优惠活动提醒</span><input v-model="settings.promotionNotice" type="checkbox" /></label>
          <label class="setting-row"><span>详情页视频自动播放</span><input v-model="settings.autoPlayVideo" type="checkbox" /></label>
          <label class="setting-row"><span>隐私模式</span><input v-model="settings.privacyMode" type="checkbox" /></label>
        </div>
        <button type="button" class="primary-btn" @click="saveSettings">保存设置</button>
      </div>
    </section>

    <section v-else class="service-page service-page--split">
      <div class="service-card">
        <h3>常见问题</h3>
        <div class="faq-list">
          <article v-for="item in faqList" :key="item.q" class="faq-item">
            <strong>{{ item.q }}</strong>
            <p>{{ item.a }}</p>
          </article>
        </div>
      </div>
      <div class="service-card">
        <h3>需要人工帮助？</h3>
        <p class="help-copy">订单异常、售后问题、物流疑问都可以直接进入在线客服。</p>
        <button type="button" class="primary-btn" @click="router.push('/buyer/chat?mode=official')">联系在线客服</button>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.service-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}

.service-page {
  display: grid;
  gap: 18px;
}

.service-page--split {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.service-card {
  background: rgba(255, 255, 255, 0.88);
  border-radius: 28px;
  padding: 22px;
  box-shadow: var(--shadow-card);
}

.service-card h3 {
  margin: 0 0 14px;
}

.favorite-grid,
.address-list,
.coupon-list,
.faq-list {
  display: grid;
  gap: 12px;
}

.favorite-item,
.address-item,
.coupon-item,
.faq-item {
  display: grid;
  gap: 12px;
  align-items: center;
  border-radius: 20px;
  padding: 14px;
  background: rgba(255, 248, 243, 0.92);
}

.favorite-item {
  grid-template-columns: 88px 1fr auto;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.favorite-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(48, 31, 38, 0.08);
}

.favorite-item img {
  width: 88px;
  height: 88px;
  border-radius: 18px;
  object-fit: cover;
}

.favorite-item p,
.address-item p,
.coupon-item p,
.faq-item p,
.help-copy {
  margin: 4px 0 0;
  color: var(--ink-soft);
}

.form-grid {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
}

input,
textarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 250, 246, 0.92);
  padding: 14px 16px;
}

.primary-btn,
.ghost-btn {
  border-radius: 999px;
  padding: 11px 18px;
}

.primary-btn {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: #fff;
}

.inline-actions {
  display: flex;
  gap: 8px;
}

.coupon-item {
  grid-template-columns: 1fr auto;
}

.coupon-item.claimed {
  border: 1px solid rgba(34, 197, 94, 0.24);
}

.claimed-tag,
.address-item small {
  color: #15803d;
  font-weight: 700;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(255, 236, 226, 0.9);
  border-radius: 20px;
  background: rgba(255, 248, 243, 0.82);
}

.settings-card {
  padding-bottom: 28px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.setting-row input[type='checkbox'] {
  width: 20px;
  height: 20px;
  flex: none;
  margin: 0;
  accent-color: #ff8fb1;
}

.empty-state {
  color: var(--ink-soft);
  text-align: center;
  padding: 28px 18px;
  border: 1px dashed var(--line-soft);
  border-radius: 18px;
}

.danger-btn {
  color: #b91c1c;
}

@media (max-width: 860px) {
  .service-page--split,
  .favorite-item,
  .coupon-item {
    grid-template-columns: 1fr;
  }

  .service-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .service-toolbar .ghost-btn,
  .service-toolbar .primary-btn {
    width: 100%;
  }

  .settings-grid {
    grid-template-columns: 1fr;
  }

  .inline-actions {
    flex-direction: column;
  }
}
</style>
