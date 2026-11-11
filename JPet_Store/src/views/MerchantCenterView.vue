<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getImagePath } from '@/utils/imagePath'
import { useAuthStore } from '@/stores/auth'
import { useCartBadgeStore } from '@/stores/cartBadge'
import { merchantApi } from '@/api/merchant'

import '@/styles/legacy/common.css'
import '@/styles/legacy/merchant-center.css'

const auth = useAuthStore()
const cartBadge = useCartBadgeStore()
const router = useRouter()
const route = useRoute()

const userInfo = reactive({
  name: '',
  email: '',
  phone: '',
  registerTime: '',
  introduction: '主营宠物用品与精选服务，支持同城快速发货与售后保障。',
  avatarUrl: '',
})

const isEditing = ref(false)
const editButtonsHtml = ref('edit')
const storeStats = reactive({
  monthRevenue: 0,
  totalRevenue: 0,
  trendPercent: 0,
  rating: 0,
})

const trendLabel = ref('')
const trendClass = ref('')
const ratingText = ref('')
const avatarBusy = ref(false)
const authModalOpen = ref(false)
const accountNotice = ref('')
const officialAuthBusy = ref(false)
const officialAuthRecords = ref([])
const merchantApplicationStatus = ref(null)
const avatarInputRef = ref(null)
const authForm = reactive({
  companyName: '',
  email: '',
  phone: '',
  certificateFile: null,
  certificateFileName: '',
  certificatePreviewUrl: '',
})

const isMerchantApproved = computed(() => auth.role === 'merchant_user')
const activeOfficialAuthRecord = computed(() =>
  officialAuthRecords.value.find((item) => String(item?.status || '').toLowerCase() === 'active') || null,
)
const hasOfficialFlagshipAuth = computed(() => Boolean(activeOfficialAuthRecord.value))
const certifiedStoreName = computed(() => activeOfficialAuthRecord.value?.companyName || userInfo.name || '当前店铺')
const phoneDisplayText = computed(() => userInfo.phone?.trim() || '暂未设置')
const displayedRevenue = computed(() => (storeStats.monthRevenue > 0 ? storeStats.monthRevenue : storeStats.totalRevenue))
const revenueTitle = computed(() => (storeStats.monthRevenue > 0 ? '本月营业额' : '累计营业额'))

function loadUserInfo() {
  const saved = localStorage.getItem('userInfo')
  if (saved) {
    try {
      Object.assign(userInfo, JSON.parse(saved))
    } catch {
      /* ignore */
    }
  }
  if (auth.user?.username && !userInfo.name) {
    userInfo.name = auth.user.username
  }
}

async function loadMerchantProfile() {
  try {
    const profile = await merchantApi.getProfile()
    if (profile) {
      userInfo.name = profile.merchantName || userInfo.name
      userInfo.email = profile.email || userInfo.email
      userInfo.phone = profile.phone || userInfo.phone
      userInfo.registerTime = profile.registerTime || userInfo.registerTime
      userInfo.introduction = profile.introduction || userInfo.introduction
      userInfo.avatarUrl = profile.avatarUrl || userInfo.avatarUrl
      storeStats.totalRevenue = Number(profile.turnoverFromCompletedOrders || 0)
    }
  } catch {
    // 接口未就绪时回退本地缓存
    loadUserInfo()
  }
}

function enableEdit() {
  isEditing.value = true
  editButtonsHtml.value = 'save'
}

function cancelEdit() {
  isEditing.value = false
  editButtonsHtml.value = 'edit'
  void loadMerchantProfile()
}

async function saveUserInfo() {
  const payload = {
    merchantName: userInfo.name,
    email: userInfo.email,
    introduction: userInfo.introduction,
    imageUrl: userInfo.avatarUrl,
  }
  try {
    const profile = await merchantApi.updateProfile(payload)
    userInfo.avatarUrl = profile.avatarUrl || userInfo.avatarUrl
  } catch {
    localStorage.setItem('userInfo', JSON.stringify({ ...userInfo }))
  }
  cancelEdit()
  alert('保存成功！')
}

function changeAvatar() {
  if (avatarBusy.value) {
    return
  }
  avatarInputRef.value?.click()
}

async function onPickAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请上传图片格式的头像文件')
    return
  }

  avatarBusy.value = true
  try {
    const profile = await merchantApi.uploadAvatar(file)
    userInfo.avatarUrl = profile.avatarUrl || userInfo.avatarUrl
    accountNotice.value = '头像已更新成功。'
    alert('头像更换成功')
  } catch (error) {
    alert(error instanceof Error ? error.message : '头像更换失败，请稍后重试')
  } finally {
    avatarBusy.value = false
  }
}

function openOfficialAuthModal() {
  if (hasOfficialFlagshipAuth.value) {
    return
  }
  authModalOpen.value = true
}

function closeOfficialAuthModal() {
  authModalOpen.value = false
  if (route.query.openApply) {
    router.replace({
      path: '/profile',
      query: {
        ...route.query,
        openApply: undefined,
      },
    })
  }
}

function resetAuthForm() {
  authForm.companyName = ''
  authForm.email = ''
  authForm.phone = ''
  authForm.certificateFile = null
  authForm.certificateFileName = ''
  if (authForm.certificatePreviewUrl) {
    URL.revokeObjectURL(authForm.certificatePreviewUrl)
  }
  authForm.certificatePreviewUrl = ''
}

function onPickCertificate(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请上传图片格式的证书文件')
    return
  }
  if (authForm.certificatePreviewUrl) {
    URL.revokeObjectURL(authForm.certificatePreviewUrl)
  }
  authForm.certificateFile = file
  authForm.certificateFileName = file.name
  authForm.certificatePreviewUrl = URL.createObjectURL(file)
}

async function loadOfficialAuthRecords() {
  if (!isMerchantApproved.value) {
    officialAuthRecords.value = []
    return
  }
  try {
    officialAuthRecords.value = await merchantApi.listOfficialAuthRecords()
  } catch {
    officialAuthRecords.value = []
  }
}

async function loadMerchantApplicationStatus() {
  try {
    merchantApplicationStatus.value = await merchantApi.getApplicationStatus()
    if (merchantApplicationStatus.value) {
      authForm.companyName = merchantApplicationStatus.value.merchantName || authForm.companyName
      authForm.email = merchantApplicationStatus.value.email || userInfo.email || authForm.email
      authForm.phone = merchantApplicationStatus.value.phone || userInfo.phone || authForm.phone
    }
  } catch {
    merchantApplicationStatus.value = null
  }
}

function formatAuthStatus(status) {
  const value = String(status || '').toLowerCase()
  if (value === 'pending') return '待审核'
  if (value === 'active') return '已通过'
  if (value === 'rejected') return '已驳回'
  return status || '-'
}

async function submitOfficialAuth() {
  if (!authForm.companyName.trim()) {
    alert('请先填写公司名称')
    return
  }
  if (!authForm.email.trim()) {
    alert('请先填写联系邮箱')
    return
  }
  if (!authForm.phone.trim()) {
    alert('请先填写联系方式')
    return
  }
  if (!authForm.certificateFile) {
    alert('请先上传相关证书图片')
    return
  }
  officialAuthBusy.value = true
  try {
    if (isMerchantApproved.value) {
      const imageUrl = await merchantApi.uploadOfficialAuthImage(authForm.certificateFile)
      await merchantApi.submitOfficialAuth({
        companyName: authForm.companyName.trim(),
        certificateImageUrl: imageUrl,
      })
      accountNotice.value = '官方旗舰店认证申请已提交，请等待管理员审核。'
      await loadOfficialAuthRecords()
      alert('旗舰店认证申请已提交')
    } else {
      await merchantApi.submitAuth({
        companyName: authForm.companyName.trim(),
        email: authForm.email.trim(),
        phone: authForm.phone.trim(),
        certificate: authForm.certificateFile,
      })
      userInfo.email = authForm.email.trim()
      userInfo.phone = authForm.phone.trim()
      accountNotice.value = '商家入驻申请已提交，请等待管理员审核。'
      await loadMerchantApplicationStatus()
      alert('提交申请成功，等待平台审核')
    }
  } catch (error) {
    alert(error instanceof Error ? error.message : '提交申请失败，请稍后重试')
  } finally {
    officialAuthBusy.value = false
  }
  resetAuthForm()
  closeOfficialAuthModal()
}

function menuAlert(msg) {
  alert(msg)
}

function openProductAuditCenter() {
  if (!isMerchantApproved.value) {
    alert('当前账号尚未获得商家权限，请先完成商家入驻申请并等待审核通过。')
    return
  }
  router.push('/medical')
}

function logout() {
  if (confirm('确定要退出登录吗？')) {
    auth.logout()
  }
}

onMounted(async () => {
  await auth.refresh()
  await cartBadge.refresh()
  await loadMerchantApplicationStatus()
  if (typeof route.query.err === 'string') {
    accountNotice.value = route.query.err
  } else if (String(merchantApplicationStatus.value?.status || '').toLowerCase() === 'rejected') {
    accountNotice.value = `商家入驻申请已被驳回：${merchantApplicationStatus.value?.auditReason || '请修改资料后重新提交。'}`
  } else if (String(merchantApplicationStatus.value?.status || '').toLowerCase() === 'pending') {
    accountNotice.value = '商家入驻申请已提交，请等待管理员审核。'
  } else if (!isMerchantApproved.value) {
    accountNotice.value = '当前账号还不是正式商家，请先完成商家入驻申请，审核通过后才能上传和管理商品。'
  } else if (hasOfficialFlagshipAuth.value) {
    accountNotice.value = `${certifiedStoreName.value} 已完成官方旗舰店认证，前台可按认证店铺身份展示。`
  } else {
    accountNotice.value = '当前账号已具备商家权限，如需展示官方旗舰店标识，可继续提交旗舰店认证材料。'
  }
  await loadMerchantProfile()
  await loadOfficialAuthRecords()

  if (isMerchantApproved.value && hasOfficialFlagshipAuth.value) {
    accountNotice.value = `${certifiedStoreName.value} 已完成官方旗舰店认证，前台可按认证店铺身份展示。`
  }

  try {
    const stats = await merchantApi.getStats()
    if (stats) {
      storeStats.monthRevenue = Number(stats.monthRevenue || 0)
      storeStats.totalRevenue = Number(stats.totalRevenue || storeStats.totalRevenue || 0)
      storeStats.trendPercent = Number(stats.trendPercent || 0)
      storeStats.rating = Number(stats.rating || 0)
    }
  } catch {
    // 忽略，使用本地默认值
  }

  if (storeStats.monthRevenue <= 0 && storeStats.totalRevenue > 0) {
    trendClass.value = 'up'
    trendLabel.value = `累计已成交 ¥${storeStats.totalRevenue.toFixed(2)}`
  } else if (storeStats.monthRevenue <= 0 && storeStats.trendPercent === 0) {
    trendClass.value = ''
    trendLabel.value = '暂无营收数据'
  } else if (storeStats.trendPercent >= 0) {
    trendClass.value = 'up'
    trendLabel.value = `较上月增长 ${storeStats.trendPercent.toFixed(1)}%`
  } else {
    trendClass.value = 'down'
    trendLabel.value = `较上月下降 ${Math.abs(storeStats.trendPercent).toFixed(1)}%`
  }

  if (storeStats.rating <= 0) ratingText.value = '暂无评价'
  else if (storeStats.rating >= 4.5) ratingText.value = '好评如潮'
  else if (storeStats.rating >= 3.5) ratingText.value = '褒贬不一'
  else ratingText.value = '一般'
})

watch(
  () => route.query.openApply,
  (value) => {
    if (value && !isMerchantApproved.value) {
      authModalOpen.value = true
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  resetAuthForm()
})
</script>

<template>
  <div class="App">

    <div class="user-box">
      <div class="user-header">
        <h2 class="user-header-text">个人中心</h2>
        <div id="editButtons">
          <template v-if="editButtonsHtml === 'edit'">
            <button type="button" class="user-header-btn-edit" @click="enableEdit">编辑资料</button>
          </template>
          <template v-else>
            <button type="button" class="user-header-btn-save" @click="saveUserInfo">保存</button>
            <button type="button" class="user-header-btn-cancel" @click="cancelEdit">取消</button>
          </template>
        </div>
      </div>

      <div v-if="accountNotice" class="account-notice" :class="{ pending: !isMerchantApproved }">
        {{ accountNotice }}
      </div>

      <div
        v-if="String(merchantApplicationStatus?.status || '').toLowerCase() === 'rejected'"
        class="account-notice"
      >
        上次入驻申请驳回原因：{{ merchantApplicationStatus?.auditReason || '请修改资料后重新提交。' }}
      </div>

      <div class="user-content">
        <div class="user-avatar-section">
          <img :src="getImagePath(userInfo.avatarUrl || 'images/logo192.png')" alt="用户头像" class="user-avatar" id="userAvatar" />
          <input ref="avatarInputRef" class="avatar-input-hidden" type="file" accept="image/*" @change="onPickAvatar" />
          <template v-if="hasOfficialFlagshipAuth">
            <div class="official-store-badge">
              <span class="official-store-icon">✔</span>
              <div class="official-store-content">
                <strong>{{ certifiedStoreName }}</strong>
                <span>官方旗舰店认证已完成</span>
              </div>
            </div>
          </template>
          <button v-else type="button" class="official-badge-btn" @click="openOfficialAuthModal">
            <span>✔</span>
            <span>{{ isMerchantApproved ? '申请官方旗舰店认证' : '注册商家' }}</span>
          </button>
          <button
            v-show="isEditing"
            id="changeAvatarBtn"
            type="button"
            class="change-avatar-btn"
            @click="changeAvatar"
          >
            {{ avatarBusy ? '上传中...' : '更换头像' }}
          </button>
        </div>

        <div id="userInfoForm" class="user-info">
          <div class="user-info-item">
            <label>店铺名称：</label>
            <span v-show="!isEditing" id="userName" class="user-info-name">{{ userInfo.name }}</span>
            <input v-show="isEditing" id="userNameInput" v-model="userInfo.name" type="text" />
          </div>
          <div class="user-info-item">
            <label>邮箱：</label>
            <span v-show="!isEditing" id="userEmail" class="user-info-email">{{ userInfo.email }}</span>
            <input v-show="isEditing" id="userEmailInput" v-model="userInfo.email" type="email" />
          </div>
          <div class="user-info-item">
            <label>手机号：</label>
            <span id="userPhone" class="user-info-phone">{{ phoneDisplayText }}</span>
          </div>
          <div class="user-info-item">
            <label>注册时间：</label>
            <span id="registerTime" class="user-info-register-time">{{ userInfo.registerTime }}</span>
          </div>
          <div class="user-info-item full-width">
            <label>商家介绍：</label>
            <div class="merchant-intro-box">
              <span v-show="!isEditing" id="userIntro" class="user-info-personal-introduction">{{
                userInfo.introduction
              }}</span>
              <textarea
                v-show="isEditing"
                id="userIntroInput"
                v-model="userInfo.introduction"
                rows="3"
              />
            </div>
          </div>

          <div class="store-metric-grid">
            <article class="store-metric-card">
              <h4>{{ revenueTitle }}</h4>
              <p class="metric-main">¥{{ displayedRevenue.toLocaleString('zh-CN') }}</p>
              <p class="metric-sub" :class="trendClass">{{ trendLabel }}</p>
            </article>
            <article class="store-metric-card">
              <h4>商店整体评价</h4>
              <p class="metric-main">{{ storeStats.rating > 0 ? `★ ${storeStats.rating.toFixed(1)}` : '--' }}</p>
              <p class="metric-sub">{{ ratingText }}</p>
            </article>
          </div>
        </div>
      </div>

      <div class="user-menu">
        <h3>{{ isMerchantApproved ? '商家服务' : '申请开店后可用服务' }}</h3>
        <div class="menu-items">
          <div class="menu-item" @click="openProductAuditCenter">
            <span>🛍️</span>
            <span>商品上架管理</span>
          </div>
          <div class="menu-item" @click="menuAlert('订单发货功能开发中...')">
            <span>📦</span>
            <span>订单发货</span>
          </div>
          <div class="menu-item" @click="menuAlert('售后管理功能开发中...')">
            <span>🧾</span>
            <span>售后管理</span>
          </div>
          <div class="menu-item" @click="menuAlert('库存预警功能开发中...')">
            <span>📊</span>
            <span>库存预警</span>
          </div>
          <div class="menu-item" @click="menuAlert('营销活动功能开发中...')">
            <span>🎯</span>
            <span>营销活动</span>
          </div>
          <div class="menu-item" @click="menuAlert('店铺设置功能开发中...')">
            <span>⚙️</span>
            <span>店铺设置</span>
          </div>
          <div class="menu-item" @click="menuAlert('商家客服功能开发中...')">
            <span>💬</span>
            <span>商家客服</span>
          </div>
        </div>
      </div>

      <div v-if="isMerchantApproved" class="user-menu auth-record-panel">
        <h3>{{ hasOfficialFlagshipAuth ? '旗舰店认证信息' : '旗舰店认证记录' }}</h3>
        <div v-if="officialAuthRecords.length" class="auth-record-list">
          <article v-for="record in officialAuthRecords" :key="record.id" class="auth-record-card">
            <div class="auth-record-head">
              <strong>{{ record.authenticationName }}</strong>
              <span class="auth-record-status" :class="String(record.status || '').toLowerCase()">{{ formatAuthStatus(record.status) }}</span>
            </div>
            <p>公司名称：{{ record.companyName }}</p>
            <p>认证等级：{{ record.authLevel || '官方旗舰店' }}</p>
            <p>申请时间：{{ record.createdAt || '-' }}</p>
            <p>{{ String(record.status || '').toLowerCase() === 'active' ? '认证结果：店铺名称已可按官方旗舰店展示' : `审核意见：${record.auditReason || '待审核'}` }}</p>
          </article>
        </div>
        <div v-else class="account-notice">暂无旗舰店认证记录。</div>
      </div>

      <div class="user-footer">
        <button type="button" class="user-footer-btn-logout" @click="logout">退出登录</button>
      </div>
    </div>

    <div v-if="authModalOpen" class="auth-modal-overlay" @click="closeOfficialAuthModal">
      <div class="auth-modal" @click.stop>
        <button type="button" class="auth-modal-close" @click="closeOfficialAuthModal">✕</button>
        <h3>{{ isMerchantApproved ? '官方旗舰店认证申请' : '注册商家申请' }}</h3>

        <div class="auth-form-item">
          <label for="companyNameInput">{{ isMerchantApproved ? '公司名称' : '店铺/公司名称' }}</label>
          <input
            id="companyNameInput"
            v-model="authForm.companyName"
            type="text"
            placeholder="请输入公司全称"
          />
        </div>

        <div v-if="!isMerchantApproved" class="auth-form-item">
          <label for="merchantEmailInput">联系邮箱</label>
          <input
            id="merchantEmailInput"
            v-model="authForm.email"
            type="email"
            placeholder="请输入商家联系邮箱"
          />
        </div>

        <div v-if="!isMerchantApproved" class="auth-form-item">
          <label for="merchantPhoneInput">联系方式</label>
          <input
            id="merchantPhoneInput"
            v-model="authForm.phone"
            type="text"
            placeholder="请输入手机号或座机号"
          />
        </div>

        <div class="auth-form-item">
          <label>相关证书图片</label>
          <label class="auth-upload-btn">
            选择本地图片
            <input type="file" accept="image/*" @change="onPickCertificate" />
          </label>
          <p class="auth-upload-name">{{ authForm.certificateFileName || '尚未选择证书图片' }}</p>
          <img
            v-if="authForm.certificatePreviewUrl"
            :src="authForm.certificatePreviewUrl"
            alt="证书预览"
            class="auth-certificate-preview"
          />
        </div>

        <button type="button" class="auth-submit-btn" :disabled="officialAuthBusy" @click="submitOfficialAuth">
          {{ officialAuthBusy ? '提交中...' : '提交申请' }}
        </button>
      </div>
    </div>
  </div>
</template>
