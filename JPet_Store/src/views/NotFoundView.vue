<script setup>
import { getContextPath } from '@/utils/context'

const ctx = getContextPath()
</script>

<template>
  <div style="padding: 40px; text-align: center; font-family: system-ui, sans-serif">
    <h1>404</h1>
    <p>页面未找到</p>
    <a :href="ctx + '/index'" style="color: #2563eb">返回首页</a>
  </div>
</template>
