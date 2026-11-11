<script setup>
import { computed, onMounted, ref } from 'vue'
import { getImagePath } from '@/utils/imagePath'
import { useAuthStore } from '@/stores/auth'
import { useCartBadgeStore } from '@/stores/cartBadge'
import { merchantOrderApi } from '@/api/merchant'

import '@/styles/legacy/common.css'
import '@/styles/legacy/logistics.css'

const auth = useAuthStore()
const cartBadge = useCartBadgeStore()

const shippingTab = ref('unshipped')
const unshippedList = ref([])
const inTransitList = ref([])
const historyTab = ref('cancelled')
const cancelledList = ref([])
const arrivedList = ref([])
const selectedHistoryIds = ref([])

const detailOpen = ref(false)
const detailItem = ref(null)
const shippingActionLoading = ref(false)

const activeList = computed(() =>
  shippingTab.value === 'unshipped' ? unshippedList.value : inTransitList.value,
)

const activeHistoryList = computed(() =>
  historyTab.value === 'cancelled' ? cancelledList.value : arrivedList.value,
)

const selectedHistoryCount = computed(() => selectedHistoryIds.value.length)

function parseDateTime(raw) {
  if (!raw) return null
  const normalized = String(raw).replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function getLatestShipDate(raw) {
  const createdAt = parseDateTime(raw)
  if (!createdAt) return null
  return new Date(createdAt.getTime() + 7 * 24 * 60 * 60 * 1000)
}

function formatDateTime(raw) {
  const date = raw instanceof Date ? raw : parseDateTime(raw)
  if (!date) return '-'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function getLatestShipText(raw) {
  const latestShipDate = getLatestShipDate(raw)
  return latestShipDate ? formatDateTime(latestShipDate) : '-'
}

function fallbackText(value, fallback = '暂无信息') {
  const normalized = String(value ?? '').trim()
  return normalized || fallback
}

function resolveDestination(item) {
  if (item?.destination) return item.destination
  if (item?.address) {
    return String(item.address).split(/[ ,，]/)[0] || '待确认地址'
  }
  return '待确认地址'
}

function resolveProgress(item) {
  return fallbackText(item?.progress, shippingTab.value === 'in-transit' ? '运输中' : '待发货')
}

function resolveCurrentNode(item) {
  return fallbackText(item?.currentNode, shippingTab.value === 'in-transit' ? '物流节点更新中' : '等待商家发货')
}

function resolveUpdatedTime(item) {
  return formatDateTime(item?.updatedAt || item?.finishedAt || item?.createdAt)
}

function resolveFinishedTime(item) {
  return formatDateTime(item?.finishedAt || item?.updatedAt || item?.createdAt)
}

function resolveMapPercent(value, fallback) {
  const numeric = Number(value)
  if (Number.isFinite(numeric) && numeric >= 0 && numeric <= 100) return numeric
  return fallback
}

function isUrgentShipDeadline(raw) {
  const latestShipDate = getLatestShipDate(raw)
  if (!latestShipDate) return false
  return latestShipDate.getTime() - Date.now() < 3 * 24 * 60 * 60 * 1000
}

function maskName(name) {
  if (!name) return '***'
  return `${name[0]}**`
}

function maskPhone(phone) {
  if (!phone || phone.length < 7) return '***'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function openDetail(item) {
  detailItem.value = item
  detailOpen.value = true
}

function closeDetail() {
  detailOpen.value = false
  detailItem.value = null
}

async function markDetailItemShipped() {
  if (!detailItem.value?.id || shippingActionLoading.value) return
  shippingActionLoading.value = true
  try {
    const updated = await merchantOrderApi.markShipped(detailItem.value.id)
    unshippedList.value = unshippedList.value.filter((item) => Number(item.id) !== Number(updated.id))
    inTransitList.value = [updated, ...inTransitList.value.filter((item) => Number(item.id) !== Number(updated.id))]
    detailItem.value = updated
    shippingTab.value = 'in-transit'
    detailOpen.value = false
  } finally {
    shippingActionLoading.value = false
  }
}

function toggleHistorySelect(id, checked) {
  if (checked) {
    if (!selectedHistoryIds.value.includes(id)) selectedHistoryIds.value.push(id)
    return
  }
  selectedHistoryIds.value = selectedHistoryIds.value.filter((x) => x !== id)
}

function clearInvalidSelections() {
  const validIds = new Set(activeHistoryList.value.map((x) => Number(x.id)))
  selectedHistoryIds.value = selectedHistoryIds.value.filter((id) => validIds.has(id))
}

function switchHistoryTab(tab) {
  historyTab.value = tab
  clearInvalidSelections()
}

function deleteSelectedHistory() {
  if (!selectedHistoryIds.value.length) return
  const ids = new Set(selectedHistoryIds.value)
  if (historyTab.value === 'cancelled') {
    cancelledList.value = cancelledList.value.filter((x) => !ids.has(Number(x.id)))
  } else {
    arrivedList.value = arrivedList.value.filter((x) => !ids.has(Number(x.id)))
  }
  selectedHistoryIds.value = []
}

function onHistoryCheckboxChange(id, event) {
  toggleHistorySelect(id, event.target.checked)
}

onMounted(async () => {
  await auth.refresh()
  await cartBadge.refresh()
  try {
    const unshippedRes = await merchantOrderApi.list({ status: '待发货' })
    const inTransitRes = await merchantOrderApi.list({ status: '已发货' })
    const cancelledRes = await merchantOrderApi.listHistory({ status: '已取消' })
    const arrivedRes = await merchantOrderApi.listHistory({ status: '已完成' })
    unshippedList.value = unshippedRes.list
    inTransitList.value = inTransitRes.list
    cancelledList.value = cancelledRes.list
    arrivedList.value = arrivedRes.list
  } catch {
    unshippedList.value = []
    inTransitList.value = []
    cancelledList.value = []
    arrivedList.value = []
  }
})
</script>

<template>
  <div class="App">

    <div class="logistics-page">
      <section class="logistics-main-module">
        <div class="module-head">
          <h2>✅ 待处理物流订单</h2>
          <div class="mini-tabs">
            <button
              type="button"
              class="mini-tab-btn"
              :class="{ active: shippingTab === 'unshipped' }"
              @click="shippingTab = 'unshipped'"
            >
              未寄出商品
            </button>
            <button
              type="button"
              class="mini-tab-btn"
              :class="{ active: shippingTab === 'in-transit' }"
              @click="shippingTab = 'in-transit'"
            >
              在途商品
            </button>
          </div>
        </div>

        <div class="shipping-grid">
          <div v-if="!activeList.length" class="empty-tip">暂无相关商品</div>
          <div v-for="item in activeList" v-else :key="`${shippingTab}-${item.id}`" class="ship-card" @click="openDetail(item)">
            <img :src="getImagePath(item.image)" :alt="item.name" class="ship-image" />
            <div class="ship-info">
              <h3>{{ item.name }}</h3>
              <p>目的地：{{ resolveDestination(item) }}</p>
              <p v-if="shippingTab === 'unshipped'">
                最晚发货时间：
                <span :class="{ 'ship-deadline-urgent': isUrgentShipDeadline(item.createdAt) }">
                  {{ getLatestShipText(item.createdAt) }}
                </span>
              </p>
              <p v-else>当前节点：{{ resolveCurrentNode(item) }}</p>
              <div class="ship-price">¥{{ item.amount.toFixed(2) }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="logistics-history-module">
        <div class="module-head">
          <h2>📚 历史物流订单</h2>
          <div class="mini-tabs">
            <button
              type="button"
              class="mini-tab-btn"
              :class="{ active: historyTab === 'cancelled' }"
              @click="switchHistoryTab('cancelled')"
            >
              已取消
            </button>
            <button
              type="button"
              class="mini-tab-btn"
              :class="{ active: historyTab === 'arrived' }"
              @click="switchHistoryTab('arrived')"
            >
              已到达
            </button>
          </div>
        </div>

        <div class="history-body">
          <div class="history-list">
            <div v-if="!activeHistoryList.length" class="empty-tip">暂无相关记录</div>
            <label v-for="item in activeHistoryList" v-else :key="`h-${item.id}`" class="history-item">
              <input
                type="checkbox"
                class="history-checkbox"
                :checked="selectedHistoryIds.includes(item.id)"
                @change="onHistoryCheckboxChange(item.id, $event)"
              />
              <img :src="getImagePath(item.image)" :alt="item.name" class="history-image" />
              <div class="history-info">
                <h4>{{ item.name }}</h4>
                <p v-if="historyTab === 'cancelled'">取消原因：{{ item.reason }}</p>
                <p v-else>签收人：{{ fallbackText(item.receiver || item.receiverName, '待确认签收人') }}</p>
                <p>完成时间：{{ resolveFinishedTime(item) }}</p>
                <div v-if="historyTab === 'arrived' && item.reviewContent" class="history-review-box">
                  <strong>买家评价</strong>
                  <p>评分：{{ item.reviewRating || 0 }} 星</p>
                  <p>用户：{{ item.reviewerName || '匿名用户' }}</p>
                  <p>{{ item.reviewContent }}</p>
                  <small>{{ item.reviewTime }}</small>
                </div>
              </div>
            </label>
          </div>

          <aside class="history-side">
            <div class="side-count">已勾选：{{ selectedHistoryCount }} 件</div>
            <button
              type="button"
              class="history-delete-btn"
              :disabled="selectedHistoryCount === 0"
              @click="deleteSelectedHistory"
            >
              删除
            </button>
          </aside>
        </div>
      </section>
    </div>

    <div v-if="detailOpen && detailItem" class="logistics-detail-overlay" @click="closeDetail">
      <div class="logistics-detail-modal" @click.stop>
        <button type="button" class="detail-close-btn" @click="closeDetail">✕</button>
        <h3>{{ shippingTab === 'unshipped' ? '未寄出商品详情' : '在途商品物流详情' }}</h3>

        <template v-if="shippingTab === 'unshipped'">
          <div class="detail-basic">
            <img :src="getImagePath(detailItem.image)" :alt="detailItem.name" />
            <div>
              <p><strong>商品：</strong>{{ detailItem.name }}</p>
              <p><strong>选购属性：</strong>{{ detailItem.skuSummary || '默认规格' }}</p>
              <p><strong>购买数量：</strong>{{ detailItem.quantity || 1 }}</p>
              <p><strong>单价：</strong>¥{{ Number(detailItem.unitPrice || 0).toFixed(2) }}</p>
              <p><strong>目的地：</strong>{{ detailItem.destination }}</p>
              <p><strong>收件人：</strong>{{ maskName(detailItem.receiverName) }}</p>
              <p><strong>联系方式：</strong>{{ maskPhone(detailItem.receiverPhone) }}</p>
              <p><strong>详细地址：</strong>{{ detailItem.address }}</p>
              <p>
                <strong>最晚发货时间：</strong>
                <span :class="{ 'ship-deadline-urgent': isUrgentShipDeadline(detailItem.createdAt) }">
                  {{ getLatestShipText(detailItem.createdAt) }}
                </span>
              </p>
              <div class="detail-actions">
                <button type="button" class="ship-action-btn" :disabled="shippingActionLoading" @click="markDetailItemShipped">
                  {{ shippingActionLoading ? '更新中...' : '标记为已寄出' }}
                </button>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="logistics-map">
            <div class="map-grid"></div>
            <div class="route-line"></div>
            <div class="package-marker" :style="{ left: `${resolveMapPercent(detailItem.mapX, 56)}%`, top: `${resolveMapPercent(detailItem.mapY, 50)}%` }">📦</div>
            <div class="start-marker">仓库</div>
            <div class="end-marker">目的地</div>
          </div>
          <div class="detail-transport">
            <p><strong>运单号：</strong>{{ fallbackText(detailItem.trackingNo, '等待物流公司揽收后生成') }}</p>
            <p><strong>物流状态：</strong>{{ resolveProgress(detailItem) }}</p>
            <p><strong>当前节点：</strong>{{ resolveCurrentNode(detailItem) }}</p>
            <p><strong>选购属性：</strong>{{ detailItem.skuSummary || '默认规格' }}</p>
            <p><strong>购买数量：</strong>{{ detailItem.quantity || 1 }}</p>
            <p><strong>最新时间：</strong>{{ resolveUpdatedTime(detailItem) }}</p>
            <p><strong>收件人：</strong>{{ maskName(detailItem.receiverName) }}（{{ maskPhone(detailItem.receiverPhone) }}）</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
