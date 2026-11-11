import type { BuyerOrder, ChatContact, ChatMessage } from '@/buyer/types/models'
import type { MerchantOrder } from '@/types/merchant'

type ViewerRole = 'buyer' | 'merchant' | 'admin' | 'official'

type SharedSender = 'buyer' | 'merchant' | 'official'

type SharedChatMessage = {
  id: string
  sender: SharedSender
  content: string
  time: string
  timestamp: number
}

type SharedChatThread = {
  id: string
  orderId: string
  orderNo?: string
  productName: string
  skuSummary?: string
  merchantUserId?: string
  merchantName: string
  buyerUserId?: string
  buyerName: string
  buyerAvatar?: string
  officialRequesterRole?: 'buyer' | 'merchant'
  officialRequesterId?: string
  officialRequesterName?: string
  unreadBuyer: number
  unreadMerchant: number
  unreadOfficial: number
  updatedAt: number
  lastMessage: string
}

type SharedChatStore = {
  threads: Record<string, SharedChatThread>
  messages: Record<string, SharedChatMessage[]>
}

export type ChatThreadTarget = {
  threadId: string
  orderId: string
  orderNo?: string
  productName: string
  skuSummary?: string
  merchantName: string
  buyerName: string
}

const STORAGE_KEY = 'jpet-store:order-chat:v1'

function uid(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

function nowStamp() {
  return Date.now()
}

function formatTime(timestamp = nowStamp()) {
  const current = new Date(timestamp)
  return `${String(current.getHours()).padStart(2, '0')}:${String(current.getMinutes()).padStart(2, '0')}`
}

function normalizeText(value?: string | null, fallback = '') {
  const trimmed = String(value ?? '').trim()
  return trimmed || fallback
}

function readStore(): SharedChatStore {
  if (typeof window === 'undefined') {
    return { threads: {}, messages: {} }
  }
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return { threads: {}, messages: {} }
  }
  try {
    const parsed = JSON.parse(raw) as SharedChatStore
    return {
      threads: parsed?.threads || {},
      messages: parsed?.messages || {},
    }
  } catch {
    return { threads: {}, messages: {} }
  }
}

function writeStore(store: SharedChatStore) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
}

function threadIdOfOrder(orderId: string | number) {
  return `order-${String(orderId)}`
}

function officialThreadIdOfBuyer(buyerUserId: string | number) {
  return `official-${String(buyerUserId)}`
}

function createGreeting(productName: string, merchantName: string) {
  return `您好，这里是${merchantName}，关于“${productName}”的订单问题可以直接留言。`
}

function createOfficialGreeting() {
  return '欢迎来到爱宠生活馆，有需要可以直接问我。'
}

function createMerchantOfficialGreeting(merchantName: string) {
  return `您好，这里是官方管理员，${merchantName} 如有审核、订单或系统问题可以直接留言。`
}

function toChatMessage(message: SharedChatMessage, viewerRole: ViewerRole): ChatMessage {
  let sender: ChatMessage['sender']
  if (viewerRole === 'admin') {
    sender = message.sender === 'official' ? 'agent' : 'user'
  } else {
    const normalizedViewer = viewerRole === 'official' ? 'official' : viewerRole
    sender = message.sender === normalizedViewer ? 'user' : 'agent'
  }
  return {
    id: message.id,
    sender,
    content: message.content,
    time: message.time,
  }
}

function defaultBuyerName(order: BuyerOrder, fallbackUsername: string) {
  return normalizeText(order.receiverName, fallbackUsername || '买家')
}

function touchThread(store: SharedChatStore, threadId: string) {
  const thread = store.threads[threadId]
  if (!thread) {
    return
  }
  const last = store.messages[threadId]?.at(-1)
  thread.updatedAt = last?.timestamp ?? thread.updatedAt
  thread.lastMessage = last?.content ?? thread.lastMessage
}

export function syncBuyerOrderThreads(orders: BuyerOrder[], payload: { buyerUserId: string; buyerName: string; buyerAvatar?: string }) {
  const store = readStore()
  const validThreadIds = new Set<string>()

  orders.forEach((order) => {
    if (!order.id || !order.merchantName) {
      return
    }
    const threadId = threadIdOfOrder(order.id)
    validThreadIds.add(threadId)
    const current = store.threads[threadId]
    const next: SharedChatThread = {
      id: threadId,
      orderId: String(order.id),
      orderNo: order.orderNo,
      productName: normalizeText(order.name, '订单商品'),
      skuSummary: normalizeText(order.skuSummary),
      merchantUserId: order.merchantId ? String(order.merchantId) : current?.merchantUserId,
      merchantName: normalizeText(order.merchantName, current?.merchantName || '商家'),
      buyerUserId: payload.buyerUserId,
      buyerName: normalizeText(defaultBuyerName(order, payload.buyerName), payload.buyerName),
      buyerAvatar: normalizeText(payload.buyerAvatar, current?.buyerAvatar || ''),
      unreadBuyer: current?.unreadBuyer ?? 0,
      unreadMerchant: current?.unreadMerchant ?? 0,
      unreadOfficial: current?.unreadOfficial ?? 0,
      updatedAt: current?.updatedAt ?? nowStamp(),
      lastMessage: current?.lastMessage ?? `关于“${normalizeText(order.name, '订单商品')}”的订单咨询`,
    }
    store.threads[threadId] = next
    if (!store.messages[threadId]?.length) {
      store.messages[threadId] = [
        {
          id: uid('msg'),
          sender: 'merchant',
          content: createGreeting(next.productName, next.merchantName),
          time: formatTime(),
          timestamp: nowStamp(),
        },
      ]
      touchThread(store, threadId)
    }
  })

  Object.values(store.threads).forEach((thread) => {
    if (thread.buyerUserId !== payload.buyerUserId) {
      return
    }
    if (thread.id.startsWith('official-')) {
      return
    }
    if (validThreadIds.has(thread.id)) {
      return
    }
    delete store.threads[thread.id]
    delete store.messages[thread.id]
  })

  const officialThreadId = officialThreadIdOfBuyer(payload.buyerUserId)
  const existingOfficial = store.threads[officialThreadId]
  store.threads[officialThreadId] = {
    id: officialThreadId,
    orderId: officialThreadId,
    productName: '官方客服',
    merchantName: '官方客服',
    buyerUserId: payload.buyerUserId,
    buyerName: payload.buyerName,
    officialRequesterRole: 'buyer',
    officialRequesterId: payload.buyerUserId,
    officialRequesterName: payload.buyerName,
    unreadBuyer: existingOfficial?.unreadBuyer ?? 1,
    unreadMerchant: 0,
    unreadOfficial: existingOfficial?.unreadOfficial ?? 0,
    updatedAt: existingOfficial?.updatedAt ?? nowStamp(),
    lastMessage: existingOfficial?.lastMessage ?? createOfficialGreeting(),
  }
  if (!store.messages[officialThreadId]?.length) {
    store.messages[officialThreadId] = [
      {
        id: uid('msg'),
        sender: 'official',
        content: createOfficialGreeting(),
        time: formatTime(),
        timestamp: nowStamp(),
      },
    ]
  }
  touchThread(store, officialThreadId)
  writeStore(store)
}

export function syncMerchantOfficialThread(payload: { merchantUserId: string; merchantName: string }) {
  const store = readStore()
  const threadId = `official-merchant-${payload.merchantUserId}`
  const current = store.threads[threadId]
  store.threads[threadId] = {
    id: threadId,
    orderId: threadId,
    productName: '商家后台问题反馈',
    merchantUserId: payload.merchantUserId,
    merchantName: payload.merchantName,
    buyerName: payload.merchantName,
    officialRequesterRole: 'merchant',
    officialRequesterId: payload.merchantUserId,
    officialRequesterName: payload.merchantName,
    unreadBuyer: 0,
    unreadMerchant: current?.unreadMerchant ?? 0,
    unreadOfficial: current?.unreadOfficial ?? 0,
    updatedAt: current?.updatedAt ?? nowStamp(),
    lastMessage: current?.lastMessage ?? '审核、订单异常或系统问题可直接反馈给管理员',
  }
  if (!store.messages[threadId]?.length) {
    store.messages[threadId] = [
      {
        id: uid('msg'),
        sender: 'official',
        content: createMerchantOfficialGreeting(payload.merchantName),
        time: formatTime(),
        timestamp: nowStamp(),
      },
    ]
  }
  touchThread(store, threadId)
  writeStore(store)
}

export function syncMerchantOrderThreads(orders: MerchantOrder[], payload: { merchantUserId: string; merchantName: string }) {
  const store = readStore()
  const validThreadIds = new Set<string>()
  orders.forEach((order) => {
    if (!order.id) {
      return
    }
    const threadId = threadIdOfOrder(order.id)
    validThreadIds.add(threadId)
    const current = store.threads[threadId]
    store.threads[threadId] = {
      id: threadId,
      orderId: String(order.id),
      productName: normalizeText(order.name, current?.productName || '订单商品'),
      skuSummary: normalizeText(order.skuSummary, current?.skuSummary || ''),
      merchantUserId: payload.merchantUserId,
      merchantName: normalizeText(payload.merchantName, current?.merchantName || '商家'),
      buyerUserId: order.buyerUserId != null ? String(order.buyerUserId) : current?.buyerUserId,
      buyerName: normalizeText(order.buyerName || order.receiverName || order.receiver, current?.buyerName || '买家'),
      buyerAvatar: normalizeText(order.buyerAvatar, current?.buyerAvatar || ''),
      unreadBuyer: current?.unreadBuyer ?? 0,
      unreadMerchant: current?.unreadMerchant ?? 0,
      unreadOfficial: current?.unreadOfficial ?? 0,
      updatedAt: current?.updatedAt ?? nowStamp(),
      lastMessage: current?.lastMessage ?? `关于“${normalizeText(order.name, '订单商品')}”的订单咨询`,
    }
    if (!store.messages[threadId]?.length) {
      store.messages[threadId] = [
        {
          id: uid('msg'),
          sender: 'merchant',
          content: createGreeting(normalizeText(order.name, '订单商品'), payload.merchantName),
          time: formatTime(),
          timestamp: nowStamp(),
        },
      ]
    }
    touchThread(store, threadId)
  })

  Object.values(store.threads).forEach((thread) => {
    if (thread.merchantUserId !== payload.merchantUserId) {
      return
    }
    if (thread.officialRequesterRole === 'merchant') {
      return
    }
    if (validThreadIds.has(thread.id)) {
      return
    }
    delete store.threads[thread.id]
    delete store.messages[thread.id]
  })
  writeStore(store)
}

export function listBuyerContacts(buyerUserId: string): ChatContact[] {
  const store = readStore()
  return Object.values(store.threads)
    .filter((thread) => thread.buyerUserId === buyerUserId)
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .map((thread) => ({
      id: thread.id,
      name: thread.id.startsWith('official-') ? '官方客服' : thread.merchantName,
      avatar: thread.id.startsWith('official-') ? '🏢' : '🏪',
      lastMessage: thread.id.startsWith('official-')
        ? thread.lastMessage
        : `关于“${thread.productName}” ${thread.lastMessage}`,
      online: true,
      unread: thread.unreadBuyer,
      type: thread.id.startsWith('official-') ? 'official' : 'store',
      orderId: thread.orderId,
      orderNo: thread.orderNo,
      productName: thread.productName,
      subtitle: thread.id.startsWith('official-') ? '平台订单与规则咨询' : `订单商品：${thread.productName}`,
    }))
}

export function listMerchantContacts(merchantUserId: string) {
  const store = readStore()
  return Object.values(store.threads)
    .filter((thread) =>
      thread.merchantUserId === merchantUserId
      || (thread.officialRequesterRole === 'merchant' && thread.officialRequesterId === merchantUserId)
    )
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .map((thread, index) => ({
      id: thread.id,
      name: thread.officialRequesterRole === 'merchant' ? '官方管理员' : thread.buyerName,
      avatar: thread.officialRequesterRole === 'merchant'
        ? '🏢'
        : normalizeText(thread.buyerAvatar, ['🧑', '👩', '👨', '🧕', '👱'][index % 5]),
      role: thread.officialRequesterRole === 'merchant' ? '管理员' : '买家',
      lastMessage: thread.officialRequesterRole === 'merchant'
        ? thread.lastMessage
        : `关于“${thread.productName}” ${thread.lastMessage}`,
      unread: thread.unreadMerchant,
      orderId: thread.orderId,
      productName: thread.productName,
      subtitle: thread.officialRequesterRole === 'merchant'
        ? '商家后台 / 审核 / 订单异常反馈'
        : (thread.skuSummary || `订单商品：${thread.productName}`),
    }))
}

export function listOfficialContacts() {
  const store = readStore()
  return Object.values(store.threads)
    .filter((thread) => thread.id.startsWith('official-'))
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .map((thread) => ({
      id: thread.id,
      name: thread.officialRequesterRole === 'merchant'
        ? (thread.officialRequesterName || thread.merchantName)
        : thread.buyerName,
      avatar: '🏢',
      role: thread.officialRequesterRole === 'merchant' ? '商家' : '买家',
      lastMessage: thread.lastMessage,
      unread: thread.unreadOfficial,
      orderId: thread.orderId,
      productName: thread.productName,
      subtitle: thread.officialRequesterRole === 'merchant'
        ? `商家反馈 · ${thread.officialRequesterName || thread.merchantName}`
        : `买家反馈 · ${thread.buyerName}`,
    }))
}

export function getThreadTarget(threadId: string): ChatThreadTarget | null {
  const store = readStore()
  const thread = store.threads[threadId]
  if (!thread) {
    return null
  }
  return {
    threadId: thread.id,
    orderId: thread.orderId,
    orderNo: thread.orderNo,
    productName: thread.productName,
    skuSummary: thread.skuSummary,
    merchantName: thread.merchantName,
    buyerName: thread.buyerName,
  }
}

export function listThreadMessages(threadId: string, viewerRole: ViewerRole) {
  const store = readStore()
  return (store.messages[threadId] || []).map((message) => toChatMessage(message, viewerRole))
}

export function markThreadRead(threadId: string, viewerRole: ViewerRole) {
  const store = readStore()
  const thread = store.threads[threadId]
  if (!thread) {
    return
  }
  if (viewerRole === 'buyer') {
    thread.unreadBuyer = 0
  } else if (viewerRole === 'admin') {
    thread.unreadOfficial = 0
  } else {
    thread.unreadMerchant = 0
  }
  writeStore(store)
}

function officialReply(content: string) {
  if (content.includes('订单') || content.includes('发货')) {
    return '已为你记录订单问题，若涉及发货或售后，我们会优先核查并协助处理。'
  }
  if (content.includes('违规') || content.includes('举报')) {
    return '平台会尽快核对相关订单与商品信息，确认后会做进一步处理。'
  }
  return '官方客服已收到你的消息，我们会尽快回复。'
}

export function sendThreadMessage(threadId: string, sender: 'buyer' | 'merchant' | 'official', content: string) {
  const store = readStore()
  const thread = store.threads[threadId]
  if (!thread) {
    return [] as ChatMessage[]
  }
  const timestamp = nowStamp()
  const threadMessages = store.messages[threadId] || []
  threadMessages.push({
    id: uid('msg'),
    sender,
    content,
    time: formatTime(timestamp),
    timestamp,
  })

  if (threadId.startsWith('official-') && sender === 'buyer') {
    threadMessages.push({
      id: uid('msg'),
      sender: 'official',
      content: officialReply(content),
      time: formatTime(nowStamp()),
      timestamp: nowStamp(),
    })
    thread.unreadOfficial += 1
  } else if (threadId.startsWith('official-') && sender === 'official') {
    thread.unreadBuyer += 1
  } else if (sender === 'buyer') {
    thread.unreadMerchant += 1
  } else {
    thread.unreadBuyer += 1
  }

  store.messages[threadId] = threadMessages
  touchThread(store, threadId)
  if (sender === 'buyer') {
    thread.unreadBuyer = 0
  } else if (sender === 'official') {
    thread.unreadOfficial = 0
  } else {
    thread.unreadMerchant = 0
  }
  writeStore(store)
  return threadMessages.map((message) => toChatMessage(message, sender))
}

export function buildOrderShareMessage(contact: { orderNo?: string; productName?: string; subtitle?: string }) {
  const orderNo = normalizeText(contact.orderNo, '-')
  const productName = normalizeText(contact.productName, '订单商品')
  const subtitle = normalizeText(contact.subtitle)
  const extra = subtitle && subtitle !== `订单商品：${productName}` ? `\n相关信息：${subtitle}` : ''
  return `这是我的相关订单，请帮我跟进：\n订单号：${orderNo}\n商品：${productName}${extra}`
}