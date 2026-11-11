import { ref } from 'vue'
import { defineStore } from 'pinia'
import { cartApi } from '@/api'
import { useAuthStore } from './auth'

export const useCartBadgeStore = defineStore('cartBadge', () => {
  const count = ref(0)
  const visible = ref(false)

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.user?.id) {
      visible.value = false
      count.value = 0
      return
    }
    try {
      const items = await cartApi.listUnpurchased(auth.user.id)
      const n = Array.isArray(items) ? items.length : 0
      count.value = n
      visible.value = n > 0
    } catch {
      visible.value = false
      count.value = 0
    }
  }

  return { count, visible, refresh }
})
