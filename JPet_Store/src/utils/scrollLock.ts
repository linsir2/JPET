/** 与旧站 common.js 中 lockBodyScroll / unlockBodyScroll 一致 */
let modalOpenCount = 0

export function lockBodyScroll() {
  if (typeof document === 'undefined') return

  modalOpenCount += 1
  if (modalOpenCount === 1) {
    const scrollbarOffset = window.innerWidth - document.documentElement.clientWidth
    document.body.dataset.originalPaddingRight = document.body.style.paddingRight || ''
    if (scrollbarOffset > 0) {
      document.body.style.paddingRight = `${scrollbarOffset}px`
    }
    document.body.classList.add('modal-open')
  }
}

export function unlockBodyScroll() {
  if (typeof document === 'undefined') return

  if (modalOpenCount > 0) {
    modalOpenCount -= 1
  }

  if (modalOpenCount === 0) {
    document.body.classList.remove('modal-open')
    const originalPaddingRight = document.body.dataset.originalPaddingRight
    if (typeof originalPaddingRight !== 'undefined') {
      document.body.style.paddingRight = originalPaddingRight
      delete document.body.dataset.originalPaddingRight
    } else {
      document.body.style.paddingRight = ''
    }
  }
}
