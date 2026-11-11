<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { profileApi } from '@/buyer/api/services';
import { useAppStore } from '@/buyer/stores/app';
import type { UserProfile } from '@/buyer/types/models';

const router = useRouter();
const store = useAppStore();

const profile = ref<UserProfile | null>(null);
const editing = ref(false);
const saveMessage = ref('');
const form = reactive({
  username: '',
  email: '',
  phone: '',
  intro: '',
  address: '',
  preferences: ''
});

const menuItems = [
  { label: '我的订单', path: '/buyer/orders' },
  { label: '我的收藏', path: '/buyer/favorites' },
  { label: '收货地址', path: '/buyer/addresses' },
  { label: '优惠券', path: '/buyer/coupons' },
  { label: '设置', path: '/buyer/settings' },
  { label: '帮助中心', path: '/buyer/help' },
];

function openService(item: { label: string; path?: string }) {
  if (item.path) {
    router.push(item.path);
  }
}

function syncForm(next: UserProfile) {
  form.username = next.username;
  form.email = next.email;
  form.phone = next.phone;
  form.intro = next.intro;
  form.address = next.address;
  form.preferences = next.preferences.join('、');
}

onMounted(async () => {
  profile.value = await profileApi.getProfile();
  if (profile.value) {
    syncForm(profile.value);
  }
});

async function saveProfile() {
  profile.value = await profileApi.updateProfile({
    username: form.username,
    email: form.email,
    phone: form.phone,
    intro: form.intro,
    address: form.address,
    preferences: form.preferences.split(/[、,，]/).map((item) => item.trim()).filter(Boolean)
  });
  await store.refreshSession();
  editing.value = false;
  saveMessage.value = '资料已保存。';
}

async function logout() {
  await store.logout();
}
</script>

<template>
  <AppShell active="profile" title="个人中心" subtitle="保留原来的‘我的’页面结构，但把资料编辑和状态同步整理成前端模块">
    <section v-if="profile" class="profile-layout">
      <div class="profile-card">
        <div class="profile-head">
          <div class="avatar-block">
            <img :src="profile.avatar" :alt="profile.username" />
            <div>
              <h2>{{ profile.username }}</h2>
              <p>注册于 {{ profile.joinedAt }}</p>
            </div>
          </div>
          <button class="edit-btn" @click="editing = !editing">{{ editing ? '取消编辑' : '编辑资料' }}</button>
        </div>

        <p v-if="saveMessage" class="save-message">{{ saveMessage }}</p>

        <div class="info-grid">
          <label>
            <span>用户名</span>
            <input v-model="form.username" :disabled="!editing" type="text" />
          </label>
          <label>
            <span>邮箱</span>
            <input v-model="form.email" :disabled="!editing" type="email" />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="form.phone" :disabled="!editing" type="tel" />
          </label>
          <label>
            <span>偏好标签</span>
            <input v-model="form.preferences" :disabled="!editing" type="text" />
          </label>
          <label class="full">
            <span>个人简介</span>
            <textarea v-model="form.intro" :disabled="!editing" rows="4"></textarea>
          </label>
          <label class="full">
            <span>收货地址</span>
            <textarea v-model="form.address" :disabled="!editing" rows="3"></textarea>
          </label>
        </div>

        <div class="footer-actions">
          <button v-if="editing" class="save-btn" @click="saveProfile">保存资料</button>
          <button class="ghost-btn" @click="logout">退出登录</button>
        </div>
      </div>

      <aside class="menu-card">
        <h3>我的服务</h3>
        <button
          v-for="item in menuItems"
          :key="item.label"
          class="menu-item"
          :class="{ actionable: Boolean(item.path) }"
          @click="openService(item)"
        >
          <span>{{ item.label }}</span>
          <small v-if="item.path">进入</small>
        </button>
      </aside>
    </section>
  </AppShell>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 18px;
}

.profile-card,
.menu-card {
  min-width: 0;
  border-radius: 30px;
  padding: 22px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.profile-head,
.avatar-block,
.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.avatar-block img {
  width: 86px;
  height: 86px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-block h2,
.menu-card h3 {
  margin: 0;
}

.avatar-block p,
.save-message,
.menu-item small,
label span {
  color: var(--ink-soft);
}

.edit-btn,
.save-btn,
.ghost-btn {
  padding: 12px 18px;
  border-radius: 999px;
}

.edit-btn,
.save-btn {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.72);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

label {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.full {
  grid-column: 1 / -1;
}

input,
textarea {
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 250, 246, 0.9);
}

textarea {
  resize: vertical;
}

input:disabled,
textarea:disabled {
  opacity: 0.88;
}

.footer-actions {
  justify-content: flex-end;
  margin-top: 18px;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 16px;
  margin-top: 12px;
  border-radius: 18px;
  background: rgba(255, 248, 243, 0.92);
}

.menu-item.actionable {
  cursor: pointer;
}

@media (max-width: 980px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .profile-head,
  .avatar-block,
  .footer-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>