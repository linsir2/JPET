<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { catalogApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';

const props = defineProps<{
  active: 'index' | 'category' | 'medical' | 'boarding' | 'cart' | 'profile' | 'chat';
  title?: string;
  subtitle?: string;
}>();

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const buyerBase = '/buyer';
const PAGE_SIZE = 6;
const keyword = ref('');
const suggestions = ref<string[]>([]);
const suggestionsVisible = ref(false);
const suggestionLoading = ref(false);
const suggestionPage = ref(0);
let suggestionTimer: number | undefined;

const navItems = [
  { key: 'index', label: '首页', path: `${buyerBase}/index` },
  { key: 'category', label: '商品分类', path: `${buyerBase}/category` },
  { key: 'chat', label: '在线客服', path: `${buyerBase}/chat?mode=official` },
  { key: 'profile', label: '我的', path: `${buyerBase}/profile` }
] as const;

function hideSuggestions() {
  suggestionsVisible.value = false;
}

const totalSuggestionPages = computed(() => Math.max(1, Math.ceil(suggestions.value.length / PAGE_SIZE)));

const visibleSuggestions = computed(() => {
  const start = suggestionPage.value * PAGE_SIZE;
  return suggestions.value.slice(start, start + PAGE_SIZE);
});

async function loadSuggestions(input: string) {
  const trimmed = input.trim();
  if (!trimmed) {
    suggestions.value = [];
    suggestionPage.value = 0;
    hideSuggestions();
    return;
  }

  suggestionLoading.value = true;
  try {
    const result = await catalogApi.getSuggestions(trimmed);
    if (keyword.value.trim() !== trimmed) {
      return;
    }
    suggestions.value = result;
    suggestionPage.value = 0;
    suggestionsVisible.value = true;
  } catch {
    suggestions.value = [];
    suggestionPage.value = 0;
    hideSuggestions();
  } finally {
    if (keyword.value.trim() === trimmed) {
      suggestionLoading.value = false;
    }
  }
}

function rotateSuggestions() {
  if (totalSuggestionPages.value <= 1) {
    return;
  }
  suggestionPage.value = (suggestionPage.value + 1) % totalSuggestionPages.value;
}

function search() {
  const trimmed = keyword.value.trim();
  if (!trimmed) return;
  hideSuggestions();
  router.push({ path: `${buyerBase}/category`, query: { search: trimmed } });
}

function searchKeyword(k: string) {
  keyword.value = k;
  search();
}

function handleSearchInput() {
  if (suggestionTimer) {
    window.clearTimeout(suggestionTimer);
  }
  suggestionTimer = window.setTimeout(() => {
    void loadSuggestions(keyword.value);
  }, 180);
}

function handleSearchFocus() {
  if (keyword.value.trim()) {
    void loadSuggestions(keyword.value);
  }
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  if (!target?.closest('.mini-search')) {
    hideSuggestions();
  }
}

async function handleLogout() {
  await store.logout();
}

watch(
  () => route.query.search,
  (value) => {
    keyword.value = typeof value === 'string' ? value : '';
  },
  { immediate: true }
);

onMounted(() => {
  document.addEventListener('click', handleDocumentClick);
});

onUnmounted(() => {
  if (suggestionTimer) {
    window.clearTimeout(suggestionTimer);
  }
  document.removeEventListener('click', handleDocumentClick);
});
</script>

<template>
  <div class="app-layout">
    <!-- 极简悬浮导航栏 -->
    <header class="floating-header">
      <div class="header-container glass-panel">
        
        <!-- 左侧 Logo -->
        <div class="brand-logo" @click="router.push(`${buyerBase}/index`)">
          <div class="logo-mark">🐾</div>
          <span class="brand-name">{{ title ?? 'PetShop Plus.' }}</span>
        </div>

        <!-- 中间 导航链接 -->
        <nav class="center-nav">
          <a v-for="item in navItems" :key="item.key" 
            class="nav-link" :class="{ 'is-active': props.active === item.key }"
            @click="router.push(item.path)">
            {{ item.label }}
          </a>
        </nav>

        <!-- 右侧 操作区 (搜索、购物车、用户) -->
        <div class="right-actions">
          <!-- 迷你搜索 -->
          <div class="mini-search">
            <span class="search-icon">🔍</span>
            <input
              v-model="keyword"
              placeholder="探索更多..."
              @input="handleSearchInput"
              @focus="handleSearchFocus"
              @keydown.enter="search"
              @keydown.esc="hideSuggestions"
            />
            <div v-show="suggestionsVisible" class="search-suggestions">
              <div v-if="suggestionLoading" class="suggestion-empty">正在搜索...</div>
              <template v-else-if="suggestions.length">
                <div class="suggestions-head">
                  <span class="suggestions-title">相关搜索</span>
                  <button
                    v-if="suggestions.length > PAGE_SIZE"
                    type="button"
                    class="suggestions-refresh"
                    @click="rotateSuggestions"
                  >
                    换一换
                  </button>
                </div>
                <button
                  v-for="item in visibleSuggestions"
                  :key="item"
                  type="button"
                  class="suggestion-item"
                  @click="searchKeyword(item)"
                >
                  {{ item }}
                </button>
              </template>
              <div v-else class="suggestion-empty">暂无相关搜索</div>
            </div>
          </div>

          <!-- 购物车 -->
          <button class="icon-btn cart-btn" @click="router.push(`${buyerBase}/cart` )" aria-label="购物车">
            <span class="icon">🛒</span>
            <span v-if="store.cartCount.value > 0" class="badge dot"></span>
          </button>

          <!-- 用户模块 (已登录/未登录) -->
          <div class="user-module" v-if="store.state.user">
            <div class="avatar-dropdown">
              <div class="avatar" @click="router.push(`${buyerBase}/profile`)">
                {{ store.state.user.username.charAt(0).toUpperCase() }}
              </div>
              <div class="dropdown-menu">
                <a @click="router.push(`${buyerBase}/profile`)">主页</a>
                <a @click="router.push(`${buyerBase}/chat?mode=official`)">客服</a>
                <hr />
                <a class="danger" @click="handleLogout">退出</a>
              </div>
            </div>
          </div>
          <div v-else class="auth-module">
            <button class="btn-text" @click="router.push({ path: '/login', query: { tab: 'buyer' } })">登录</button>
            <button class="btn-primary" @click="router.push({ path: '/login', query: { tab: 'buyer' } })">注册</button>
          </div>
        </div>

      </div>
    </header>

    <!-- 弹性页面主体 -->
    <main class="page-container">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 悬浮 Header */
.floating-header {
  position: sticky;
  top: 16px;
  z-index: 100;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 16px;
  box-sizing: border-box;
}

.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  border-radius: 999px; /* 胶囊形状 */
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 1);
  box-shadow: 0 8px 32px rgba(174, 106, 126, 0.08);
}

/* 品牌 Logo */
.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.2s ease;
}
.brand-logo:hover { transform: scale(1.02); }
.logo-mark {
  font-size: 24px;
  background: linear-gradient(135deg, var(--brand-pink), var(--brand-peach));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 4px 6px rgba(255, 122, 168, 0.2));
}
.brand-name {
  font-size: 18px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--ink-strong);
  letter-spacing: -0.5px;
}

/* 中间导航 */
.center-nav {
  display: flex;
  gap: 32px;
}
.nav-link {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-soft);
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 999px;
  transition: all 0.3s ease;
}
.nav-link:hover {
  color: var(--ink-strong);
  background: rgba(0, 0, 0, 0.03);
}
.nav-link.is-active {
  color: var(--brand-pink);
  background: rgba(255, 122, 168, 0.08);
  font-weight: 600;
}

/* 右侧操作区 */
.right-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 迷你搜索框 */
.mini-search {
  position: relative;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(200, 200, 200, 0.3);
  padding: 6px 16px;
  border-radius: 999px;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02);
  transition: all 0.3s ease;
}
.mini-search:focus-within {
  background: #fff;
  border-color: rgba(255, 122, 168, 0.3);
  box-shadow: 0 0 0 3px rgba(255, 122, 168, 0.1);
}
.mini-search input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  width: 120px;
  padding-left: 8px;
  transition: width 0.3s ease;
}
.mini-search input:focus { width: 180px; }
.search-icon { font-size: 14px; opacity: 0.6; }

.search-suggestions {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  display: grid;
  gap: 4px;
  padding: 10px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 184, 115, 0.22);
  box-shadow: 0 20px 40px rgba(48, 31, 38, 0.14);
  backdrop-filter: blur(14px);
  z-index: 20;
}

.suggestions-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 2px 4px 8px;
}

.suggestions-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-soft);
}

.suggestions-refresh {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #ff6b9e;
  background: rgba(255, 143, 177, 0.12);
}

.suggestion-item,
.suggestion-empty {
  width: 100%;
  text-align: left;
  border-radius: 14px;
  padding: 10px 12px;
  font-size: 13px;
}

.suggestion-item {
  background: rgba(255, 248, 243, 0.9);
  color: var(--ink-strong);
}

.suggestion-item:hover {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.14), rgba(255, 184, 115, 0.18));
}

.suggestion-empty {
  color: var(--ink-soft);
}

/* 图标按钮 (购物车) */
.icon-btn {
  position: relative;
  background: none;
  border: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 18px;
}
.icon-btn:hover { background: rgba(0, 0, 0, 0.04); }
.badge.dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 8px;
  height: 8px;
  background: var(--brand-pink);
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(255, 122, 168, 0.4);
}

/* 用户与登录 */
.auth-module {
  display: flex;
  gap: 8px;
}
.btn-text {
  background: none;
  border: none;
  font-weight: 600;
  font-size: 14px;
  color: var(--ink-soft);
  cursor: pointer;
}
.btn-text:hover { color: var(--ink-strong); }
.btn-primary {
  background: var(--ink-strong);
  color: #fff;
  border: none;
  padding: 8px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn-primary:hover {
  background: #111;
  transform: translateY(-1px);
}

/* 头像与下拉 */
.avatar-dropdown {
  position: relative;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-peach), var(--brand-pink));
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 600;
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(255, 122, 168, 0.3);
}
.dropdown-menu {
  position: absolute;
  top: 48px;
  right: 0;
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.08);
  border: 1px solid rgba(0,0,0,0.05);
  padding: 8px;
  width: 140px;
  display: flex;
  flex-direction: column;
  opacity: 0;
  visibility: hidden;
  transform: translateY(10px);
  transition: all 0.3s ease;
}
.avatar-dropdown:hover .dropdown-menu {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.dropdown-menu a {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-soft);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.dropdown-menu a:hover {
  background: rgba(0,0,0,0.03);
  color: var(--ink-strong);
}
.dropdown-menu a.danger { color: #e74c3c; }
.dropdown-menu a.danger:hover { background: rgba(231,76,60,0.1); }
.dropdown-menu hr { border: none; border-top: 1px solid var(--line-soft); margin: 6px 0; }

/* 主体容器 */
.page-container {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px 80px;
  box-sizing: border-box;
}

@media (max-width: 900px) {
  .center-nav { display: none; }
  .mini-search input { width: 80px; }
}
</style>
