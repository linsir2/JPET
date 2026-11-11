<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { chatApi } from '@/buyer/api/services';
import type { ChatContact, ChatMessage } from '@/buyer/types/models';
import { buildOrderShareMessage } from '@/utils/orderChat';

const route = useRoute();
const router = useRouter();
const allContacts = ref<ChatContact[]>([]);
const contacts = ref<ChatContact[]>([]);
const activeContact = ref<ChatContact | null>(null);
const messages = ref<ChatMessage[]>([]);
const draft = ref('');
const chatMode = computed(() => String(route.query.mode || 'official'));
const chatTitle = computed(() => (chatMode.value === 'merchant' ? '联系商家' : '官方客服'));
const chatSubtitle = computed(() =>
  chatMode.value === 'merchant'
    ? '订单沟通、发货确认与售后协商都在这里处理'
    : '这里是平台管理员 / 官方客服入口，用于订单异常、规则咨询与问题反馈'
);
const hasMerchantContact = computed(() => allContacts.value.some((item) => item.type === 'store'));

function goToMerchantOrders() {
  router.push('/buyer/orders');
}

async function switchMode(mode: 'official' | 'merchant') {
  await router.push({
    path: '/buyer/chat',
    query: mode === 'merchant' ? { mode: 'merchant' } : { mode: 'official' },
  });
}

function filterContacts(allContacts: ChatContact[]) {
  if (chatMode.value === 'merchant') {
    return allContacts.filter((item) => item.type === 'store');
  }
  return allContacts.filter((item) => item.type === 'official');
}

async function loadContacts() {
  allContacts.value = await chatApi.listContacts();
  contacts.value = filterContacts(allContacts.value);
  if (!contacts.value.length) {
    activeContact.value = null;
    messages.value = [];
    return;
  }

  const preferredOrderId = String(route.query.orderId || '');
  const preferredContact = preferredOrderId
    ? contacts.value.find((item) => String(item.orderId || '') === preferredOrderId)
    : null;
  const preservedContact = contacts.value.find((item) => item.id === activeContact.value?.id);
  await selectContact(preferredContact || preservedContact || contacts.value[0]);
}

async function selectContact(contact: ChatContact) {
  activeContact.value = contact;
  messages.value = await chatApi.listMessages(contact.id);
}

watch(
  () => [route.query.mode, route.query.orderId],
  async () => {
    await loadContacts();
  }
);

onMounted(async () => {
  await loadContacts();
});

async function sendMessage() {
  if (!activeContact.value || !draft.value.trim()) {
    return;
  }
  messages.value = await chatApi.sendMessage(activeContact.value.id, draft.value.trim());
  await loadContacts();
  draft.value = '';
}

async function shareOrder() {
  if (!activeContact.value) {
    return;
  }
  messages.value = await chatApi.sendMessage(activeContact.value.id, buildOrderShareMessage(activeContact.value));
  await loadContacts();
}
</script>

<template>
  <AppShell active="chat" :title="chatTitle" :subtitle="chatSubtitle">
    <section class="chat-layout">
      <aside class="contact-panel">
        <div class="contact-panel-head">
          <h2>{{ chatMode === 'merchant' ? '商家会话' : '官方会话' }}</h2>
          <div class="chat-switcher">
            <button type="button" :class="{ active: chatMode === 'official' }" @click="switchMode('official')">官方客服</button>
            <button type="button" :class="{ active: chatMode === 'merchant' }" @click="switchMode('merchant')">联系商家</button>
          </div>
        </div>
        <button
          v-for="contact in contacts"
          :key="contact.id"
          class="contact-card"
          :class="{ active: activeContact?.id === contact.id }"
          @click="selectContact(contact)"
        >
          <div class="avatar">{{ contact.avatar }}</div>
          <div class="copy">
            <div class="top-row">
              <strong>{{ contact.name }}</strong>
              <span v-if="contact.unread">{{ contact.unread }}</span>
            </div>
            <p>{{ contact.lastMessage }}</p>
          </div>
        </button>
      </aside>

      <section class="conversation-panel" v-if="activeContact">
        <header class="conversation-head">
          <div>
            <h2>{{ activeContact.name }}</h2>
            <p>{{ activeContact.subtitle || (activeContact.online ? '当前在线' : '稍后回复') }}</p>
          </div>
          <div class="conversation-actions">
            <button
              v-if="chatMode === 'official' && hasMerchantContact"
              type="button"
              class="merchant-entry-btn"
              @click="switchMode('merchant')"
            >
              查看商家会话
            </button>
            <button v-else-if="chatMode === 'official'" type="button" class="merchant-entry-btn" @click="goToMerchantOrders">
              去订单联系商家
            </button>
          </div>
        </header>

        <div class="message-list">
          <article v-for="message in messages" :key="message.id" class="message" :class="message.sender">
            <p>{{ message.content }}</p>
            <small>{{ message.time }}</small>
          </article>
        </div>

        <footer class="composer">
          <button class="emoji-btn">🐾</button>
          <input v-model="draft" type="text" placeholder="输入你想咨询的问题，比如发货、推荐、寄养安排等..." @keydown.enter="sendMessage" />
          <button v-if="chatMode === 'merchant'" class="order-btn" @click="shareOrder">发送订单</button>
          <button class="send-btn" @click="sendMessage">发送</button>
        </footer>
      </section>

      <section v-else class="conversation-panel conversation-panel--empty">
        <div class="empty-state-group">
          <div class="empty-state">{{ chatMode === 'merchant' ? '暂无商家会话，请先从订单页发起联系。' : '当前没有官方客服会话。' }}</div>
          <button v-if="chatMode === 'merchant'" type="button" class="merchant-entry-btn" @click="goToMerchantOrders">去订单页</button>
        </div>
      </section>
    </section>
  </AppShell>
</template>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

.contact-panel,
.conversation-panel {
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.contact-panel {
  padding: 18px;
}

.contact-panel-head {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contact-panel h2,
.conversation-head h2 {
  margin: 0;
}

.chat-switcher {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.chat-switcher button {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--ink-soft);
}

.chat-switcher button.active {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.18), rgba(255, 208, 133, 0.22));
  color: #6f3d1f;
  border-color: rgba(255, 173, 120, 0.55);
}

.contact-card {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 12px;
  width: 100%;
  margin-top: 14px;
  padding: 14px;
  border-radius: 22px;
  text-align: left;
  background: rgba(255, 248, 243, 0.9);
}

.contact-card.active {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.2), rgba(255, 208, 133, 0.24));
}

.avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  font-size: 24px;
}

.copy p,
.conversation-head p,
.message small {
  color: var(--ink-soft);
}

.top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.top-row span {
  min-width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: #ff6b9d;
  color: white;
  font-size: 12px;
}

.conversation-panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 680px;
}

.conversation-panel--empty {
  place-items: center;
}

.empty-state-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.conversation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 0;
}

.conversation-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.merchant-entry-btn {
  border: 1px solid var(--line-soft);
  background: rgba(255, 248, 243, 0.95);
  color: #7a4c2a;
  border-radius: 16px;
  padding: 10px 14px;
}

.message-list {
  padding: 20px;
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
  background: linear-gradient(135deg, #ff91b2, #ffcf85);
  color: white;
}

.composer {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  gap: 12px;
  padding: 18px 20px 20px;
}

.composer input {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
}

.emoji-btn,
.order-btn,
.send-btn {
  width: 52px;
  height: 52px;
  border-radius: 18px;
}

.emoji-btn {
  background: rgba(255, 255, 255, 0.8);
}

.send-btn {
  width: auto;
  padding: 0 18px;
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

.order-btn {
  width: auto;
  padding: 0 18px;
  background: rgba(255, 248, 243, 0.95);
  color: #7a4c2a;
  border: 1px solid var(--line-soft);
}

@media (max-width: 960px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .conversation-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .conversation-actions {
    width: 100%;
  }

  .conversation-actions .merchant-entry-btn {
    width: 100%;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .message {
    max-width: 100%;
  }
}

.empty-state {
  color: var(--ink-soft);
  align-self: center;
  justify-self: center;
}
</style>