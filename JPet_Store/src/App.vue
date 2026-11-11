<template>
  <RouterView />
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { initCatCursor } from '@/utils/initCatCursor'

const route = useRoute()

function syncTheme(segment) {
  document.body.classList.toggle('buyer-theme', segment === 'buyer')
}

onMounted(() => {
  initCatCursor()
})

watch(
  () => route.meta.segment,
  (segment) => {
    syncTheme(segment)
  },
  { immediate: true },
)

onUnmounted(() => {
  document.body.classList.remove('buyer-theme')
})
</script>
