import http from './http'

function unwrap(response) {
  return response?.data?.data
}

export const productApi = {
  list(params = {}) {
    return http.get('/api/products', { params }).then(unwrap)
  },
  get(id) {
    return http.get('/api/products', { params: { id } }).then(unwrap)
  },
}

export const cartApi = {
  list(userId) {
    return http.get('/api/cart', { params: { userId } }).then(unwrap)
  },
  listUnpurchased(userId) {
    return http.get('/api/cart/unpurchased', { params: { userId } }).then(unwrap)
  },
  listPurchased(userId) {
    return http.get('/api/cart/purchased', { params: { userId } }).then(unwrap)
  },
  add(payload) {
    return http.post('/api/cart', payload).then(unwrap)
  },
  updateQuantity(cartItemId, quantity) {
    return http.put('/api/cart', null, { params: { cartItemId, quantity } }).then(unwrap)
  },
  updatePurchased(cartItemId, purchased) {
    return http.put('/api/cart', null, { params: { cartItemId, purchased: purchased ? 1 : 0 } }).then(unwrap)
  },
  remove(cartItemId) {
    return http.delete('/api/cart', { params: { cartItemId } }).then(unwrap)
  },
}
