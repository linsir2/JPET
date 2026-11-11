<script setup lang="ts">
import { ref, watch } from 'vue'
import { getProductFallbackImage } from '@/utils/assets'
import type { Product } from '@/buyer/types/models';

const props = defineProps<{
  product: Product;
  isFavorite?: boolean;
}>();

const emit = defineEmits<{
  detail: [product: Product];
  add: [product: Product];
  favorite: [product: Product];
}>();

const imageSrc = ref(props.product.image)

const hasRating = () => Number.isFinite(props.product.rating) && props.product.rating > 0

function getFallbackImage() {
  return getProductFallbackImage(props.product)
}

watch(
  () => props.product.image,
  (value) => {
    imageSrc.value = value || getFallbackImage()
  },
  { immediate: true }
)

function handleImageError() {
  const fallbackImage = getFallbackImage()
  if (imageSrc.value !== fallbackImage) {
    imageSrc.value = fallbackImage
  }
}
</script>

<template>
  <article class="p-card" @click="emit('detail', product)">
    
    <div class="p-image-wrapper">
      <img :src="imageSrc" :alt="product.name" class="p-image" @error="handleImageError" />
      <div class="p-badges">
        <span v-for="tag in product.tags.slice(0, 2)" :key="tag" class="p-badge">{{ tag }}</span>
      </div>
      <button
        class="p-favorite-btn"
        :class="{ active: props.isFavorite }"
        @click.stop="emit('favorite', product)"
        :title="props.isFavorite ? '取消收藏' : '加入收藏'"
      >
        <span class="favorite-icon">{{ props.isFavorite ? '♥' : '♡' }}</span>
      </button>
      <button class="p-quick-add" @click.stop="emit('add', product)" title="快速加入购物车">
        <span class="plus-icon">+</span>
      </button>
    </div>

    <div class="p-info">
      <div class="p-meta">
        <span class="p-category">{{ product.category }}</span>
        <span class="p-rating">
          <template v-if="hasRating()">
            <i class="star">★</i> {{ product.rating.toFixed(1) }}
          </template>
          <template v-else>暂无评分</template>
        </span>
      </div>
      
      <h3 class="p-title">{{ product.name }}</h3>
      <p class="p-desc">{{ product.description }}</p>
      
      <div class="p-footer">
        <div class="p-price">
          <span class="currency">¥</span>
          <span class="amount">{{ product.price }}</span>
        </div>
        <div class="p-stats">
          已售 {{ product.purchaseCount }}
        </div>
      </div>
    </div>
    
  </article>
</template>

<style scoped>
.p-card {
  background: var(--bg-panel);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 12px 32px rgba(174, 106, 126, 0.08);
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  display: flex;
  flex-direction: column;
}

.p-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 24px 48px rgba(174, 106, 126, 0.16);
  border-color: #fff;
}

.p-image-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 1; /* 正方形大图 */
  background: linear-gradient(135deg, #fdfbfb, #f5f7fa);
  overflow: hidden;
}

.p-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s ease;
}

.p-card:hover .p-image {
  transform: scale(1.08);
}

.p-badges {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  gap: 6px;
  z-index: 2;
}

.p-badge {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-strong);
  letter-spacing: 0.5px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
}

.p-quick-add {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--brand-pink), var(--brand-peach));
  border: none;
  color: white;
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: 0 8px 16px rgba(255, 122, 168, 0.3);
  transform: translateY(20px);
  opacity: 0;
  transition: all 0.3s ease;
  z-index: 10;
}
.p-favorite-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  color: #ff6b9e;
  display: grid;
  place-items: center;
  cursor: pointer;
  z-index: 10;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.12);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.p-favorite-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 10px 20px rgba(255, 107, 158, 0.2);
}
.p-favorite-btn.active {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.96), rgba(255, 184, 115, 0.96));
  color: white;
}
.favorite-icon {
  font-size: 20px;
  line-height: 1;
}
.p-quick-add .plus-icon {
  font-size: 24px;
  font-weight: 300;
  line-height: 1;
}
.p-quick-add:hover {
  transform: scale(1.1) !important;
  box-shadow: 0 12px 24px rgba(255, 122, 168, 0.4);
}
.p-card:hover .p-quick-add {
  transform: translateY(0);
  opacity: 1;
}

/* Info Section */
.p-info {
  padding: 20px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.p-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.p-category {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-pink);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.p-rating {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-soft);
  display: flex;
  align-items: center;
  gap: 4px;
}
.star {
  color: #ffb171;
  font-style: normal;
}

.p-title {
  font-size: 18px;
  font-weight: 800;
  margin: 0 0 6px 0;
  color: var(--ink-strong);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.p-desc {
  font-size: 13px;
  color: var(--ink-soft);
  margin: 0 0 16px 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.p-footer {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.p-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--ink-strong);
}
.currency {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-soft);
}
.amount {
  font-size: 24px;
  font-family: var(--font-display);
  font-weight: 800;
  letter-spacing: -0.5px;
}

.p-stats {
  font-size: 12px;
  color: var(--ink-soft);
  font-weight: 500;
  background: var(--bg-soft);
  padding: 4px 8px;
  border-radius: 8px;
}
</style>