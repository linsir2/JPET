<script setup>
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { getImagePath } from '@/utils/imagePath'
import { calculateProductPrice } from '@/utils/productPrice'
import { lockBodyScroll, unlockBodyScroll } from '@/utils/scrollLock'
import { useCatalogStore } from '@/stores/catalog'
import { useLocalOffShelfStore } from '@/stores/offShelfLocal'

const props = defineProps({
  modelValue: Boolean,
  product: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'product-updated'])

const selected = reactive({ color: null, size: null, spec: null })
const editedDescription = ref('')
const editingDescription = ref(false)
const customBasePrice = ref(null)
const specCustomPrices = reactive({})
const editableStock = ref(0)
const reviewsExpanded = ref(false)

const productReviews = computed(() => {
  if (!props.product) return []
  if (Array.isArray(props.product.buyerReviews) && props.product.buyerReviews.length) {
    return props.product.buyerReviews
  }
  return []
})

const reviewBeltItems = computed(() => productReviews.value)
const hasReviews = computed(() => productReviews.value.length > 0)

watch(
  () => props.product,
  (p) => {
    if (!p) return
    reviewsExpanded.value = false
    selected.color = p.colors?.length ? p.colors[0] : null
    selected.size = p.sizes?.length ? p.sizes[0] : null
    selected.spec = p.specifications?.length ? p.specifications[0] : null
    editedDescription.value = p.detailDescription || ''
    editingDescription.value = false
    editableStock.value = Number(p.stock || 0)
    customBasePrice.value = null
    Object.keys(specCustomPrices).forEach((k) => delete specCustomPrices[k])
  },
  { immediate: true },
)

const fallbackPrice = computed(() =>
  props.product ? calculateProductPrice(props.product, selected.spec, selected.size) : 0,
)

function getVariantList(product) {
  return Array.isArray(product?.variantPrices) ? product.variantPrices : []
}

function findVariantBySpec(product, spec) {
  const normalizedSpec = String(spec ?? '').trim().toLowerCase()
  if (!normalizedSpec) return null

  return getVariantList(product).find((variant) => {
    const combination = String(variant?.combination ?? '').trim().toLowerCase()
    if (combination === normalizedSpec) return true
    return Object.values(variant?.values || {}).some(
      (value) => String(value ?? '').trim().toLowerCase() === normalizedSpec,
    )
  }) ?? null
}

const currentPrice = computed(() => {
  if (!props.product) return 0
  if (selected.spec && specCustomPrices[selected.spec] !== undefined) {
    return specCustomPrices[selected.spec]
  }
  if (customBasePrice.value !== null) {
    return customBasePrice.value
  }
  return fallbackPrice.value
})

const originalPrice = computed(() => Math.floor(currentPrice.value * 1.2))

watch(
  () => props.modelValue,
  (open) => {
    if (open) lockBodyScroll()
    else unlockBodyScroll()
  },
)

function close() {
  emit('update:modelValue', false)
  unlockBodyScroll()
}

function pickOption(type, value) {
  if (type === 'color') selected.color = value
  if (type === 'size') selected.size = value
  if (type === 'spec') selected.spec = value
}

function editDescription() {
  editingDescription.value = true
}

function saveDescription() {
  editingDescription.value = false
  if (props.product) {
    props.product.detailDescription = editedDescription.value.trim()
    emit('product-updated', { id: props.product.id, field: 'detailDescription' })
  }
}

/** 将规格价写回商品对象，供列表/外部与 calculateProductPrice 读取 */
function applySpecPrice(product, spec, price) {
  if (!product || !spec) return
  const n = Number(Number(price).toFixed(2))
  if (!product.specificationPrices || typeof product.specificationPrices !== 'object') {
    product.specificationPrices = {}
  }
  product.specificationPrices[spec] = n
  if (product.attributes && typeof product.attributes === 'object') {
    if (!product.attributes.specificationPrices || typeof product.attributes.specificationPrices !== 'object') {
      product.attributes.specificationPrices = {}
    }
    product.attributes.specificationPrices[spec] = n
  }
}

function applyVariantPrice(product, spec, price) {
  const variant = findVariantBySpec(product, spec)
  if (!variant) return false
  variant.price = Number(Number(price).toFixed(2))
  return true
}

/** 多规格时，列表展示价取各规格最低价；并同步 minPrice（商家 SPU 列表用） */
function syncMainPriceFromSpecs(product) {
  if (!product) return
  const sp =
    product.specificationPrices ||
    (product.attributes && product.attributes.specificationPrices)
  if (sp && typeof sp === 'object') {
    const nums = Object.values(sp)
      .map((x) => parseFloat(x))
      .filter((n) => Number.isFinite(n) && n > 0)
    if (nums.length) {
      const min = Math.min(...nums)
      product.price = min
      if (product.minPrice != null && product.minPrice !== undefined) {
        product.minPrice = min
      }
    }
  }
}

function syncMainPriceFromVariants(product) {
  if (!product) return
  const nums = getVariantList(product)
    .map((variant) => Number(variant?.price))
    .filter((price) => Number.isFinite(price) && price > 0)

  if (nums.length) {
    const min = Math.min(...nums)
    product.price = min
    if (product.minPrice != null && product.minPrice !== undefined) {
      product.minPrice = min
    }
  }
}

/** 按尺寸计价（无「规格」仅有「尺寸」时，改价必须写 sizePrices，否则界面仍显示旧价） */
function applySizePrice(product, size, price) {
  if (!product || !size) return
  const n = Number(Number(price).toFixed(2))
  if (!product.sizePrices || typeof product.sizePrices !== 'object') {
    product.sizePrices = {}
  }
  product.sizePrices[size] = n
  if (product.attributes && typeof product.attributes === 'object') {
    if (!product.attributes.sizePrices || typeof product.attributes.sizePrices !== 'object') {
      product.attributes.sizePrices = {}
    }
    product.attributes.sizePrices[size] = n
  }
}

function syncMainPriceFromSizes(product) {
  if (!product) return
  const sz = product.sizePrices || (product.attributes && product.attributes.sizePrices)
  if (sz && typeof sz === 'object') {
    const nums = Object.values(sz)
      .map((x) => parseFloat(x))
      .filter((n) => Number.isFinite(n) && n > 0)
    if (nums.length) {
      const min = Math.min(...nums)
      product.price = min
      if (product.minPrice != null && product.minPrice !== undefined) {
        product.minPrice = min
      }
    }
  }
}

function notifyProductUpdated(field) {
  if (props.product) emit('product-updated', { id: props.product.id, field })
}

function editCurrentPrice() {
  const current = currentPrice.value.toFixed(2)
  const input = window.prompt('请输入新的价格（元）', current)
  if (input === null) return
  const next = Number(input)
  if (!Number.isFinite(next) || next <= 0) {
    alert('请输入有效的价格')
    return
  }
  const p = props.product
  const v = Number(next.toFixed(2))
  if (selected.spec && applyVariantPrice(p, selected.spec, v)) {
    applySpecPrice(p, selected.spec, v)
    delete specCustomPrices[selected.spec]
    syncMainPriceFromVariants(p)
  } else if (selected.spec) {
    applySpecPrice(p, selected.spec, v)
    delete specCustomPrices[selected.spec]
    syncMainPriceFromSpecs(p)
  } else if (selected.size) {
    applySizePrice(p, selected.size, v)
    syncMainPriceFromSizes(p)
  } else {
    p.price = v
    if (p.minPrice != null && p.minPrice !== undefined) p.minPrice = v
  }
  customBasePrice.value = null
  notifyProductUpdated('price')
}

function getSpecPrice(spec) {
  if (!props.product) return 0
  const variant = findVariantBySpec(props.product, spec)
  if (variant?.price != null) return Number(variant.price) || 0
  if (specCustomPrices[spec] !== undefined) return specCustomPrices[spec]
  return calculateProductPrice(props.product, spec, null)
}

function editSpecPrice(spec) {
  const input = window.prompt(`设置 ${spec} 的价格（元）`, getSpecPrice(spec).toFixed(2))
  if (input === null) return
  const next = Number(input)
  if (!Number.isFinite(next) || next <= 0) {
    alert('请输入有效的价格')
    return
  }
  const v = Number(next.toFixed(2))
  if (applyVariantPrice(props.product, spec, v)) {
    applySpecPrice(props.product, spec, v)
    delete specCustomPrices[spec]
    syncMainPriceFromVariants(props.product)
    notifyProductUpdated('price')
    return
  }
  applySpecPrice(props.product, spec, v)
  delete specCustomPrices[spec]
  syncMainPriceFromSpecs(props.product)
  notifyProductUpdated('price')
}

function changeStock(delta) {
  const next = Math.max(0, Number(editableStock.value || 0) + delta)
  editableStock.value = next
  if (props.product) {
    props.product.stock = next
    props.product.stockCount = next
    notifyProductUpdated('stock')
  }
}

function applyStock() {
  const next = Math.max(0, Math.floor(Number(editableStock.value || 0)))
  editableStock.value = next
  if (props.product) {
    props.product.stock = next
    props.product.stockCount = next
    notifyProductUpdated('stock')
  }
  alert(`库存已更新为 ${next} 件（演示模式）`)
}

function temporaryOffShelf() {
  if (!props.product) return
  const ok = window.confirm(`确定将「${props.product.name}」暂时下架吗？`)
  if (!ok) return
  const snap = { ...props.product }
  const offId = props.product.id ?? props.product.spuId
  useLocalOffShelfStore().mark(offId, snap)
  props.product.status = '已下架'
  // 商品页可能编辑的是 merchantProducts 里的对象，审核页合并的是 catalog：同 id 一并标记
  const catalog = useCatalogStore()
  const twin = catalog.getById(offId)
  if (twin && twin !== props.product) {
    twin.status = '已下架'
  }
  emit('product-updated', { id: offId, field: 'status', value: '已下架' })
  alert('商品已暂时下架（演示模式）')
  close()
}

function starsText(n) {
  const full = Math.round(Number(n) || 0)
  return '★'.repeat(full) + '☆'.repeat(Math.max(0, 5 - full))
}

function toggleReviewsMore() {
  reviewsExpanded.value = !reviewsExpanded.value
}

onUnmounted(() => {
  unlockBodyScroll()
})
</script>

<template>
  <div
    v-if="modelValue && product"
    id="productDetailModal"
    class="product-detail-overlay"
    style="display: flex"
    @click="close"
  >
    <div class="product-detail-container" @click.stop>
      <button type="button" class="detail-close-btn" @click="close">✕</button>
      <div class="detail-content" id="productDetailContent">
        <div class="detail-left">
          <div class="detail-image-box">
            <img :src="getImagePath(product.image)" :alt="product.name" />
          </div>
          <div class="detail-sales-info">
            <span class="sales-count">已售 {{ product.sales }} 件</span>
            <span class="stock-count">库存 {{ product.stock }} 件</span>
          </div>
          <div class="detail-description detail-description-left" @click="editDescription">
            <h3>商品介绍（点击编辑）</h3>
            <textarea
              v-if="editingDescription"
              v-model="editedDescription"
              class="description-editor"
              rows="5"
              @click.stop
              @blur="saveDescription"
            />
            <p v-else>{{ editedDescription || '点击这里填写商品介绍' }}</p>
          </div>

          <!-- 商品介绍下方：浅色评论传送带，点击查看全部 -->
          <div class="detail-description pdm-reviews-wrap" @click.stop>
            <div class="pdm-reviews-head">
              <h3>商品用户评价</h3>
              <button
                type="button"
                class="spec-edit-btn"
                :disabled="!hasReviews"
                @click="toggleReviewsMore"
              >
                {{ hasReviews ? (reviewsExpanded ? '收起评论' : '查看全部评论') : '暂无评价' }}
              </button>
            </div>
            <div v-if="hasReviews" class="pdm-reviews-viewport">
              <div class="pdm-reviews-track">
                <div
                  v-for="r in reviewBeltItems"
                  :key="'a-' + r.id"
                  class="pdm-reviews-card"
                  role="button"
                  tabindex="0"
                  @click="reviewsExpanded = true"
                  @keydown.enter.prevent="reviewsExpanded = true"
                >
                  <div class="pdm-reviews-card-top">
                    <span class="pdm-reviews-user">{{ r.user }}</span>
                    <span class="pdm-reviews-stars">{{ starsText(r.rating) }}</span>
                  </div>
                  <p class="pdm-reviews-snippet">{{ r.text }}</p>
                  <span class="pdm-reviews-date">{{ r.date }}</span>
                </div>
                <div
                  v-for="r in reviewBeltItems"
                  :key="'b-' + r.id"
                  class="pdm-reviews-card"
                  role="button"
                  tabindex="0"
                  @click="reviewsExpanded = true"
                  @keydown.enter.prevent="reviewsExpanded = true"
                >
                  <div class="pdm-reviews-card-top">
                    <span class="pdm-reviews-user">{{ r.user }}</span>
                    <span class="pdm-reviews-stars">{{ starsText(r.rating) }}</span>
                  </div>
                  <p class="pdm-reviews-snippet">{{ r.text }}</p>
                  <span class="pdm-reviews-date">{{ r.date }}</span>
                </div>
              </div>
            </div>
            <div v-else class="pdm-reviews-empty">暂无真实用户评价，成交后才会显示。</div>
            <transition name="pdm-reviews-expand">
              <div v-if="reviewsExpanded && hasReviews" class="pdm-reviews-more">
                <div
                  v-for="r in productReviews"
                  :key="'full-' + r.id"
                  class="pdm-reviews-more-row"
                >
                  <div class="pdm-reviews-more-meta">
                    <strong>{{ r.user }}</strong>
                    <span class="pdm-reviews-stars">{{ starsText(r.rating) }}</span>
                    <span class="pdm-reviews-more-date">{{ r.date }}</span>
                  </div>
                  <p>{{ r.text }}</p>
                </div>
              </div>
            </transition>
          </div>
        </div>
        <div class="detail-right">
          <h2 class="detail-product-name" :data-product-id="product.id">{{ product.name }}</h2>
          <p class="detail-product-category">{{ product.category }}</p>
          <div class="detail-price-box clickable-price" @click="editCurrentPrice">
            <span id="detailPrice" class="detail-price">¥{{ currentPrice.toFixed(2) }}</span>
            <span id="detailOriginalPrice" class="detail-original-price"
              >¥{{ originalPrice.toFixed(2) }}</span
            >
            <span class="price-edit-tip">点击价格可修改</span>
          </div>
          <div v-if="product.weight" class="detail-spec-info">
            <span class="spec-label">重量：</span>
            <span class="spec-value">{{ product.weight }}</span>
          </div>
          <div v-if="product.dimensions" class="detail-spec-info">
            <span class="spec-label">商品尺寸：</span>
            <span class="spec-value">{{ product.dimensions }}</span>
          </div>
          <div v-if="product.colors?.length" class="detail-option-box">
            <h4>选择颜色</h4>
            <div id="colorOptions" class="option-buttons">
              <button
                v-for="(c, idx) in product.colors"
                :key="'c' + idx"
                type="button"
                class="option-btn"
                :class="{ active: selected.color === c }"
                @click="pickOption('color', c)"
              >
                {{ c }}
              </button>
            </div>
          </div>
          <div v-if="product.sizes?.length" class="detail-option-box">
            <h4>选择尺寸</h4>
            <div id="sizeOptions" class="option-buttons">
              <button
                v-for="(s, idx) in product.sizes"
                :key="'s' + idx"
                type="button"
                class="option-btn"
                :class="{ active: selected.size === s }"
                @click="pickOption('size', s)"
              >
                {{ s }}
              </button>
            </div>
          </div>
          <div v-if="product.specifications?.length" class="detail-option-box">
            <h4>选择规格</h4>
            <div id="specOptions" class="option-buttons">
              <button
                v-for="(sp, idx) in product.specifications"
                :key="'sp' + idx"
                type="button"
                class="option-btn"
                :class="{ active: selected.spec === sp }"
                @click="pickOption('spec', sp)"
              >
                {{ sp }}
              </button>
            </div>
            <div class="spec-price-editor">
              <div v-for="(sp, idx) in product.specifications" :key="'spep' + idx" class="spec-price-row">
                <span class="spec-name">{{ sp }}</span>
                <span class="spec-price">¥{{ getSpecPrice(sp).toFixed(2) }}</span>
                <button type="button" class="spec-edit-btn" @click="editSpecPrice(sp)">修改</button>
              </div>
            </div>
          </div>
          <div class="detail-option-box">
            <h4>库存情况</h4>
            <div class="stock-control">
              <button type="button" class="stock-btn" @click="changeStock(-1)">-</button>
              <input
                id="detailStock"
                v-model.number="editableStock"
                type="number"
                class="stock-input"
                min="0"
              />
              <button type="button" class="stock-btn" @click="changeStock(1)">+</button>
              <button type="button" class="stock-apply-btn" @click="applyStock">保存库存</button>
            </div>
          </div>
          <div class="detail-actions">
            <button type="button" class="detail-off-shelf-btn" @click="temporaryOffShelf">
              暂时下架商品
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pdm-reviews-wrap {
  cursor: default;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.pdm-reviews-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.pdm-reviews-head h3 {
  margin: 0;
}

/* 浅色传送带区域 */
.pdm-reviews-viewport {
  overflow: hidden;
  max-width: 100%;
  margin: 0 -4px;
  padding: 8px 4px;
  background: #fafbfc;
  border-radius: 10px;
  border: 1px solid #eef2f6;
  contain: layout;
}

.pdm-reviews-empty {
  padding: 18px 14px;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
  background: #fafbfc;
  border: 1px dashed #dbe4f0;
  border-radius: 10px;
}

.pdm-reviews-track {
  display: flex;
  gap: 10px;
  width: max-content;
  animation: pdmReviewsMarquee 36s linear infinite;
}

.pdm-reviews-viewport:hover .pdm-reviews-track {
  animation-play-state: paused;
}

@keyframes pdmReviewsMarquee {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-50%);
  }
}

.pdm-reviews-card {
  flex: 0 0 auto;
  width: 200px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
}

.pdm-reviews-card:hover {
  border-color: #dbe4f0;
}

.pdm-reviews-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.pdm-reviews-user {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.pdm-reviews-stars {
  font-size: 11px;
  color: #e8a317;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.pdm-reviews-snippet {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #666;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pdm-reviews-date {
  font-size: 11px;
  color: #999;
}

.pdm-reviews-more {
  margin-top: 12px;
  max-height: 200px;
  overflow-y: auto;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.pdm-reviews-more-row {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.pdm-reviews-more-row:last-child {
  border-bottom: none;
}

.pdm-reviews-more-row p {
  margin: 6px 0 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: #666;
}

.pdm-reviews-more-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #333;
}

.pdm-reviews-more-date {
  margin-left: auto;
  font-size: 12px;
  color: #999;
}

.pdm-reviews-expand-enter-active,
.pdm-reviews-expand-leave-active {
  transition: opacity 0.2s ease;
}

.pdm-reviews-expand-enter-from,
.pdm-reviews-expand-leave-to {
  opacity: 0;
}
</style>
