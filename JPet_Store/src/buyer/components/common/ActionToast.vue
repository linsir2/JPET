<script setup lang="ts">
defineProps<{
  visible: boolean;
  message: string;
}>();
</script>

<template>
  <Transition name="toast-fade">
    <div v-if="visible && message" class="action-toast" role="status" aria-live="polite">
      <span class="toast-icon">✓</span>
      <span class="toast-message">{{ message }}</span>
    </div>
  </Transition>
</template>

<style scoped>
.action-toast {
  position: fixed;
  top: 88px;
  right: 24px;
  z-index: 80;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  max-width: min(420px, calc(100vw - 32px));
  padding: 14px 18px;
  border-radius: 18px;
  background: rgba(26, 42, 34, 0.92);
  color: #fffdf8;
  box-shadow: 0 18px 40px rgba(21, 30, 26, 0.22);
  backdrop-filter: blur(10px);
}

.toast-icon {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(98, 214, 138, 0.22);
  color: #8ff0a4;
  font-weight: 800;
}

.toast-message {
  line-height: 1.5;
  font-size: 14px;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 640px) {
  .action-toast {
    top: 76px;
    right: 16px;
    left: 16px;
    max-width: none;
  }
}
</style>