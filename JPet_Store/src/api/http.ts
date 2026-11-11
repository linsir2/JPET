import axios, { AxiosHeaders } from 'axios'
import { getContextPath } from '@/utils/context'
import { clearAuthSession, ensureValidAccessToken } from '@/utils/authSession'

function formatApiErrorMessage(payload: any, fallback: string) {
  const message = payload?.message || fallback
  const data = payload?.data
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return message
  }
  const firstDetail = Object.values(data).find((value) => typeof value === 'string' && value.trim())
  if (!firstDetail) {
    return message
  }
  return message === '参数校验失败' ? firstDetail : `${message}: ${firstDetail}`
}

function formatNetworkFailure(error: any) {
  const status = error?.response?.status
  if (status === 502 || status === 503 || status === 504) {
    return '后端服务暂时不可用，请先启动 pet-shop-backend 后再重试'
  }

  const rawMessage = String(error?.message || '')
  if (error?.code === 'ERR_NETWORK' || /ECONNREFUSED|Network Error/i.test(rawMessage)) {
    return '无法连接后端服务，请确认 pet-shop-backend 已启动且监听 8080 端口'
  }

  return null
}

const http = axios.create({
  withCredentials: true,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  config.baseURL = getContextPath()
  return config
})

http.interceptors.request.use(async (config) => {
  const requestUrl = config.url || ''
  if (!requestUrl.startsWith('/api/auth/')) {
    const token = await ensureValidAccessToken()
    if (token) {
      const headers = AxiosHeaders.from(config.headers)
      headers.set('Authorization', `Bearer ${token}`)
      config.headers = headers
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code === 'number' && payload.code >= 400) {
      return Promise.reject(new Error(formatApiErrorMessage(payload, '请求失败')))
    }
    return response
  },
  async (error) => {
    const originalRequest = error?.config
    const requestUrl = originalRequest?.url || ''
    if (
      error?.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !requestUrl.startsWith('/api/auth/')
    ) {
      originalRequest._retry = true
      const refreshedToken = await ensureValidAccessToken(true)
      if (refreshedToken) {
        const headers = AxiosHeaders.from(originalRequest.headers)
        headers.set('Authorization', `Bearer ${refreshedToken}`)
        originalRequest.headers = headers
        return http.request(originalRequest)
      }
      clearAuthSession()
    }
    const networkFailureMessage = formatNetworkFailure(error)
    if (networkFailureMessage) {
      return Promise.reject(new Error(networkFailureMessage))
    }
    const message = formatApiErrorMessage(error?.response?.data, error.message || '网络请求失败')
    return Promise.reject(new Error(message))
  },
)

export default http
