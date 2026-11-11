import axios from 'axios'
import { getContextPath } from '@/utils/context'

export type AuthRole = 'normal_user' | 'merchant_user' | 'main_admin' | 'sub_admin' | string

export interface AuthUser {
  id: number
  username: string
  role: AuthRole | null
}

export interface LoginSessionPayload {
  accessToken: string
  refreshToken: string
  role?: AuthRole | null
}

interface PersistedAuthSession {
  accessToken: string
  refreshToken: string
  role: AuthRole | null
}

interface JwtPayload {
  userId?: number | string
  userName?: string
  role?: AuthRole
  exp?: number
}

interface ResolvedAuthSession {
  accessToken: string | null
  refreshToken: string | null
  role: AuthRole | null
  user: AuthUser | null
}

const AUTH_STORAGE_KEY = 'jpet.jwt.session'
const EXPIRY_SKEW_SECONDS = 30

let refreshPromise: Promise<string | null> | null = null

function decodeBase64Url(segment: string) {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
  return atob(padded)
}

function readPersistedSession(): PersistedAuthSession | null {
  if (typeof window === 'undefined') {
    return null
  }
  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw)
    if (!parsed?.accessToken || !parsed?.refreshToken) {
      return null
    }
    return {
      accessToken: String(parsed.accessToken),
      refreshToken: String(parsed.refreshToken),
      role: parsed.role ? String(parsed.role) : null,
    }
  } catch {
    return null
  }
}

function writePersistedSession(session: PersistedAuthSession | null) {
  if (typeof window === 'undefined') {
    return
  }
  if (!session) {
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
    return
  }
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function decodeJwtPayload(token?: string | null): JwtPayload | null {
  if (!token) {
    return null
  }
  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }
  try {
    return JSON.parse(decodeBase64Url(parts[1])) as JwtPayload
  } catch {
    return null
  }
}

export function isAdminRole(role?: AuthRole | null) {
  return role === 'main_admin' || role === 'sub_admin'
}

export function isMerchantRole(role?: AuthRole | null) {
  return role === 'merchant_user'
}

export function isNormalUserRole(role?: AuthRole | null) {
	return role === 'normal_user'
}

export function getDefaultRouteByRole(role?: AuthRole | null) {
  if (isAdminRole(role)) {
    return '/admin'
  }
  if (isMerchantRole(role)) {
    return '/index'
  }
  return '/buyer/index'
}

export function readAuthSession(): ResolvedAuthSession {
  const persisted = readPersistedSession()
  if (!persisted) {
    return {
      accessToken: null,
      refreshToken: null,
      role: null,
      user: null,
    }
  }

  const payload = decodeJwtPayload(persisted.accessToken)
  const resolvedRole = persisted.role || payload?.role || null
  const rawUserId = payload?.userId
  const numericUserId = rawUserId == null ? NaN : Number(rawUserId)
  const user = Number.isFinite(numericUserId)
    ? {
        id: numericUserId,
        username: payload?.userName || '用户',
        role: resolvedRole,
      }
    : null

  return {
    accessToken: persisted.accessToken,
    refreshToken: persisted.refreshToken,
    role: resolvedRole,
    user,
  }
}

export function saveLoginSession(payload: LoginSessionPayload) {
  const decoded = decodeJwtPayload(payload.accessToken)
  writePersistedSession({
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    role: payload.role || decoded?.role || null,
  })
}

export function updateAccessToken(accessToken: string) {
  const persisted = readPersistedSession()
  if (!persisted) {
    return
  }
  const decoded = decodeJwtPayload(accessToken)
  writePersistedSession({
    accessToken,
    refreshToken: persisted.refreshToken,
    role: persisted.role || decoded?.role || null,
  })
}

export function clearAuthSession() {
  writePersistedSession(null)
}

export function isTokenExpired(token?: string | null, skewSeconds = EXPIRY_SKEW_SECONDS) {
  const exp = decodeJwtPayload(token)?.exp
  if (!exp) {
    return true
  }
  const current = Math.floor(Date.now() / 1000)
  return exp <= current + skewSeconds
}

async function requestTokenRefresh(refreshToken: string) {
  try {
    const { data } = await axios.post(
      `${getContextPath()}/api/auth/token/refresh`,
      { refreshToken },
      {
        withCredentials: true,
        timeout: 15000,
      },
    )
    const refreshedToken =
      data?.data?.access_token || data?.data?.accessToken || data?.access_token || data?.accessToken
    if (!refreshedToken) {
      throw new Error(data?.message || '刷新 access token 失败')
    }
    updateAccessToken(String(refreshedToken))
    return String(refreshedToken)
  } catch {
    clearAuthSession()
    return null
  }
}

export async function ensureValidAccessToken(forceRefresh = false) {
  const session = readAuthSession()
  if (!session.accessToken && !session.refreshToken) {
    return null
  }
  if (!forceRefresh && session.accessToken && !isTokenExpired(session.accessToken)) {
    return session.accessToken
  }
  if (!session.refreshToken) {
    clearAuthSession()
    return null
  }
  if (!refreshPromise) {
    refreshPromise = requestTokenRefresh(session.refreshToken).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}