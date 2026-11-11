import { getContextPath } from '@/utils/context'

function joinPath(prefix: string, suffix: string) {
  const normalizedPrefix = prefix.replace(/\/+$/, '')
  const normalizedSuffix = suffix.replace(/^\/+/, '')
  if (!normalizedPrefix) {
    return `/${normalizedSuffix}`
  }
  return `${normalizedPrefix}/${normalizedSuffix}`
}

const DEFAULT_PRODUCT_IMAGE_PATH = '/images/logo192.png'
const PRODUCT_PLACEHOLDER_MAP = [
  { keywords: ['饮水', '饮水机'], image: '/images/商品-宠物饮水机1.jpg' },
  { keywords: ['逗猫棒'], image: '/images/商品-逗猫棒1.jpg' },
  { keywords: ['狗粮'], image: '/images/商品-狗粮1.jpg' },
  { keywords: ['猫粮'], image: '/images/商品-猫粮1.jpg' },
  { keywords: ['喂食', '喂食机'], image: '/images/商品-宠物喂食机1.jpg' },
  { keywords: ['航空箱'], image: '/images/商品-宠物航空箱1.jpg' },
  { keywords: ['狗窝'], image: '/images/商品-狗窝1.jpg' },
  { keywords: ['猫窝'], image: '/images/商品-猫窝1.jpg' },
  { keywords: ['猫砂盆'], image: '/images/商品-猫砂盆1.jpg' },
  { keywords: ['狗盆'], image: '/images/商品-狗盆1.jpg' },
  { keywords: ['遛狗绳'], image: '/images/商品-遛狗绳1.jpg' },
  { keywords: ['猫爬架'], image: '/images/商品-豪华猫爬架.jpg' },
]

export function getDefaultProductImage() {
  return joinPath(import.meta.env.BASE_URL || '/', DEFAULT_PRODUCT_IMAGE_PATH)
}

export function getProductFallbackImage(product?: { name?: string | null; tags?: Array<string | null | undefined> }) {
  const haystack = [product?.name ?? '', ...(product?.tags ?? [])]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()

  const matched = PRODUCT_PLACEHOLDER_MAP.find((item) =>
    item.keywords.some((keyword) => haystack.includes(keyword.toLowerCase()))
  )

  if (matched) {
    return joinPath(import.meta.env.BASE_URL || '/', matched.image)
  }

  return getDefaultProductImage()
}

export function resolveAssetUrl(raw?: string | null) {
  if (!raw) {
    return ''
  }
  if (/^(https?:)?\/\//i.test(raw) || raw.startsWith('data:') || raw.startsWith('blob:')) {
    return raw
  }
  if (raw.startsWith('/uploads/') || raw.startsWith('uploads/')) {
    const path = raw.startsWith('/') ? raw : `/${raw}`
    return `${getContextPath()}${path}`
  }
  if (raw.startsWith('/images/') || raw.startsWith('images/')) {
    return joinPath(import.meta.env.BASE_URL || '/', raw)
  }
  return raw
}