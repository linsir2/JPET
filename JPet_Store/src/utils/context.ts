/**
 * 与旧站 window.contextPath 对齐（Thymeleaf 注入）
 */
export function getContextPath() {
  if (typeof window !== 'undefined' && window.__CONTEXT_PATH__ != null) {
    return window.__CONTEXT_PATH__
  }
  return import.meta.env.VITE_CONTEXT_PATH ?? ''
}

export function setContextPath(value) {
  const v = value ?? ''
  if (typeof window !== 'undefined') {
    window.__CONTEXT_PATH__ = v
    window.contextPath = v
  }
}
