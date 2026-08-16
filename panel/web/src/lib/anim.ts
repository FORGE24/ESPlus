// ═══════════════════════════════════════════════════════════
// ES+ Panel — Animation Utilities (GSAP + Anime.js)
// ═══════════════════════════════════════════════════════════

import gsap from 'gsap'
import anime from 'animejs'

/** Stagger reveal children of a container (GSAP) */
export function staggerReveal(container: HTMLElement, selector = '[data-reveal]') {
  const items = container.querySelectorAll(selector)
  if (!items.length) return
  gsap.fromTo(
    items,
    { opacity: 0, y: 20 },
    { opacity: 1, y: 0, duration: 0.5, stagger: 0.06, ease: 'power3.out', clearProps: 'all' },
  )
}

/** Animate a number counting up (Anime.js) */
export function animateCount(
  el: HTMLElement,
  to: number,
  opts: { duration?: number; decimals?: number } = {},
) {
  const { duration = 1000, decimals = 0 } = opts
  const obj = { val: 0 }
  anime({
    targets: obj,
    val: to,
    duration,
    easing: 'easeOutExpo',
    round: decimals === 0 ? 1 : Math.pow(10, decimals),
    update: () => {
      el.textContent = decimals === 0
        ? Math.round(obj.val).toLocaleString()
        : obj.val.toFixed(decimals)
    },
  })
}

/** Page enter animation — fade + slide up the main content */
export function pageEnter(el: HTMLElement) {
  gsap.fromTo(
    el,
    { opacity: 0, y: 12 },
    { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' },
  )
}

/** Card hover tilt micro-interaction (Anime.js) */
export function cardTilt(el: HTMLElement) {
  el.addEventListener('mousemove', (e) => {
    const rect = el.getBoundingClientRect()
    const x = (e.clientX - rect.left) / rect.width - 0.5
    const y = (e.clientY - rect.top) / rect.height - 0.5
    anime({
      targets: el,
      rotateY: x * 4,
      rotateX: -y * 4,
      duration: 300,
      easing: 'easeOutQuad',
    })
  })
  el.addEventListener('mouseleave', () => {
    anime({
      targets: el,
      rotateY: 0,
      rotateX: 0,
      duration: 400,
      easing: 'easeOutElastic(1, 0.5)',
    })
  })
}

/** Toast slide-in animation (GSAP) */
export function toastIn(el: HTMLElement) {
  gsap.fromTo(
    el,
    { opacity: 0, x: 40, scale: 0.9 },
    { opacity: 1, x: 0, scale: 1, duration: 0.3, ease: 'back.out(1.4)' },
  )
}

/** Pulse glow for urgent elements (GSAP) */
export function pulseGlow(el: HTMLElement, color = 'rgba(239, 68, 68, 0.5)') {
  gsap.to(el, {
    boxShadow: `0 0 20px ${color}`,
    duration: 0.8,
    repeat: -1,
    yoyo: true,
    ease: 'sine.inOut',
  })
}

/** Draw a sparkline bar chart into a container (canvas-free, div bars) with GSAP grow */
export function sparklineGrow(container: HTMLElement, values: number[], maxVal: number) {
  container.innerHTML = ''
  values.forEach((v) => {
    const bar = document.createElement('div')
    bar.style.cssText = `flex:1;min-width:2px;max-width:8px;background:linear-gradient(180deg,var(--accent-cyan),#0891b2);border-radius:2px 2px 0 0;opacity:0.85;`
    container.appendChild(bar)
    gsap.fromTo(
      bar,
      { height: 0 },
      { height: `${(v / maxVal) * 100}%`, duration: 0.6, ease: 'power2.out' },
    )
  })
}
