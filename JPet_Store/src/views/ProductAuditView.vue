<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { getImagePath } from '@/utils/imagePath'
import { useAuthStore } from '@/stores/auth'
import { useCartBadgeStore } from '@/stores/cartBadge'
import { useCatalogStore } from '@/stores/catalog'
import { useLocalOffShelfStore } from '@/stores/offShelfLocal'
import { merchantProductAuditApi } from '@/api/merchant'

import '@/styles/legacy/common.css'
import '@/styles/legacy/product-audit.css'

const auth = useAuthStore()
const cartBadge = useCartBadgeStore()
const catalog = useCatalogStore()
const localOffShelf = useLocalOffShelfStore()
const MAX_UPLOAD_IMAGE_SIZE = 20 * 1024 * 1024

const pendingProducts = ref([])
const approvedProducts = ref([])
const selectedPendingId = ref(null)
const previewSpec = ref('500g')
const createMode = ref(false)
const editingProductId = ref(null)
const tagInput = ref('')
const attrNameInput = ref('')
const selectedVariantRowIdx = ref(-1)
const archivedTab = ref('off-shelf')
const offShelfProducts = ref([])
const failedProducts = ref([])
const uploadPreviewUrl = ref('')
const editingSourceStatus = ref('')
const draftForm = ref({
  image: null,
  imageName: '',
  name: '',
  description: '',
  stock: 0,
  tags: [],
  attributeKeys: [],
  variantRows: [],
})

function ensureRowValues(row) {
  if (!row.values || typeof row.values !== 'object') row.values = {}
  for (const k of draftForm.value.attributeKeys) {
    if (row.values[k] === undefined) row.values[k] = ''
  }
  // 清理不存在的 key，避免残留
  for (const k of Object.keys(row.values)) {
    if (!draftForm.value.attributeKeys.includes(k)) delete row.values[k]
  }
}

function syncRowsToKeys() {
  for (const row of draftForm.value.variantRows) ensureRowValues(row)
}

const reviewVisibleProducts = computed(() => [...pendingProducts.value, ...approvedProducts.value])

const selectedPending = computed(() => {
  if (!reviewVisibleProducts.value.length) return null
  return (
    reviewVisibleProducts.value.find((x) => String(x.id) === String(selectedPendingId.value)) ||
    reviewVisibleProducts.value[0] ||
    null
  )
})

const reviewStats = computed(() => {
  const inReview = pendingProducts.value.length
  const approved = approvedProducts.value.length
  return { approved, inReview }
})

const selectedPendingIsApproved = computed(() => String(selectedPending.value?.status || '') === '已上架')

function normalizeVariantCombination(raw) {
  return String(raw || '')
    .split('/')
    .map((item) => item.trim())
    .filter(Boolean)
    .join(' / ')
}

function resolveInitialPreviewSpec(product) {
  const firstVariant = Array.isArray(product?.variantPrices) ? product.variantPrices[0]?.combination : ''
  if (firstVariant) return normalizeVariantCombination(firstVariant)
  const firstSpec = Array.isArray(product?.specifications) ? product.specifications[0] : ''
  return normalizeVariantCombination(firstSpec) || '默认规格'
}

const selectedPendingVariantPrices = computed(() =>
  Array.isArray(selectedPending.value?.variantPrices) ? selectedPending.value.variantPrices : [],
)

const previewSpecOptions = computed(() => {
  if (selectedPendingVariantPrices.value.length) {
    return selectedPendingVariantPrices.value.map((item) => normalizeVariantCombination(item.combination))
  }
  return Array.isArray(selectedPending.value?.specifications)
    ? selectedPending.value.specifications.map((item) => normalizeVariantCombination(item))
    : []
})

const selectedPreviewVariant = computed(() => {
  const normalizedPreview = normalizeVariantCombination(previewSpec.value)
  if (!selectedPendingVariantPrices.value.length) return null
  return (
    selectedPendingVariantPrices.value.find(
      (item) => normalizeVariantCombination(item.combination) === normalizedPreview,
    ) || selectedPendingVariantPrices.value[0]
  )
})

const previewDisplayPrice = computed(() => Number(selectedPreviewVariant.value?.price ?? selectedPending.value?.price ?? 0))

const previewDisplaySpec = computed(
  () => selectedPreviewVariant.value?.combination || previewSpec.value || '默认规格',
)

function normalizeRejectedReason(reason) {
  return String(reason || '').trim()
}

function normalizeProductName(name) {
  return String(name || '').trim().toLowerCase()
}

function isGenericRejectedReason(reason) {
  const normalized = normalizeRejectedReason(reason)
  return !normalized || normalized === '管理员审核未通过'
}

function buildFailedProductDedupKey(product) {
  const name = normalizeProductName(product?.name)
  if (name) return `name:${name}`
  const id = product?.id == null ? '' : String(product.id).trim()
  if (id) return `id:${id}`
  const image = String(product?.image || '').trim().toLowerCase()
  return `image:${image}`
}

function shouldReplaceFailedProduct(existingProduct, incomingProduct) {
  const existingReason = normalizeRejectedReason(existingProduct?.reason)
  const incomingReason = normalizeRejectedReason(incomingProduct?.reason)
  if (isGenericRejectedReason(existingReason) && !isGenericRejectedReason(incomingReason)) {
    return true
  }
  if (!isGenericRejectedReason(existingReason) && isGenericRejectedReason(incomingReason)) {
    return false
  }
  const existingTime = String(existingProduct?.submittedAt || '').trim()
  const incomingTime = String(incomingProduct?.submittedAt || '').trim()
  if (!existingTime && incomingTime) return true
  return false
}

function dedupeFailedProducts(list) {
  const deduped = []
  const keyIndexMap = new Map()
  for (const product of list) {
    const key = buildFailedProductDedupKey(product)
    const existingIndex = keyIndexMap.get(key)
    if (existingIndex == null) {
      keyIndexMap.set(key, deduped.length)
      deduped.push(product)
      continue
    }
    if (shouldReplaceFailedProduct(deduped[existingIndex], product)) {
      deduped[existingIndex] = product
    }
  }
  return deduped
}

function filterSupersededFailedProducts(list, reviewProducts) {
  const activeNameSet = new Set(
    reviewProducts.map((product) => normalizeProductName(product?.name)).filter(Boolean),
  )
  return list.filter((product) => {
    const failedName = normalizeProductName(product?.name)
    return !failedName || !activeNameSet.has(failedName)
  })
}

function selectPending(id) {
  createMode.value = false
  selectedPendingId.value = id
  const p = reviewVisibleProducts.value.find((x) => String(x.id) === String(id))
  previewSpec.value = resolveInitialPreviewSpec(p)
}

function addStockDelta(delta) {
  const p = selectedPending.value
  if (!p) return
  p.stock = Math.max(0, Number(p.stock || 0) + delta)
}

function editCurrentPendingProduct() {
  const p = selectedPending.value
  if (!p) return
  createMode.value = true
  hydrateDraftFromProduct(p, 'review')
}

function hydrateDraftFromProduct(product, sourceStatus) {
  editingProductId.value = product.id
  editingSourceStatus.value = sourceStatus
  if (uploadPreviewUrl.value) {
    URL.revokeObjectURL(uploadPreviewUrl.value)
    uploadPreviewUrl.value = ''
  }
  draftForm.value = {
    image: product.image || null,
    imageName: product.image ? '当前商品图片' : '',
    name: product.name || '',
    description: product.description || '',
    stock: Number(product.stock || 0),
    tags: Array.isArray(product.tags) ? [...product.tags] : [],
    attributeKeys: [],
    variantRows: [],
  }
  // 解析已有 variantPrices：组合格式为 "key=value / key2=value"
  if (Array.isArray(product.variantPrices) && product.variantPrices.length) {
    const keySet = new Set()
    const parsedRows = product.variantPrices.map((r) => {
      const values = {}
      const txt = String(r?.combination || '')
      const parts = txt.split('/').map((x) => x.trim()).filter(Boolean)
      for (const part of parts) {
        const [kRaw, ...vRaw] = part.split('=')
        const k = String(kRaw || '').trim()
        const v = vRaw.join('=').trim()
        if (!k) continue
        keySet.add(k)
        values[k] = v
      }
      return { values, price: String(r?.price ?? product.price ?? '') }
    })
    draftForm.value.attributeKeys = Array.from(keySet)
    draftForm.value.variantRows = parsedRows.length ? parsedRows : []
  } else {
    // 兼容旧数据：当还没有 variantPrices 时，尝试从 specifications 回填编辑行
    const specs = Array.isArray(product.specifications) ? product.specifications : []
    const keySet = new Set()
    const rows = []
    for (const sp of specs) {
      const txt = String(sp || '').trim()
      if (!txt) continue
      const values = {}
      // 新格式示例：重量=1kg / 口味=鸡肉；旧格式示例：500g
      const parts = txt.split('/').map((x) => x.trim()).filter(Boolean)
      let parsedKv = false
      for (const part of parts) {
        if (!part.includes('=')) continue
        const [kRaw, ...vRaw] = part.split('=')
        const k = String(kRaw || '').trim()
        const v = vRaw.join('=').trim()
        if (!k) continue
        values[k] = v
        keySet.add(k)
        parsedKv = true
      }
      if (!parsedKv) {
        values['重量'] = txt
        keySet.add('重量')
      }
      rows.push({ values, price: product.price ? String(product.price) : '' })
    }
    draftForm.value.attributeKeys = Array.from(keySet)
    draftForm.value.variantRows = rows
  }
  if (!draftForm.value.attributeKeys.length && draftForm.value.variantRows.length) {
    draftForm.value.attributeKeys = ['重量']
  }
  syncRowsToKeys()
  tagInput.value = ''
  selectedVariantRowIdx.value = draftForm.value.variantRows.length ? 0 : -1
}

async function withdrawCurrentRequest() {
  const p = selectedPending.value
  if (!p) return
  if (selectedPendingIsApproved.value) {
    alert('已通过商品不在审核队列中，无法撤回审核请求')
    return
  }
  try {
    await merchantProductAuditApi.withdraw(p.id)
  } catch {
    alert('撤回失败，请稍后重试')
    return
  }
  const idx = pendingProducts.value.findIndex((x) => x.id === p.id)
  if (idx < 0) return
  pendingProducts.value.splice(idx, 1)
  failedProducts.value.unshift({
    ...p,
    status: 'failed',
    reason: '取消审核',
    submittedAt: '刚刚',
  })
  if (pendingProducts.value.length) {
    selectPending(pendingProducts.value[0].id)
  } else {
    selectedPendingId.value = null
  }
}

function openCreatePanel() {
  createMode.value = true
  editingProductId.value = null
  editingSourceStatus.value = ''
  if (uploadPreviewUrl.value) {
    URL.revokeObjectURL(uploadPreviewUrl.value)
    uploadPreviewUrl.value = ''
  }
  const p = selectedPending.value
  draftForm.value = {
    image: null,
    imageName: '',
    name: p?.name || '',
    description: '',
    stock: p?.stock || 0,
    tags: [],
    attributeKeys: [],
    variantRows: [],
  }
  syncRowsToKeys()
  tagInput.value = ''
  selectedVariantRowIdx.value = -1
}

function onPickLocalImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > MAX_UPLOAD_IMAGE_SIZE) {
    alert('图片不能超过 20MB，请压缩后再上传')
    event.target.value = ''
    return
  }
  if (uploadPreviewUrl.value) URL.revokeObjectURL(uploadPreviewUrl.value)
  uploadPreviewUrl.value = URL.createObjectURL(file)
  draftForm.value.image = file
  draftForm.value.imageName = file.name
}

async function resolveDraftImageUrl() {
  if (draftForm.value.image instanceof File) {
    return merchantProductAuditApi.uploadImage(draftForm.value.image)
  }
  if (typeof draftForm.value.image === 'string') {
    return draftForm.value.image.trim()
  }
  return ''
}

function addDraftTag() {
  const v = tagInput.value.trim()
  if (!v) return
  if (!draftForm.value.tags.includes(v)) {
    draftForm.value.tags.push(v)
  }
  tagInput.value = ''
}

function removeDraftTag(idx) {
  draftForm.value.tags.splice(idx, 1)
}

function addAttributeKey() {
  const k = attrNameInput.value.trim()
  if (!k) return
  if (!draftForm.value.attributeKeys.includes(k)) {
    draftForm.value.attributeKeys.push(k)
    syncRowsToKeys()
  }
  attrNameInput.value = ''
}

function removeAttributeKey(idx) {
  draftForm.value.attributeKeys.splice(idx, 1)
  syncRowsToKeys()
}

function addVariantRow() {
  const row = { values: {}, price: '' }
  draftForm.value.variantRows.push(row)
  ensureRowValues(row)
  selectedVariantRowIdx.value = draftForm.value.variantRows.length - 1
}

function removeVariantRow(idx) {
  draftForm.value.variantRows.splice(idx, 1)
}

function deleteSelectedVariantRow() {
  const idx = selectedVariantRowIdx.value
  if (idx < 0 || idx >= draftForm.value.variantRows.length) {
    alert('请先点击选中要删除的行')
    return
  }
  removeVariantRow(idx)
  if (!draftForm.value.variantRows.length) {
    selectedVariantRowIdx.value = -1
    return
  }
  selectedVariantRowIdx.value = Math.min(idx, draftForm.value.variantRows.length - 1)
}

function selectVariantRow(idx) {
  selectedVariantRowIdx.value = idx
}

function extractVariantPayload() {
  const rows = draftForm.value.variantRows
    .map((r) => ({
      values: r.values && typeof r.values === 'object' ? r.values : {},
      price: Number(r.price),
    }))
    .filter((r) => Object.values(r.values).some((v) => String(v || '').trim()) || r.price)
  if (!rows.length) return { ok: false, message: '请至少添加一行属性价格' }
  const keys = draftForm.value.attributeKeys
  if (!keys.length) return { ok: false, message: '请先添加属性名' }
  if (rows.some((r) => keys.some((k) => !String(r.values?.[k] || '').trim()))) {
    return { ok: false, message: '每行都需要填写所有属性值' }
  }
  if (rows.some((r) => !Number.isFinite(r.price) || r.price <= 0)) {
    return { ok: false, message: '每行都需要填写有效价格' }
  }
  return { ok: true, rows }
}

async function submitNewProductReview() {
  if (!draftForm.value.image) {
    alert('请先从本地选择商品图片')
    return
  }
  if (!draftForm.value.name.trim()) {
    alert('请先输入商品名称')
    return
  }
  if (!draftForm.value.tags.length) {
    alert('请至少添加一个标签')
    return
  }
  const variant = extractVariantPayload()
  if (!variant.ok) {
    alert(variant.message)
    return
  }
  const minPrice = Math.min(...variant.rows.map((r) => r.price))
  const keys = draftForm.value.attributeKeys
  const toComboText = (values) => keys.map((k) => `${k}=${String(values?.[k] ?? '').trim()}`).join(' / ')
  const specLabels = variant.rows.map((r) => toComboText(r.values))
  const variantPrices = variant.rows.map((r) => ({
    combination: toComboText(r.values),
    price: r.price,
  }))

  let imageUrl = ''
  try {
    imageUrl = await resolveDraftImageUrl()
  } catch (error) {
    alert(error instanceof Error ? error.message : '图片上传失败，请稍后重试')
    return
  }

  if (!imageUrl) {
    alert('商品图片上传失败，请重新选择图片')
    return
  }

  const sharedStock = Number(draftForm.value.stock || 0)

  if (editingProductId.value !== null) {
    const editingId = editingProductId.value
    let updatedFromApi = null
    const currentProduct =
      reviewVisibleProducts.value.find((x) => String(x.id) === String(editingId)) ||
      failedProducts.value.find((x) => String(x.id) === String(editingId)) ||
      offShelfProducts.value.find((x) => String(x.id) === String(editingId)) ||
      null
    const editingApproved = approvedProducts.value.some((x) => String(x.id) === String(editingId))
    const editingFailed = failedProducts.value.some((x) => String(x.id) === String(editingId))
    const existingVariantMap = new Map(
      (Array.isArray(currentProduct?.variantPrices) ? currentProduct.variantPrices : []).map((item) => [
        item.combination,
        item,
      ]),
    )
    try {
      updatedFromApi = await merchantProductAuditApi.update(editingId, {
        id: editingId,
        name: draftForm.value.name.trim(),
        description: draftForm.value.description.trim(),
        imageUrl,
        status: 'pending',
        tags: [...draftForm.value.tags],
        coreAttributes: [...draftForm.value.attributeKeys],
        skus: variant.rows.map((row) => {
          const combination = toComboText(row.values)
          const existingVariant = existingVariantMap.get(combination)
          return {
            id: existingVariant?.skuId,
            attributeValues: row.values,
            status: 'available',
            imageUrl,
            price: row.price,
            stock: sharedStock,
          }
        }),
      })
    } catch (error) {
      alert(error instanceof Error ? error.message : '修改提交失败，请稍后重试')
      return
    }
    const nextProduct = {
      ...(currentProduct || {}),
      ...(updatedFromApi || {}),
      status: '待审核',
      submittedAt: '刚刚',
      reason: '',
    }
    if (editingApproved) {
      approvedProducts.value = approvedProducts.value.filter((x) => String(x.id) !== String(editingId))
      pendingProducts.value.unshift(nextProduct)
      selectedPendingId.value = nextProduct.id
    } else if (editingFailed) {
      failedProducts.value = failedProducts.value.filter((x) => String(x.id) !== String(editingId))
      pendingProducts.value.unshift(nextProduct)
      selectedPendingId.value = nextProduct.id
    } else {
      const idx = pendingProducts.value.findIndex((x) => String(x.id) === String(editingId))
      if (idx >= 0) {
        pendingProducts.value[idx] = nextProduct
        selectedPendingId.value = pendingProducts.value[idx].id
      } else {
        pendingProducts.value.unshift(nextProduct)
        selectedPendingId.value = nextProduct.id
      }
    }
    createMode.value = false
    editingProductId.value = null
    editingSourceStatus.value = ''
    alert('商品修改已保存，请等待管理员重新审核')
    return
  }

  let createdFromApi = null
  try {
    createdFromApi = await merchantProductAuditApi.create({
      name: draftForm.value.name.trim(),
      description: draftForm.value.description.trim(),
      imageUrl,
      status: 'pending',
      tags: [...draftForm.value.tags],
      coreAttributes: [...draftForm.value.attributeKeys],
      skus: variant.rows.map((row) => ({
        attributeValues: row.values,
        status: 'available',
        imageUrl,
        price: row.price,
        stock: sharedStock,
      })),
    })
  } catch (error) {
    alert(error instanceof Error ? error.message : '提交审核失败，请稍后重试')
    return
  }

  const newItemId = Date.now()
  pendingProducts.value.unshift({
    ...(createdFromApi || {}),
    id: createdFromApi?.id ?? newItemId,
    name: draftForm.value.name.trim(),
    price: createdFromApi?.price ?? minPrice,
    stock: createdFromApi?.stock ?? sharedStock,
    sales: 0,
    description: draftForm.value.description.trim() || '新商品待审核',
    image: createdFromApi?.image || imageUrl,
    category: '新品',
    status: createdFromApi?.status || '待审核',
    submittedBy: '当前运营',
    submittedAt: '刚刚',
    tags: [...draftForm.value.tags],
    specifications: specLabels,
    variantPrices,
  })
  selectedPendingId.value = createdFromApi?.id ?? newItemId
  createMode.value = false
  editingSourceStatus.value = ''
  alert('新商品已提交审核')
}

function getRejectedReason(product) {
  const reason = String(product?.reason || '').trim()
  return reason || '管理员未填写驳回原因，请联系平台管理员。'
}

function initMockAuditData() {
  const source = catalog.products.slice(0, 8)
  pendingProducts.value = source.map((p, idx) => ({
    ...p,
    status: idx % 5 === 0 ? 'reviewing' : 'pending',
    submittedBy: idx % 2 === 0 ? '运营小王' : '商家中心',
    submittedAt: `2026-03-2${(idx % 5) + 1} 1${idx}:2${idx}`,
    specifications:
      p.specifications && p.specifications.length ? p.specifications : ['500g', '1kg', '2kg', '5kg'],
  }))
  if (pendingProducts.value.length) {
    selectPending(pendingProducts.value[0].id)
  }
  approvedProducts.value = source.slice(0, 2).map((p, idx) => ({
    ...p,
    status: '已上架',
    submittedBy: '当前商家',
    submittedAt: `2026-04-1${idx + 2} 10:1${idx}`,
    specifications:
      p.specifications && p.specifications.length ? p.specifications : ['默认规格'],
  }))
  // 兜底演示数据不再默认注入“已下架”，避免刷新后出现固定三条伪数据。
  // 真实“已下架”由接口结果 + 本地下架标记 mergeCatalogOffShelfIntoList() 合并生成。
  offShelfProducts.value = []
  failedProducts.value = source.slice(3, 6).map((p, idx) => ({
    ...p,
    reason: idx % 2 === 0 ? '图片信息不完整，请补全主图' : '属性字段不规范，请重新填写',
  }))
}

const archivedList = computed(() =>
  archivedTab.value === 'off-shelf' ? offShelfProducts.value : failedProducts.value,
)

function syncCatalogOnShelfAfterUnmark(product) {
  const k = localOffShelf.rowIdKey(product) ?? product?.id
  localOffShelf.unmark(k)
  const twin = catalog.getById(k)
  if (twin) twin.status = '已上架'
  if (product && typeof product === 'object') {
    product.status = '已上架'
  }
}

async function reopenProduct(product) {
  const k = itemKey(product)
  if (!k) {
    alert('无法识别商品编号，暂时无法重新上架')
    return
  }
  // 尽量与服务端同步，失败时仍保持前端演示流程可继续。
  try {
    await merchantProductAuditApi.updateStatus(k, 'published')
  } catch {
    // 兼容部分后端只提供审核接口或未实现状态更新：继续执行本地回滚。
  }
  syncCatalogOnShelfAfterUnmark(product)
  for (let i = offShelfProducts.value.length - 1; i >= 0; i -= 1) {
    if (sameProductId(itemKey(offShelfProducts.value[i]), k)) {
      offShelfProducts.value.splice(i, 1)
    }
  }
  alert('商品已重新上架')
}

function resubmitFailedProduct(product) {
  createMode.value = true
  hydrateDraftFromProduct(product, 'failed')
}

function primaryArchivedAction(product) {
  if (archivedTab.value === 'failed') {
    resubmitFailedProduct(product)
    return
  }
  void reopenProduct(product)
}

function removeProductFromList(list, key) {
  for (let i = list.length - 1; i >= 0; i -= 1) {
    if (sameProductId(itemKey(list[i]), key)) {
      list.splice(i, 1)
    }
  }
}

function purgeDeletedProductLocally(product) {
  const key = itemKey(product)
  if (!key) return
  removeProductFromList(pendingProducts.value, key)
  removeProductFromList(approvedProducts.value, key)
  removeProductFromList(offShelfProducts.value, key)
  removeProductFromList(failedProducts.value, key)
  removeProductFromList(catalog.products, key)
  localOffShelf.unmark(key)

  if (sameProductId(selectedPendingId.value, key)) {
    const next = reviewVisibleProducts.value[0]
    selectedPendingId.value = next?.id ?? null
  }
}

async function removeArchivedProduct(product) {
  const key = itemKey(product)
  if (!key) {
    alert('无法识别商品编号，暂时无法删除')
    return
  }
  const confirmed = window.confirm(`确定删除「${product.name}」吗？删除后将不再出现在商品审核列表中。`)
  if (!confirmed) return
  try {
    await merchantProductAuditApi.remove(key)
  } catch (error) {
    alert(error instanceof Error ? error.message : '删除商品失败，请稍后重试')
    return
  }
  purgeDeletedProductLocally(product)
}

function sameProductId(a, b) {
  return a != null && b != null && (a === b || String(a) === String(b))
}

function itemKey(item) {
  return localOffShelf.rowIdKey(item) ?? null
}

const defaultOffShelfReason = '商家在商品页点击「暂时下架」'

function upsertOffShelfRow(p, reason) {
  const pk = itemKey(p)
  if (pk == null) return
  const idx = offShelfProducts.value.findIndex((x) => sameProductId(itemKey(x), pk))
  const row = {
    ...p,
    id: p.id ?? pk,
    reason: reason || p.reason || p.offShelfReason || defaultOffShelfReason,
  }
  if (idx >= 0) {
    offShelfProducts.value[idx] = { ...offShelfProducts.value[idx], ...row }
  } else {
    offShelfProducts.value.unshift({ ...row })
  }
}

/** catalog 中已下架 + 本地下架 id（含仅远程列表、不在 catalog 的快照）合并进「已下架」 */
function mergeCatalogOffShelfIntoList() {
  const delisted = catalog.products.filter((p) => String(p?.status || '') === '已下架')
  const idsDone = new Set()
  for (const p of delisted) {
    const k = itemKey(p)
    if (!k) continue
    idsDone.add(k)
    upsertOffShelfRow(p, p.reason || p.offShelfReason || defaultOffShelfReason)
  }
  for (const k of Object.keys(localOffShelf.byId)) {
    if (!localOffShelf.byId[k]) continue
    if (idsDone.has(k)) continue
    const p = catalog.getById(k)
    const base = p ? { ...p, status: '已下架' } : localOffShelf.getSnapshot(k)
    if (!base) continue
    idsDone.add(k)
    upsertOffShelfRow(base, base.reason || base.offShelfReason || defaultOffShelfReason)
  }
}

watch(
  () => catalog.products,
  () => {
    mergeCatalogOffShelfIntoList()
  },
  { deep: true },
)

watch(
  () => localOffShelf.byId,
  () => {
    mergeCatalogOffShelfIntoList()
  },
  { deep: true },
)

onMounted(async () => {
  await catalog.loadAll()
  await auth.refresh()
  await cartBadge.refresh()
  try {
    const pendingRes = await merchantProductAuditApi.listPending()
    const approvedRes = await merchantProductAuditApi.listPublished()
    const offShelfRes = await merchantProductAuditApi.listOffShelf()
    const failedRes = await merchantProductAuditApi.listRejected()
    pendingProducts.value = pendingRes.list
    approvedProducts.value = approvedRes.list
    offShelfProducts.value = offShelfRes.list
    failedProducts.value = filterSupersededFailedProducts(
      dedupeFailedProducts(failedRes.list),
      [...pendingRes.list, ...approvedRes.list],
    )
    if (reviewVisibleProducts.value.length) {
      selectPending(reviewVisibleProducts.value[0].id)
    }
    if (!offShelfProducts.value.length && failedProducts.value.length) {
      archivedTab.value = 'failed'
    }
  } catch {
    pendingProducts.value = []
    approvedProducts.value = []
    offShelfProducts.value = []
    failedProducts.value = []
  }
  mergeCatalogOffShelfIntoList()
})

onUnmounted(() => {
  if (uploadPreviewUrl.value) URL.revokeObjectURL(uploadPreviewUrl.value)
})
</script>

<template>
  <div class="App">

    <div class="audit-page">
      <div class="audit-layout">
        <section class="audit-left">
          <div class="audit-stat-row">
            <span>待审核：{{ reviewStats.inReview }}</span>
            <span>已通过：{{ reviewStats.approved }}</span>
          </div>
          <div class="audit-list">
            <div
              v-for="item in reviewVisibleProducts"
              :key="item.id"
              class="audit-item"
              :class="{ active: selectedPending?.id === item.id }"
              @click="selectPending(item.id)"
            >
              <div class="audit-item-title">{{ item.name }}</div>
              <div class="audit-item-meta">
                <span>提交人：{{ item.submittedBy }}</span>
                <span>{{ item.submittedAt }}</span>
              </div>
              <div class="audit-item-status">{{ item.status }}</div>
            </div>
          </div>
          <div class="audit-actions">
            <button type="button" class="btn-primary" @click="openCreatePanel">上传新商品</button>
          </div>
        </section>

        <section class="audit-right" v-if="createMode">
          <div class="preview-phone">
            <div class="upload-image-box">
              <span class="upload-label">上传图片</span>
              <label class="local-upload-btn">
                从本地选择图片
                <input type="file" accept="image/*" @change="onPickLocalImage" />
              </label>
              <div class="upload-file-name">{{ draftForm.imageName || '尚未选择图片' }}</div>
              <img v-if="uploadPreviewUrl" :src="uploadPreviewUrl" alt="预览图" class="upload-preview-image" />
              <img
                v-else-if="typeof draftForm.image === 'string' && draftForm.image"
                :src="getImagePath(draftForm.image)"
                alt="当前商品图"
                class="upload-preview-image"
              />
            </div>
            <div class="create-form">
              <label class="form-label" for="newProductName">商品名称</label>
              <input id="newProductName" v-model="draftForm.name" type="text" placeholder="请输入商品名称" />
              <label class="form-label" for="newProductTag">商品标签</label>
              <div class="attr-input-row">
                <input id="newProductTag" v-model="tagInput" type="text" placeholder="请输入标签（如：无谷、低脂、新品）" />
                <button type="button" @click="addDraftTag">添加</button>
              </div>
              <div class="attr-list">
                <span
                  v-for="(tag, idx) in draftForm.tags"
                  :key="tag + idx"
                  class="attr-chip"
                  @click="removeDraftTag(idx)"
                >
                  {{ tag }} ×
                </span>
              </div>
              <label class="form-label" for="newProductStock">初始库存（件）</label>
              <input
                id="newProductStock"
                v-model.number="draftForm.stock"
                type="number"
                min="0"
                placeholder="请输入初始库存"
              />
              <label class="form-label" for="newProductDesc">商品描述</label>
              <textarea
                id="newProductDesc"
                v-model="draftForm.description"
                rows="4"
                placeholder="请输入商品描述"
              />
            </div>
            <div class="attr-editor">
              <div class="attr-title">添加属性名（不确定有几个属性）</div>
              <div class="attr-input-row variant-key-row">
                <input v-model="attrNameInput" type="text" placeholder="属性名（如：重量、口味、颜色）" />
                <button type="button" class="variant-btn variant-btn-primary" @click="addAttributeKey">添加</button>
              </div>
              <div class="attr-list">
                <span
                  v-for="(k, idx) in draftForm.attributeKeys"
                  :key="`attrkey-${k}-${idx}`"
                  class="attr-chip variant-chip"
                >
                  <span class="variant-chip-text">{{ k }}</span>
                  <button type="button" class="variant-chip-x" @click="removeAttributeKey(idx)">×</button>
                </span>
              </div>

              <div class="attr-title" style="margin-top: 10px">属性行定价（每行手动输入：各属性值 + 价格）</div>
              <div class="variant-table">
                <div class="variant-head">
                  <template v-if="draftForm.attributeKeys.length">
                    <span v-for="k in draftForm.attributeKeys" :key="`head-${k}`" class="variant-th">{{ k }}</span>
                    <span class="variant-th">价格</span>
                  </template>
                  <div class="variant-head-actions">
                    <button type="button" class="variant-btn variant-btn-add" @click="addVariantRow">+</button>
                    <button
                      type="button"
                      class="variant-btn variant-btn-danger"
                      :disabled="selectedVariantRowIdx < 0"
                      @click="deleteSelectedVariantRow"
                    >
                      -
                    </button>
                  </div>
                </div>
              </div>
              <div class="variant-rows">
                <div
                  v-for="(row, idx) in draftForm.variantRows"
                  :key="`variant-${idx}`"
                  class="variant-row"
                  :class="{ 'variant-row-active': idx === selectedVariantRowIdx }"
                  @click="selectVariantRow(idx)"
                >
                  <input
                    v-for="k in draftForm.attributeKeys"
                    :key="`cell-${idx}-${k}`"
                    v-model="row.values[k]"
                    class="variant-input"
                    type="text"
                    :placeholder="`请输入${k}`"
                  />
                  <input
                    v-model="row.price"
                    class="variant-input variant-input-price"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="请输入价格"
                  />
                </div>
              </div>
            </div>
            <button type="button" class="submit-review-btn" @click="submitNewProductReview">
              {{ editingSourceStatus === 'failed' ? '确认修改并重新提交审核' : '提交审核' }}
            </button>
          </div>
        </section>
        <section class="audit-right" v-else-if="selectedPending">
          <div class="preview-phone">
            <div class="preview-image-wrap">
              <img :src="getImagePath(selectedPending.image)" :alt="selectedPending.name" class="preview-image" />
            </div>
            <div class="preview-info">
              <h3>{{ selectedPending.name }}</h3>
              <p>{{ selectedPending.description || '暂无描述' }}</p>
              <div class="preview-price">¥{{ previewDisplayPrice.toFixed(2) }}</div>
              <div class="preview-spec-summary">当前规格：{{ previewDisplaySpec }}</div>
            </div>
            <div class="preview-specs">
              <button
                v-for="sp in previewSpecOptions"
                :key="sp"
                type="button"
                class="spec-chip"
                :class="{ active: previewSpec === sp }"
                @click="previewSpec = sp"
              >
                {{ sp }}
              </button>
            </div>
            <div v-if="selectedPendingVariantPrices.length" class="preview-variant-price-list">
              <div
                v-for="item in selectedPendingVariantPrices"
                :key="`${selectedPending.id}-${item.skuId || item.combination}`"
                class="preview-variant-price-row"
                :class="{ active: normalizeVariantCombination(item.combination) === previewSpec }"
                @click="previewSpec = normalizeVariantCombination(item.combination)"
              >
                <span>{{ item.combination }}</span>
                <strong>¥{{ Number(item.price || 0).toFixed(2) }}</strong>
              </div>
            </div>
            <div class="preview-stock">
              <span>库存情况</span>
              <div class="stock-ctrl">
                <button type="button" @click="addStockDelta(-1)">-</button>
                <strong>{{ selectedPending.stock || 0 }}</strong>
                <button type="button" @click="addStockDelta(1)">+</button>
              </div>
            </div>
            <div class="pending-detail-actions">
              <button type="button" class="edit-info-btn" @click="editCurrentPendingProduct">
                修改相关信息
              </button>
              <button type="button" class="withdraw-btn" :disabled="selectedPendingIsApproved" @click="withdrawCurrentRequest">撤回请求</button>
            </div>
            <div v-if="selectedPendingIsApproved" class="approved-tip">
              该商品已通过审核并上架；如在这里修改，将自动重新提交审核。
            </div>
          </div>
        </section>
        <section class="audit-right" v-else>
          <div class="preview-empty">暂无审核商品或已通过商品</div>
        </section>
      </div>

      <section class="archived-panel">
        <div class="archived-head">
          <h3>📦 已下架 / 审核失败产品</h3>
          <div class="archived-tabs">
            <button
              type="button"
              class="archived-tab-btn"
              :class="{ active: archivedTab === 'off-shelf' }"
              @click="archivedTab = 'off-shelf'"
            >
              已下架
            </button>
            <button
              type="button"
              class="archived-tab-btn"
              :class="{ active: archivedTab === 'failed' }"
              @click="archivedTab = 'failed'"
            >
              审核失败
            </button>
          </div>
        </div>

        <div class="archived-list">
          <div v-if="!archivedList.length" class="archived-empty">暂无数据</div>
          <div v-for="item in archivedList" v-else :key="`${archivedTab}-${itemKey(item) || item.name}`" class="archived-item">
            <div class="archived-main">
              <img :src="getImagePath(item.image)" :alt="item.name" class="archived-image" />
              <div class="archived-info">
                <div class="archived-title-row">
                  <h4>{{ item.name }}</h4>
                  <span class="archived-badge" :class="archivedTab === 'failed' ? 'failed' : 'off-shelf'">
                    {{ archivedTab === 'failed' ? '审核失败' : '已下架' }}
                  </span>
                </div>
                <p v-if="archivedTab === 'failed'" class="archived-reason-label">驳回原因</p>
                <p class="archived-reason-text">{{ archivedTab === 'failed' ? getRejectedReason(item) : item.reason }}</p>
              </div>
            </div>
            <div class="archived-actions">
              <button type="button" class="reopen-btn" @click="primaryArchivedAction(item)">
                {{ archivedTab === 'failed' ? '前往修改后重新提交' : '重新上架' }}
              </button>
              <button type="button" class="delete-btn" @click="removeArchivedProduct(item)">删除</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.variant-key-row input {
  flex: 1;
}

.variant-btn {
  height: 36px;
  min-width: 36px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  cursor: pointer;
  font-weight: 700;
  line-height: 1;
  transition: transform 0.08s ease, filter 0.12s ease, background 0.12s ease;
}

.variant-btn:active {
  transform: translateY(1px);
}

.variant-btn-primary {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
  box-shadow: 0 8px 16px rgba(37, 99, 235, 0.18);
}

.variant-btn-add {
  background: linear-gradient(135deg, #ff8a5b 0%, #ff6b9d 100%);
  color: #fff;
  width: 44px;
  padding: 0;
  border-radius: 999px;
  box-shadow: 0 10px 18px rgba(255, 107, 157, 0.22);
}

.variant-btn-danger {
  background: #0f172a;
  color: #fff;
  width: 44px;
  padding: 0;
  border-radius: 14px;
  opacity: 0.9;
}

.variant-btn-danger:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.variant-btn-danger:hover {
  opacity: 1;
  filter: brightness(1.05);
}

.variant-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 6px;
  cursor: default;
}

.variant-chip-text {
  font-weight: 700;
}

.variant-chip-x {
  border: none;
  background: rgba(29, 78, 216, 0.12);
  color: #1d4ed8;
  width: 22px;
  height: 22px;

.preview-spec-summary {
  margin-top: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.preview-variant-price-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.preview-variant-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
  color: #334155;
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease, transform 0.08s ease;
}

.preview-variant-price-row.active {
  border-color: #f97316;
  box-shadow: 0 10px 18px rgba(249, 115, 22, 0.14);
  background: #fff7ed;
}

.preview-variant-price-row strong {
  color: #0f172a;
  white-space: nowrap;
}
  border-radius: 999px;
  cursor: pointer;
  line-height: 22px;
  font-weight: 900;
}

.variant-chip-x:hover {
  background: rgba(29, 78, 216, 0.18);
}

.variant-table {
  margin-top: 10px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fafc;
  padding: 10px;
}

.variant-head {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(90px, 1fr)) minmax(96px, 1fr) 56px;
  gap: 10px;
  align-items: center;
}

.variant-head-actions {
  display: inline-flex;
  gap: 10px;
  justify-content: flex-end;
}

.variant-head-actions .variant-btn-danger {
  width: 44px;
  border-radius: 999px;
}

.variant-th {
  font-size: 12px;
  font-weight: 800;
  color: #334155;
  padding-left: 2px;
}

.variant-rows {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.variant-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(90px, 1fr)) minmax(96px, 1fr) 56px;
  gap: 10px;
  align-items: center;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 10px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
  cursor: pointer;
}

.variant-row:hover {
  border-color: rgba(37, 99, 235, 0.35);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.08);
}

.variant-row-active {
  border-color: rgba(245, 158, 11, 0.8);
  box-shadow: 0 10px 22px rgba(245, 158, 11, 0.14);
}

.variant-row-active .variant-input {
  border-color: rgba(245, 158, 11, 0.45);
}

.variant-input {
  width: 100%;
  height: 36px;
  border-radius: 12px;
}

.variant-input:focus {
  outline: none;
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.22);
}

.variant-input-price {
  text-align: left;
}

.archived-reason-label {
  margin: 10px 0 4px;
  font-size: 13px;
  font-weight: 700;
  color: #dc2626;
}

.archived-reason-text {
  margin: 0;
  line-height: 1.6;
  color: #475569;
}

@media (max-width: 768px) {
  /* 窄屏：输入区左侧堆叠，删除按钮固定右侧，避免 “-” 被挤到最下面 */
  .variant-head {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .variant-th {
    display: none;
  }

  .variant-row {
    grid-template-columns: 1fr;
    gap: 10px;
    align-items: stretch;
  }

  .variant-head-actions {
    justify-content: flex-start;
  }
}
</style>
