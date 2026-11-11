<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

// Component Imports
import ProductCard from '@/buyer/components/common/ProductCard.vue';
import ProductModal from '@/buyer/components/common/ProductModal.vue';
import ActionToast from '@/buyer/components/common/ActionToast.vue';
import AppShell from '@/buyer/components/layout/AppShell.vue';

// API and Store
import { cartApi, favoriteApi, homeApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';
import type { BannerSlide, FriendPost, Product } from '@/buyer/types/models';

const router = useRouter();
const store = useAppStore();

// State
const banners = ref<BannerSlide[]>([]);
const posts = ref<FriendPost[]>([]);
const featuredProducts = ref<Product[]>([]);
const activeBanner = ref(0);
const selectedProduct = ref<Product | null>(null);
const favoriteIds = ref<string[]>([]);
const toastVisible = ref(false);
const toastMessage = ref('');
let toastTimer: number | undefined;

const currentBanner = computed(() => banners.value[activeBanner.value] ?? null);

// 轮播图计时器逻辑
let timer: number | undefined;

function startTimer() {
  stopTimer();
  timer = window.setInterval(() => {
    nextBanner(1);
  }, 4000);
}

function stopTimer() {
  if (timer) window.clearInterval(timer);
}

function nextBanner(step: number) {
  if (banners.value.length === 0) return;
  let next = activeBanner.value + step;
  if (next >= banners.value.length) next = 0;
  if (next < 0) next = banners.value.length - 1;
  activeBanner.value = next;
}

function goToBanner(index: number) {
  activeBanner.value = index;
  startTimer(); // 手动点击后重置时间
}

onMounted(async () => {
  try {
    const dashboard = await homeApi.getDashboard();
    banners.value = dashboard.banners || [];
    posts.value = dashboard.posts || [];
    featuredProducts.value = dashboard.featuredProducts || [];
    favoriteIds.value = await favoriteApi.listIds();
    
    if (banners.value.length > 1) {
      startTimer();
    }
  } catch (err) {
    console.error("Failed to load dashboard data", err);
  }
});

onUnmounted(() => {
  stopTimer();
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
});

function showCartToast(message: string) {
  toastMessage.value = message;
  toastVisible.value = true;
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
  toastTimer = window.setTimeout(() => {
    toastVisible.value = false;
  }, 2200);
}

// Cart Actions
async function addToCart(product: Product) {
  if (!store.state.user) {
    router.push({ path: '/login', query: { tab: 'buyer', redirect: '/buyer/cart' } });
    return;
  }
  await cartApi.add(product, 1, {});
  await store.reloadCart();
  showCartToast(`已将“${product.name}”加入购物车`);
}

async function addFromModal(payload: { product: Product; quantity: number; selection: Record<string, string | undefined> }) {
  if (!store.state.user) {
    router.push({ path: '/login', query: { tab: 'buyer', redirect: '/buyer/cart' } });
    return;
  }
  await cartApi.add(payload.product, payload.quantity, payload.selection);
  await store.reloadCart();
  showCartToast(`已加入 ${payload.quantity} 件“${payload.product.name}”到购物车`);
  selectedProduct.value = null;
}

async function toggleFavorite(product: Product) {
  favoriteIds.value = await favoriteApi.toggle(product.id);
}
</script>

<template>
  <AppShell active="index">
    <ActionToast :visible="toastVisible" :message="toastMessage" />
    <div class="home-wrapper">
      
      <!-- 1. 顶部轮播图 / Top Hero Banner -->
      <section class="hero-slider" 
               v-if="currentBanner" 
               :style="{ backgroundColor: currentBanner.accent || '#fff0f5' }"
               @mouseenter="stopTimer"
               @mouseleave="startTimer">
        
        <Transition name="slider-fade" mode="out-in">
          <div class="slider-content" :key="currentBanner.id">
            <div class="hero-info">
              <span class="badge">本周推荐</span>
              <h1 class="title">{{ currentBanner.title }}</h1>
              <p class="subtitle">{{ currentBanner.subtitle }}</p>
              <button class="btn-shop" @click="router.push('/buyer/category')">马上选购</button>
            </div>
            <div class="hero-image">
              <img :src="currentBanner.image" alt="banner" />
            </div>
          </div>
        </Transition>

        <!-- 左右切换按键 -->
        <button class="nav-btn prev" @click="nextBanner(-1)">‹</button>
        <button class="nav-btn next" @click="nextBanner(1)">›</button>

        <!-- 指示器 -->
        <div class="dots">
          <span v-for="(b, i) in banners" 
                :key="b.id" 
                class="dot" 
                :class="{ active: activeBanner === i }" 
                @click="goToBanner(i)"></span>
        </div>
      </section>

      <!-- 2. 服务快捷入口卡片 (金刚区) -->
      <section class="quick-links">
        <div class="quick-card" @click="router.push('/buyer/category')">
          <div class="icon-wrap" style="background:#fff0f5; color:#ff6b9e;">🛍️</div>
          <div class="text-wrap">
            <h3>商品分类</h3>
            <p>海量主粮零食</p>
          </div>
        </div>
        <div class="quick-card" @click="router.push('/buyer/cart')">
          <div class="icon-wrap" style="background:#e0f2fe; color:#38bdf8;">🛒</div>
          <div class="text-wrap">
            <h3>购物车</h3>
            <p>已选商品统一结算</p>
          </div>
        </div>
        <div class="quick-card" @click="router.push('/buyer/profile')">
          <div class="icon-wrap" style="background:#f0fdf4; color:#4ade80;">👤</div>
          <div class="text-wrap">
            <h3>个人中心</h3>
            <p>查看资料与服务</p>
          </div>
        </div>
        <div class="quick-card" @click="router.push('/buyer/chat?mode=official')">
          <div class="icon-wrap" style="background:#fef9c3; color:#f59e0b;">💬</div>
          <div class="text-wrap">
            <h3>官方客服</h3>
            <p>全天极速响应</p>
          </div>
        </div>
      </section>

      <div class="main-content">
        <!-- 3. 左侧：宠物圈动态 (清爽版) -->
        <aside class="left-sidebar">
          <div class="elegant-panel">
            <div class="panel-header">
              <h2>🐾 宠友圈</h2>
            </div>
            <div class="post-list">
              <div class="post-item" v-for="post in posts.slice(0,3)" :key="post.id">
                <div class="user-profile">
                  <img :src="post.avatar" :alt="post.name" class="avatar" />
                  <span class="name">{{ post.name }}</span>
                </div>
                <p class="post-message">{{ post.message }}</p>
                <img v-if="post.image" :src="post.image" class="post-image" />
              </div>
            </div>
          </div>
        </aside>

        <!-- 4. 右侧：热卖商品网格 -->
        <main class="right-products">
          <div class="panel-header section-title">
            <h2>🔥 热卖好物推荐</h2>
            <button class="view-all-btn" @click="router.push('/buyer/category')">查看全部 ></button>
          </div>
          <div class="product-grid">
            <ProductCard
              v-for="product in featuredProducts"
              :key="product.id"
              :product="product"
              :is-favorite="favoriteIds.includes(product.id)"
              @detail="selectedProduct = $event"
              @add="addToCart"
              @favorite="toggleFavorite"
            />
          </div>
        </main>
      </div>

    </div>

    <ProductModal
      :visible="Boolean(selectedProduct)"
      :product="selectedProduct"
      :is-favorite="selectedProduct ? favoriteIds.includes(selectedProduct.id) : false"
      @close="selectedProduct = null"
      @confirm="addFromModal"
      @favorite="toggleFavorite"
    />
  </AppShell>
</template>

<style scoped>
.home-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  gap: 32px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* === 1. Hero Banner 卡片 === */
.hero-slider {
  position: relative;
  height: 380px;
  border-radius: 28px;
  padding: 0 60px;
  overflow: hidden;
  margin-top: 10px;
  transition: background-color 0.6s ease;
}
.slider-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 100%;
}

/* 轮播图过渡动画 */
.slider-fade-enter-active,
.slider-fade-leave-active {
  transition: opacity 0.5s ease, transform 0.5s ease;
}
.slider-fade-enter-from {
  opacity: 0;
  transform: translateX(30px);
}
.slider-fade-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

.hero-info {
  max-width: 440px;
  z-index: 2;
}
.badge {
  display: inline-block;
  background: rgba(255, 255, 255, 0.9);
  color: #ff6b9e;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 20px;
}
.title {
  font-size: 46px;
  font-weight: 900;
  color: #2c2c2c;
  margin: 0 0 12px 0;
  line-height: 1.1;
  letter-spacing: -0.5px;
}
.subtitle {
  font-size: 17px;
  color: #555;
  margin-bottom: 32px;
  line-height: 1.5;
}
.btn-shop {
  background: #ff6b9e;
  color: white;
  border: none;
  padding: 14px 32px;
  border-radius: 30px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(255, 107, 158, 0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}
.btn-shop:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 25px rgba(255, 107, 158, 0.4);
}
.hero-image {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}
.hero-image img {
  max-height: 90%;
  max-width: 100%;
  object-fit: contain;
  animation: gentleFloat 5s ease-in-out infinite alternate;
}
@keyframes gentleFloat {
  0% { transform: translateY(0px) scale(1.05); }
  100% { transform: translateY(-12px) scale(1.05); }
}

/* 导航按键 */
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.5);
  border: none;
  font-size: 24px;
  display: grid;
  place-items: center;
  color: #333;
  cursor: pointer;
  opacity: 0;
  transition: all 0.3s ease;
  z-index: 10;
  backdrop-filter: blur(4px);
}
.hero-slider:hover .nav-btn {
  opacity: 1;
}
.nav-btn:hover {
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  transform: translateY(-50%) scale(1.1);
}
.nav-btn.prev { left: 20px; }
.nav-btn.next { right: 20px; }

/* 底部指示器 */
.dots {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 10;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.15);
  cursor: pointer;
  transition: all 0.3s ease;
}
.dot:hover {
  background: rgba(0, 0, 0, 0.3);
}
.dot.active {
  width: 24px;
  background: #ff6b9e;
}

/* === 2. Quick Links (横向4个块) === */
.quick-links {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.quick-card {
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid rgba(0,0,0,0.02);
}
.quick-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(255, 107, 158, 0.1);
  border-color: rgba(255, 107, 158, 0.1);
}
.icon-wrap {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}
.text-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.text-wrap h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #2c2c2c;
}
.text-wrap p {
  margin: 0;
  font-size: 13px;
  color: #888;
}

/* === 3. Main Content Layout === */
.main-content {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  margin-bottom: 60px;
}

.left-sidebar {
  width: 320px;
  flex-shrink: 0;
}
.elegant-panel {
  background: #ffffff;
  border-radius: 24px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.panel-header h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  color: #2c2c2c;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.post-item {
  padding-bottom: 24px;
  border-bottom: 1px solid #f2f2f2;
}
.post-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.user-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #eee;
}
.name {
  font-size: 15px;
  font-weight: 700;
  color: #333;
}
.post-message {
  font-size: 14px;
  color: #555;
  line-height: 1.6;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.post-image {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 12px;
}

.right-products {
  flex: 1;
}
.section-title h2 {
  font-size: 26px;
  font-weight: 800;
  color: #2c2c2c;
  margin-bottom: 8px;
}
.view-all-btn {
  background: white;
  border: 1px solid #e0e0e0;
  color: #555;
  padding: 8px 16px;
  border-radius: 20px;
  font-weight: bold;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.view-all-btn:hover {
  background: #ff6b9e;
  border-color: #ff6b9e;
  color: white;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

@media (max-width: 900px) {
  .hero-slider { height: auto; padding: 40px 24px; flex-direction: column; text-align: center; }
  .slider-content { flex-direction: column; }
  .hero-info { margin-bottom: 30px; }
  .hero-image img { max-height: 200px; transform: none; animation: none; }
  .nav-btn { display: none; }
  
  .quick-links { grid-template-columns: repeat(2, 1fr); }
  .main-content { flex-direction: column; }
  .left-sidebar { width: 100%; }
}
</style>
