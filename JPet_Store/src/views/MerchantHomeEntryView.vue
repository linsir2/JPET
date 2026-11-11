<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import IndexView from '@/views/IndexView.vue'
import MerchantWorkbenchView from '@/views/MerchantWorkbenchView.vue'

const auth = useAuthStore()

const activeComponent = computed(() => (auth.canAccessMerchant ? IndexView : MerchantWorkbenchView))
</script>

<template>
  <component :is="activeComponent" />
</template>