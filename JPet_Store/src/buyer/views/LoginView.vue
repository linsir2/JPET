<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const router = useRouter();
const route = useRoute();

onMounted(() => {
  router.replace({
    path: '/login',
    query: {
      ...route.query,
      tab: 'buyer'
    }
  });
});
</script>

<template>
  <div></div>
</template>