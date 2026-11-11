import { getContextPath } from './context'
import { resolveAssetUrl } from './assets'

/** 与旧站 getImagePath 一致：contextPath + '/' + imageName */
export function getImagePath(imageName) {
  if (!imageName) return ''
  const raw = String(imageName).trim()
  if (/^(https?:)?\/\//.test(raw) || raw.startsWith('data:') || raw.startsWith('blob:')) {
    return raw
  }

  const resolvedAssetUrl = resolveAssetUrl(raw)
  if (resolvedAssetUrl && resolvedAssetUrl !== raw) {
    return resolvedAssetUrl
  }

  const ctx = String(getContextPath() || '').replace(/\/+$/, '')
  const base = String(import.meta.env.BASE_URL || '/').replace(/\/+$/, '')
  const rawPath = raw.replace(/^\/+/, '')
  const encodedPath = rawPath
    .split('/')
    .map((segment) => encodeURIComponent(segment))
    .join('/')

  const basePath = base ? `${base}/` : '/'
  if (rawPath.startsWith(basePath.replace(/^\//, ''))) {
    return `${ctx}/${encodedPath}`.replace(/\/+/g, '/').replace(':/', '://')
  }

  return `${ctx}${basePath}${encodedPath}`.replace(/\/+/g, '/').replace(':/', '://')
}
