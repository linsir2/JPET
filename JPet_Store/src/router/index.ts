import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/buyer/stores/app'

/** 与 LoginInterceptor 一致：/chat 不强制登录 */
const PUBLIC_PATHS = new Set(['/login', '/chat'])
const BUYER_PUBLIC_PATHS = new Set(['/buyer/index', '/buyer/category', '/buyer/chat'])
const BUYER_AUTH_PATHS = new Set([
  '/buyer/cart',
  '/buyer/profile',
  '/buyer/orders',
  '/buyer/favorites',
  '/buyer/addresses',
  '/buyer/coupons',
  '/buyer/settings',
  '/buyer/help',
])

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/buyer/index' },
    { path: '/buyer', redirect: '/buyer/index' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/ChatView.vue') },
    { path: '/merchant/onboarding', redirect: '/profile' },
    { path: '/buyer/login', redirect: '/login?tab=buyer' },
    {
      path: '/buyer/index',
      name: 'buyer-index',
      component: () => import('@/buyer/views/HomeView.vue'),
      meta: { segment: 'buyer', title: '买家首页' },
    },
    {
      path: '/buyer/category',
      name: 'buyer-category',
      component: () => import('@/buyer/views/CategoryView.vue'),
      meta: { segment: 'buyer', title: '商品分类' },
    },
    {
      path: '/buyer/cart',
      name: 'buyer-cart',
      component: () => import('@/buyer/views/CartView.vue'),
      meta: { segment: 'buyer', title: '购物车' },
    },
    {
      path: '/buyer/profile',
      name: 'buyer-profile',
      component: () => import('@/buyer/views/ProfileView.vue'),
      meta: { segment: 'buyer', title: '个人中心' },
    },
    {
      path: '/buyer/orders',
      name: 'buyer-orders',
      component: () => import('@/buyer/views/OrdersView.vue'),
      meta: { segment: 'buyer', title: '我的订单' },
    },
    {
      path: '/buyer/favorites',
      name: 'buyer-favorites',
      component: () => import('@/buyer/views/ServiceCenterView.vue'),
      meta: { segment: 'buyer', title: '我的收藏' },
    },
    {
      path: '/buyer/addresses',
      name: 'buyer-addresses',
      component: () => import('@/buyer/views/ServiceCenterView.vue'),
      meta: { segment: 'buyer', title: '收货地址' },
    },
    {
      path: '/buyer/coupons',
      name: 'buyer-coupons',
      component: () => import('@/buyer/views/ServiceCenterView.vue'),
      meta: { segment: 'buyer', title: '优惠券' },
    },
    {
      path: '/buyer/settings',
      name: 'buyer-settings',
      component: () => import('@/buyer/views/ServiceCenterView.vue'),
      meta: { segment: 'buyer', title: '设置' },
    },
    {
      path: '/buyer/help',
      name: 'buyer-help',
      component: () => import('@/buyer/views/ServiceCenterView.vue'),
      meta: { segment: 'buyer', title: '帮助中心' },
    },
    {
      path: '/buyer/chat',
      name: 'buyer-chat',
      component: () => import('@/buyer/views/ChatView.vue'),
      meta: { segment: 'buyer', title: '在线客服' },
    },
    // 带有 DashboardLayout 的路由
    {
      path: '/',
      component: () => import('@/components/layout/DashboardLayout.vue'),
      children: [
        { path: 'index', name: 'index', component: () => import('@/views/MerchantHomeEntryView.vue'), meta: { title: '商家工作台' } },
        { path: 'category', name: 'category', component: () => import('@/views/CategoryView.vue'), meta: { audience: 'merchant', title: '分类管理' } },
        { path: 'cart', name: 'logistics', component: () => import('@/views/LogisticsView.vue'), meta: { audience: 'merchant', title: '物流管理' } },
        { path: 'profile', name: 'merchant-center', component: () => import('@/views/MerchantCenterView.vue'), meta: { title: '个人中心' } },
        { path: 'medical', name: 'product-audit', component: () => import('@/views/ProductAuditView.vue'), meta: { audience: 'merchant', title: '商品审核' } },
        { path: 'admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { audience: 'admin', title: '系统管理' } },
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'notfound', component: () => import('@/views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.segment === 'buyer') {
  const auth = useAuthStore()
  const buyerStore = useAppStore()
  if (!auth.loaded) {
    await auth.refresh()
  }

  if (!buyerStore.state.initialized || buyerStore.state.user?.id !== (auth.user?.id ? String(auth.user.id) : undefined)) {
    await buyerStore.refreshSession()
  }

  if (BUYER_PUBLIC_PATHS.has(to.path)) {
    return true
  }

  if (BUYER_AUTH_PATHS.has(to.path) && !auth.loggedIn) {
    return {
    path: '/login',
    query: { tab: 'buyer', redirect: to.fullPath },
    }
  }

  if (BUYER_AUTH_PATHS.has(to.path) && auth.loggedIn && !auth.canAccessBuyer) {
    return { path: auth.getHomePath() }
  }

  return true
  }

  const auth = useAuthStore()
  if (!auth.loaded) {
    await auth.refresh()
  }

  if (to.path === '/login') {
    if (auth.loggedIn) {
      if (to.query.tab === 'buyer') {
        return { path: '/buyer/index' }
      }
      if (to.query.tab === 'merchant' && !auth.canAccessMerchant) {
        return { path: '/index' }
      }
      return { path: auth.getHomePath() }
    }
    return true
  }

  if (PUBLIC_PATHS.has(to.path)) {
    return true
  }

  if (!auth.loggedIn) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.meta.audience === 'admin' && !auth.canAccessAdmin) {
    return { path: auth.getHomePath() }
  }

  if (to.meta.audience === 'merchant' && !auth.canAccessMerchant) {
    return {
      path: '/index',
      query: { err: '当前账号尚未获得商家权限，请先完成商家入驻申请并等待审核通过。' },
    }
  }

  return true
})

export default router
