import http from './http'

function unwrap(response: any) {
  return response?.data?.data ?? response?.data
}

function normalizeLoginPayload(raw: any) {
  return {
    accessToken: String(raw?.access_token || raw?.accessToken || ''),
    refreshToken: String(raw?.refresh_token || raw?.refreshToken || ''),
    role: raw?.role ? String(raw.role) : null,
  }
}

export async function loginMerchant(payload: { identifier: string; password: string }) {
  const data = unwrap(await http.post('/api/auth/merchant/login', payload))
  return normalizeLoginPayload(data)
}

export async function loginBuyer(payload: { identifier: string; password: string }) {
  const data = unwrap(await http.post('/api/auth/merchant/login', payload))
  return normalizeLoginPayload(data)
}

export async function loginAdmin(payload: { account: string; password: string }) {
  const data = unwrap(await http.post('/api/auth/admin/login', payload))
  return normalizeLoginPayload(data)
}

export async function registerBuyer(payload: {
  username: string
  email: string
  password: string
}) {
  const response = await http.post('/api/auth/user/register', payload)
  return unwrap(response)
}