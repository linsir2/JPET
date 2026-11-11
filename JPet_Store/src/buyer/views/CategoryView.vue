<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ProductCard from '@/buyer/components/common/ProductCard.vue';
import ProductModal from '@/buyer/components/common/ProductModal.vue';
import ActionToast from '@/buyer/components/common/ActionToast.vue';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { cartApi, catalogApi, favoriteApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';
import type { Product } from '@/buyer/types/models';

const route = useRoute();
const router = useRouter();
const store = useAppStore();

const categories = ref<string[]>([]);
const products = ref<Product[]>([]);
const selectedCategory = ref('全部');
const selectedSubCategory = ref('全部');
const sort = ref<'default' | 'price-asc' | 'price-desc' | 'name'>('default');
const keyword = ref('');
const selectedProduct = ref<Product | null>(null);
const favoriteIds = ref<string[]>([]);
const toastVisible = ref(false);
const toastMessage = ref('');
let toastTimer: number | undefined;

const subCategories = computed(() => {
  if (selectedCategory.value === '全部') {
    return [];
  }
  const values = Array.from(
    new Set(
      products.value
        .map((product) => product.subCategory?.trim())
        .filter((item): item is string => Boolean(item) && item !== selectedCategory.value)
    )
  );
  return ['全部', ...values];
});

const displayedProducts = computed(() => {
  if (selectedSubCategory.value === '全部') {
    return products.value;
  }
  return products.value.filter((product) => product.subCategory === selectedSubCategory.value);
});

async function openProductFromQuery() {
  const detailId = typeof route.query.detail === 'string' ? route.query.detail : '';
  if (!detailId) {
    return;
  }

  const matched = products.value.find((product) => String(product.id) === detailId);
  if (matched) {
    selectedProduct.value = matched;
    return;
  }

  try {
    selectedProduct.value = await catalogApi.getProduct(detailId);
  } catch {
    selectedProduct.value = null;
  }
}

async function reload() {
  products.value = await catalogApi.listProducts({
    category: selectedCategory.value,
    sort: sort.value,
    search: keyword.value.trim() || undefined
  });
  await openProductFromQuery();
}

onMounted(async () => {
  categories.value = await catalogApi.getCategories();
  keyword.value = typeof route.query.search === 'string' ? route.query.search : '';
  favoriteIds.value = await favoriteApi.listIds();
  await reload();
});

onUnmounted(() => {
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

watch(
  () => [route.query.search, route.query.detail],
  async ([value]) => {
    keyword.value = typeof value === 'string' ? value : '';
    await reload();
  }
);

watch([selectedCategory, sort], reload);

async function search() {
  await router.push({ path: '/buyer/category', query: keyword.value ? { search: keyword.value } : {} });
}

async function clearFilter() {
  selectedCategory.value = '全部';
  selectedSubCategory.value = '全部';
  keyword.value = '';
  await router.push({ path: '/buyer/category' });
  await reload();
}

async function addToCart(product: Product) {
  await cartApi.add(product, 1, {});
  await store.reloadCart();
  showCartToast(`已将“${product.name}”加入购物车`);
}

async function addFromModal(payload: { product: Product; quantity: number; selection: Record<string, string | undefined> }) {
  await cartApi.add(payload.product, payload.quantity, payload.selection);
  await store.reloadCart();
  showCartToast(`已加入 ${payload.quantity} 件“${payload.product.name}”到购物车`);
  selectedProduct.value = null;
  if (route.query.detail) {
    await router.replace({ path: '/buyer/category', query: keyword.value ? { search: keyword.value } : {} });
  }
}

async function toggleFavorite(product: Product) {
  favoriteIds.value = await favoriteApi.toggle(product.id);
}

const emptyText = computed(() => {
  return keyword.value ? `没有找到与“${keyword.value}”相关的商品。` : '当前筛选条件下还没有商品。';
});

watch(selectedCategory, () => {
  selectedSubCategory.value = '全部';
});

watch(subCategories, (value) => {
  if (!value.includes(selectedSubCategory.value)) {
    selectedSubCategory.value = '全部';
  }
});
</script>

<template>
  <AppShell active="category" title="商品分类" subtitle="保留原有筛选感受，换成更顺滑的 Vue 状态流转">
    <ActionToast :visible="toastVisible" :message="toastMessage" />
    <section class="toolbar">
      <div class="search-box">
        <input v-model="keyword" placeholder="搜索商品名称、用途或标签..." @keydown.enter="search" />
        <button @click="search">搜索</button>
      </div>
      <select v-model="sort">
        <option value="default">默认排序</option>
        <option value="price-asc">价格从低到高</option>
        <option value="price-desc">价格从高到低</option>
        <option value="name">名称排序</option>
      </select>
    </section>

    <section class="filter-panel">
      <div class="filter-head">
        <div>
          <span>商品分类</span>
          <strong>{{ selectedCategory }}</strong>
        </div>
        <button class="clear-btn" @click="clearFilter">清空筛选</button>
      </div>
      <div class="filter-chips">
        <button
          v-for="category in categories"
          :key="category"
          class="chip"
          :class="{ active: selectedCategory === category }"
          @click="selectedCategory = category"
        >
          {{ category }}
        </button>
      </div>
      <div v-if="subCategories.length > 1" class="sub-filter">
        <div class="sub-filter-title">细分类别</div>
        <div class="filter-chips sub-filter-chips">
          <button
            v-for="subCategory in subCategories"
            :key="subCategory"
            class="chip sub-chip"
            :class="{ active: selectedSubCategory === subCategory }"
            @click="selectedSubCategory = subCategory"
          >
            {{ subCategory }}
          </button>
        </div>
      </div>
    </section>

    <section v-if="displayedProducts.length" class="product-grid">
      <ProductCard
        v-for="product in displayedProducts"
        :key="product.id"
        :product="product"
        :is-favorite="favoriteIds.includes(product.id)"
        @detail="selectedProduct = $event"
        @add="addToCart"
        @favorite="toggleFavorite"
      />
    </section>

    <section v-else class="empty-state">
      <h3>暂时没有结果</h3>
      <p>{{ emptyText }}</p>
      <button @click="clearFilter">返回全部商品</button>
    </section>

    <ProductModal
      :visible="Boolean(selectedProduct)"
      :product="selectedProduct"
      :is-favorite="selectedProduct ? favoriteIds.includes(selectedProduct.id) : false"
      @close="selectedProduct = null; route.query.detail && router.replace({ path: '/buyer/category', query: keyword ? { search: keyword } : {} })"
      @confirm="addFromModal"
      @favorite="toggleFavorite"
    />
  </AppShell>
</template>

<style scoped>
.toolbar,
.filter-panel,
.empty-state {
  border-radius: 28px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--shadow-card);
}

.toolbar {
  display: grid;
  grid-template-columns: 1fr 240px;
  gap: 16px;
}

.search-box {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.search-box input,
select {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 251, 247, 0.9);
}

.search-box button,
.clear-btn,
.empty-state button {
  padding: 12px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

.filter-panel {
  margin-top: 18px;
}

.filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.filter-head span {
  display: block;
  color: var(--ink-soft);
  margin-bottom: 4px;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}

.sub-filter {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(220, 196, 188, 0.6);
}

.sub-filter-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-soft);
}

.sub-filter-chips {
  margin-top: 12px;
}

.chip {
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
}

.sub-chip {
  background: rgba(255, 248, 244, 0.92);
}

.chip.active {
  background: linear-gradient(135deg, #ff91b2, #ffd085);
  color: white;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 20px;
}

.empty-state {
  margin-top: 18px;
  text-align: center;
}

.empty-state p {
  color: var(--ink-soft);
}

@media (max-width: 1080px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .toolbar,
  .search-box {
    grid-template-columns: 1fr;
  }

  .product-grid {
    grid-template-columns: 1fr;
  }

  .filter-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>