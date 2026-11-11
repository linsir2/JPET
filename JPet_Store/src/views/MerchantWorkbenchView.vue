<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const disabledFeatures = [
  { title: '商品管理', description: '提交商家入驻审核通过后，才可上架、编辑和管理商品。' },
  { title: '物流管理', description: '审核通过后可处理订单发货、物流单号和配送进度。' },
  { title: '商品审核', description: '正式商家才能提交商品审核与查看审核状态。' },
  { title: '经营数据', description: '营业额、评分、订单趋势等核心数据在商家权限开通后生效。' },
]

const pageNotice = computed(() => {
  if (typeof route.query.err === 'string' && route.query.err.trim()) {
    return route.query.err
  }
  return '当前账号已进入商家后台预览态。完成商家注册并通过管理员审核后，商家功能会自动解锁。'
})

function goToApplyPage() {
  router.push({
    path: '/profile',
    query: {
      openApply: String(Date.now()),
    },
  })
}
</script>

<template>
  <section class="merchant-workbench">
    <div class="hero-card">
      <p class="eyebrow">商家工作台</p>
      <h1>{{ auth.user?.username || '当前账号' }}，你现在看到的是商家后台预览界面</h1>
      <p class="hero-text">
        你可以先浏览商家后台结构，但商品上架、物流处理、经营数据等功能目前保持禁用。
        点击右上角或下方按钮提交商家注册申请，等待管理员审核通过后即可正式使用。
      </p>
      <div class="hero-actions">
        <button type="button" class="primary-btn" @click="goToApplyPage">注册成为商家</button>
        <span class="status-pill">当前身份：普通用户</span>
      </div>
    </div>

    <div class="notice-card">
      {{ pageNotice }}
    </div>

    <div class="feature-grid">
      <article v-for="item in disabledFeatures" :key="item.title" class="feature-card is-disabled">
        <div class="feature-head">
          <span class="lock-badge">未开通</span>
          <h3>{{ item.title }}</h3>
        </div>
        <p>{{ item.description }}</p>
        <button type="button" class="secondary-btn" disabled>审核通过后开放</button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.merchant-workbench {
  display: grid;
  gap: 20px;
}

.hero-card,
.notice-card,
.feature-card {
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.hero-card {
  padding: 28px;
  background: linear-gradient(135deg, #fff4e8 0%, #ffffff 55%, #fff9f1 100%);
  border: 1px solid rgba(255, 166, 77, 0.28);
}

.eyebrow {
  margin: 0 0 10px;
  color: #d97706;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hero-card h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
  color: #1f2937;
}

.hero-text {
  margin: 14px 0 0;
  max-width: 760px;
  color: #4b5563;
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 22px;
  flex-wrap: wrap;
}

.primary-btn,
.secondary-btn {
  border: none;
  border-radius: 999px;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 700;
}

.primary-btn {
  background: linear-gradient(135deg, #ffb648 0%, #ff8a3d 100%);
  color: #fff;
  cursor: pointer;
}

.secondary-btn {
  background: #eef2f7;
  color: #98a2b3;
}

.secondary-btn:disabled {
  cursor: not-allowed;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 13px;
  font-weight: 600;
}

.notice-card {
  padding: 16px 20px;
  border: 1px solid rgba(245, 158, 11, 0.28);
  color: #9a6700;
  background: #fffaf0;
  font-size: 14px;
  line-height: 1.7;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}

.feature-card {
  padding: 22px;
  border: 1px solid #edf2f7;
}

.feature-card.is-disabled {
  opacity: 0.92;
}

.feature-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feature-head h3 {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
}

.lock-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 10px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
}

.feature-card p {
  margin: 14px 0 18px;
  color: #6b7280;
  line-height: 1.7;
  min-height: 72px;
}

@media (max-width: 768px) {
  .hero-card {
    padding: 22px;
  }

  .hero-card h1 {
    font-size: 24px;
  }
}
</style>