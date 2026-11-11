import { getImagePath } from './imagePath'

/** 自 static/js/cat-cursor.js 迁移 */
export function initCatCursor() {
  const cursorImage = getImagePath('images/cat_transparent.gif')
  const catElement = document.createElement('div')
  catElement.id = 'followCat'
  catElement.style.cssText = `
        width: 50px;
        height: 50px;
        position: fixed;
        background-image: url('${cursorImage}');
        background-size: cover;
        background-repeat: no-repeat;
        pointer-events: none;
        z-index: 99999;
        transition: transform 0.1s ease-out;
    `

  const append = () => document.body.appendChild(catElement)
  if (document.body) append()
  else document.addEventListener('DOMContentLoaded', append)

  let deg = 0
  let y = 0
  let imgx = 0
  let imgy = 0
  let imgl = 0
  let imgt = 0
  let index = 0
  let animationFrameId = null

  function handleMouseMove(event) {
    const img = catElement
    if (!img) return
    imgx = event.clientX - img.offsetLeft - img.clientWidth / 2
    imgy = event.clientY - img.offsetTop - img.clientHeight / 2
    deg = (360 * Math.atan(imgy / imgx)) / (2 * Math.PI)
    index = 0
    if (img.offsetLeft < event.clientX) {
      y = -180
    } else {
      y = 0
    }
  }

  function animate() {
    const img = catElement
    if (!img) return
    img.style.transform = `rotateZ(${deg}deg) rotateY(${y}deg)`
    if (index < 50) {
      imgl += imgx / 50
      imgt += imgy / 50
      index++
    }
    img.style.left = `${imgl}px`
    img.style.top = `${imgt}px`
    animationFrameId = requestAnimationFrame(animate)
  }

  window.addEventListener('mousemove', handleMouseMove)
  animationFrameId = requestAnimationFrame(animate)

  window.addEventListener('beforeunload', () => {
    window.removeEventListener('mousemove', handleMouseMove)
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
  })
}
