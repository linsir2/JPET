import http from './http'
import type {
  MerchantAuthPayload,
  MerchantApplicationStatus,
  MerchantOrder,
  MerchantProductItem,
  MerchantProfile,
  MerchantProfilePayload,
  MerchantSpuListQuery,
  MerchantSpu,
  MerchantStats,
  PageQuery,
  PageResult,
} from '@/types/merchant'

type MerchantProductListRaw = {
  products?: Array<{
    id: number | string
    name: string
    imageUrl?: string
    image_url?: string
    minPrice?: number
    min_price?: number
    sales?: number
  }>
  pageTotal?: number
  page_total?: number
}

type MerchantProductDetailRaw = {
  id: number | string
  name: string
  description?: string
  imageUrl?: string
  image_url?: string
  status?: string
  auditReason?: string
  audit_reason?: string
  tags?: string[]
  minPrice?: number
  min_price?: number
  sales?: number
  coreAttributes?: string[]
  core_attributes?: string[]
  buyerReviews?: Array<{
    id?: number | string
    user?: string
    avatar?: string
    rating?: number
    text?: string
    date?: string
  }>
  buyer_reviews?: Array<{
    id?: number | string
    user?: string
    avatar?: string
    rating?: number
    text?: string
    date?: string
  }>
  createTime?: string
  create_time?: string
  updateTime?: string
  update_time?: string
  skus?: Array<{
    id: number | string
    attributeValues?: Record<string, unknown>
    attribute_values?: Record<string, unknown>
    imageUrl?: string
    image_url?: string
    price?: number
    stock?: number
    status?: string
  }>
}

function toProductSkuPayload(payload: Record<string, any>) {
  const skuPayload: Record<string, any> = {
    attribute_values: payload.attributeValues ?? payload.attribute_values ?? {},
  }
  if (payload.id != null) skuPayload.id = payload.id
  if (payload.spuId != null) skuPayload.spu_id = payload.spuId
  if (payload.status != null) skuPayload.status = payload.status
  if (payload.imageUrl != null || payload.image_url != null) {
    skuPayload.image_url = payload.imageUrl ?? payload.image_url
  }
  if (payload.price != null) skuPayload.price = payload.price
  if (payload.stock != null) skuPayload.stock = payload.stock
  return skuPayload
}

function toProductSpuPayload(payload: Record<string, any>) {
  const spuPayload: Record<string, any> = {}
  if (payload.id != null) spuPayload.id = payload.id
  if (payload.name != null) spuPayload.name = payload.name
  if (payload.description != null) spuPayload.description = payload.description
  if (payload.imageUrl != null || payload.image_url != null) {
    spuPayload.image_url = payload.imageUrl ?? payload.image_url
  }
  if (payload.status != null) spuPayload.status = payload.status
  if (payload.coreAttributes != null || payload.core_attributes != null) {
    spuPayload.core_attributes = payload.coreAttributes ?? payload.core_attributes
  }
  if (payload.tags != null) spuPayload.tags = payload.tags
  if (Array.isArray(payload.skus)) {
    spuPayload.skus = payload.skus.map((sku: Record<string, any>) => toProductSkuPayload(sku))
  }
  return spuPayload
}

function unwrap<T = unknown>(response: any): T {
  const data = response?.data
  if (data && typeof data === 'object' && 'data' in data) {
    return data.data as T
  }
  return data as T
}

function unwrapPage<T = unknown>(response: any): PageResult<T> {
  const raw = unwrap<any>(response)
  if (Array.isArray(raw)) {
    return {
      list: raw as T[],
      total: raw.length,
      page: 1,
      size: raw.length || 10,
    }
  }
  const list = Array.isArray(raw?.list) ? raw.list : []
  return {
    list,
    total: Number(raw?.total ?? list.length ?? 0),
    page: Number(raw?.page ?? 1),
    size: Number(raw?.size ?? 10),
  }
}

function mapMerchantProfile(raw: any): MerchantProfile {
  return {
    id: raw?.id,
    merchantName: raw?.merchant_name || raw?.merchantName || '',
    email: raw?.email || '',
    phone: raw?.phone || '',
    registerTime: raw?.registration_date || raw?.registrationDate || raw?.registerTime || '',
    introduction: raw?.description || raw?.introduction || '',
    avatarUrl: raw?.image_url || raw?.imageUrl || raw?.avatar_url || raw?.avatarUrl || '',
    verifiedName: raw?.verified_name || raw?.verifiedName || '',
    tags: Array.isArray(raw?.tags) ? raw.tags : [],
    turnoverFromCompletedOrders: Number(raw?.turnover_from_completed_orders ?? raw?.turnoverFromCompletedOrders ?? 0),
  }
}

function mapMerchantStats(raw: any): MerchantStats {
  return {
    monthRevenue: Number(raw?.month_revenue ?? raw?.monthRevenue ?? 0),
    trendPercent: Number(raw?.trend_percent ?? raw?.trendPercent ?? 0),
    rating: Number(raw?.rating ?? 0),
    totalRevenue: Number(raw?.total_revenue ?? raw?.totalRevenue ?? 0),
  }
}

function mapMerchantOrder(raw: any): MerchantOrder {
  const address = raw?.address ?? raw?.receiver_address ?? ''
  const destination = raw?.destination || (address ? String(address).split(/[ ,，]/)[0] : '')
  const updatedAt = raw?.updated_at ?? raw?.updatedAt ?? raw?.finished_at ?? raw?.finishedAt ?? raw?.created_at ?? raw?.createdAt ?? ''
  const finishedAt = raw?.finished_at ?? raw?.finishedAt ?? raw?.completed_at ?? raw?.completedAt ?? raw?.updated_at ?? raw?.updatedAt ?? ''
  const progress = raw?.progress ?? raw?.shipping_progress ?? ''
  const currentNode = raw?.current_node ?? raw?.currentNode ?? ''
  const receiverName = raw?.receiver_name ?? raw?.receiverName ?? raw?.receiver ?? ''

  return {
    id: raw?.id,
    name: raw?.name || '-',
    image: raw?.image || raw?.image_url || raw?.imageUrl || '',
    amount: Number(raw?.amount ?? 0),
    unitPrice: Number(raw?.unit_price ?? raw?.unitPrice ?? 0),
    quantity: Number(raw?.quantity ?? 1),
    destination,
    receiverName,
    receiverPhone: raw?.receiver_phone ?? raw?.receiverPhone ?? '',
    address,
    skuSummary: raw?.sku_summary ?? raw?.skuSummary ?? '',
    skuAttributes: raw?.sku_attributes ?? raw?.skuAttributes ?? {},
    createdAt: raw?.created_at ?? raw?.createdAt ?? '',
    trackingNo: raw?.tracking_no ?? raw?.trackingNo ?? '',
    currentNode,
    progress,
    mapX: Number(raw?.map_x ?? raw?.mapX ?? (progress === '已寄出' ? 44 : 82)),
    mapY: Number(raw?.map_y ?? raw?.mapY ?? 50),
    updatedAt,
    reason: raw?.reason ?? raw?.cancel_reason ?? raw?.cancelReason ?? '',
    receiver: raw?.receiver ?? receiverName,
    buyerUserId: raw?.buyer_user_id ?? raw?.buyerUserId ?? '',
    buyerName: raw?.buyer_name ?? raw?.buyerName ?? raw?.reviewer_name ?? raw?.reviewerName ?? receiverName,
    buyerAvatar: raw?.buyer_avatar ?? raw?.buyerAvatar ?? '',
    finishedAt,
    reviewRating: Number(raw?.review_rating ?? raw?.reviewRating ?? 0),
    reviewContent: raw?.review_content ?? raw?.reviewContent ?? '',
    reviewerName: raw?.reviewer_name ?? raw?.reviewerName ?? '',
    reviewTime: raw?.review_time ?? raw?.reviewTime ?? '',
  }
}

function formatProductStatus(status?: string) {
  const normalized = String(status || '').trim().toLowerCase()
  if (normalized === 'pending') return '待审核'
  if (normalized === 'rejected') return '审核失败'
  if (normalized === 'published') return '已上架'
  if (normalized === 'taken_down') return '已下架'
  if (normalized === 'draft') return '草稿'
  return status || ''
}

function buildCombination(attributeValues?: Record<string, unknown>) {
  const entries = Object.entries(attributeValues || {})
  if (!entries.length) return '默认规格'
  return entries.map(([key, value]) => `${key}=${String(value ?? '').trim()}`).join(' / ')
}

function mapProductDetail(detail: MerchantProductDetailRaw): MerchantProductItem {
  const skus = Array.isArray(detail?.skus) ? detail.skus : []
  const variantPrices = skus.map((sku) => ({
    skuId: sku.id,
    combination: buildCombination(sku.attributeValues ?? sku.attribute_values),
    price: Number(sku.price ?? 0),
    stock: Number(sku.stock ?? 0),
    image: sku.imageUrl ?? sku.image_url,
    values: Object.fromEntries(
      Object.entries(sku.attributeValues ?? sku.attribute_values ?? {}).map(([key, value]) => [key, String(value ?? '').trim()]),
    ),
  }))
  const stock = skus.reduce((sum, sku) => sum + Number(sku.stock || 0), 0)
  return {
    id: detail.id,
    name: detail.name,
    image: detail.imageUrl ?? detail.image_url,
    price: Number(detail.minPrice ?? detail.min_price ?? variantPrices[0]?.price ?? 0),
    stock,
    sales: Number(detail.sales || 0),
    description: detail.description,
    status: formatProductStatus(detail.status),
    submittedBy: '当前商家',
    submittedAt: detail.updateTime ?? detail.update_time ?? detail.createTime ?? detail.create_time ?? '',
    specifications: variantPrices.map((item) => item.combination),
    variantPrices,
    tags: Array.isArray(detail.tags) ? detail.tags : [],
    coreAttributes: Array.isArray(detail.coreAttributes ?? detail.core_attributes)
      ? (detail.coreAttributes ?? detail.core_attributes ?? [])
      : [],
    buyerReviews: Array.isArray(detail.buyerReviews ?? detail.buyer_reviews)
      ? (detail.buyerReviews ?? detail.buyer_reviews ?? []).map((item) => ({
          id: item?.id ?? '',
          user: item?.user || '匿名用户',
          avatar: item?.avatar || '',
          rating: Number(item?.rating ?? 0),
          text: item?.text || '',
          date: item?.date || '',
        }))
      : [],
    reason: detail.auditReason ?? detail.audit_reason ?? '',
  }
}

async function fetchProductDetailItem(id: string | number) {
  const response = await http.get(`/api/merchant/products/${id}`)
  return mapProductDetail(unwrap<MerchantProductDetailRaw>(response))
}

async function listMerchantProductItems(status: string) {
  const response = await http.post('/api/merchant/products', {
    page: 1,
    size: 100,
    status,
    tags: [],
    keyword: '',
  })
  const raw = unwrap<MerchantProductListRaw>(response)
  const products = Array.isArray(raw?.products) ? raw.products : []
  const detailList = await Promise.all(products.map((product) => fetchProductDetailItem(product.id)))
  return {
    list: detailList,
    total: detailList.length,
    page: 1,
    size: detailList.length || 100,
  }
}

export const merchantApi = {
  getProfile() {
    return http.get('/api/merchant/profile').then((res) => mapMerchantProfile(unwrap(res)))
  },
  updateProfile(payload: MerchantProfilePayload) {
    return http
      .put('/api/merchant/profile', {
        merchant_name: payload.merchantName,
        email: payload.email,
        description: payload.introduction,
        image_url: payload.imageUrl,
      })
      .then((res) => mapMerchantProfile(unwrap(res)))
  },
  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http
      .post('/api/merchant/profile/avatar', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((res) => mapMerchantProfile(unwrap(res)))
  },
  getStats() {
    return http.get('/api/merchant/stats').then((res) => mapMerchantStats(unwrap(res)))
  },
  async submitAuth(payload: MerchantAuthPayload) {
    const form = new FormData()
    form.append('file', payload.certificate)
    const uploadRes = await http.post('/api/merchant/apply/image', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const uploaded = unwrap<{ imageUrl: string }>(uploadRes)
    return http
      .post('/api/merchant/apply', {
        merchant_name: payload.companyName,
        email: payload.email,
        phone: payload.phone,
        image_url: uploaded.imageUrl,
        description: '商家入驻申请待审核',
        tags: [],
      })
      .then((res) => unwrap(res))
  },
  getApplicationStatus() {
    return http.post('/api/merchant/apply/status').then((res) => {
      const raw = unwrap<any>(res)
      if (!raw) return null
      return {
        merchantId: raw?.merchant_id || raw?.merchantId,
        merchantName: raw?.merchant_name || raw?.merchantName || '',
        email: raw?.email || '',
        phone: raw?.phone || '',
        imageUrl: raw?.image_url || raw?.imageUrl || '',
        status: raw?.status || '',
        auditReason: raw?.audit_reason || raw?.auditReason || '',
        createdAt: raw?.created_at || raw?.createdAt || '',
      } satisfies MerchantApplicationStatus
    })
  },
  async uploadOfficialAuthImage(file: File) {
    const form = new FormData()
    form.append('file', file)
    const response = await http.post('/api/merchant/authentications/image', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return unwrap<{ imageUrl: string }>(response).imageUrl
  },
  submitOfficialAuth(payload: { companyName: string; certificateImageUrl: string }) {
    return http
      .post('/api/merchant/authentications/official', {
        company_name: payload.companyName,
        certificate_image_url: payload.certificateImageUrl,
      })
      .then((res) => unwrap(res))
  },
  listOfficialAuthRecords() {
    return http.get('/api/merchant/authentications').then((res) => {
      const raw = unwrap<any[]>(res)
      return Array.isArray(raw)
        ? raw.map((item) => ({
            id: item?.id,
            authenticationName: item?.authentication_name || item?.authenticationName || '-',
            companyName: item?.company_name || item?.companyName || '-',
            certificateImageUrl: item?.certificate_image_url || item?.certificateImageUrl || '',
            status: item?.status || '-',
            auditReason: item?.audit_reason || item?.auditReason || '',
            authLevel: item?.auth_level || item?.authLevel || '-',
            createdAt: item?.created_at || item?.createdAt || '',
          }))
        : []
    })
  },
}

export const merchantOrderApi = {
  list(params: PageQuery & Record<string, any> = {}) {
    return http.get('/api/merchant/orders', { params }).then((res) => {
      const page = unwrapPage<any>(res)
      return {
        ...page,
        list: Array.isArray(page.list) ? page.list.map(mapMerchantOrder) : [],
      }
    })
  },
  listHistory(params: PageQuery & Record<string, any> = {}) {
    return http.get('/api/merchant/orders/history', { params }).then((res) => {
      const page = unwrapPage<any>(res)
      return {
        ...page,
        list: Array.isArray(page.list) ? page.list.map(mapMerchantOrder) : [],
      }
    })
  },
  markShipped(id: string | number) {
    return http.put(`/api/merchant/orders/${id}/ship`).then((res) => mapMerchantOrder(unwrap(res)))
  },
}

export const merchantProductAuditApi = {
  listPending(params: PageQuery & Record<string, any> = {}) {
    void params
    return listMerchantProductItems('pending')
  },
  listPublished() {
    return listMerchantProductItems('published')
  },
  listOffShelf() {
    return listMerchantProductItems('taken_down')
  },
  listRejected() {
    return listMerchantProductItems('rejected')
  },
  getDetail(id: string | number) {
    return fetchProductDetailItem(id)
  },
  uploadImage(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http
      .post('/api/merchant/products/image', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((res) => unwrap<{ imageUrl: string }>(res).imageUrl)
  },
  create(payload: Record<string, any>) {
    return http.post('/api/merchant/products/spu', toProductSpuPayload(payload)).then(async (res) => {
      const result = unwrap<{ productId: number | string }>(res)
      return fetchProductDetailItem(result.productId)
    })
  },
  update(_id: string | number, payload: Record<string, any>) {
    return http
      .put('/api/merchant/products/spu', toProductSpuPayload(payload))
      .then(async () => fetchProductDetailItem(payload.id))
  },
  withdraw(id: string | number) {
    return http.put('/api/merchant/products/spu', { id, status: 'draft' }).then((res) => unwrap(res))
  },
  updateStatus(id: string | number, status: string) {
    return http.put('/api/merchant/products/spu', { id, status }).then((res) => unwrap(res))
  },
  remove(id: string | number) {
    return http.delete('/api/merchant/products/spu', { data: [id] }).then((res) => unwrap(res))
  },
}

export const merchantSpuApi = {
  list(params: MerchantSpuListQuery = {}) {
    return http.get('/api/merchant/spu', { params }).then((res) => unwrapPage<MerchantSpu>(res))
  },
  getById(id: string | number) {
    return http.get(`/api/merchant/spu/${id}`).then((res) => unwrap<MerchantSpu>(res))
  },
  create(payload: MerchantSpu) {
    return http.post('/api/merchant/spu', payload).then((res) => unwrap<MerchantSpu>(res))
  },
  update(id: string | number, payload: Partial<MerchantSpu>) {
    return http.put(`/api/merchant/spu/${id}`, payload).then((res) => unwrap<MerchantSpu>(res))
  },
  remove(id: string | number) {
    return http.delete(`/api/merchant/spu/${id}`).then((res) => unwrap(res))
  },
}
