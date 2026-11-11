const CACHE_VERSION = 'jpet-store-v2'
const STATIC_CACHE = `${CACHE_VERSION}-static`
const API_CACHE = `${CACHE_VERSION}-api`
const APP_SHELL_PATTERNS = [
  '/spa/',
  '/spa/index.html',
]
const STATIC_URL_PATTERNS = [
  '/spa/assets/',
  '/spa/images/',
  '/spa/favicon.svg',
  '/spa/icons.svg',
  '/uploads/products/',
  '/spa/uploads/products/',
]
const PUBLIC_API_PATTERNS = [
  '/api/products',
]

self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting())
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== STATIC_CACHE && key !== API_CACHE)
          .map((key) => caches.delete(key)),
      ),
    ).then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event

  if (request.method !== 'GET') {
    return
  }

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) {
    return
  }

  if (matchesPattern(url.pathname, APP_SHELL_PATTERNS)) {
    event.respondWith(networkFirst(request, STATIC_CACHE))
    return
  }

  if (matchesPattern(url.pathname, STATIC_URL_PATTERNS)) {
    event.respondWith(cacheFirst(request, STATIC_CACHE))
    return
  }

  if (matchesPattern(url.pathname, PUBLIC_API_PATTERNS)) {
    event.respondWith(staleWhileRevalidate(request, API_CACHE))
  }
})

function matchesPattern(pathname, patterns) {
  return patterns.some((pattern) => pathname === pattern || pathname.startsWith(pattern))
}

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)
  if (cached) {
    return cached
  }

  const response = await fetch(request)
  if (response.ok) {
    cache.put(request, response.clone())
  }
  return response
}

async function staleWhileRevalidate(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)

  const networkRequest = fetch(request)
    .then((response) => {
      if (response.ok) {
        cache.put(request, response.clone())
      }
      return response
    })
    .catch(() => null)

  if (cached) {
    eventSafeWaitUntil(networkRequest)
    return cached
  }

  const networkResponse = await networkRequest
  if (networkResponse) {
    return networkResponse
  }

  return new Response(JSON.stringify({ code: 503, message: '缓存未命中且网络不可用' }), {
    status: 503,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}

async function networkFirst(request, cacheName) {
  const cache = await caches.open(cacheName)
  try {
    const response = await fetch(request)
    if (response.ok) {
      cache.put(request, response.clone())
    }
    return response
  } catch {
    const cached = await cache.match(request)
    if (cached) {
      return cached
    }

    return new Response('network unavailable', {
      status: 503,
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
      },
    })
  }
}

function eventSafeWaitUntil(promise) {
  promise.catch(() => undefined)
}