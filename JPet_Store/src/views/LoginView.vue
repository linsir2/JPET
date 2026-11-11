<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { loginAdmin, loginBuyer, loginMerchant, registerBuyer } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { getDefaultRouteByRole, isAdminRole, isMerchantRole, isNormalUserRole } from '@/utils/authSession'
import { getImagePath } from '@/utils/imagePath'
import { useAppStore as useBuyerAppStore } from '@/buyer/stores/app'

import '@/styles/legacy/login.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const buyerStore = useBuyerAppStore()

const loginHeaderStyle = ref<Record<string, string>>({})
const loginLoading = ref(false)
const buyerRegisterLoading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const activeTab = ref<'buyer' | 'merchant'>('buyer')
const buyerRegisterErrorMsg = ref('')
const buyerRegisterOpen = ref(false)
const captchaCode = ref('')

const buyerLoginForm = reactive({
  credential: '',
  password: '',
  captcha: '',
})

const merchantLoginForm = reactive({
  credential: '',
  password: '',
  captcha: '',
})

const buyerRegisterForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const buyerRegisterErrors = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const currentLoginForm = computed(() => activeTab.value === 'buyer' ? buyerLoginForm : merchantLoginForm)
const loginTitle = computed(() => activeTab.value === 'buyer' ? '买家登录' : '商家 / 管理员登录')
const loginSubtitle = computed(() => {
  if (activeTab.value === 'buyer') {
    return '默认先进入买家界面，买家注册和商家注册完全分开。'
  }
  if (route.query.redirect === '/admin') {
    return '管理员也从这个入口登录，成功后会直接进入管理员控制台。'
  }
  return '商家和管理员共用后台登录入口，系统会根据身份自动跳转。'
})
const entryHint = computed(() => activeTab.value === 'buyer'
  ? '先浏览买家商城，登录后可进入购物车和个人中心。'
  : '商家和管理员共用后台入口，管理员无需单独切换到其他页面登录。')

function getLoginSuccessTarget(role: string | null) {
  const raw = route.query.redirect
  if (activeTab.value === 'buyer') {
    if (typeof raw === 'string' && raw.startsWith('/') && raw !== '/admin') {
      return raw
    }
    return '/buyer/index'
  }
  if (typeof raw === 'string' && raw.startsWith('/')) {
    if (isAdminRole(role)) {
      return raw === '/admin' ? raw : '/admin'
    }
    if (raw !== '/admin') {
      return raw
    }
  }
	if (isNormalUserRole(role)) {
    return '/index'
	}
  return getDefaultRouteByRole(role)
}

function getSeason(month: number) {
  if (month >= 3 && month <= 5) return 'spring'
  if (month >= 6 && month <= 8) return 'summer'
  if (month >= 9 && month <= 11) return 'autumn'
  return 'winter'
}

function getSeasonBackground() {
  const currentMonth = dayjs().month() + 1
  const season = getSeason(currentMonth)
  const map = {
    spring: getImagePath('images/spring.jpg'),
    summer: getImagePath('images/summer.jpg'),
    autumn: getImagePath('images/autumn.jpg'),
    winter: getImagePath('images/winter.jpg'),
  }
  return map[season] || map.spring
}

function generateCaptcha() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  captchaCode.value = Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

function clearMessages() {
  errorMsg.value = ''
  successMsg.value = ''
}

function syncTabFromRoute() {
  activeTab.value = route.query.tab === 'merchant' || route.query.redirect === '/admin' ? 'merchant' : 'buyer'
}

function switchTab(tab: 'buyer' | 'merchant') {
  activeTab.value = tab
  router.replace({
    query: {
    ...route.query,
    tab,
    },
  })
  clearMessages()
}

function openBuyerRegisterModal(event: MouseEvent) {
  event.preventDefault()
  clearMessages()
  buyerRegisterErrorMsg.value = ''
  buyerRegisterOpen.value = true
}

function closeBuyerRegisterModal() {
  buyerRegisterOpen.value = false
  buyerRegisterErrorMsg.value = ''
  buyerRegisterErrors.username = ''
  buyerRegisterErrors.email = ''
  buyerRegisterErrors.password = ''
  buyerRegisterErrors.confirmPassword = ''
}

async function navigateAfterLoginSuccess(role: string | null) {
  await auth.refresh()
  await buyerStore.refreshSession()
  await router.replace(getLoginSuccessTarget(role))
}

async function tryBuyerLogin(credential: string, password: string) {
  return loginBuyer({
    identifier: credential,
    password,
  })
}

async function tryMerchantLogin(credential: string, password: string) {
  return loginMerchant({
    identifier: credential,
    password,
  })
}

async function tryAdminLogin(credential: string, password: string) {
  return loginAdmin({
    account: credential,
    password,
  })
}

async function smartLogin(credential: string, password: string) {
  const preferAdmin = route.query.redirect === '/admin'
  const loginAttempts = preferAdmin
    ? [
        () => tryAdminLogin(credential, password),
        () => tryMerchantLogin(credential, password),
      ]
    : [
        () => tryMerchantLogin(credential, password),
        () => tryAdminLogin(credential, password),
      ]

  let lastError: unknown = null
  for (const attempt of loginAttempts) {
    try {
      return await attempt()
    } catch (error) {
      lastError = error
    }
  }
  throw lastError instanceof Error ? lastError : new Error('账号或密码错误，请稍后重试')
}

async function onLoginSubmit(event: Event) {
  event.preventDefault()
  clearMessages()

  if (currentLoginForm.value.captcha.trim().toUpperCase() !== captchaCode.value) {
    errorMsg.value = '验证码错误，请重新输入'
    currentLoginForm.value.captcha = ''
    generateCaptcha()
    return
  }

  loginLoading.value = true
  try {
    const credential = currentLoginForm.value.credential.trim()
    const session = activeTab.value === 'buyer'
		? await tryBuyerLogin(credential, currentLoginForm.value.password)
		: await smartLogin(credential, currentLoginForm.value.password)

    auth.applySession(session)
    successMsg.value = '登录成功，正在根据权限跳转...'
    await navigateAfterLoginSuccess(session.role)
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
    currentLoginForm.value.captcha = ''
    generateCaptcha()
  } finally {
    loginLoading.value = false
  }
}

function validateFormRegister(
	form: { username: string; email: string; password: string; confirmPassword: string },
	errors: { username: string; email: string; password: string; confirmPassword: string },
) {
	const username = form.username.trim()
	const email = form.email.trim()
	const password = form.password

	errors.username = username.length >= 3 ? '' : '用户名长度至少 3 位'
	errors.email = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email)
    ? ''
    : '请输入有效的邮箱地址'

  if (password.length < 8) {
		errors.password = '密码长度至少 8 位'
  } else if (!/^[A-Za-z\d_]+$/.test(password)) {
		errors.password = '密码仅支持字母、数字和下划线'
  } else if (!/(?=.*[a-z])/.test(password) || !/(?=.*[A-Z])/.test(password) || !/(?=.*\d)/.test(password)) {
		errors.password = '密码需同时包含大小写字母和数字'
  } else {
		errors.password = ''
  }

	errors.confirmPassword =
		form.confirmPassword === password ? '' : '两次输入的密码不一致'

  return !(
		errors.username ||
		errors.email ||
		errors.password ||
		errors.confirmPassword
  )
}

function validateBuyerRegister() {
	return validateFormRegister(buyerRegisterForm, buyerRegisterErrors)
}

async function onBuyerRegisterSubmit(event: Event) {
	event.preventDefault()
	clearMessages()
	buyerRegisterErrorMsg.value = ''
	if (!validateBuyerRegister()) {
	  return
	}

	buyerRegisterLoading.value = true
	try {
    const registeredUsername = buyerRegisterForm.username.trim()
	  await registerBuyer({
    username: registeredUsername,
		email: buyerRegisterForm.email.trim(),
		password: buyerRegisterForm.password,
	  })
	  closeBuyerRegisterModal()
	  buyerRegisterForm.username = ''
	  buyerRegisterForm.email = ''
	  buyerRegisterForm.password = ''
	  buyerRegisterForm.confirmPassword = ''
    buyerLoginForm.credential = registeredUsername
	  buyerLoginForm.password = ''
	  buyerLoginForm.captcha = ''
	  successMsg.value = '买家注册成功，请在买家标签页登录。'
	  switchTab('buyer')
	  generateCaptcha()
	} catch (error) {
	  buyerRegisterErrorMsg.value = error instanceof Error ? error.message : '注册失败，请稍后重试'
	} finally {
	  buyerRegisterLoading.value = false
	}
}

onMounted(() => {
  loginHeaderStyle.value = {
    backgroundImage: `url(${getSeasonBackground()})`,
  }
  syncTabFromRoute()
  generateCaptcha()
  if (typeof route.query.err === 'string') {
    errorMsg.value = route.query.err
  }
})

watch(() => route.query.tab, () => {
	syncTabFromRoute()
})
</script>

<template>
  <div class="login-page">
    <div id="loginHeader" class="login-scene" :style="loginHeaderStyle">
      <div class="login-scene-overlay"></div>

      <section class="login-shell">
        <div class="login-brand-panel">
          <p class="login-eyebrow">JPet Store</p>
          <h1 class="login-title">宠物商店</h1>
          <p class="login-description">{{ entryHint }}</p>

          <div class="login-mini-notes">
            <span>默认进入买家商城</span>
            <span>商家 / 管理员共用后台入口</span>
            <span>买家 / 商家注册分离</span>
          </div>
        </div>

        <form class="login-panel" @submit="onLoginSubmit">
          <div class="login-panel-header">
            <p class="panel-kicker">JPet Store</p>
            <div class="role-tabs">
              <button
                type="button"
                class="role-tab"
                :class="{ active: activeTab === 'buyer' }"
                @click="switchTab('buyer')"
              >
                买家
              </button>
              <button
                type="button"
                class="role-tab"
                :class="{ active: activeTab === 'merchant' }"
                @click="switchTab('merchant')"
              >
                商家 / 管理员
              </button>
            </div>
            <h2>{{ loginTitle }}</h2>
            <p>{{ loginSubtitle }}</p>
          </div>

          <div v-if="errorMsg" class="error-message-box">{{ errorMsg }}</div>
          <div v-if="successMsg" class="success-message-box">{{ successMsg }}</div>

          <div class="login-field-stack">
            <div class="login-field">
              <label class="login-form-label">账号</label>
              <input
                v-model="currentLoginForm.credential"
                class="login-form-input"
                :class="{ 'input-error': !!errorMsg }"
                type="text"
                :placeholder="activeTab === 'buyer' ? '请输入买家用户名或邮箱' : '请输入商家用户名、邮箱或管理员账号'"
                autocomplete="username"
                required
              />
            </div>

            <div class="login-field">
              <label class="login-form-label">密码</label>
              <input
                v-model="currentLoginForm.password"
                class="login-form-input"
                :class="{ 'input-error': !!errorMsg }"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
                required
              />
            </div>

            <div class="login-field">
              <label class="login-form-label">验证码</label>
              <div class="captcha-row">
                <input
                  v-model="currentLoginForm.captcha"
                  class="login-form-input captcha-input"
                  :class="{ 'input-error': !!errorMsg && errorMsg.includes('验证码') }"
                  type="text"
                  maxlength="4"
                  placeholder="请输入验证码"
                  autocomplete="off"
                  required
                />
                <button type="button" class="captcha-box" @click="generateCaptcha">{{ captchaCode }}</button>
              </div>
              <button type="button" class="captcha-refresh" @click="generateCaptcha">看不清？换一个</button>
            </div>
          </div>

          <button class="login-form-button" type="submit" :disabled="loginLoading">
            {{ loginLoading ? '登录中...' : activeTab === 'buyer' ? '登录并进入买家商城' : '登录并进入后台' }}
          </button>

          <div class="login-panel-footer">
            <template v-if="activeTab === 'buyer'">
              <span>没有买家账号？</span>
              <a class="login-form-link" href="#" @click="openBuyerRegisterModal">立即注册</a>
            </template>
            <template v-else>
              <span>可以用原账号先登录商家界面再注册</span>
            </template>
          </div>
        </form>
      </section>
    </div>

    <div
    v-show="buyerRegisterOpen"
    class="register-overlay"
    @click="closeBuyerRegisterModal"
  >
    <div class="register-content" @click.stop>
    <div class="register-header">
      <h2 class="register-title">买家注册</h2>
      <button type="button" class="register-close" @click="closeBuyerRegisterModal">
      <span class="register-close-icon">×</span>
      </button>
    </div>

    <form class="register-form" @submit="onBuyerRegisterSubmit">
      <p class="register-tip">买家注册成功后可直接登录进入买家商城。</p>
      <div v-if="buyerRegisterErrorMsg" class="register-message register-message-error">{{ buyerRegisterErrorMsg }}</div>
      <div class="form-group">
      <label class="form-label">用户名</label>
      <input v-model="buyerRegisterForm.username" class="form-input" type="text" placeholder="3 位及以上用户名" required />
      <span class="error-message">{{ buyerRegisterErrors.username }}</span>
      </div>
      <div class="form-group">
      <label class="form-label">邮箱</label>
      <input v-model="buyerRegisterForm.email" class="form-input" type="email" placeholder="请输入常用邮箱" required />
      <span class="error-message">{{ buyerRegisterErrors.email }}</span>
      </div>
      <div class="form-group">
      <label class="form-label">密码</label>
      <input
        v-model="buyerRegisterForm.password"
        class="form-input"
        type="password"
        placeholder="至少 8 位，含大小写字母和数字"
        required
      />
      <span class="error-message">{{ buyerRegisterErrors.password }}</span>
      </div>
      <div class="form-group">
      <label class="form-label">确认密码</label>
      <input
        v-model="buyerRegisterForm.confirmPassword"
        class="form-input"
        type="password"
        placeholder="再次输入密码确认"
        required
      />
      <span class="error-message">{{ buyerRegisterErrors.confirmPassword }}</span>
      </div>
      <div class="form-actions">
      <button type="button" class="btn-cancel" @click="closeBuyerRegisterModal">取消</button>
      <button type="submit" class="btn-submit" :disabled="buyerRegisterLoading">
        {{ buyerRegisterLoading ? '提交中...' : '注册' }}
      </button>
      </div>
    </form>
    </div>
  </div>

  </div>
</template>

<style scoped>
.role-tabs {
  display: inline-flex;
  padding: 4px;
  gap: 4px;
  border-radius: 999px;
  background: rgba(47, 36, 29, 0.08);
}

.role-tab {
  min-height: 40px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6e6258;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.role-tab.active {
  background: linear-gradient(120deg, #f97316, #14b8a6);
  color: #fff;
  box-shadow: 0 10px 24px rgba(20, 184, 166, 0.18);
}

.login-context-tip {
  margin: 0;
}
</style>