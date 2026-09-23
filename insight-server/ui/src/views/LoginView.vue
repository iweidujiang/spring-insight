<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '../services/AuthService'
import InsightMark from '../components/InsightMark.vue'

const router = useRouter()
const route = useRoute()
const username = ref('admin')
const password = ref('')
const error = ref('')
const loading = ref(false)

/** 背景拓扑节点：在视口内缓慢漂移 */
type DriftNode = {
  id: number
  x: number
  y: number
  r: number
  core?: boolean
  vx: number
  vy: number
}

const nodes = ref<DriftNode[]>([
  { id: 0, x: 180, y: 160, r: 9, vx: 0.22, vy: 0.14 },
  { id: 1, x: 360, y: 460, r: 8, vx: -0.16, vy: 0.18 },
  { id: 2, x: 760, y: 300, r: 13, core: true, vx: 0.12, vy: -0.15 },
  { id: 3, x: 1120, y: 140, r: 9, vx: -0.18, vy: 0.12 },
  { id: 4, x: 1320, y: 420, r: 9, vx: 0.14, vy: -0.17 },
  { id: 5, x: 980, y: 640, r: 8, vx: -0.13, vy: -0.11 },
  { id: 6, x: 260, y: 700, r: 8, vx: 0.17, vy: -0.14 }
])

const edges = [
  [0, 2],
  [1, 2],
  [2, 3],
  [2, 4],
  [2, 5],
  [1, 5],
  [4, 5],
  [3, 4],
  [0, 1],
  [5, 6]
]

const W = 1600
const H = 900

const edgePaths = computed(() =>
  edges.map(([a, b], index) => {
    const from = nodes.value[a]
    const to = nodes.value[b]
    const mx = (from.x + to.x) / 2
    const my = (from.y + to.y) / 2
    const bend = index % 2 === 0 ? 48 : -48
    const cx = mx + (to.y - from.y) * 0.08 + bend * 0.2
    const cy = my - (to.x - from.x) * 0.08 + bend * 0.12
    return `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}`
  })
)

let raf = 0
let lastTs = 0

function tick(ts: number) {
  if (!lastTs) lastTs = ts
  const dt = Math.min(32, ts - lastTs) / 16.67
  lastTs = ts

  nodes.value = nodes.value.map((node) => {
    let { x, y, vx, vy } = node
    x += vx * dt
    y += vy * dt
    if (x < 80 || x > W - 80) {
      vx *= -1
      x = Math.min(W - 80, Math.max(80, x))
      vy += (Math.random() - 0.5) * 0.05
    }
    if (y < 70 || y > H - 70) {
      vy *= -1
      y = Math.min(H - 70, Math.max(70, y))
      vx += (Math.random() - 0.5) * 0.05
    }
    const speed = Math.hypot(vx, vy)
    if (speed > 0.35) {
      vx *= 0.35 / speed
      vy *= 0.35 / speed
    }
    return { ...node, x, y, vx, vy }
  })

  raf = requestAnimationFrame(tick)
}

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  raf = requestAnimationFrame(tick)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
})

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await login(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/'
    await router.replace(redirect)
  } catch (e: any) {
    error.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="si-login">
    <div class="si-login__stage" aria-hidden="true">
      <div class="si-login__wash"></div>
      <div class="si-login__grid"></div>
      <div class="si-login__orb si-login__orb--a"></div>
      <div class="si-login__orb si-login__orb--b"></div>
      <div class="si-login__orb si-login__orb--c"></div>

      <svg class="si-login__graph" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice">
        <defs>
          <linearGradient id="siEdgeGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#2dd4bf" stop-opacity="0.05" />
            <stop offset="45%" stop-color="#5eead4" stop-opacity="0.75" />
            <stop offset="100%" stop-color="#99f6e4" stop-opacity="0.08" />
          </linearGradient>
        </defs>
        <path
          v-for="(d, i) in edgePaths"
          :key="'e-' + i"
          class="si-login__edge"
          :class="{ 'si-login__edge--slow': i % 2 === 1 }"
          :d="d"
        />
        <g v-for="node in nodes" :key="node.id">
          <circle
            class="si-login__halo"
            :class="{ 'si-login__halo--core': node.core }"
            :cx="node.x"
            :cy="node.y"
            :r="node.core ? 28 : 18"
          />
          <circle
            class="si-login__node"
            :class="{ 'si-login__node--core': node.core }"
            :cx="node.x"
            :cy="node.y"
            :r="node.r"
          />
        </g>
      </svg>
    </div>

    <div class="si-login__sheet">
      <section class="si-login__brand">
        <div class="si-login__lockup">
          <div class="si-login__mark-wrap">
            <InsightMark :size="56" />
          </div>
          <div class="si-login__lockup-text">
            <p class="si-login__kicker">SPRING INSIGHT</p>
            <span>Spring Insight</span>
          </div>
        </div>
        <h1>看清每一次调用</h1>
        <p class="si-login__lead">
          轻量监测中心。服务拓扑、链路瀑布、错误与延迟放在一处，用来看清谁调了谁、慢在哪、错在哪。
        </p>
      </section>

      <section class="si-login__panel">
        <h2>登录</h2>
        <form @submit.prevent="onSubmit">
          <label>
            <span class="visually-hidden">用户名</span>
            <input v-model="username" autocomplete="username" placeholder="用户名" required />
          </label>
          <label>
            <span class="visually-hidden">密码</span>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="密码"
              required
            />
          </label>
          <p v-if="error" class="si-login__error">{{ error }}</p>
          <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.si-login {
  position: relative;
  isolation: isolate;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  overflow: hidden;
  background:
    radial-gradient(ellipse 70% 55% at 12% 18%, rgba(45, 212, 191, 0.28), transparent 58%),
    radial-gradient(ellipse 55% 50% at 88% 12%, rgba(13, 148, 136, 0.26), transparent 55%),
    radial-gradient(ellipse 60% 45% at 78% 88%, rgba(20, 83, 45, 0.35), transparent 60%),
    radial-gradient(ellipse 50% 40% at 8% 82%, rgba(15, 118, 110, 0.22), transparent 55%),
    linear-gradient(155deg, #031016 0%, #06241f 38%, #0a1a22 68%, #041014 100%);
}

.si-login__stage {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.si-login__wash {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(204, 251, 241, 0.05), transparent 42%),
    linear-gradient(90deg, rgba(15, 118, 110, 0.12), transparent 35%, rgba(45, 212, 191, 0.08) 100%);
  opacity: 0.9;
}

.si-login__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(94, 234, 212, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(94, 234, 212, 0.07) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: radial-gradient(ellipse at 50% 48%, #000 0%, transparent 82%);
  opacity: 0.85;
}

.si-login__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(8px);
  animation: siOrb 18s ease-in-out infinite alternate;
}

.si-login__orb--a {
  width: 360px;
  height: 360px;
  right: -4%;
  top: -6%;
  background: radial-gradient(circle, rgba(45, 212, 191, 0.34), transparent 68%);
}

.si-login__orb--b {
  width: 300px;
  height: 300px;
  left: -6%;
  bottom: -4%;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.28), transparent 70%);
  animation-duration: 22s;
  animation-direction: alternate-reverse;
}

.si-login__orb--c {
  width: 240px;
  height: 240px;
  left: 42%;
  top: 36%;
  background: radial-gradient(circle, rgba(94, 234, 212, 0.14), transparent 70%);
  animation-duration: 26s;
}

.si-login__graph {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.95;
}

.si-login__edge {
  fill: none;
  stroke: url(#siEdgeGrad);
  stroke-width: 1.4;
  stroke-dasharray: 7 9;
  animation: siDash 10s linear infinite;
}

.si-login__edge--slow {
  animation-duration: 16s;
  animation-direction: reverse;
  opacity: 0.85;
}

.si-login__halo {
  fill: rgba(45, 212, 191, 0.12);
}

.si-login__halo--core {
  fill: rgba(94, 234, 212, 0.18);
}

.si-login__node {
  fill: #042f2e;
  stroke: #5eead4;
  stroke-width: 2.2;
  filter: drop-shadow(0 0 8px rgba(45, 212, 191, 0.85));
}

.si-login__node--core {
  fill: #0f766e;
  stroke: #ccfbf1;
  stroke-width: 2.6;
}

.si-login__sheet {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(240px, 0.92fr) minmax(280px, 1.08fr);
  width: min(860px, 100%);
  min-height: 460px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(94, 234, 212, 0.18);
  box-shadow:
    0 22px 60px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(15, 118, 110, 0.12);
  backdrop-filter: blur(2px);
}

.si-login__brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.95rem;
  padding: 2.5rem 2.1rem;
  background:
    radial-gradient(480px 260px at 0% 0%, rgba(45, 212, 191, 0.18), transparent 58%),
    linear-gradient(165deg, rgba(20, 48, 46, 0.96), rgba(12, 28, 32, 0.96));
  color: #e7eef0;
}

.si-login__lockup {
  display: flex;
  align-items: center;
  gap: 0.95rem;
}

.si-login__mark-wrap {
  flex: 0 0 auto;
  padding: 0.2rem;
  border-radius: 14px;
  background: rgba(94, 234, 212, 0.12);
  box-shadow:
    0 0 0 1px rgba(94, 234, 212, 0.28),
    0 10px 28px rgba(15, 118, 110, 0.35);
}

.si-login__mark-wrap :deep(.si-mark) {
  display: block;
  border-radius: 12px;
}

.si-login__lockup-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.si-login__kicker {
  margin: 0;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: #5eead4;
}

.si-login__lockup-text > span {
  font-size: 1.55rem;
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: #f0fdfa;
}

.si-login__brand h1 {
  margin: 0.15rem 0 0;
  font-size: 1.15rem;
  font-weight: 650;
  color: #d5e4e2;
}

.si-login__lead {
  margin: 0;
  max-width: 22rem;
  font-size: 0.92rem;
  line-height: 1.7;
  color: rgba(214, 226, 224, 0.78);
}

.si-login__panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 2.4rem 2.3rem;
  background: rgba(12, 15, 17, 0.94);
  color: #f4f7f6;
}

.si-login__panel h2 {
  margin: 0 0 1.35rem;
  font-size: 1.35rem;
  font-weight: 650;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

input {
  width: 100%;
  border: 1px solid #2a3336;
  border-radius: 4px;
  padding: 0.72rem 0.8rem;
  background: #1a1f22;
  color: #f4f7f6;
  font-size: 0.95rem;
}

input::placeholder {
  color: #8b9896;
}

input:focus {
  outline: none;
  border-color: #2dd4bf;
}

button {
  margin-top: 0.35rem;
  border: 0;
  border-radius: 4px;
  padding: 0.72rem 1rem;
  background: #0f766e;
  color: #fff;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
}

button:hover:not(:disabled) {
  background: #0d9488;
}

button:disabled {
  opacity: 0.7;
  cursor: wait;
}

.si-login__error {
  margin: 0;
  color: #fca5a5;
  font-size: 0.85rem;
}

@keyframes siDash {
  to {
    stroke-dashoffset: -180;
  }
}

@keyframes siOrb {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-18px, 14px, 0) scale(1.08);
  }
}

@media (max-width: 760px) {
  .si-login__sheet {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .si-login__brand,
  .si-login__panel {
    padding: 1.6rem 1.35rem;
  }

  .si-login__lockup-text > span {
    font-size: 1.3rem;
  }

  .si-login__graph {
    opacity: 0.7;
  }
}

@media (prefers-reduced-motion: reduce) {
  .si-login__edge,
  .si-login__orb {
    animation: none;
  }
}
</style>
