<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { merchantApi, merchantOrderApi } from '@/api/merchant'
import { useAuthStore } from '@/stores/auth'
import {
  listMerchantContacts,
  listThreadMessages,
  markThreadRead,
  sendThreadMessage,
  syncMerchantOfficialThread,
  syncMerchantOrderThreads,
} from '@/utils/orderChat'
import type { ChatMessage } from '@/buyer/types/models'
import type { MerchantOrder } from '@/types/merchant'

type MerchantChatContact = {
  id: string
  name: string
  avatar: string
  role: string
  lastMessage: string
  unread: number
  orderId: string
  productName: string
  subtitle?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const contacts = ref<MerchantChatContact[]>([])
const activeContact = ref<MerchantChatContact | null>(null)
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const merchantName = ref('当前商家')
const merchantKey = ref('')
let refreshTimer: ReturnType<typeof setInterval> | null = null
const activePlaceholder = computed(() =>
  activeContact.value?.role === '管理员'
    ? '输入审核、订单异常、系统问题等需要管理员协助的内容...'
    : '输入发货、规格、售后等订单回复内容...'
)

function isImageAvatar(value: string) {
  const raw = String(value || '').trim()
  return Boolean(raw) && (
    /^(https?:)?\/\//.test(raw)
    || raw.startsWith('/')
    || raw.startsWith('images/')
    || raw.startsWith('uploads/')
  )
}

async function hydrateThreads() {
  await auth.refresh()
  const [profile, applicationStatus, currentOrders, historyOrders] = await Promise.all([
    merchantApi.getProfile(),
    merchantApi.getApplicationStatus(),
    merchantOrderApi.list(),
    merchantOrderApi.listHistory(),
  ])
  merchantName.value = profile?.merchantName || auth.user?.username || '当前商家'
  merchantKey.value = String(applicationStatus?.merchantId || profile?.id || '')
  const rows = [
    ...(Array.isArray(currentOrders?.list) ? currentOrders.list : []),
    ...(Array.isArray(historyOrders?.list) ? historyOrders.list : []),
  ] as MerchantOrder[]
  syncMerchantOrderThreads(rows, {
    merchantUserId: merchantKey.value,
    merchantName: merchantName.value,
  })
  syncMerchantOfficialThread({
    merchantUserId: merchantKey.value,
    merchantName: merchantName.value,
  })
  contacts.value = listMerchantContacts(merchantKey.value)
}

async function selectContact(contact: MerchantChatContact) {
  activeContact.value = contact
  markThreadRead(contact.id, 'merchant')
  messages.value = listThreadMessages(contact.id, 'merchant')
  contacts.value = listMerchantContacts(merchantKey.value)
}

async function refreshContacts() {
  const preservedThreadId = activeContact.value?.id || String(route.query.threadId || '')
  const preservedOrderId = String(route.query.orderId || '')
  await hydrateThreads()
  if (!contacts.value.length) {
    activeContact.value = null
    messages.value = []
    return
  }
  const preferred = preservedThreadId
    ? contacts.value.find((item) => item.id === preservedThreadId)
    : (preservedOrderId ? contacts.value.find((item) => String(item.orderId) === preservedOrderId) : null)
  await selectContact(preferred || contacts.value[0])
}

function handleChatStorageChange(event: StorageEvent) {
  if (event.key && event.key !== 'jpet-store:order-chat:v1') {
    return
  }
  void refreshContacts()
}

async function sendMessage() {
  if (!activeContact.value || !draft.value.trim()) {
    return
  }
  messages.value = sendThreadMessage(activeContact.value.id, 'merchant', draft.value.trim())
  contacts.value = listMerchantContacts(merchantKey.value)
  draft.value = ''
}

function goBack() {
  router.push('/index')
}

onMounted(async () => {
  window.addEventListener('storage', handleChatStorageChange)
  await refreshContacts()
  refreshTimer = setInterval(() => {
    void refreshContacts()
  }, 3000)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleChatStorageChange)
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<template>
  <div class="chat-page">
    <div class="chat-page-shell">
      <aside class="contact-panel">
        <div class="panel-head">
          <button type="button" class="back-btn" @click="goBack">← 返回</button>
          <h2>联系会话列表</h2>
        </div>

        <div v-if="contacts.length" class="contact-list">
          <button
            v-for="contact in contacts"
            :key="contact.id"
            type="button"
            class="contact-card"
            :class="{ active: activeContact?.id === contact.id }"
            @click="selectContact(contact)"
          >
            <div class="avatar">
              <img v-if="isImageAvatar(contact.avatar)" :src="contact.avatar" :alt="contact.name" class="avatar-image" />
              <span v-else>{{ contact.avatar }}</span>
            </div>
            <div class="copy">
              <div class="top-row">
                <strong>{{ contact.name }}</strong>
                <span class="role-tag">{{ contact.role }}</span>
              </div>
              <p class="product-line">{{ contact.subtitle || `订单商品：${contact.productName}` }}</p>
              <p>{{ contact.lastMessage }}</p>
            </div>
            <span v-if="contact.unread" class="badge">{{ contact.unread }}</span>
          </button>
        </div>

        <div v-else class="empty-state">暂无会话，可通过订单联系买家，或直接给管理员反馈问题。</div>
      </aside>

      <section class="conversation-panel" v-if="activeContact">
        <header class="conversation-head">
          <div>
            <h2>{{ activeContact.name }}</h2>
            <p>{{ activeContact.subtitle || `订单商品：${activeContact.productName}` }}</p>
          </div>
          <div class="head-meta">
            <strong>{{ merchantName }}</strong>
            <span>当前在线</span>
          </div>
        </header>

        <div class="message-list">
          <article v-for="message in messages" :key="message.id" class="message" :class="message.sender">
            <p>{{ message.content }}</p>
            <small>{{ message.time }}</small>
          </article>
        </div>

        <footer class="composer">
          <input v-model="draft" type="text" :placeholder="activePlaceholder" @keydown.enter="sendMessage" />
          <button type="button" class="send-btn" @click="sendMessage">发送</button>
        </footer>
      </section>

      <section v-else class="conversation-panel conversation-panel--empty">
        <div class="empty-state">请选择左侧会话，可查看买家订单沟通或联系管理员。</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  min-height: 100vh;
  padding: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 194, 153, 0.32), transparent 28%),
    radial-gradient(circle at bottom left, rgba(255, 151, 181, 0.2), transparent 24%),
    #fffaf5;
}

.chat-page-shell {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 18px;
  min-height: calc(100vh - 56px);
}

.contact-panel,
.conversation-panel {
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 50px rgba(100, 59, 20, 0.08);
}

.contact-panel {
  padding: 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h2,
.conversation-head h2 {
  margin: 0;
}

.back-btn {
  border: 1px solid rgba(143, 104, 64, 0.18);
  background: #fff7ef;
  color: #6e4b2b;
}

.contact-list {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.contact-card {
  position: relative;
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 12px;
  width: 100%;
  padding: 16px;
  border-radius: 22px;
  text-align: left;
  background: rgba(255, 248, 243, 0.95);
  border: 1px solid transparent;
}

.contact-card.active {
  border-color: rgba(255, 174, 123, 0.45);
  background: linear-gradient(135deg, rgba(255, 222, 198, 0.55), rgba(255, 243, 234, 0.9));
}

.avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #d8a56b, #74b6a1);
  color: white;
  font-size: 24px;
}

.avatar-image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.copy {
  min-width: 0;
}

.top-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: #e7f5f2;
  color: #0c7a76;
  font-size: 12px;
}

.copy p {
  margin: 4px 0 0;
  color: #7e6b5d;
}

.product-line {
  font-weight: 600;
  color: #5c4532;
}

.badge {
  min-width: 28px;
  height: 28px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: #ff5757;
  color: white;
  font-weight: 700;
}

.conversation-panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 720px;
}

.conversation-panel--empty {
  display: grid;
  place-items: center;
}

.conversation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 24px 24px 0;
}

.conversation-head p,
.message small,
.head-meta span {
  color: #8d7a6b;
}

.head-meta {
  text-align: right;
}

.message-list {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.message {
  max-width: 72%;
  padding: 14px 16px;
  border-radius: 22px;
}

.message p {
  margin: 0;
  line-height: 1.7;
}

.message.agent {
  align-self: flex-start;
  background: rgba(255, 248, 243, 0.95);
}

.message.user {
  align-self: flex-end;
  background: linear-gradient(135deg, #ff9b7d, #ffb46a);
  color: white;
}

.composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding: 18px 24px 24px;
}

.composer input {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(170, 145, 121, 0.22);
}

.send-btn {
  padding: 0 20px;
  border-radius: 18px;
  background: linear-gradient(135deg, #ff8f8a, #ffb05d);
  color: white;
}

.empty-state {
  color: #816d5d;
}

button {
  cursor: pointer;
}

@media (max-width: 960px) {
  .chat-page-shell {
    grid-template-columns: 1fr;
  }
}
</style>