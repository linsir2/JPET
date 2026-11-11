<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import AppShell from '@/buyer/components/layout/AppShell.vue';
import { serviceApi } from '@/buyer/api/services';
import type { BoardingStore } from '@/buyer/types/models';

const stores = ref<BoardingStore[]>([]);
const allStores = ref<BoardingStore[]>([]);
const currentCity = ref('全部');
const selectedStore = ref<BoardingStore | null>(null);
const currentImage = ref(0);
const reservation = reactive({
  petName: '',
  days: 3,
  note: ''
});
const reservationMessage = ref('');

async function reload(city = currentCity.value) {
  stores.value = await serviceApi.listBoardingStores(city);
}

onMounted(async () => {
  allStores.value = await serviceApi.listBoardingStores('全部');
  await reload();
});

const cities = computed(() => ['全部', ...new Set(allStores.value.map((store) => store.city))]);

async function selectCity(city: string) {
  currentCity.value = city;
  await reload(city);
}

async function submitReservation() {
  if (!selectedStore.value) {
    return;
  }
  const result = await serviceApi.reserveBoarding(selectedStore.value.id, reservation);
  reservationMessage.value = `预约已提交，编号 ${result.reservationNo}`;
}
</script>

<template>
  <AppShell active="boarding" title="宠物寄养" subtitle="沿用你原来的暖心寄养调性，把门店和预约流程整理成独立模块">
    <section class="city-bar">
      <button
        v-for="city in cities"
        :key="city"
        class="city-chip"
        :class="{ active: currentCity === city }"
        @click="selectCity(city)"
      >
        {{ city }}
      </button>
    </section>

    <section class="store-grid">
      <article v-for="store in stores" :key="store.id" class="store-card">
        <img :src="store.cover" :alt="store.name" />
        <div class="card-copy">
          <div class="top-row">
            <div>
              <h3>{{ store.name }}</h3>
              <p>{{ store.city }} · {{ store.area }}</p>
            </div>
            <strong>★ {{ store.rating.toFixed(1) }}</strong>
          </div>
          <p>{{ store.description }}</p>
          <div class="tag-row">
            <span v-for="service in store.services.slice(0, 3)" :key="service">{{ service }}</span>
          </div>
          <div class="bottom-row">
            <div>
              <small>价格参考</small>
              <strong>{{ store.priceText }}</strong>
            </div>
            <button @click="selectedStore = store; currentImage = 0; reservationMessage = ''">查看详情</button>
          </div>
        </div>
      </article>
    </section>

    <teleport to="body">
      <div v-if="selectedStore" class="modal-overlay" @click.self="selectedStore = null">
        <div class="detail-panel">
          <button class="close-btn" @click="selectedStore = null">✕</button>
          <div class="detail-grid">
            <div class="gallery">
              <img :src="selectedStore.gallery[currentImage]" :alt="selectedStore.name" />
              <div class="gallery-actions">
                <button @click="currentImage = (currentImage - 1 + selectedStore.gallery.length) % selectedStore.gallery.length">‹</button>
                <button @click="currentImage = (currentImage + 1) % selectedStore.gallery.length">›</button>
              </div>
            </div>

            <div class="detail-copy">
              <span class="city-pill">{{ selectedStore.city }} · {{ selectedStore.area }}</span>
              <h2>{{ selectedStore.name }}</h2>
              <p>{{ selectedStore.description }}</p>
              <div class="service-list">
                <span v-for="service in selectedStore.services" :key="service">{{ service }}</span>
              </div>
              <div class="detail-meta">
                <div>
                  <small>联系电话</small>
                  <strong>{{ selectedStore.contact }}</strong>
                </div>
                <div>
                  <small>价格参考</small>
                  <strong>{{ selectedStore.priceText }}</strong>
                </div>
              </div>

              <div class="reservation-form">
                <h3>在线预约</h3>
                <label><span>宠物名字</span><input v-model="reservation.petName" type="text" /></label>
                <label><span>寄养天数</span><input v-model.number="reservation.days" type="number" min="1" /></label>
                <label><span>备注说明</span><textarea v-model="reservation.note" rows="3"></textarea></label>
                <p v-if="reservationMessage" class="success">{{ reservationMessage }}</p>
                <button class="reserve-btn" @click="submitReservation">提交预约</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </teleport>
  </AppShell>
</template>

<style scoped>
.city-bar,
.detail-panel {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.city-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px;
}

.city-chip {
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
}

.city-chip.active,
.reserve-btn,
.bottom-row button {
  background: linear-gradient(135deg, #ff8fb1, #ffb873);
  color: white;
}

.store-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.store-card {
  border-radius: 30px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.store-card img {
  width: 100%;
  height: 220px;
  object-fit: cover;
}

.card-copy {
  padding: 18px;
}

.top-row,
.bottom-row,
.detail-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.top-row h3,
.detail-copy h2,
.reservation-form h3 {
  margin: 0;
}

.card-copy p,
.detail-copy p,
.top-row p,
.success {
  color: var(--ink-soft);
}

.tag-row,
.service-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.tag-row span,
.service-list span,
.city-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 248, 243, 0.92);
}

.bottom-row {
  margin-top: 18px;
}

.bottom-row small,
.detail-meta small {
  display: block;
  color: var(--ink-soft);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(61, 43, 47, 0.4);
  backdrop-filter: blur(12px);
  z-index: 20;
}

.detail-panel {
  position: relative;
  width: min(980px, 100%);
  padding: 24px;
}

.close-btn {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.gallery {
  position: relative;
  border-radius: 24px;
  overflow: hidden;
  min-height: 380px;
}

.gallery img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-actions {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 16px;
  display: flex;
  justify-content: space-between;
}

.gallery-actions button {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.82);
}

.detail-copy {
  display: grid;
  gap: 16px;
}

.reservation-form {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 248, 243, 0.92);
}

.reservation-form label {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.reservation-form input,
.reservation-form textarea {
  width: 100%;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid var(--line-soft);
}

.reserve-btn {
  margin-top: 16px;
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
}

@media (max-width: 1080px) {
  .store-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .store-grid {
    grid-template-columns: 1fr;
  }
}
</style>