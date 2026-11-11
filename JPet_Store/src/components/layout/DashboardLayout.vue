<template>
  <el-container class="dashboard-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
      <div class="logo-container" @click="router.push('/')">
        <el-icon class="logo-icon" :size="24"><Shop /></el-icon>
        <span class="logo-text" v-show="!isCollapse">JPet Store Admin</span>
      </div>

      <el-menu
        :default-active="activePath"
        class="side-menu"
        :collapse="isCollapse"
        :collapse-transition="true"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <el-menu-item index="/index">
          <el-icon><HomeFilled /></el-icon>
          <template #title>{{ auth.canAccessMerchant ? '商家首页' : '商家工作台' }}</template>
        </el-menu-item>
        <el-menu-item index="/category" :disabled="!auth.canAccessMerchant">
          <el-icon><List /></el-icon>
          <template #title>分类管理</template>
        </el-menu-item>
        <el-menu-item index="/cart" :disabled="!auth.canAccessMerchant">
          <el-icon><Van /></el-icon>
          <template #title>物流管理</template>
        </el-menu-item>
        <el-menu-item index="/medical" :disabled="!auth.canAccessMerchant">
          <el-icon><FirstAidKit /></el-icon>
          <template #title>商品审核/申请</template>
        </el-menu-item>
        
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <template #title>个人中心</template>
        </el-menu-item>

        <el-menu-item v-if="auth.canAccessAdmin" index="/admin">
          <el-icon><Setting /></el-icon>
          <template #title>系统管理</template>
        </el-menu-item>

      </el-menu>
    </el-aside>

    <el-container class="main-container">
      <!-- 顶部 Header -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRouteName }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <button v-if="showMerchantApplyButton" type="button" class="merchant-apply-btn" @click="openMerchantApply">
            注册成为商家
          </button>
          <el-tooltip content="回到前台/主页" placement="bottom" v-if="auth.loggedIn">
            <el-icon class="action-icon" @click="router.push('/')"><Monitor /></el-icon>
          </el-tooltip>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="avatar-wrapper">
              <el-avatar :size="32" :src="auth.user?.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <span class="username">{{ auth.user?.username || '未登录' }}</span>
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主要内容区 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ArrowDown,
  Expand,
  FirstAidKit,
  Fold,
  HomeFilled,
  List,
  Monitor,
  Setting,
  Shop,
  User,
  Van,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const isCollapse = ref(false)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const activePath = computed(() => route.path)
const showMerchantApplyButton = computed(() => auth.loggedIn && !auth.canAccessMerchant && !auth.canAccessAdmin)

const currentRouteName = computed(() => {
  if (route.meta?.title) return route.meta.title
  return auth.canAccessAdmin ? '管理面板' : '商家工作台'
})

function openMerchantApply() {
  router.push({
    path: '/profile',
    query: {
      ...route.query,
      openApply: String(Date.now()),
    },
  })
}

const handleCommand = (command) => {
  if (command === 'logout') {
    auth.logout()
  } else if (command === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.dashboard-layout {
  height: 100vh;
  width: 100%;
}

.aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
  box-shadow: 2px 0 6px rgba(0,21,41,0.35);
  display: flex;
  flex-direction: column;
}

.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3643;
  color: #fff;
  cursor: pointer;
  padding: 0 10px;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  color: #409EFF;
}

.logo-text {
  margin-left: 10px;
  font-weight: 600;
  font-size: 16px;
}

.side-menu {
  flex: 1;
  border-right: none;
}
.side-menu:not(.el-menu--collapse) {
  width: 220px;
}

.main-container {
  display: flex;
  flex-direction: column;
  background-color: #f0f2f5;
}

.header {
  height: 60px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 9;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  margin-right: 20px;
  transition: color 0.3s;
}
.collapse-btn:hover {
  color: #409EFF;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.merchant-apply-btn {
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #ffb648 0%, #ff8a3d 100%);
  color: #fff;
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 10px 20px rgba(255, 138, 61, 0.2);
}

.merchant-apply-btn:hover {
  filter: brightness(1.03);
}

.action-icon {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;
}
.action-icon:hover {
  color: #409EFF;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 8px;
  border-radius: 4px;
}
.avatar-wrapper:hover {
  background: rgba(0,0,0,0.025);
}

.username {
  margin-left: 8px;
  font-size: 14px;
  color: #333;
}

.main-content {
  padding: 24px;
  box-sizing: border-box;
  overflow-y: auto;
  position: relative;
}

/* 渐变动画 */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all .3s;
}
.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>