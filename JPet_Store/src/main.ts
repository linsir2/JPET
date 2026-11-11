import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './buyer/styles/theme.css'
import App from './App.vue'
import router from './router'
import { setContextPath } from './utils/context'

setContextPath(import.meta.env.VITE_CONTEXT_PATH ?? '')

if ('serviceWorker' in navigator && import.meta.env.PROD) {
	window.addEventListener('load', () => {
		navigator.serviceWorker.register('/spa/sw.js').catch((error) => {
			console.warn('service worker register failed', error)
		})
	})
}

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
