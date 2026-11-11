import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * SPA 内「暂时下架」演示状态：接口重新拉取会覆盖商品对象，用 id 记录下架，加载列表后再写回 status。
 */
/** 列表行/商品对象上可能出现的 id 字段（与后端 JSON 对齐） */
function rowIdKey(row: unknown): string | null {
  if (!row || typeof row !== 'object') return null
  const r = row as Record<string, unknown>
  const raw = r.id ?? r.spuId ?? r.productId
  if (raw == null || raw === '') return null
  return String(raw)
}

export const useLocalOffShelfStore = defineStore('localOffShelf', () => {
  const byId = ref<Record<string, boolean>>({})
  const snapshots = ref<Record<string, Record<string, unknown>>>({})

  function mark(id: string | number | undefined | null, product?: Record<string, unknown> | null) {
    if (id == null) return
    const k = String(id)
    byId.value = { ...byId.value, [k]: true }
    if (product && typeof product === 'object') {
      snapshots.value = { ...snapshots.value, [k]: { ...product } }
    }
  }

  function unmark(id: string | number | undefined | null) {
    if (id == null) return
    const k = String(id)
    if (!byId.value[k]) return
    const b = { ...byId.value }
    delete b[k]
    byId.value = b
    const s = { ...snapshots.value }
    delete s[k]
    snapshots.value = s
  }

  function has(id: string | number | undefined | null) {
    if (id == null) return false
    return Boolean(byId.value[String(id)])
  }

  function applyToProducts(list: unknown[] | undefined) {
    if (!Array.isArray(list)) return
    for (const p of list as Array<Record<string, unknown>>) {
      if (!p) continue
      const k = rowIdKey(p)
      if (k && has(k)) {
        p.status = '已下架'
      }
    }
  }

  /** 商品 Tab 列表用：与 mark 时 id 一致即可命中（不依赖仅 status 字段） */
  function isRowMarkedOffShelf(row: unknown): boolean {
    const k = rowIdKey(row)
    return k ? has(k) : false
  }

  function getSnapshot(id: string | number) {
    return snapshots.value[String(id)]
  }

  return { byId, snapshots, mark, unmark, has, applyToProducts, getSnapshot, isRowMarkedOffShelf, rowIdKey }
})
