import http from './http'
import { resolveAssetUrl } from '@/utils/assets'

function unwrap(response: any) {
  return response?.data?.data
}

function normalizePaged(raw: any, mapper: (item: any) => any = (item) => item) {
  const list = Array.isArray(raw?.list) ? raw.list.map(mapper) : []
  return {
    list,
    total: Number(raw?.total ?? list.length ?? 0),
  }
}

function normalizeUser(item: any) {
  return {
    id: item?.id,
    username: item?.username || '-',
    email: item?.email || '-',
    role: item?.role || '-',
    status: item?.status || '-',
    profile: item?.profile || '',
    createdAt: item?.created_at || item?.createdAt || '',
  }
}

function normalizeMerchantAudit(item: any) {
  return {
    id: item?.id,
    merchantName: item?.merchant_name || item?.merchantName || '-',
    userId: item?.user_id || item?.userId || '-',
    username: item?.username || '-',
    email: item?.email || '-',
    phone: item?.phone || '-',
    imageUrl: resolveAssetUrl(item?.image_url || item?.imageUrl || ''),
    description: item?.description || '',
    status: item?.status || '-',
    auditReason: item?.audit_reason || item?.auditReason || '',
    createdAt: item?.created_at || item?.createdAt || '',
  }
}

function normalizeProductAudit(item: any) {
  return {
    id: item?.id,
    merchantId: item?.merchant_id || item?.merchantId || '-',
    name: item?.name || '-',
    description: item?.description || '',
    imageUrl: resolveAssetUrl(item?.image_url || item?.imageUrl || ''),
    status: item?.status || '-',
    auditReason: item?.audit_reason || item?.auditReason || '',
    minPrice: item?.min_price ?? item?.minPrice ?? '-',
    coreAttributes: item?.core_attributes || item?.coreAttributes || [],
    createdAt: item?.created_at || item?.createdAt || '',
  }
}

function normalizeProductDetail(item: any) {
  return {
    ...normalizeProductAudit(item),
    tags: Array.isArray(item?.tags) ? item.tags : [],
    sales: Number(item?.sales ?? 0),
    skus: Array.isArray(item?.skus)
      ? item.skus.map((sku: any) => ({
          id: sku?.id,
          status: sku?.status || '-',
          price: Number(sku?.price ?? 0),
          stock: Number(sku?.stock ?? 0),
          imageUrl: resolveAssetUrl(sku?.image_url || sku?.imageUrl || ''),
          attributeValues: sku?.attribute_values || sku?.attributeValues || {},
        }))
      : [],
  }
}

function normalizeAuthentication(item: any) {
  return {
    id: item?.id,
    merchantId: item?.merchant_id || item?.merchantId || '-',
    authenticationName: item?.authentication_name || item?.authenticationName || '-',
    companyName: item?.company_name || item?.companyName || '-',
    certificateImageUrl: item?.certificate_image_url || item?.certificateImageUrl || '',
    status: item?.status || '-',
    auditReason: item?.audit_reason || item?.auditReason || '',
    authLevel: item?.auth_level || item?.authLevel || '-',
    validUntil: item?.valid_until || item?.validUntil || '',
    createdAt: item?.created_at || item?.createdAt || '',
  }
}

export const adminUserApi = {
  list(params = {}) {
    return http.get('/api/admin/users', { params }).then((res) => normalizePaged(unwrap(res), normalizeUser))
  },
  get(id: string | number) {
    return http.get(`/api/admin/users/${id}`).then((res) => normalizeUser(unwrap(res)))
  },
  updateStatus(id: string | number, status: string) {
    return http.put(`/api/admin/users/${id}/status`, { status }).then(unwrap)
  },
}

export const adminMerchantApi = {
  listAudits(params = {}) {
    return http
      .get('/api/admin/merchants/audits', { params })
      .then((res) => normalizePaged(unwrap(res), normalizeMerchantAudit))
  },
  audit(id: string | number, payload: Record<string, any>) {
    return http
      .put(`/api/admin/merchants/${id}/audit`, {
        audit_status: payload.auditStatus,
        audit_reason: payload.auditReason,
      })
      .then(unwrap)
  },
  getAuthentications(merchantId: string | number) {
    return http
      .get(`/api/admin/merchants/${merchantId}/authentications`)
      .then((res) => {
        const raw = unwrap(res)
        return Array.isArray(raw) ? raw.map(normalizeAuthentication) : []
      })
  },
}

export const adminAuthenticationApi = {
  listAudits(params = {}) {
    return http
      .get('/api/admin/merchants/authentications/audits', { params })
      .then((res) => normalizePaged(unwrap(res), normalizeAuthentication))
  },
  audit(id: string | number, payload: Record<string, any>) {
    return http
      .put(`/api/admin/merchants/authentications/${id}/audit`, {
        audit_status: payload.auditStatus,
        audit_reason: payload.auditReason,
      })
      .then(unwrap)
  },
}

export const adminProductApi = {
  listAudits(params = {}) {
    return http
      .get('/api/admin/products/audits', { params })
      .then((res) => normalizePaged(unwrap(res), normalizeProductAudit))
  },
  getDetail(id: string | number) {
    return http.get(`/api/admin/products/${id}`).then((res) => normalizeProductDetail(unwrap(res)))
  },
  audit(id: string | number, payload: Record<string, any>) {
    return http
      .put(`/api/admin/products/${id}/audit`, {
        audit_status: payload.auditStatus,
        audit_reason: payload.auditReason,
      })
      .then(unwrap)
  },
}