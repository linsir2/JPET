<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ProductDetailModal from '@/components/ProductDetailModal.vue'
import { getImagePath } from '@/utils/imagePath'
import { useCatalogStore } from '@/stores/catalog'
import { useLocalOffShelfStore } from '@/stores/offShelfLocal'
import { useRouter } from 'vue-router'
import { merchantProductAuditApi } from '@/api/merchant'

import '@/styles/legacy/common.css'
import '@/styles/legacy/productDetail.css'
import '@/styles/legacy/category.css'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()
const localOffShelf = useLocalOffShelfStore()

const selectedCategory = ref('全部')
const selectedSubCategory = ref('全部')
const sortBy = ref('default')
const merchantProducts = ref([])
const pageNo = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const usingRemote = ref(false)

const FACET_GROUP_RULES = [
  {
    label: '宠物种类',
    keywords: ['狗狗', '狗', '犬', '猫咪', '猫', '兔子', '兔', '仓鼠', '鹦鹉', '鸟', '龟', '鱼', '爬宠'],
  },
  {
    label: '用品品类',
    keywords: ['主粮', '零食', '窝', '垫', '喂食', '饮水', '食盆', '外出箱', '航空箱', '玩具', '牵引', '猫砂', '清洁', '护理', '服饰', '笼', '用品', '家居', '设备'],
  },
  {
    label: '商品特色',
    keywords: ['新品', '热销', '纯种', '无谷', '五谷', '天然', '智能', '静音', '高蛋白', '保暖', '防滑', '透气', '定时', '远程', '互动', '舒适'],
  },
]

function normalizeLabel(value) {
  const normalized = String(value ?? '').trim()
  return normalized && normalized !== '全部' && normalized !== '默认' ? normalized : ''
}

function collectProductLabels(product) {
  const tags = Array.isArray(product?.tags) ? product.tags : []
  return [...new Set([
    normalizeLabel(product?.category),
    normalizeLabel(product?.subCategory),
    ...tags.map((tag) => normalizeLabel(tag)),
  ].filter(Boolean))]
}

function resolveFacetGroup(label) {
  const matched = FACET_GROUP_RULES.find((group) =>
    group.keywords.some((keyword) => label.includes(keyword)),
  )
  return matched?.label || '其他分类'
}

function buildFacetMap(product) {
  const facetMap = {}
  collectProductLabels(product).forEach((label) => {
    const group = resolveFacetGroup(label)
    if (!facetMap[group]) {
      facetMap[group] = []
    }
    if (!facetMap[group].includes(label)) {
      facetMap[group].push(label)
    }
  })
  return facetMap
}

function getSourceProducts() {
  return usingRemote.value ? merchantProducts.value : catalog.products
}

const categoryButtons = computed(() => {
  const categories = ['全部']
  getSourceProducts().forEach((product) => {
    Object.keys(buildFacetMap(product)).forEach((groupLabel) => {
      if (groupLabel && !categories.includes(groupLabel)) {
        categories.push(groupLabel)
      }
    })
  })
  return categories
})

const subCategoryButtons = computed(() => {
  if (selectedCategory.value === '全部') {
    return []
  }
  const subCategories = ['全部']
  getSourceProducts().forEach((product) => {
    const values = buildFacetMap(product)[selectedCategory.value] || []
    values.forEach((value) => {
      if (value && !subCategories.includes(value)) {
        subCategories.push(value)
      }
    })
  })
  return subCategories
})

const filteredProductPool = computed(() => {
  let list = [...getSourceProducts()]

  if (selectedCategory.value !== '全部') {
    list = list.filter((product) => {
      const values = buildFacetMap(product)[selectedCategory.value] || []
      if (selectedSubCategory.value === '全部') {
        return values.length > 0
      }
      return values.includes(selectedSubCategory.value)
    })
  }

  if (searchKeyword.value.length) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (p) =>
        String(p?.name || '').toLowerCase().includes(kw) ||
        String(p?.description || '').toLowerCase().includes(kw),
    )
  }

  switch (sortBy.value) {
    case 'heat-asc':
      list.sort((a, b) => getHeatScore(a) - getHeatScore(b))
      break
    case 'heat-desc':
      list.sort((a, b) => getHeatScore(b) - getHeatScore(a))
      break
    case 'purchase-asc':
      list.sort((a, b) => getPurchaseCount(a) - getPurchaseCount(b))
      break
    case 'purchase-desc':
      list.sort((a, b) => getPurchaseCount(b) - getPurchaseCount(a))
      break
    default:
      break
  }

  list.sort((a, b) => Number(isOffShelf(a)) - Number(isOffShelf(b)))
  return list
})

const totalCount = computed(() => filteredProductPool.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))

const filteredProducts = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return filteredProductPool.value.slice(start, start + pageSize.value)
})
const searchKeyword = computed(() => String(route.query.search || '').trim())

function getPurchaseCount(product) {
  const v = Number(product.purchaseCount ?? product.sales ?? 0)
  return Number.isFinite(v) ? v : 0
}

function getHeatScore(product) {
  const raw = product.heatScore
  if (raw !== undefined && raw !== null) {
    const v = Number(raw)
    if (Number.isFinite(v)) return v
  }
  return getPurchaseCount(product)
}

function getStockCount(product) {
  const v = Number(product.stockCount ?? product.stock ?? 0)
  return Number.isFinite(v) ? v : 0
}

function isOffShelf(product) {
  if (String(product?.status || '') === '已下架') return true
  // 接口可能把 status 刷回「已上架」，但本地下架 id 仍在；并让 computed 依赖 Pinia byId
  return localOffShelf.isRowMarkedOffShelf(product)
}

function selectCategory(cat) {
  selectedCategory.value = cat
  selectedSubCategory.value = '全部'
  pageNo.value = 1
}

function selectSubCategory(subCategory) {
  selectedSubCategory.value = subCategory
  pageNo.value = 1
}

function clearFilter() {
  selectedCategory.value = '全部'
  selectedSubCategory.value = '全部'
  sortBy.value = 'default'
  pageNo.value = 1
  router.replace({ path: '/category' })
}

const detailOpen = ref(false)
const detailProduct = ref(null)

function openProductDetail(p) {
  // 远程列表数据在 merchantProducts 里，必须与列表项同一引用，否则改 status 不会反映到卡片上
  if (usingRemote.value) {
    const fromMerchant = merchantProducts.value.find(
      (x) => x.id === p.id || String(x.id) === String(p.id),
    )
    if (fromMerchant) {
      detailProduct.value = fromMerchant
      detailOpen.value = true
      return
    }
  }
  const raw = catalog.getById(p.id)
  detailProduct.value = raw || p
  detailOpen.value = true
}

function mapSortParams() {
  switch (sortBy.value) {
    case 'heat-asc':
      return { sortField: 'heat', sortOrder: 'asc' }
    case 'heat-desc':
      return { sortField: 'heat', sortOrder: 'desc' }
    case 'purchase-asc':
      return { sortField: 'purchaseCount', sortOrder: 'asc' }
    case 'purchase-desc':
      return { sortField: 'purchaseCount', sortOrder: 'desc' }
    default:
      return {}
  }
}

async function loadRemoteProducts() {
  loading.value = true
  try {
    const pageData = await merchantProductAuditApi.listPublished()
    usingRemote.value = true
    const normalizedList = pageData.list.map((item) => ({
      ...item,
      category: item.tags?.[0] || '全部',
      subCategory: item.tags?.[1] || '',
      image: item.image ?? item.imageUrl ?? item.image_url ?? '',
      price: item.minPrice ?? item.price,
      stock: Number(item.stock ?? 0),
      purchaseCount: item.purchaseCount ?? 0,
      heatScore: item.heatScore ?? item.purchaseCount ?? 0,
      specifications: item.specifications || [],
    }))
    merchantProducts.value = normalizedList
    localOffShelf.applyToProducts(merchantProducts.value)
  } catch {
    usingRemote.value = false
    merchantProducts.value = []
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (pageNo.value <= 1) return
  pageNo.value -= 1
}

function nextPage() {
  if (pageNo.value >= totalPages.value) return
  pageNo.value += 1
}

onMounted(async () => {
  await catalog.loadAll()
  await loadRemoteProducts()
})

watch(
  () => route.query.search,
  async () => {
    pageNo.value = 1
    await loadRemoteProducts()
  },
)

watch([selectedCategory, selectedSubCategory, sortBy], () => {
  pageNo.value = 1
})

watch(subCategoryButtons, (nextButtons) => {
  if (selectedSubCategory.value !== '全部' && !nextButtons.includes(selectedSubCategory.value)) {
    selectedSubCategory.value = '全部'
  }
})

watch(pageSize, () => {
  pageNo.value = 1
})
</script>

<template>
  <div class="App">

    <div class="kinds-ui-box">
      <div class="head-tag-box">
        <div class="category-tags">
          <span class="head-tag-text">大类筛选：</span>
          <div id="categoryButtons">
            <button
              v-for="(cat, index) in categoryButtons"
              :key="cat"
              type="button"
              class="category-btn"
              :class="{ active: selectedCategory === cat }"
              :style="{ '--index': index }"
              @click="selectCategory(cat)"
            >
              {{ cat }}
            </button>
          </div>
        </div>

        <div v-if="selectedCategory !== '全部' && subCategoryButtons.length > 1" class="sub-category-filter">
          <span class="head-tag-text">小类筛选：</span>
          <div class="sub-category-buttons">
            <button
              v-for="subCategory in subCategoryButtons"
              :key="subCategory"
              type="button"
              class="head-tag-btn"
              :class="{ active: selectedSubCategory === subCategory }"
              @click="selectSubCategory(subCategory)"
            >
              {{ subCategory }}
            </button>
          </div>
        </div>

        <div class="selected-filters">
          <span class="head-tag-text">当前筛选：</span>
          <span id="selectedCategory" class="head-tag-text-selected">{{ selectedCategory }}</span>
          <span v-if="selectedSubCategory !== '全部'" class="head-tag-text-selected">{{ selectedSubCategory }}</span>
          <button
            v-show="selectedCategory !== '全部' || selectedSubCategory !== '全部'"
            id="clearFilterBtn"
            type="button"
            class="head-tag-btn-clear"
            @click="clearFilter"
          >
            清空筛选
          </button>
        </div>
      </div>

      <div class="filter-select-box">
        <span class="filter-select-text">排序方式：</span>
        <select id="sortSelect" v-model="sortBy" class="filter-select-select">
          <option value="default">默认排序</option>
          <option value="heat-asc">按热度递增</option>
          <option value="heat-desc">按热度递减</option>
          <option value="purchase-asc">按购买量递增</option>
          <option value="purchase-desc">按购买量递减</option>
        </select>
      </div>

      <div id="productList" class="kinds-list-box">
        <div v-if="loading" class="no-products">商品加载中...</div>
        <div v-else-if="!filteredProducts.length" class="no-products">暂无商品</div>
        <div
          v-for="product in filteredProducts"
          v-else
          :key="product.id"
          class="kinds-item"
          :class="{ 'kinds-item-off-shelf': isOffShelf(product) }"
        >
          <img
            :src="getImagePath(product.image)"
            :alt="product.name"
            style="cursor: pointer"
            @click="openProductDetail(product)"
          />
          <div class="kinds-item-info">
            <h4 class="kinds-item-name" style="cursor: pointer" @click="openProductDetail(product)">
              {{ product.name }}
            </h4>
            <p class="kinds-item-description">{{ product.description }}</p>
            <div class="kinds-item-metrics">
              <span>购买人数：{{ getPurchaseCount(product) }}</span>
              <span>热度：{{ getHeatScore(product) }}</span>
              <span>库存：{{ getStockCount(product) }}</span>
            </div>
            <div class="kinds-item-footer">
              <span class="kinds-item-price">¥{{ Number(product.price || 0).toFixed(0) }}</span>
              <span v-if="isOffShelf(product)" class="kinds-item-status-tag">已下架</span>
            </div>
          </div>
        </div>
      </div>

      <div class="kinds-pagination">
        <button type="button" class="page-btn" :disabled="pageNo <= 1" @click="prevPage">上一页</button>
        <span class="page-text">第 {{ pageNo }} / {{ totalPages }} 页（共 {{ totalCount }} 条）</span>
        <button type="button" class="page-btn" :disabled="pageNo >= totalPages" @click="nextPage">下一页</button>
        <select v-model.number="pageSize" class="page-size-select">
          <option :value="8">8条/页</option>
          <option :value="12">12条/页</option>
          <option :value="16">16条/页</option>
        </select>
      </div>
    </div>

    <ProductDetailModal v-model="detailOpen" :product="detailProduct" />
  </div>
</template>
