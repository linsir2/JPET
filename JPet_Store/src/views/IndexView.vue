<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ProductDetailModal from '@/components/ProductDetailModal.vue'
import { merchantApi, merchantOrderApi } from '@/api/merchant'
import { getContextPath } from '@/utils/context'
import { getImagePath } from '@/utils/imagePath'
import { listMerchantContacts, syncMerchantOfficialThread, syncMerchantOrderThreads } from '@/utils/orderChat'
import { useAuthStore } from '@/stores/auth'
import { useCatalogStore } from '@/stores/catalog'
import { useCartBadgeStore } from '@/stores/cartBadge'
import { useLocalOffShelfStore } from '@/stores/offShelfLocal'

import '@/styles/legacy/common.css'
import '@/styles/legacy/productDetail.css'
import '@/styles/legacy/index.css'

const ctx = getContextPath()
const router = useRouter()
const auth = useAuthStore()
const catalog = useCatalogStore()
const cartBadge = useCartBadgeStore()
const localOffShelf = useLocalOffShelfStore()

const bannerData = [
  {
    image: getImagePath('images/沙发小猫.jpg'),
    title: '温暖陪伴，舒适生活',
    subtitle: '给毛孩子一个温馨的家',
  },
  {
    image: getImagePath('images/草地小狗.jpg'),
    title: '自由奔跑，快乐成长',
    subtitle: '让爱宠享受健康快乐每一天',
  },
  {
    image: getImagePath('images/yL1CSVRwjzJExhRPqQt0nw3c3bf89dd6ed5b79d3ddfd7e5634345a.jpg'),
    title: '优质宠物用品专家',
    subtitle: '为爱宠精选每一件好物',
  },
]

const currentBannerIndex = ref(0)
const bannerTitle = computed(() => bannerData[currentBannerIndex.value].title)
const bannerSubtitle = computed(() => bannerData[currentBannerIndex.value].subtitle)
const bannerImage = computed(() => bannerData[currentBannerIndex.value].image)

let bannerTimer = null

function prevBanner() {
  currentBannerIndex.value =
    (currentBannerIndex.value - 1 + bannerData.length) % bannerData.length
}

function nextBanner() {
  currentBannerIndex.value = (currentBannerIndex.value + 1) % bannerData.length
}

function gotoBanner(i) {
  currentBannerIndex.value = i
}

function startBannerAutoPlay() {
  if (bannerTimer) clearInterval(bannerTimer)
  bannerTimer = setInterval(nextBanner, 3000)
}

function isOffShelf(product) {
  if (String(product?.status || '') === '已下架') return true
  return localOffShelf.isRowMarkedOffShelf(product)
}

const hotProducts = computed(() =>
  [...catalog.products]
    .filter((p) => !isOffShelf(p))
    .sort((a, b) => Number(b?.purchaseCount ?? b?.sales ?? 0) - Number(a?.purchaseCount ?? a?.sales ?? 0))
    .slice(0, 4),
)

const hotProductsWithMetrics = computed(() =>
  hotProducts.value.map((product, index) => {
    const purchaseCount = Number(product.purchaseCount ?? product.sales ?? 0)
    const stockCount = Number(product.stock || 0)
    const heatScore = Math.max(1, Math.round(purchaseCount / 20) + (index < 2 ? 2 : 1))
    return { ...product, purchaseCount, stockCount, heatScore }
  }),
)

const buyerReviews = computed(() => {
  const reviews = []
  hotProductsWithMetrics.value.forEach((product) => {
    const productReviews = Array.isArray(product?.buyerReviews) ? product.buyerReviews : []
    productReviews.forEach((review, index) => {
      reviews.push({
        id: review?.id ?? `${product.id}-review-${index}`,
        user: review?.user || review?.username || '匿名用户',
        avatar: review?.avatar || 'images/logo192.png',
        rating: Number(review?.rating || 0),
        content: review?.content || review?.text || '',
        productName: product.name,
        productImage: product.image,
      })
    })
  })
  return reviews
})

const detailOpen = ref(false)
const detailProduct = ref(null)

function openProductDetail(product) {
  const pid = product?.id ?? product?.spuId
  detailProduct.value = catalog.getById(pid) || product
  detailOpen.value = true
}

function closeProductDetail() {
  detailOpen.value = false
}

const buyerChatRows = ref([])
let merchantChatTimer = null

function isImageAvatar(value) {
  const raw = String(value || '').trim()
  return Boolean(raw) && (
    /^(https?:)?\/\//.test(raw)
    || raw.startsWith('/')
    || raw.startsWith('images/')
    || raw.startsWith('uploads/')
  )
}

function getStoreNameByCategory(category) {
  const storeMap = {
    猫咪用品: '喵星人专卖店',
    狗狗用品: '汪星人生活馆',
    饮食用品: '宠物营养中心',
    出行用品: '宠物出行专家',
    狗狗: '宠物之家',
  }
  return storeMap[category] || '宠物用品店'
}

function getStoreAvatar(category) {
  const avatarMap = {
    猫咪用品: '🐱',
    狗狗用品: '🐶',
    饮食用品: '🍖',
    出行用品: '✈️',
    狗狗: '🏠',
  }
  return avatarMap[category] || '🏪'
}

async function loadMerchantChatRows() {
  try {
    const [profile, applicationStatus, currentOrders, historyOrders] = await Promise.all([
      merchantApi.getProfile(),
      merchantApi.getApplicationStatus(),
      merchantOrderApi.list(),
      merchantOrderApi.listHistory(),
    ])
    const orders = [
      ...(Array.isArray(currentOrders?.list) ? currentOrders.list : []),
      ...(Array.isArray(historyOrders?.list) ? historyOrders.list : []),
    ]
    syncMerchantOrderThreads(orders, {
      merchantUserId: String(applicationStatus?.merchantId || profile?.id || ''),
      merchantName: profile?.merchantName || auth.welcomeUsername,
    })
    syncMerchantOfficialThread({
      merchantUserId: String(applicationStatus?.merchantId || profile?.id || ''),
      merchantName: profile?.merchantName || auth.welcomeUsername,
    })
    buyerChatRows.value = listMerchantContacts(String(applicationStatus?.merchantId || profile?.id || ''))
  } catch {
    buyerChatRows.value = []
  }
}

function handleChatStorageChange(event) {
  if (event?.key && event.key !== 'jpet-store:order-chat:v1') {
    return
  }
  void loadMerchantChatRows()
}

function openChat(chatRow) {
  router.push({
    path: '/chat',
    query: {
      orderId: chatRow.orderId,
      threadId: chatRow.id,
    },
  })
}

function logout() {
  auth.logout()
}

const searchInput = ref('')
const suggestions = ref([])
const suggestionsVisible = ref(false)
let searchTimeout = null

function hideSuggestions() {
  suggestionsVisible.value = false
}

function searchProductsForAutocomplete(keyword) {
  if (!keyword?.trim()) {
    hideSuggestions()
    return
  }
  fetch(`${ctx}/api/products?keyword=${encodeURIComponent(keyword.trim())}`, {
    credentials: 'include',
  })
    .then((r) => r.json())
    .then((result) => {
      if (result.code === 200 && result.data) {
        suggestions.value = result.data.slice(0, 10)
        suggestionsVisible.value = true
      } else hideSuggestions()
    })
    .catch(() => hideSuggestions())
}

function onSearchInput() {
  const keyword = searchInput.value.trim()
  clearTimeout(searchTimeout)
  if (!keyword) {
    hideSuggestions()
    return
  }
  searchTimeout = setTimeout(() => searchProductsForAutocomplete(keyword), 300)
}

function highlightKeyword(text, keyword) {
  if (!keyword) return text
  const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

function selectSuggestion(product) {
  hideSuggestions()
  openProductDetail(product)
}

function performSearch() {
  const keyword = searchInput.value.trim()
  hideSuggestions()
  if (!keyword) {
    alert('请输入搜索关键词')
    return
  }
  router.push({ path: '/category', query: { search: keyword } })
}

function onDocClick(e) {
  const el = e.target
  if (!el.closest?.('.search-box-wrapper')) hideSuggestions()
}

const tooltip = ref(null)
const tooltipStyle = ref({})
let tooltipTimeout = null
const TOOLTIP_WIDTH = 340
const TOOLTIP_GAP = 16

function showProductTooltip(event, product) {
  clearTimeout(tooltipTimeout)
  const pid = product?.id ?? product?.spuId
  const canonical = catalog.getById(pid) || product
  tooltip.value = canonical
  const card = event.currentTarget
  if (!card?.getBoundingClientRect) {
    tooltipStyle.value = {}
    return
  }

  const rect = card.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const preferRight = rect.right + TOOLTIP_GAP + TOOLTIP_WIDTH <= viewportWidth - 20
  const left = preferRight
    ? rect.right + TOOLTIP_GAP
    : Math.max(20, rect.left - TOOLTIP_WIDTH - TOOLTIP_GAP)
  const top = Math.min(
    Math.max(20, rect.top + 12),
    Math.max(20, viewportHeight - 220),
  )

  tooltipStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
  }
}

function hideProductTooltip() {
  tooltipTimeout = setTimeout(() => {
    tooltip.value = null
  }, 200)
}

function onTooltipEnter() {
  clearTimeout(tooltipTimeout)
}

const friendWrapper = ref(null)

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  window.addEventListener('storage', handleChatStorageChange)
  await catalog.loadAll()
  startBannerAutoPlay()
  await auth.refresh()
  await cartBadge.refresh()
  await loadMerchantChatRows()
  merchantChatTimer = setInterval(() => {
    void loadMerchantChatRows()
  }, 3000)

  const wrapper = friendWrapper.value
  const scroller = wrapper?.querySelector?.('#friendPostsScroller')
  if (wrapper && scroller) {
    const posts = Array.from(scroller.children)
    if (posts.length) {
      posts.forEach((post) => scroller.appendChild(post.cloneNode(true)))
    }
    let isPaused = false
    let touchTimeoutId = null
    const pause = () => {
      if (isPaused) return
      scroller.style.animationPlayState = 'paused'
      isPaused = true
    }
    const resume = () => {
      if (!isPaused) return
      scroller.style.animationPlayState = 'running'
      isPaused = false
    }
    wrapper.addEventListener('mouseenter', pause)
    wrapper.addEventListener('mouseleave', resume)
    wrapper.addEventListener(
      'touchstart',
      () => {
        pause()
        if (touchTimeoutId) clearTimeout(touchTimeoutId)
      },
      { passive: true },
    )
    wrapper.addEventListener('touchend', () => {
      if (touchTimeoutId) clearTimeout(touchTimeoutId)
      touchTimeoutId = setTimeout(resume, 1200)
    })
  }
})

watch(
  () => auth.user,
  () => {
    cartBadge.refresh()
  },
)

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('storage', handleChatStorageChange)
  if (bannerTimer) clearInterval(bannerTimer)
  if (merchantChatTimer) clearInterval(merchantChatTimer)
})
</script>

<template>
  <div class="App">

    <div class="top-action-row">
      <div class="search-bar-container">
        <div class="search-box-wrapper">
        <input
          id="productSearchInput"
          v-model="searchInput"
          type="text"
          class="search-input"
          placeholder="搜索商品名称..."
          autocomplete="off"
          @input="onSearchInput"
          @keydown.enter.prevent="performSearch"
          @keydown.esc="hideSuggestions"
        />
        <button type="button" class="search-button" @click="performSearch">🔍</button>
        <div
          v-show="suggestionsVisible"
          id="searchSuggestions"
          class="search-suggestions"
        >
          <div v-if="!suggestions.length" class="no-suggestions">未找到相关商品</div>
          <div
            v-for="p in suggestions"
            v-else
            :key="p.id"
            class="search-suggestion-item"
            @click="selectSuggestion(p)"
          >
            <img :src="getImagePath(p.image)" :alt="p.name" class="suggestion-image" />
            <div class="suggestion-info">
              <div class="suggestion-name" v-html="highlightKeyword(p.name, searchInput.trim())" />
              <div class="suggestion-price">¥{{ Number(p.price).toFixed(2) }}</div>
            </div>
          </div>
        </div>
        </div>
      </div>

      <div class="user-info-bar">
        <div class="welcome-card">
          <div class="welcome-copy">
            <span class="welcome-label">欢迎</span>
            <span class="welcome-text">{{ auth.welcomeUsername }}</span>
          </div>
        </div>
        <button class="logout-btn logout-btn-inline" type="button" @click="logout">退出登录</button>
      </div>
    </div>

    <div class="shop-head-box">
      <div class="banner-title">
        <h2 id="bannerTitle">{{ bannerTitle }}</h2>
        <p id="bannerSubtitle" class="banner-subtitle">{{ bannerSubtitle }}</p>
      </div>
      <img id="bannerImage" :src="bannerImage" alt="轮播图" class="banner-image" />
      <div class="banner-nav-shell banner-nav-shell--left" style="left: 16px; right: auto; top: 50%; transform: translateY(-50%);">
        <button type="button" class="banner-btn-left banner-btn-standalone" @click="prevBanner">‹</button>
      </div>
      <div class="banner-nav-shell banner-nav-shell--right" style="right: 16px; left: auto; top: 50%; transform: translateY(-50%);">
        <button type="button" class="banner-btn-right banner-btn-standalone" @click="nextBanner">›</button>
      </div>
      <div id="bannerIndicators" class="banner-indicators">
        <span
          v-for="(_, i) in bannerData"
          :key="i"
          class="indicator"
          :class="{ active: i === currentBannerIndex }"
          @click="gotoBanner(i)"
        />
      </div>
    </div>

    <div class="shop-main-ui">
      <div ref="friendWrapper" class="left-pyq-box">
        <h3>📝 商品用户评价</h3>
        <div id="friendPostsWrapper" class="friend-posts-wrapper">
          <div v-if="buyerReviews.length" id="friendPostsScroller" class="friend-posts-scroller">
            <div v-for="(review, idx) in buyerReviews" :key="idx" class="friend-post review-item">
              <img
                :src="getImagePath(review.productImage)"
                :alt="review.productName"
                class="friend-product-image"
              />
              <div class="friend-header">
                <img :src="getImagePath(review.avatar || 'images/logo192.png')" :alt="review.user" class="friend-avatar" />
                <span class="friend-name">{{ review.user }}</span>
              </div>
              <div class="review-product">商品：{{ review.productName }}</div>
              <div class="review-rating">评分：{{ '★'.repeat(review.rating) }}</div>
              <p class="friend-message">{{ review.content }}</p>
            </div>
          </div>
          <div v-else class="review-empty-state">暂无真实用户评价</div>
        </div>
      </div>

      <div class="center-goods-box">
        <h3>🔥 热销宠物用品</h3>
        <div id="productGrid" class="goods-grid">
          <div v-if="!hotProducts.length" class="no-products">暂无商品</div>
          <div
            v-for="product in hotProductsWithMetrics"
            v-else
            :key="product.id"
            class="goods-item"
            @mouseenter="showProductTooltip($event, product)"
            @mouseleave="hideProductTooltip"
            @click="openProductDetail(product)"
          >
            <img
              :src="getImagePath(product.image)"
              :alt="product.name"
              style="cursor: pointer"
              @click="openProductDetail(product)"
            />
            <h4 style="cursor: pointer" @click="openProductDetail(product)">{{ product.name }}</h4>
            <p class="goods-description">{{ product.description }}</p>
            <div class="goods-metrics">
              <span>购买人数：{{ product.purchaseCount }}</span>
              <span>热度：{{ product.heatScore }}</span>
              <span>库存：{{ product.stockCount }}</span>
            </div>
            <div class="goods-footer">
              <span class="goods-price">¥{{ product.price }}</span>
              <span v-if="isOffShelf(product)" class="kinds-item-status-tag">已下架</span>
            </div>
          </div>
        </div>
      </div>

      <div class="right-customer-service-message-box">
        <h3>💬 联系会话列表</h3>
        <div class="service-list">
          <div
            v-for="s in buyerChatRows"
            :key="s.id"
            class="service-item"
            @click="openChat(s)"
          >
            <div class="service-avatar">
              <img v-if="isImageAvatar(s.avatar)" :src="getImagePath(s.avatar)" :alt="s.name" class="avatar-image" />
              <span v-else class="avatar-icon">{{ s.avatar }}</span>
            </div>
            <div class="service-info">
              <div class="service-name">
                {{ s.name }}
                <span class="service-role">{{ s.role }}</span>
              </div>
              <div v-if="s.subtitle" class="service-last-message">{{ s.subtitle }}</div>
              <div class="service-last-message">{{ s.lastMessage }}</div>
            </div>
            <div class="service-badge" v-if="s.unread">
              <span class="unread-count">{{ s.unread }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <ProductDetailModal v-model="detailOpen" :product="detailProduct" @close="closeProductDetail" />

    <div
      v-if="tooltip"
      id="productTooltip"
      class="product-tooltip"
      :style="tooltipStyle"
      @mouseenter="onTooltipEnter"
      @mouseleave="hideProductTooltip"
    >
      <div class="tooltip-header">
        <h3>{{ tooltip.name }}</h3>
        <span class="tooltip-price">¥{{ Number(tooltip.price).toFixed(2) }}</span>
      </div>
      <div class="tooltip-content">
        <p class="tooltip-description">{{ tooltip.description || '暂无描述' }}</p>
        <div class="tooltip-specs">
          <span v-if="tooltip.category">类别：{{ tooltip.category }}</span>
          <span v-if="tooltip.stock !== undefined">库存：{{ tooltip.stock }}</span>
          <span v-if="tooltip.sales !== undefined">已售：{{ tooltip.sales }}</span>
        </div>
      </div>
      <div class="tooltip-footer">
        <button type="button" class="tooltip-btn" @click="openProductDetail(tooltip)">查看详情</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.banner-nav-shell {
  position: absolute;
  z-index: 3;
}

.banner-btn-standalone {
  position: relative;
  inset: auto;
  display: grid;
  place-items: center;
}
</style>
