export interface MerchantProfile {
  id?: number
  merchantName: string
  email?: string
  phone?: string
  registerTime?: string
  introduction: string
  avatarUrl?: string
  verifiedName?: string
  tags?: string[]
  turnoverFromCompletedOrders?: number
}

export interface MerchantStats {
  monthRevenue: number
  trendPercent: number
  rating: number
  totalRevenue?: number
}

export interface MerchantProfilePayload {
  merchantName: string
  introduction: string
  imageUrl?: string
  email?: string
  phone?: string
  tags?: string[]
}

export interface MerchantAuthPayload {
  companyName: string
  email: string
  phone: string
  certificate: File
}

export interface MerchantApplicationStatus {
  merchantId?: number
  merchantName?: string
  email?: string
  phone?: string
  imageUrl?: string
  status?: string
  auditReason?: string
  createdAt?: string
}

export interface MerchantOrder {
  id: number | string
  name: string
  image?: string
  amount?: number
  unitPrice?: number
  quantity?: number
  destination?: string
  receiverName?: string
  receiverPhone?: string
  address?: string
  skuSummary?: string
  skuAttributes?: Record<string, string>
  createdAt?: string
  trackingNo?: string
  currentNode?: string
  progress?: string
  mapX?: number
  mapY?: number
  updatedAt?: string
  reason?: string
  receiver?: string
  buyerUserId?: number | string
  buyerName?: string
  buyerAvatar?: string
  finishedAt?: string
  reviewRating?: number
  reviewContent?: string
  reviewerName?: string
  reviewTime?: string
}

export interface MerchantProductItem {
  id: number | string
  name: string
  image?: string
  price?: number
  stock?: number
  sales?: number
  description?: string
  status?: string
  submittedBy?: string
  submittedAt?: string
  specifications?: string[]
  variantPrices?: Array<{
    skuId?: number | string
    combination: string
    price: number
    stock?: number
    image?: string
    values?: Record<string, string>
  }>
  tags?: string[]
  coreAttributes?: string[]
  buyerReviews?: Array<{
    id: number | string
    user: string
    avatar?: string
    rating: number
    text: string
    date: string
  }>
  reason?: string
}

export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface MerchantSku {
  id?: number | string
  skuCode?: string
  image?: string
  price: number
  stock: number
  status: '可售卖' | '不可售卖'
  attributes: Record<string, string>
}

export interface MerchantSpu {
  id?: number | string
  name: string
  description?: string
  image?: string
  status: '草稿' | '待审核' | '已上架' | '已下架' | '已拒绝'
  rejectReason?: string
  tags?: string[]
  category?: string
  minPrice?: number
  price?: number
  stock?: number
  purchaseCount?: number
  heatScore?: number
  attributes?: string[]
  specifications?: string[]
  skus?: MerchantSku[]
}

export interface MerchantSpuListQuery extends PageQuery {
  category?: string
  status?: string
  tag?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}
