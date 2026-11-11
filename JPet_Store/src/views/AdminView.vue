<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminAuthenticationApi, adminMerchantApi, adminProductApi, adminUserApi } from '@/api/admin'
import type { ChatMessage } from '@/buyer/types/models'
import { useAuthStore } from '@/stores/auth'
import { listOfficialContacts, listThreadMessages, markThreadRead, sendThreadMessage } from '@/utils/orderChat'

type AdminTab = 'overview' | 'feedback' | 'users' | 'merchant-audits' | 'flagship-audits' | 'product-audits' | 'settings'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref<AdminTab>('overview')
const loading = ref(false)

const users = ref<any[]>([])
const merchantAudits = ref<any[]>([])
const flagshipAudits = ref<any[]>([])
const productAudits = ref<any[]>([])

const userTotal = ref(0)
const merchantTotal = ref(0)
const flagshipTotal = ref(0)
const productTotal = ref(0)

const userStatusBusyId = ref<number | null>(null)
const merchantBusyId = ref<number | null>(null)
const flagshipBusyId = ref<number | null>(null)
const productBusyId = ref<number | null>(null)
const selectedMerchantId = ref<number | string | null>(null)
const selectedFlagshipId = ref<number | string | null>(null)
const selectedProductId = ref<number | string | null>(null)
const merchantAuthentications = ref<any[]>([])
const merchantAuthLoading = ref(false)
const selectedProductDetail = ref<any | null>(null)
const productDetailLoading = ref(false)
const officialContacts = ref<any[]>([])
const activeOfficialContact = ref<any | null>(null)
const officialMessages = ref<ChatMessage[]>([])
const officialDraft = ref('')

const buyerOfficialContacts = computed(() => officialContacts.value.filter((item) => item.role === '买家'))
const merchantOfficialContacts = computed(() => officialContacts.value.filter((item) => item.role === '商家'))

const selectedMerchant = computed(() =>
  merchantAudits.value.find((item) => String(item.id) === String(selectedMerchantId.value)) || null,
)

const selectedFlagship = computed(() =>
  flagshipAudits.value.find((item) => String(item.id) === String(selectedFlagshipId.value)) || null,
)

const selectedProduct = computed(() => selectedProductDetail.value)

const cards = computed(() => [
  { label: '用户总数', value: userTotal.value },
  { label: '待审商家', value: merchantTotal.value },
  { label: '旗舰店待审', value: flagshipTotal.value },
  { label: '待审商品', value: productTotal.value },
])

const queueItems = computed(() => [
  {
    label: '商家入驻申请',
    value: merchantTotal.value,
    description: merchantTotal.value ? '建议优先核验营业资质与店铺资料。' : '当前没有新的商家入驻申请。',
    tone: merchantTotal.value ? 'warn' : 'calm',
  },
  {
    label: '旗舰店认证申请',
    value: flagshipTotal.value,
    description: flagshipTotal.value ? '关注证书图片、公司名称与主体一致性。' : '当前没有新的旗舰店认证申请。',
    tone: flagshipTotal.value ? 'brand' : 'calm',
  },
  {
    label: '商品上架申请',
    value: productTotal.value,
    description: productTotal.value ? '建议在资质审核完成后处理商品上架。' : '当前没有新的商品待上架。',
    tone: productTotal.value ? 'success' : 'calm',
  },
])

const recentActivities = computed(() => {
  const merchantRows = merchantAudits.value.slice(0, 2).map((item) => ({
    id: `merchant-${item.id}`,
    title: item.merchantName || `商家 ${item.id}`,
    subtitle: `商家入驻申请 · 用户 ${item.userId || '-'}`,
    time: formatDateTime(item.createdAt),
    tag: '商家审核',
  }))
  const flagshipRows = flagshipAudits.value.slice(0, 2).map((item) => ({
    id: `flagship-${item.id}`,
    title: item.companyName || `认证申请 ${item.id}`,
    subtitle: `${item.authenticationName || '官方旗舰店认证'} · 商家 ${item.merchantId || '-'}`,
    time: formatDateTime(item.createdAt),
    tag: '旗舰店审核',
  }))
  const productRows = productAudits.value.slice(0, 2).map((item) => ({
    id: `product-${item.id}`,
    title: item.name || `商品 ${item.id}`,
    subtitle: `商品上架审核 · 商家 ${item.merchantId || '-'}`,
    time: formatDateTime(item.createdAt),
    tag: '商品审核',
  }))
  return [...merchantRows, ...flagshipRows, ...productRows].slice(0, 6)
})

const riskSignals = computed(() => [
  {
    title: '审核链路优先级',
    description:
      merchantTotal.value + flagshipTotal.value > 0
        ? '当前存在资质审核任务，建议先完成商家与旗舰店审核，再处理商品上架。'
        : '当前资质审核队列为空，可以直接关注商品与用户状态。',
  },
  {
    title: '后台入口状态',
    description: '管理员、商家、普通用户均已统一到同一 JWT 登录体系，当前前端入口正常。',
  },
  {
    title: '审核负载',
    description:
      productTotal.value >= 5
        ? '商品待审量较高，建议安排集中上架审核。'
        : '当前待审商品量可控，适合逐单复核。',
  },
])

const settingGroups = computed(() => [
  {
    title: '权限与账号',
    badge: '已启用',
    items: [
      { name: '管理员双角色入口', value: '主管理员 / 子管理员共用后台' },
      { name: '访问控制', value: '由后端角色权限精确校验' },
      { name: '登录机制', value: 'JWT 会话与路由守卫联动' },
    ],
  },
  {
    title: '审核流程配置',
    badge: '在线',
    items: [
      { name: '商家审核', value: '支持通过 / 驳回' },
      { name: '旗舰店审核', value: '支持证书核验与审核状态回写' },
      { name: '商品审核', value: '待审商品上架流程已接通' },
    ],
  },
  {
    title: '部署与资源',
    badge: '稳定',
    items: [
      { name: '前端部署', value: '构建后写入后端静态目录 /spa' },
      { name: '图片资源', value: '商品图与认证图均支持上传访问' },
      { name: '运行模式', value: 'Spring Boot + Vite 联调 / 静态部署均可用' },
    ],
  },
])

const roadmapItems = computed(() => [
  `当前共有 ${merchantTotal.value + flagshipTotal.value + productTotal.value} 个审核相关任务可处理。`,
  '如需继续恢复旧后台能力，下一步优先补订单管理、公告配置与审计日志。',
  '系统设置页已改成配置总览，可继续扩成真实表单配置面板。',
])

const feedbackSections = computed(() => [
  {
    key: 'buyer',
    title: '买家反馈',
    emptyText: '当前还没有买家反馈。',
    contacts: buyerOfficialContacts.value,
  },
  {
    key: 'merchant',
    title: '商家反馈',
    emptyText: '当前还没有商家反馈。',
    contacts: merchantOfficialContacts.value,
  },
])

async function loadDashboardData() {
  loading.value = true
  try {
    const [userRes, merchantRes, flagshipRes, productRes] = await Promise.all([
      adminUserApi.list({ page: 1, size: 8 }),
      adminMerchantApi.listAudits({ page: 1, size: 8 }),
      adminAuthenticationApi.listAudits({ page: 1, size: 8 }),
      adminProductApi.listAudits({ page: 1, size: 8 }),
    ])

    users.value = userRes?.list || []
    userTotal.value = Number(userRes?.total || users.value.length)

    merchantAudits.value = merchantRes?.list || []
    merchantTotal.value = Number(merchantRes?.total || merchantAudits.value.length)

    flagshipAudits.value = flagshipRes?.list || []
    flagshipTotal.value = Number(flagshipRes?.total || flagshipAudits.value.length)

    productAudits.value = productRes?.list || []
    productTotal.value = Number(productRes?.total || productAudits.value.length)

    const nextMerchant =
      merchantAudits.value.find((item) => String(item.id) === String(selectedMerchantId.value)) || merchantAudits.value[0]
    if (nextMerchant) {
      await selectMerchant(nextMerchant)
    } else {
      selectedMerchantId.value = null
      merchantAuthentications.value = []
    }

    const nextFlagship =
      flagshipAudits.value.find((item) => String(item.id) === String(selectedFlagshipId.value)) || flagshipAudits.value[0]
    selectedFlagshipId.value = nextFlagship?.id ?? null

    const nextProduct =
      productAudits.value.find((item) => String(item.id) === String(selectedProductId.value)) || productAudits.value[0]
    if (nextProduct) {
      await selectProduct(nextProduct)
    } else {
      selectedProductId.value = null
      selectedProductDetail.value = null
    }

    officialContacts.value = listOfficialContacts()
    if (officialContacts.value.length) {
      const nextOfficial = officialContacts.value.find((item) => item.id === activeOfficialContact.value?.id) || officialContacts.value[0]
      selectOfficialContact(nextOfficial)
    } else {
      activeOfficialContact.value = null
      officialMessages.value = []
    }
  } finally {
    loading.value = false
  }
}

async function selectMerchant(item: any) {
  selectedMerchantId.value = item?.id ?? null
  if (!item?.id) {
    merchantAuthentications.value = []
    return
  }
  merchantAuthLoading.value = true
  try {
    merchantAuthentications.value = await adminMerchantApi.getAuthentications(item.id)
  } catch {
    merchantAuthentications.value = []
  } finally {
    merchantAuthLoading.value = false
  }
}

function selectFlagship(item: any) {
  selectedFlagshipId.value = item?.id ?? null
}

async function selectProduct(item: any) {
  selectedProductId.value = item?.id ?? null
  if (!item?.id) {
    selectedProductDetail.value = null
    return
  }
  productDetailLoading.value = true
  try {
    selectedProductDetail.value = await adminProductApi.getDetail(item.id)
  } catch {
    selectedProductDetail.value = item ?? null
  } finally {
    productDetailLoading.value = false
  }
}

async function updateUserStatus(user: any) {
  const nextStatus = user.status === '正常' || user.status === 'normal' ? '拉黑' : '正常'
  userStatusBusyId.value = Number(user.id)
  try {
    await adminUserApi.updateStatus(user.id, nextStatus)
    await loadDashboardData()
  } catch (error) {
    alert(error instanceof Error ? error.message : '更新用户状态失败')
  } finally {
    userStatusBusyId.value = null
  }
}

function requestRejectReason(targetLabel: string) {
  const reason = window.prompt(`请填写“${targetLabel}”驳回理由：`, '')
  if (reason === null) return null
  const normalized = reason.trim()
  if (!normalized) {
    alert('驳回时必须填写理由')
    return null
  }
  return normalized
}

async function auditMerchant(item: any, approved: boolean) {
  const rejectReason = approved ? '' : requestRejectReason(item?.merchantName || '商家入驻申请')
  if (!approved && rejectReason === null) return
  merchantBusyId.value = Number(item.id)
  try {
    await adminMerchantApi.audit(item.id, {
      auditStatus: approved ? 'active' : 'rejected',
      auditReason: rejectReason,
    })
    await loadDashboardData()
  } catch (error) {
    alert(error instanceof Error ? error.message : '商家审核失败')
  } finally {
    merchantBusyId.value = null
  }
}

async function auditFlagship(item: any, approved: boolean) {
  const rejectReason = approved ? '官方旗舰店认证审核通过' : requestRejectReason(item?.companyName || '旗舰店认证申请')
  if (!approved && rejectReason === null) return
  flagshipBusyId.value = Number(item.id)
  try {
    await adminAuthenticationApi.audit(item.id, {
      auditStatus: approved ? 'active' : 'rejected',
      auditReason: rejectReason,
    })
    await loadDashboardData()
  } catch (error) {
    alert(error instanceof Error ? error.message : '旗舰店认证审核失败')
  } finally {
    flagshipBusyId.value = null
  }
}

async function auditProduct(item: any, approved: boolean) {
  const rejectReason = approved ? '' : requestRejectReason(item?.name || '商品上架申请')
  if (!approved && rejectReason === null) return
  productBusyId.value = Number(item.id)
  try {
    await adminProductApi.audit(item.id, {
      auditStatus: approved ? 'published' : 'rejected',
      auditReason: rejectReason,
    })
    await loadDashboardData()
  } catch (error) {
    alert(error instanceof Error ? error.message : '商品审核失败')
  } finally {
    productBusyId.value = null
  }
}

function selectOfficialContact(item: any) {
  activeOfficialContact.value = item || null
  if (!item?.id) {
    officialMessages.value = []
    return
  }
  markThreadRead(item.id, 'admin')
  officialMessages.value = listThreadMessages(item.id, 'admin')
  officialContacts.value = listOfficialContacts()
}

function sendOfficialReply() {
  if (!activeOfficialContact.value || !officialDraft.value.trim()) {
    return
  }
  officialMessages.value = sendThreadMessage(activeOfficialContact.value.id, 'official', officialDraft.value.trim())
  officialContacts.value = listOfficialContacts()
  officialDraft.value = ''
}

function logout() {
  auth.logout()
}

function formatDateTime(value: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

function formatMoney(value: number | string) {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return amount.toFixed(2)
}

function formatSkuAttributes(attributeValues: Record<string, any>) {
  const entries = Object.entries(attributeValues || {}).filter(([, value]) => value != null && String(value).trim())
  if (!entries.length) return '默认规格'
  return entries.map(([key, value]) => `${key}=${value}`).join(' / ')
}

const selectedProductStock = computed(() =>
  Array.isArray(selectedProduct.value?.skus)
    ? selectedProduct.value.skus.reduce((sum: number, sku: any) => sum + Number(sku?.stock ?? 0), 0)
    : 0,
)

function formatStatus(status: string) {
  const value = String(status || '').trim().toLowerCase()
  if (value === 'pending') return '待审核'
  if (value === 'active') return '已通过'
  if (value === 'rejected') return '已驳回'
  if (value === 'published') return '已上架'
  return status || '-'
}

onMounted(async () => {
  await auth.refresh()
  await loadDashboardData()
})
</script>

<template>
  <div class="admin-shell">
    <header class="admin-hero">
      <div>
        <p class="admin-kicker">JWT Admin Console</p>
        <h1>管理员控制台</h1>
        <p class="admin-subtitle">管理员界面已经并入同一前端工程，并通过 JWT 角色守卫与商家界面自动分流。</p>
      </div>
      <div class="admin-actions">
        <span class="admin-user">{{ auth.welcomeUsername }}</span>
        <button type="button" class="secondary-btn" @click="router.push('/index')">进入商家页</button>
        <button type="button" class="danger-btn" @click="logout">退出登录</button>
      </div>
    </header>

    <section class="admin-cards">
      <article v-for="card in cards" :key="card.label" class="admin-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <nav class="admin-tabs">
      <button type="button" :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">控制面板</button>
      <button type="button" :class="{ active: activeTab === 'feedback' }" @click="activeTab = 'feedback'">问题反馈</button>
      <button type="button" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">用户管理</button>
      <button type="button" :class="{ active: activeTab === 'merchant-audits' }" @click="activeTab = 'merchant-audits'">商家审核</button>
      <button type="button" :class="{ active: activeTab === 'flagship-audits' }" @click="activeTab = 'flagship-audits'">旗舰店审核</button>
      <button type="button" :class="{ active: activeTab === 'product-audits' }" @click="activeTab = 'product-audits'">商品审核</button>
      <button type="button" :class="{ active: activeTab === 'settings' }" @click="activeTab = 'settings'">系统设置</button>
    </nav>

    <section v-if="loading" class="admin-panel empty-state">数据加载中...</section>

    <section v-else-if="activeTab === 'overview'" class="admin-panel admin-overview-panel">
      <div class="overview-hero-card">
        <div>
          <p class="section-kicker">运营总览</p>
          <h2>今日后台焦点</h2>
          <p class="overview-hero-text">
            当前共有 {{ merchantTotal + flagshipTotal + productTotal }} 条待处理审核链路，建议优先处理资质相关任务，再进入商品上架审核。
          </p>
        </div>
        <div class="overview-hero-badges">
          <span class="hero-badge">JWT 统一登录</span>
          <span class="hero-badge">审核流程在线</span>
          <span class="hero-badge">静态部署可用</span>
        </div>
      </div>

      <div class="queue-grid">
        <article v-for="queue in queueItems" :key="queue.label" class="queue-card" :class="`tone-${queue.tone}`">
          <div class="queue-card-head">
            <span>{{ queue.label }}</span>
            <strong>{{ queue.value }}</strong>
          </div>
          <p>{{ queue.description }}</p>
        </article>
      </div>

      <div class="overview-grid overview-grid-main">
        <article class="overview-block activity-block">
          <div class="block-head">
            <div>
              <p class="section-kicker">最近进入队列</p>
              <h3>待处理记录</h3>
            </div>
            <span class="block-note">展示最近 6 条</span>
          </div>
          <div v-if="recentActivities.length" class="activity-list">
            <article v-for="item in recentActivities" :key="item.id" class="activity-item">
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.subtitle }}</p>
              </div>
              <div class="activity-meta">
                <span class="activity-tag">{{ item.tag }}</span>
                <time>{{ item.time }}</time>
              </div>
            </article>
          </div>
          <div v-else class="detail-muted">当前没有新的待处理记录。</div>
        </article>

        <article class="overview-block signals-block">
          <div class="block-head">
            <div>
              <p class="section-kicker">运营提醒</p>
              <h3>风控与状态信号</h3>
            </div>
          </div>
          <div class="signal-list">
            <article v-for="signal in riskSignals" :key="signal.title" class="signal-item">
              <strong>{{ signal.title }}</strong>
              <p>{{ signal.description }}</p>
            </article>
          </div>
        </article>

        <article class="overview-block feedback-block">
          <div class="block-head">
            <div>
              <p class="section-kicker">问题反馈</p>
              <h3>官方客服会话</h3>
            </div>
            <span class="block-note">买家与商家发送到官方管理员的反馈会话都会汇总到这里</span>
          </div>
          <div class="feedback-layout">
            <aside class="feedback-contact-list">
              <section v-for="section in feedbackSections" :key="section.key" class="feedback-group">
                <div class="feedback-group-head">{{ section.title }}</div>
                <button
                  v-for="contact in section.contacts"
                  :key="contact.id"
                  type="button"
                  class="feedback-contact-item"
                  :class="{ active: activeOfficialContact?.id === contact.id }"
                  @click="selectOfficialContact(contact)"
                >
                  <div>
                    <div class="feedback-contact-top">
                      <strong>{{ contact.name }}</strong>
                      <span class="feedback-role-tag">{{ contact.role }}</span>
                    </div>
                    <p>{{ contact.subtitle }}</p>
                  </div>
                  <span v-if="contact.unread" class="feedback-unread">{{ contact.unread }}</span>
                </button>
                <div v-if="!section.contacts.length" class="detail-muted">{{ section.emptyText }}</div>
              </section>
            </aside>

            <section class="feedback-chat-box" v-if="activeOfficialContact">
              <header class="feedback-chat-head">
                <div>
                  <div class="feedback-contact-top">
                    <strong>{{ activeOfficialContact.name }}</strong>
                    <span class="feedback-role-tag">{{ activeOfficialContact.role }}</span>
                  </div>
                  <p>{{ activeOfficialContact.subtitle }}</p>
                </div>
              </header>

              <div class="feedback-message-list">
                <article v-for="message in officialMessages" :key="message.id" class="feedback-message" :class="message.sender">
                  <p>{{ message.content }}</p>
                  <small>{{ message.time }}</small>
                </article>
              </div>

              <footer class="feedback-composer">
                <input v-model="officialDraft" type="text" placeholder="输入对订单、发货、违规或售后的回复..." @keydown.enter="sendOfficialReply" />
                <button type="button" class="secondary-btn" @click="sendOfficialReply">发送回复</button>
              </footer>
            </section>
          </div>
        </article>
      </div>
    </section>

    <section v-else-if="activeTab === 'feedback'" class="admin-panel">
      <div class="block-head">
        <div>
          <p class="section-kicker">问题反馈</p>
          <h3>官方客服会话</h3>
        </div>
        <span class="block-note">左侧按买家反馈与商家反馈分组，方便管理员快速分流处理</span>
      </div>
      <div class="feedback-layout">
        <aside class="feedback-contact-list">
          <section v-for="section in feedbackSections" :key="section.key" class="feedback-group">
            <div class="feedback-group-head">{{ section.title }}</div>
            <button
              v-for="contact in section.contacts"
              :key="contact.id"
              type="button"
              class="feedback-contact-item"
              :class="{ active: activeOfficialContact?.id === contact.id }"
              @click="selectOfficialContact(contact)"
            >
              <div>
                <div class="feedback-contact-top">
                  <strong>{{ contact.name }}</strong>
                  <span class="feedback-role-tag">{{ contact.role }}</span>
                </div>
                <p>{{ contact.subtitle }}</p>
              </div>
              <span v-if="contact.unread" class="feedback-unread">{{ contact.unread }}</span>
            </button>
            <div v-if="!section.contacts.length" class="detail-muted">{{ section.emptyText }}</div>
          </section>
        </aside>

        <section class="feedback-chat-box" v-if="activeOfficialContact">
          <header class="feedback-chat-head">
            <div>
              <div class="feedback-contact-top">
                <strong>{{ activeOfficialContact.name }}</strong>
                <span class="feedback-role-tag">{{ activeOfficialContact.role }}</span>
              </div>
              <p>{{ activeOfficialContact.subtitle }}</p>
            </div>
          </header>

          <div class="feedback-message-list">
            <article v-for="message in officialMessages" :key="message.id" class="feedback-message" :class="message.sender">
              <p>{{ message.content }}</p>
              <small>{{ message.time }}</small>
            </article>
          </div>

          <footer class="feedback-composer">
            <input v-model="officialDraft" type="text" placeholder="输入对订单、发货、违规或售后的回复..." @keydown.enter="sendOfficialReply" />
            <button type="button" class="secondary-btn" @click="sendOfficialReply">发送回复</button>
          </footer>
        </section>

        <section v-else class="feedback-chat-box feedback-chat-box--empty">
          <div class="detail-muted">请选择左侧问题反馈会话。</div>
        </section>
      </div>
    </section>

    <section v-else-if="activeTab === 'users'" class="admin-panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.id }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.email || '-' }}</td>
            <td>{{ user.role }}</td>
            <td>{{ user.status }}</td>
            <td>
              <button type="button" class="secondary-btn inline-btn" :disabled="userStatusBusyId === user.id" @click="updateUserStatus(user)">
                {{ user.status === '正常' || user.status === 'normal' ? '拉黑' : '恢复' }}
              </button>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="6" class="empty-state">暂无用户数据</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-else-if="activeTab === 'merchant-audits'" class="admin-panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>商家名</th>
            <th>联系邮箱</th>
            <th>联系方式</th>
            <th>用户 ID</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in merchantAudits"
            :key="item.id"
            :class="{ 'row-selected': String(selectedMerchantId) === String(item.id) }"
            @click="selectMerchant(item)"
          >
            <td>{{ item.id }}</td>
            <td>{{ item.merchantName }}</td>
            <td>{{ item.email || '-' }}</td>
            <td>{{ item.phone || '-' }}</td>
            <td>{{ item.userId }}</td>
            <td>{{ formatStatus(item.status) }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td class="action-cell">
              <button type="button" class="accept-btn" :disabled="merchantBusyId === item.id" @click="auditMerchant(item, true)">通过</button>
              <button type="button" class="reject-btn" :disabled="merchantBusyId === item.id" @click="auditMerchant(item, false)">拒绝</button>
            </td>
          </tr>
          <tr v-if="merchantAudits.length === 0">
            <td colspan="8" class="empty-state">暂无待审商家</td>
          </tr>
        </tbody>
      </table>

      <div v-if="selectedMerchant" class="audit-detail-card">
        <div class="audit-detail-head">
          <h3>{{ selectedMerchant.merchantName }}</h3>
          <span class="detail-status-chip">{{ formatStatus(selectedMerchant.status) }}</span>
        </div>
        <div class="audit-detail-body">
          <img v-if="selectedMerchant.imageUrl" :src="selectedMerchant.imageUrl" :alt="selectedMerchant.merchantName" class="detail-cover" />
          <div class="detail-content">
            <p><strong>用户 ID：</strong>{{ selectedMerchant.userId }}</p>
            <p><strong>用户名：</strong>{{ selectedMerchant.username || '-' }}</p>
            <p><strong>联系邮箱：</strong>{{ selectedMerchant.email || '-' }}</p>
            <p><strong>联系方式：</strong>{{ selectedMerchant.phone || '-' }}</p>
            <p><strong>申请时间：</strong>{{ formatDateTime(selectedMerchant.createdAt) }}</p>
            <p><strong>商家说明：</strong>{{ selectedMerchant.description || '暂无说明' }}</p>
            <p><strong>审核意见：</strong>{{ selectedMerchant.auditReason || '待审核' }}</p>
            <div class="detail-block">
              <strong>认证资质</strong>
              <div v-if="merchantAuthLoading" class="detail-muted">资质加载中...</div>
              <div v-else-if="merchantAuthentications.length" class="auth-chip-list">
                <span v-for="authItem in merchantAuthentications" :key="authItem.id" class="auth-chip">
                  {{ authItem.authenticationName }}
                </span>
              </div>
              <div v-else class="detail-muted">暂无资质记录</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="activeTab === 'flagship-audits'" class="admin-panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>认证名称</th>
            <th>商家 ID</th>
            <th>公司名称</th>
            <th>申请时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in flagshipAudits"
            :key="item.id"
            :class="{ 'row-selected': String(selectedFlagshipId) === String(item.id) }"
            @click="selectFlagship(item)"
          >
            <td>{{ item.id }}</td>
            <td>{{ item.authenticationName }}</td>
            <td>{{ item.merchantId }}</td>
            <td>{{ item.companyName }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td class="action-cell">
              <button type="button" class="accept-btn" :disabled="flagshipBusyId === item.id" @click="auditFlagship(item, true)">通过</button>
              <button type="button" class="reject-btn" :disabled="flagshipBusyId === item.id" @click="auditFlagship(item, false)">驳回</button>
            </td>
          </tr>
          <tr v-if="flagshipAudits.length === 0">
            <td colspan="6" class="empty-state">暂无待审旗舰店认证</td>
          </tr>
        </tbody>
      </table>

      <div v-if="selectedFlagship" class="audit-detail-card">
        <div class="audit-detail-head">
          <h3>{{ selectedFlagship.companyName }}</h3>
          <span class="detail-status-chip">{{ formatStatus(selectedFlagship.status) }}</span>
        </div>
        <div class="audit-detail-body">
          <img
            v-if="selectedFlagship.certificateImageUrl"
            :src="selectedFlagship.certificateImageUrl"
            :alt="selectedFlagship.companyName"
            class="detail-cover"
          />
          <div class="detail-content">
            <p><strong>认证名称：</strong>{{ selectedFlagship.authenticationName }}</p>
            <p><strong>商家 ID：</strong>{{ selectedFlagship.merchantId }}</p>
            <p><strong>认证等级：</strong>{{ selectedFlagship.authLevel || '官方旗舰店' }}</p>
            <p><strong>申请时间：</strong>{{ formatDateTime(selectedFlagship.createdAt) }}</p>
            <p><strong>审核意见：</strong>{{ selectedFlagship.auditReason || '待审核' }}</p>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="activeTab === 'product-audits'" class="admin-panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>商品名</th>
            <th>商家 ID</th>
            <th>最低价</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in productAudits"
            :key="item.id"
            :class="{ 'row-selected': String(selectedProductId) === String(item.id) }"
            @click="selectProduct(item)"
          >
            <td>{{ item.id }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.merchantId }}</td>
            <td>{{ item.minPrice }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td class="action-cell">
              <button type="button" class="accept-btn" :disabled="productBusyId === item.id" @click="auditProduct(item, true)">上架</button>
              <button type="button" class="reject-btn" :disabled="productBusyId === item.id" @click="auditProduct(item, false)">拒绝</button>
            </td>
          </tr>
          <tr v-if="productAudits.length === 0">
            <td colspan="6" class="empty-state">暂无待审商品</td>
          </tr>
        </tbody>
      </table>

      <div v-if="selectedProduct" class="audit-detail-card">
        <div class="audit-detail-head">
          <h3>{{ selectedProduct.name }}</h3>
          <span class="detail-status-chip">{{ formatStatus(selectedProduct.status) }}</span>
        </div>
        <div class="audit-detail-body">
          <img v-if="selectedProduct.imageUrl" :src="selectedProduct.imageUrl" :alt="selectedProduct.name" class="detail-cover" />
          <div class="detail-content">
            <p v-if="productDetailLoading" class="detail-muted">商品详情加载中...</p>
            <p><strong>商家 ID：</strong>{{ selectedProduct.merchantId }}</p>
            <p><strong>最低价：</strong>¥{{ formatMoney(selectedProduct.minPrice) }}</p>
            <p><strong>总库存：</strong>{{ selectedProductStock }}</p>
            <p><strong>销量：</strong>{{ selectedProduct.sales ?? 0 }}</p>
            <p><strong>创建时间：</strong>{{ formatDateTime(selectedProduct.createdAt) }}</p>
            <p><strong>商品描述：</strong>{{ selectedProduct.description || '暂无描述' }}</p>
            <p><strong>审核意见：</strong>{{ selectedProduct.auditReason || '待审核' }}</p>
            <div class="detail-block">
              <strong>商品标签</strong>
              <div v-if="selectedProduct.tags?.length" class="auth-chip-list">
                <span v-for="tag in selectedProduct.tags" :key="tag" class="auth-chip">
                  {{ tag }}
                </span>
              </div>
              <div v-else class="detail-muted">暂无标签</div>
            </div>
            <div class="detail-block">
              <strong>核心属性</strong>
              <div v-if="selectedProduct.coreAttributes?.length" class="auth-chip-list">
                <span v-for="attr in selectedProduct.coreAttributes" :key="attr" class="auth-chip">
                  {{ attr }}
                </span>
              </div>
              <div v-else class="detail-muted">暂无核心属性</div>
            </div>
            <div class="detail-block">
              <strong>规格组合与价格</strong>
              <div v-if="selectedProduct.skus?.length" class="sku-list">
                <article v-for="sku in selectedProduct.skus" :key="sku.id" class="sku-card">
                  <div class="sku-row">
                    <span class="sku-combination">{{ formatSkuAttributes(sku.attributeValues) }}</span>
                    <span class="sku-price">¥{{ formatMoney(sku.price) }}</span>
                  </div>
                  <div class="sku-row sku-meta">
                    <span>库存 {{ sku.stock ?? 0 }}</span>
                    <span>状态 {{ formatStatus(sku.status) }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="detail-muted">暂无规格组合</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else class="admin-panel admin-settings-panel">
      <div class="settings-hero-card">
        <div>
          <p class="section-kicker">设置中心</p>
          <h2>后台配置总览</h2>
          <p class="overview-hero-text">这里先集中展示当前已经生效的后台能力、权限边界与部署状态，后续可以继续扩成真实可编辑设置页。</p>
        </div>
        <div class="settings-status-box">
          <span class="settings-status-label">当前状态</span>
          <strong>核心后台能力稳定</strong>
          <p>认证审核、商品审核、登录守卫和静态部署均已接通。</p>
        </div>
      </div>

      <div class="settings-grid">
        <article v-for="group in settingGroups" :key="group.title" class="settings-card">
          <div class="settings-card-head">
            <h3>{{ group.title }}</h3>
            <span class="settings-badge">{{ group.badge }}</span>
          </div>
          <div class="settings-item-list">
            <div v-for="item in group.items" :key="item.name" class="settings-item-row">
              <span>{{ item.name }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>
      </div>

      <div class="overview-grid overview-grid-main">
        <article class="overview-block">
          <div class="block-head">
            <div>
              <p class="section-kicker">后续计划</p>
              <h3>下一步扩展建议</h3>
            </div>
          </div>
          <ol class="roadmap-list">
            <li v-for="item in roadmapItems" :key="item">{{ item }}</li>
          </ol>
        </article>

        <article class="overview-block settings-summary-block">
          <div class="block-head">
            <div>
              <p class="section-kicker">配置边界</p>
              <h3>当前说明</h3>
            </div>
          </div>
          <div class="signal-list compact">
            <article class="signal-item">
              <strong>权限配置</strong>
              <p>实际权限变更仍以 Spring Security 和后端角色配置为准，当前界面主要承担总览与运营说明。</p>
            </article>
            <article class="signal-item">
              <strong>系统参数</strong>
              <p>当前未引入单独的后台配置表，因此这里展示的是已落地能力，而不是伪交互表单。</p>
            </article>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(255, 184, 108, 0.28), transparent 30%),
    radial-gradient(circle at top right, rgba(78, 205, 196, 0.24), transparent 26%),
    linear-gradient(180deg, #fffdf7 0%, #f4f7fb 100%);
}

.admin-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  padding: 28px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 40px rgba(34, 48, 73, 0.08);
}

.admin-kicker {
  margin: 0 0 8px;
  color: #c46a1a;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.admin-hero h1 {
  margin: 0;
  font-size: 34px;
  color: #1f2a37;
}

.admin-subtitle {
  max-width: 680px;
  margin: 12px 0 0;
  color: #526074;
  line-height: 1.6;
}

.admin-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.admin-user {
  padding: 10px 14px;
  border-radius: 999px;
  background: #f3f7ff;
  color: #29508c;
  font-weight: 600;
}

.admin-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}

.admin-card,
.admin-panel {
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 16px 36px rgba(34, 48, 73, 0.08);
}

.admin-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-card span {
  color: #65748b;
}

.admin-card strong {
  font-size: 30px;
  color: #1f2a37;
}

.admin-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.admin-tabs button,
.secondary-btn,
.danger-btn,
.accept-btn,
.reject-btn {
  border: none;
  border-radius: 12px;
  padding: 10px 16px;
  font-weight: 600;
  cursor: pointer;
}

.admin-tabs button {
  background: rgba(255, 255, 255, 0.72);
  color: #4b5563;
}

.admin-tabs button.active {
  background: #1f2a37;
  color: #fff;
}

.admin-panel {
  padding: 18px;
}

.admin-overview-panel,
.admin-settings-panel {
  display: grid;
  gap: 18px;
}

.section-kicker {
  margin: 0 0 8px;
  color: #9a6a2f;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.overview-hero-card,
.settings-hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 18px;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(255, 248, 238, 0.98) 0%, rgba(247, 250, 255, 0.98) 100%);
  border: 1px solid #e8edf5;
}

.overview-hero-card h2,
.settings-hero-card h2 {
  margin: 0;
  color: #162032;
  font-size: 30px;
}

.overview-hero-text {
  margin: 12px 0 0;
  color: #516074;
  line-height: 1.75;
}

.overview-hero-badges {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.hero-badge,
.settings-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(28, 43, 68, 0.08);
  color: #233246;
  font-weight: 700;
}

.settings-status-box {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #dde6f2;
}

.settings-status-label {
  display: block;
  color: #6a7b8f;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.settings-status-box strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
  color: #162032;
}

.settings-status-box p {
  margin: 10px 0 0;
  color: #516074;
  line-height: 1.7;
}

.queue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.queue-card {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid #e6edf5;
  background: #fff;
}

.queue-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.queue-card-head span {
  color: #415066;
  font-weight: 700;
}

.queue-card-head strong {
  font-size: 28px;
  color: #162032;
}

.queue-card p {
  margin: 12px 0 0;
  color: #627387;
  line-height: 1.7;
}

.tone-warn {
  background: linear-gradient(180deg, #fffdf7 0%, #fff7e8 100%);
}

.tone-brand {
  background: linear-gradient(180deg, #f9fbff 0%, #eef5ff 100%);
}

.tone-success {
  background: linear-gradient(180deg, #f8fff9 0%, #eefcf1 100%);
}

.tone-calm {
  background: linear-gradient(180deg, #ffffff 0%, #f7f9fc 100%);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-grid-main {
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.9fr);
}

.feedback-block {
  grid-column: 1 / -1;
}

.feedback-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
}

.feedback-contact-list,
.feedback-chat-box {
  border-radius: 18px;
  background: #fbfcff;
  border: 1px solid #e7edf6;
}

.feedback-contact-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feedback-group-head {
  padding: 2px 4px;
  color: #7a889a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.feedback-contact-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: #fff7f1;
  cursor: pointer;
  text-align: left;
}

.feedback-contact-item.active {
  border-color: #f3b069;
  background: linear-gradient(135deg, rgba(255, 234, 212, 0.8), rgba(255, 247, 238, 0.95));
}

.feedback-contact-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.feedback-role-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #edf4ff;
  color: #29508c;
  font-size: 12px;
  font-weight: 700;
}

.feedback-contact-item p,
.feedback-chat-head p,
.feedback-message small {
  margin: 6px 0 0;
  color: #6b7280;
}

.feedback-unread {
  min-width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: #ff6b6b;
  color: white;
  font-size: 12px;
  font-weight: 700;
}

.feedback-chat-box {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 360px;
}

.feedback-chat-head {
  padding: 16px 18px 0;
}

.feedback-message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  overflow: auto;
}

.feedback-message {
  max-width: 75%;
  padding: 12px 14px;
  border-radius: 16px;
}

.feedback-message p {
  margin: 0;
  line-height: 1.6;
}

.feedback-message.user {
  align-self: flex-start;
  background: #fff4eb;
}

.feedback-message.agent {
  align-self: flex-end;
  background: linear-gradient(135deg, #1f2a37, #42516a);
  color: white;
}

.feedback-composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding: 0 18px 18px;
}

.feedback-composer input {
  width: 100%;
  border: 1px solid #d8e0ec;
  border-radius: 14px;
  padding: 12px 14px;
}

.overview-block {
  padding: 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, #f8fbff 100%);
  border: 1px solid #e2e8f0;
}

.overview-block h3 {
  margin: 0 0 10px;
}

.overview-block p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.block-head h3 {
  margin: 0;
  color: #162032;
}

.block-note {
  color: #6a7b8f;
  font-size: 13px;
}

.activity-list,
.signal-list,
.settings-item-list {
  display: grid;
  gap: 12px;
}

.activity-item,
.signal-item,
.settings-item-row {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(247, 250, 255, 0.92);
  border: 1px solid #e6edf7;
}

.activity-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.activity-item strong,
.signal-item strong,
.settings-item-row strong {
  color: #1d2b3f;
}

.activity-item p,
.signal-item p {
  margin: 6px 0 0;
  color: #607186;
}

.activity-meta {
  display: grid;
  justify-items: end;
  gap: 8px;
  flex-shrink: 0;
}

.activity-meta time {
  color: #738396;
  font-size: 13px;
}

.activity-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #edf4ff;
  color: #294f8b;
  font-size: 12px;
  font-weight: 700;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.settings-card {
  padding: 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, #f9fbfe 100%);
  border: 1px solid #e4ebf4;
}

.settings-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.settings-card-head h3 {
  margin: 0;
  color: #162032;
}

.settings-item-row {
  display: grid;
  gap: 8px;
}

.settings-item-row span {
  color: #66788d;
  font-size: 13px;
}

.roadmap-list {
  margin: 0;
  padding-left: 20px;
  color: #516074;
  display: grid;
  gap: 12px;
  line-height: 1.7;
}

.signal-list.compact {
  gap: 10px;
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
}

.admin-table th,
.admin-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #edf1f6;
  text-align: left;
}

.admin-table th {
  color: #5b677a;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.admin-table tbody tr {
  cursor: pointer;
}

.row-selected {
  background: rgba(243, 247, 255, 0.78);
}

.action-cell {
  display: flex;
  gap: 8px;
}

.secondary-btn {
  background: #e8eefc;
  color: #23457f;
}

.inline-btn {
  padding: 8px 12px;
}

.danger-btn,
.reject-btn {
  background: #ffe4e7;
  color: #b42318;
}

.accept-btn {
  background: #dcfce7;
  color: #166534;
}

.audit-detail-card {
  margin-top: 18px;
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(249, 251, 255, 0.96) 0%, rgba(255, 255, 255, 0.96) 100%);
  border: 1px solid #e6edf7;
}

.audit-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.audit-detail-head h3 {
  margin: 0;
  color: #1f2a37;
}

.detail-status-chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff1dc;
  color: #a15c13;
  font-weight: 700;
}

.audit-detail-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.detail-cover {
  width: 140px;
  height: 140px;
  border-radius: 16px;
  object-fit: cover;
  background: #f4f7fb;
  border: 1px solid #e8eef6;
}

.detail-content {
  flex: 1;
  display: grid;
  gap: 10px;
  color: #4d5a6d;
}

.detail-content p {
  margin: 0;
  line-height: 1.5;
}

.detail-block {
  display: grid;
  gap: 10px;
}

.auth-chip-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.auth-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #edf4ff;
  color: #2a518d;
  font-weight: 600;
}

.sku-list {
  display: grid;
  gap: 12px;
}

.sku-card {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(247, 250, 255, 0.92);
  border: 1px solid #e6edf7;
}

.sku-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.sku-row + .sku-row {
  margin-top: 8px;
}

.sku-combination {
  color: #233246;
  font-weight: 600;
}

.sku-price {
  color: #c2410c;
  font-weight: 700;
}

.sku-meta {
  color: #64748b;
  font-size: 13px;
}

.detail-muted {
  color: #7a8699;
}

.empty-state {
  text-align: center;
  color: #7a8699;
  padding: 28px 0;
}

@media (max-width: 900px) {
  .admin-shell {
    padding: 18px;
  }

  .admin-hero {
    flex-direction: column;
  }

  .overview-hero-card,
  .settings-hero-card,
  .queue-grid,
  .settings-grid,
  .overview-grid-main,
  .feedback-layout {
    grid-template-columns: 1fr;
  }

  .admin-cards,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .admin-actions,
  .action-cell,
  .admin-tabs {
    flex-wrap: wrap;
  }

  .audit-detail-body {
    flex-direction: column;
  }

  .detail-cover {
    width: 100%;
    max-width: 260px;
    height: auto;
    aspect-ratio: 1 / 1;
  }

  .admin-table {
    display: block;
    overflow-x: auto;
  }

  .activity-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .activity-meta {
    justify-items: start;
  }
}
</style>