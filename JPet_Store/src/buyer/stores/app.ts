import { computed, reactive, readonly } from 'vue';
import { cartApi } from '@/buyer/api/services';
import { useAuthStore } from '@/stores/auth';
import { resolveAssetUrl } from '@/utils/assets';
import type { CartItem, UserProfile } from '@/buyer/types/models';

const state = reactive({
  user: null as UserProfile | null,
  cart: [] as CartItem[],
  initialized: false
});

async function refreshSession() {
  const auth = useAuthStore();
  await auth.refresh();
  state.user = auth.canAccessBuyer && auth.user
    ? {
        id: String(auth.user.id),
        username: auth.user.username,
        email: '',
        phone: '',
        intro: '',
        avatar: resolveAssetUrl('/images/logo192.png'),
        joinedAt: '',
        address: '',
        preferences: []
      }
    : null;
  state.cart = await cartApi.list();
  state.initialized = true;
}

async function logout() {
  const auth = useAuthStore();
  auth.logout();
  state.user = null;
  state.cart = [];
  state.initialized = true;
}

async function reloadCart() {
  state.cart = await cartApi.list();
}

export function useAppStore() {
  return {
    state: readonly(state),
    isLoggedIn: computed(() => Boolean(state.user)),
    cartCount: computed(() => state.cart.reduce((sum, item) => sum + item.quantity, 0)),
    selectedCartCount: computed(() => state.cart.filter((item) => item.selected).length),
    refreshSession,
    reloadCart,
    fetchCart: reloadCart,
    logout
  };
}