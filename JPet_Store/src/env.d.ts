/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

declare global {
  interface Window {
    __CONTEXT_PATH__?: string
    contextPath?: string
    currentUser?: { id: number; username: string; role: string | null } | null
  }
}

interface ImportMetaEnv {
  readonly VITE_CONTEXT_PATH?: string
}

export {}
