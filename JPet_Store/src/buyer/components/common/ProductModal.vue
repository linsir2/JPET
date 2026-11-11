<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { catalogApi } from '@/buyer/api/services'
import { getProductFallbackImage } from '@/utils/assets'
import type { CartItemSelection, Product } from '@/buyer/types/models'

const props = defineProps<{
  visible: boolean
  product: Product | null
  isFavorite?: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: [payload: { product: Product; quantity: number; selection: CartItemSelection }]
  favorite: [product: Product]
}>()

const quantity = ref(1)
const selection = ref<Record<string, string>>({})
const imageSrc = ref('')
const detailedProduct = ref<Product | null>(null)
const loadingDetail = ref(false)
const detailError = ref('')
let detailRequestToken = 0

const modalProduct = computed(() => detailedProduct.value ?? props.product)
const optionGroups = computed(() => modalProduct.value?.optionGroups ?? [])
const variants = computed(() => modalProduct.value?.variants ?? [])

const selectedVariant = computed(() => {
  if (!variants.value.length || !optionGroups.value.length) {
    return null
  }
  return (
    variants.value.find((variant) =>
      optionGroups.value.every((group) => {
        const selectedValue = selection.value[group.name]
        return !!selectedValue && variant.attributes[group.name] === selectedValue
      })
    ) ?? null
  )
})

function getFallbackImage(product: Product | null) {
  return getProductFallbackImage(product ?? undefined)
}

function buildInitialSelection(product: Product | null) {
  if (!product) {
    return {}
  }
  if (product.variants?.length) {
    return { ...product.variants[0].attributes }
  }
  const initial: Record<string, string> = {}
  product.optionGroups?.forEach((group) => {
    if (group.options.length) {
      initial[group.name] = group.options[0]
    }
  })
  return initial
}

function findCompatibleVariant(partialSelection: Record<string, string>) {
  return variants.value.find((variant) =>
    Object.entries(partialSelection).every(([key, value]) => !value || variant.attributes[key] === value)
  )
}

function isOptionDisabled(groupName: string, optionValue: string) {
  if (!variants.value.length) {
    return false
  }
  const nextSelection = { ...selection.value, [groupName]: optionValue }
  return !variants.value.some((variant) =>
    Object.entries(nextSelection).every(([key, value]) => !value || variant.attributes[key] === value)
  )
}

function pickOption(groupName: string, optionValue: string) {
  if (isOptionDisabled(groupName, optionValue)) {
    return
  }
  const nextSelection: Record<string, string> = { ...selection.value, [groupName]: optionValue }
  optionGroups.value.forEach((group) => {
    if (group.name === groupName) {
      return
    }
    const currentValue = nextSelection[group.name]
    if (!currentValue) {
      return
    }
    const matched = findCompatibleVariant(nextSelection)
    if (!matched) {
      delete nextSelection[group.name]
    }
  })
  selection.value = nextSelection
}

watch(
  () => modalProduct.value,
  (value) => {
    quantity.value = 1
    selection.value = buildInitialSelection(value)
    imageSrc.value = value?.image || getFallbackImage(value)
  },
  { immediate: true }
)

watch(
  () => selectedVariant.value,
  (variant) => {
    const product = modalProduct.value
    imageSrc.value = variant?.image || product?.image || getFallbackImage(product)
  },
  { immediate: true }
)

watch(
  () => [props.visible, props.product?.id] as const,
  async ([visible, productId]) => {
    detailRequestToken += 1
    const requestToken = detailRequestToken

    if (!visible || !productId || !props.product) {
      detailedProduct.value = null
      loadingDetail.value = false
      detailError.value = ''
      return
    }

    detailedProduct.value = props.product
    loadingDetail.value = true
    detailError.value = ''

    try {
      const detail = await catalogApi.getProduct(String(productId))
      if (requestToken !== detailRequestToken) {
        return
      }
      detailedProduct.value = detail
    } catch (error) {
      if (requestToken !== detailRequestToken) {
        return
      }
      detailError.value = error instanceof Error ? error.message : '商品详情加载失败'
    } finally {
      if (requestToken === detailRequestToken) {
        loadingDetail.value = false
      }
    }
  },
  { immediate: true }
)

const finalPrice = computed(() => {
  const product = modalProduct.value
  if (!product) {
    return 0
  }
  return (selectedVariant.value?.price ?? product.price) * quantity.value
})

const currentStock = computed(() => {
  const product = modalProduct.value
  return selectedVariant.value?.stock ?? product?.stock ?? 0
})

const canSubmit = computed(() => {
  if (!modalProduct.value) {
    return false
  }
  if (!variants.value.length || !optionGroups.value.length) {
    return true
  }
  return selectedVariant.value !== null
})

function handleImageError() {
  const fallbackImage = getFallbackImage(modalProduct.value)
  if (imageSrc.value !== fallbackImage) {
    imageSrc.value = fallbackImage
  }
}

function formatDelta(delta: number) {
  if (!delta) {
    return ''
  }
  const sign = delta > 0 ? '+' : '-'
  return `${sign}¥${Math.abs(delta)}`
}

function formatPrice(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

function confirm() {
  const product = modalProduct.value
  if (!product) {
    return
  }

  emit('confirm', {
    product,
    quantity: quantity.value,
    selection: Object.fromEntries(
      Object.entries(selection.value).filter(([, value]) => Boolean(value))
    ),
  })
}
</script>

<template>
  <teleport to="body">
    <div v-if="visible && modalProduct" class="overlay" @click.self="emit('close')">
      <div class="panel">
        <button class="close-btn" @click="emit('close')">✕</button>
        <div class="grid">
          <div class="hero-image">
            <img :src="imageSrc" :alt="modalProduct.name" @error="handleImageError" />
          </div>

          <div class="detail-copy">
            <span class="category-pill">{{ modalProduct.category }} · {{ modalProduct.subCategory }}</span>
            <h2>{{ modalProduct.name }}</h2>
            <p>{{ modalProduct.description }}</p>

            <div v-if="loadingDetail" class="detail-status">正在加载完整商品规格...</div>
            <div v-else-if="detailError" class="detail-status error">{{ detailError }}</div>

            <div v-for="group in optionGroups" :key="group.name" class="option-group">
              <span>{{ group.name }}</span>
              <div class="chips">
                <button
                  v-for="option in group.options"
                  :key="option"
                  class="chip"
                  :class="{ active: selection[group.name] === option, disabled: isOptionDisabled(group.name, option) }"
                  :disabled="isOptionDisabled(group.name, option)"
                  @click="pickOption(group.name, option)"
                >
                  <span>{{ option }}</span>
                </button>
              </div>
            </div>

            <div v-if="currentStock > 0" class="stock-tip">当前库存 {{ currentStock }} 件</div>
            <div v-else class="detail-status error">当前组合暂无可售库存</div>

            <div class="quantity-row">
              <span>数量</span>
              <div class="stepper">
                <button @click="quantity = Math.max(1, quantity - 1)">-</button>
                <strong>{{ quantity }}</strong>
                <button @click="quantity += 1">+</button>
              </div>
            </div>

            <div class="bottom-bar">
              <div>
                <small>当前合计</small>
                <strong>¥{{ formatPrice(finalPrice) }}</strong>
              </div>
              <div class="bottom-actions">
                <button
                  class="favorite-btn"
                  :class="{ active: props.isFavorite }"
                  @click="modalProduct && emit('favorite', modalProduct)"
                >
                  {{ props.isFavorite ? '取消收藏' : '加入收藏' }}
                </button>
                <button class="submit-btn" :disabled="!canSubmit" @click="confirm">加入购物车</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(58, 39, 52, 0.48);
  backdrop-filter: blur(14px);
  z-index: 50;
}

.panel {
  position: relative;
  width: min(980px, 100%);
  border-radius: 32px;
  padding: 28px;
  background: rgba(255, 249, 244, 0.96);
  box-shadow: 0 30px 80px rgba(48, 31, 38, 0.26);
}

.close-btn {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
}

.grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 28px;
}

.hero-image {
  border-radius: 26px;
  overflow: hidden;
  min-height: 360px;
  background: #fff;
}

.hero-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.category-pill {
  display: inline-flex;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 160, 190, 0.18);
  color: #d46089;
}

.detail-copy h2 {
  margin: 18px 0 10px;
  font-size: 34px;
}

.detail-copy p {
  margin: 0 0 18px;
  color: var(--ink-soft);
  line-height: 1.7;
}

.detail-status {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--ink-soft);
  font-size: 14px;
}

.detail-status.error {
  color: #b45309;
  background: rgba(254, 243, 199, 0.9);
}

.option-group,
.quantity-row,
.bottom-bar {
  margin-top: 18px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.chip {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.chip.active {
  background: linear-gradient(135deg, #ff8fb1, #ffc97f);
  color: #fff;
}

.chip small {
  font-size: 12px;
  opacity: 0.85;
}

.stock-tip {
  margin-top: 14px;
  color: var(--ink-soft);
  font-size: 14px;
}

.quantity-row,
.bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
}

.stepper button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 122, 168, 0.12);
}

.bottom-bar strong {
  display: block;
  margin-top: 6px;
  font-size: 32px;
  color: #ea5b90;
}

.bottom-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.favorite-btn {
  padding: 14px 24px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(255, 143, 177, 0.35);
  color: #ff6b9e;
}

.favorite-btn.active {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.96), rgba(255, 184, 115, 0.96));
  border-color: transparent;
  color: white;
}

.submit-btn {
  padding: 14px 24px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

@media (max-width: 800px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .panel {
    padding: 20px;
  }

  .quantity-row,
  .bottom-bar,
  .bottom-actions {
    flex-direction: column;
    align-items: stretch;
  }
}

.chip.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>