<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { serviceApi } from '@/buyer/api/services';
import type { VetDoctor } from '@/buyer/types/models';

type MedicalMessage = {
  sender: 'user' | 'agent';
  content: string;
};

const doctors = ref<VetDoctor[]>([]);
const activeDoctor = ref<VetDoctor | null>(null);
const messages = ref<MedicalMessage[]>([]);
const question = ref('');

function seedConversation(doctor: VetDoctor) {
  messages.value = [
    {
      sender: 'agent',
      content: `${doctor.name} 在线，欢迎描述宠物当前状态、饮食变化和持续时间，我会先给你基础建议。`
    }
  ];
}

onMounted(async () => {
  doctors.value = await serviceApi.listDoctors();
  activeDoctor.value = doctors.value[0] ?? null;
  if (activeDoctor.value) {
    seedConversation(activeDoctor.value);
  }
});

async function sendQuestion() {
  if (!activeDoctor.value || !question.value.trim()) {
    return;
  }
  const content = question.value.trim();
  messages.value.push({ sender: 'user', content });
  question.value = '';
  const response = await serviceApi.askDoctor(activeDoctor.value.id, content);
  messages.value.push({ sender: 'agent', content: response.reply });
}
</script>

<template>
  <AppShell active="medical" title="线上诊疗" subtitle="保留原来的服务感，但把诊疗问答改造成可接 API 的前端模块">
    <section class="medical-layout">
      <aside class="doctor-panel">
        <div class="panel-head">
          <h2>医生列表</h2>
          <span>模拟接口数据</span>
        </div>
        <button
          v-for="doctor in doctors"
          :key="doctor.id"
          class="doctor-card"
          :class="{ active: activeDoctor?.id === doctor.id }"
          @click="activeDoctor = doctor; seedConversation(doctor)"
        >
          <img :src="doctor.avatar" :alt="doctor.name" />
          <div>
            <strong>{{ doctor.name }}</strong>
            <p>{{ doctor.title }} · {{ doctor.specialty }}</p>
            <small>{{ doctor.responseTime }}</small>
          </div>
        </button>
      </aside>

      <section class="conversation-panel">
        <header v-if="activeDoctor" class="conversation-head">
          <div>
            <h2>{{ activeDoctor.name }}</h2>
            <p>{{ activeDoctor.summary }}</p>
          </div>
          <span class="status" :class="{ offline: !activeDoctor.online }">
            {{ activeDoctor.online ? '在线接诊' : '当前离线' }}
          </span>
        </header>

        <div class="message-list">
          <article v-for="(message, index) in messages" :key="index" class="message" :class="message.sender">
            {{ message.content }}
          </article>
        </div>

        <footer class="composer">
          <textarea v-model="question" rows="4" placeholder="例如：猫咪这两天食欲下降，还伴有轻微呕吐，该先观察什么？"></textarea>
          <button @click="sendQuestion">发送问题</button>
        </footer>
      </section>
    </section>
  </AppShell>
</template>

<style scoped>
.medical-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

.doctor-panel,
.conversation-panel {
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.doctor-panel {
  padding: 18px;
}

.panel-head,
.conversation-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h2,
.conversation-head h2 {
  margin: 0;
}

.panel-head span,
.conversation-head p,
.doctor-card p,
.doctor-card small {
  color: var(--ink-soft);
}

.doctor-card {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 14px;
  width: 100%;
  padding: 14px;
  margin-top: 14px;
  border-radius: 22px;
  text-align: left;
  background: rgba(255, 248, 243, 0.9);
}

.doctor-card.active {
  background: linear-gradient(135deg, rgba(255, 143, 177, 0.2), rgba(255, 208, 133, 0.26));
}

.doctor-card img {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
}

.conversation-panel {
  padding: 22px;
  display: grid;
  gap: 18px;
}

.status {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(113, 220, 171, 0.16);
  color: #1f9665;
}

.status.offline {
  background: rgba(170, 170, 170, 0.14);
  color: #6f7175;
}

.message-list {
  min-height: 420px;
  max-height: 420px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 8px;
}

.message {
  max-width: 78%;
  padding: 14px 16px;
  border-radius: 22px;
  line-height: 1.75;
}

.message.agent {
  align-self: flex-start;
  background: rgba(255, 248, 243, 0.95);
}

.message.user {
  align-self: flex-end;
  background: linear-gradient(135deg, #ff91b2, #ffcf85);
  color: #fff;
}

.composer {
  display: grid;
  gap: 12px;
}

.composer textarea {
  width: 100%;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid var(--line-soft);
}

.composer button {
  justify-self: end;
  padding: 12px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: #fff;
}

@media (max-width: 960px) {
  .medical-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .conversation-head {
    flex-direction: column;
  }

  .message {
    max-width: 100%;
  }
}
</style>