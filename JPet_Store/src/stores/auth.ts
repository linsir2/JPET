import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  clearAuthSession,
  ensureValidAccessToken,
  getDefaultRouteByRole,
  isAdminRole,
  isMerchantRole,
  isNormalUserRole,
  readAuthSession,
  saveLoginSession,
} from '@/utils/authSession'

export const useAuthStore = defineStore('auth', () => {
  const session = readAuthSession()
  const user = ref(session.user)
  const role = ref(session.role)
  const loaded = ref(false)

  const loggedIn = computed(() => Boolean(user.value?.id && role.value))
  const welcomeUsername = computed(() => user.value?.username || '用户')
  const isAdmin = computed(() => isAdminRole(role.value))
  const canAccessAdmin = computed(() => isAdminRole(role.value))
  const canAccessMerchant = computed(() => isMerchantRole(role.value))
  const canAccessBuyer = computed(() => isNormalUserRole(role.value) || isMerchantRole(role.value))

  function syncFromSession() {
    const next = readAuthSession()
    user.value = next.user
    role.value = next.role
    syncWindowUser()
  }

  async function refresh() {
    await ensureValidAccessToken()
    syncFromSession()
    loaded.value = true
    return user.value
  }

  function applySession(payload) {
    saveLoginSession(payload)
    syncFromSession()
    loaded.value = true
  }

  function clear() {
    clearAuthSession()
    syncFromSession()
    loaded.value = true
  }

  function logout(nextRole = role.value) {
    clear()
    if (typeof window !== 'undefined') {
      const baseUrl = import.meta.env.BASE_URL || '/'
      const normalizedBaseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
      const targetPath = isAdminRole(nextRole) ? 'login?tab=merchant' : 'buyer/index'
      window.location.replace(`${normalizedBaseUrl}${targetPath}`)
    }
  }

  function getHomePath(nextRole = role.value) {
    return getDefaultRouteByRole(nextRole)
  }

  function syncWindowUser() {
    if (typeof window !== 'undefined') {
      window.currentUser = user.value
    }
  }

  return {
    user,
    role,
    loaded,
    loggedIn,
    welcomeUsername,
    isAdmin,
    canAccessAdmin,
    canAccessMerchant,
    canAccessBuyer,
    refresh,
    applySession,
    clear,
    logout,
    getHomePath,
    syncWindowUser,
  }
})
