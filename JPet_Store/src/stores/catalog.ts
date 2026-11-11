import { ref } from 'vue'
import { defineStore } from 'pinia'
import { merchantProductAuditApi } from '@/api/merchant'
import { useLocalOffShelfStore } from '@/stores/offShelfLocal'

function normalizeCatalogProduct(product) {
  const tags = Array.isArray(product?.tags) ? product.tags : []
  return {
    ...product,
    image: product?.image || product?.imageUrl || product?.image_url || '',
    description: product?.description || '',
    detailDescription: product?.detailDescription || product?.description || '',
    category: String(product?.category || tags[0] || '全部'),
    price: Number(product?.price ?? product?.minPrice ?? 0),
    stock: Number(product?.stock ?? 0),
    sales: Number(product?.sales ?? product?.purchaseCount ?? 0),
    purchaseCount: Number(product?.purchaseCount ?? product?.sales ?? 0),
    heatScore: Number(product?.heatScore ?? product?.purchaseCount ?? product?.sales ?? 0),
    specifications: Array.isArray(product?.specifications) ? product.specifications : [],
    buyerReviews: Array.isArray(product?.buyerReviews) ? product.buyerReviews : [],
    tags,
  }
}

export const useCatalogStore = defineStore('catalog', () => {
  const products = ref([])
  const loading = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      const pageData = await merchantProductAuditApi.listPublished()
      const list = Array.isArray(pageData?.list) ? pageData.list : []
      products.value = list.map((item) => normalizeCatalogProduct(item))
    } catch {
      products.value = []
    } finally {
      loading.value = false
    }
    useLocalOffShelfStore().applyToProducts(products.value)
    return products.value
  }

  function getById(id) {
    if (id == null) return undefined
    const sid = String(id)
    return products.value.find(
      (p) => p.id === id || String(p.id) === sid || String(p.spuId ?? '') === sid,
    )
  }

  function categories() {
    const cats = ['全部']
    products.value.forEach((product) => {
      if (product.category && !cats.includes(product.category)) {
        cats.push(product.category)
      }
    })
    return cats
  }

  return { products, loading, loadAll, getById, categories }
})
