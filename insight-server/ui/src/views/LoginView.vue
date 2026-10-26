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
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
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
            <stop offset="0%" stop-color="var(--si-login-edge-start)" stop-opacity="1" />
            <stop offset="45%" stop-color="var(--si-login-edge-mid)" stop-opacity="1" />
            <stop offset="100%" stop-color="var(--si-login-edge-end)" stop-opacity="1" />
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
      </section>

      <section class="si-login__panel">
        <h2>登录</h2>
        <el-form class="si-login__form" label-position="top" @submit.prevent="onSubmit">
          <el-form-item>
            <el-input
              v-model="username"
              autocomplete="username"
              placeholder="用户名"
              size="large"
              clearable
              :disabled="loading"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="password"
              type="password"
              autocomplete="current-password"
              placeholder="密码"
              size="large"
              show-password
              :disabled="loading"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-alert
            v-if="error"
            class="si-login__error"
            type="error"
            :title="error"
            :closable="false"
            show-icon
          />
          <el-button
            class="si-login__submit"
            type="primary"
            native-type="submit"
            size="large"
            :loading="loading"
          >
            {{ loading ? '登录中…' : '登录' }}
          </el-button>
        </el-form>
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
  background: var(--si-login-page-bg);
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
  background: var(--si-login-wash);
  opacity: 0.9;
}

.si-login__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--si-login-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--si-login-grid-line) 1px, transparent 1px);
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
  background: var(--si-login-orb-a);
}

.si-login__orb--b {
  width: 300px;
  height: 300px;
  left: -6%;
  bottom: -4%;
  background: var(--si-login-orb-b);
  animation-duration: 22s;
  animation-direction: alternate-reverse;
}

.si-login__orb--c {
  width: 240px;
  height: 240px;
  left: 42%;
  top: 36%;
  background: var(--si-login-orb-c);
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
  fill: var(--si-login-halo);
}

.si-login__halo--core {
  fill: var(--si-login-halo-core);
}

.si-login__node {
  fill: var(--si-login-node-fill);
  stroke: var(--si-login-node-stroke);
  stroke-width: 2.2;
  filter: drop-shadow(0 0 8px var(--si-login-node-glow));
}

.si-login__node--core {
  fill: var(--si-login-node-core-fill);
  stroke: var(--si-login-node-core-stroke);
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
  border: 1px solid var(--si-login-sheet-border);
  box-shadow: var(--si-login-sheet-shadow);
  backdrop-filter: blur(2px);
}

.si-login__brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.95rem;
  padding: 2.5rem 2.1rem;
  background: var(--si-login-brand-bg);
  color: var(--si-login-brand-text);
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
  background: var(--si-login-mark-bg);
  box-shadow:
    0 0 0 1px var(--si-login-mark-ring),
    var(--si-login-mark-shadow);
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
  color: var(--si-login-kicker);
}

.si-login__lockup-text > span {
  font-size: 1.55rem;
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--si-login-brand-text);
}

.si-login__brand h1 {
  margin: 0.15rem 0 0;
  font-size: 1.15rem;
  font-weight: 650;
  color: var(--si-login-brand-heading);
}


.si-login__panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 2.4rem 2.3rem;
  background: var(--si-login-panel-bg);
  color: var(--si-login-panel-text);
}

.si-login__panel h2 {
  margin: 0 0 1.35rem;
  font-size: 1.35rem;
  font-weight: 650;
}

.si-login__form {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.si-login__form :deep(.el-form-item) {
  margin-bottom: 0.5rem;
}

.si-login__form :deep(.el-input__wrapper) {
  border-radius: 4px;
  background: var(--si-login-input-bg);
  box-shadow: 0 0 0 1px var(--si-login-input-border) inset;
}

.si-login__form :deep(.el-input__wrapper:hover),
.si-login__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--si-login-focus) inset;
}

.si-login__form :deep(.el-input__inner) {
  color: var(--si-login-input-text);
  font-size: 0.95rem;
}

.si-login__form :deep(.el-input__inner::placeholder) {
  color: var(--si-login-input-placeholder);
}

.si-login__submit {
  margin-top: 0.35rem;
  width: 100%;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 700;
  --el-button-bg-color: var(--si-login-btn);
  --el-button-border-color: var(--si-login-btn);
  --el-button-hover-bg-color: var(--si-login-btn-hover);
  --el-button-hover-border-color: var(--si-login-btn-hover);
  --el-button-active-bg-color: var(--si-login-btn-hover);
  --el-button-active-border-color: var(--si-login-btn-hover);
}

.si-login__error {
  margin: 0 0 0.35rem;
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
