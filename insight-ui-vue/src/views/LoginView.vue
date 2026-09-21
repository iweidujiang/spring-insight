<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '../services/AuthService'
import InsightMark from '../components/InsightMark.vue'

const router = useRouter()
const route = useRoute()
const username = ref('admin')
const password = ref('')
const error = ref('')
const loading = ref(false)

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
    <section class="si-login__brand" aria-hidden="true">
      <div class="si-login__grid"></div>
      <div class="si-login__orb si-login__orb--a"></div>
      <div class="si-login__orb si-login__orb--b"></div>
      <svg class="si-login__graph" viewBox="0 0 640 420" fill="none">
        <defs>
          <linearGradient id="siEdge" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#2dd4bf" stop-opacity="0.15" />
            <stop offset="50%" stop-color="#5eead4" stop-opacity="0.85" />
            <stop offset="100%" stop-color="#2dd4bf" stop-opacity="0.15" />
          </linearGradient>
        </defs>
        <path class="si-login__edge" d="M120 210 C 210 120, 250 120, 320 168" />
        <path class="si-login__edge si-login__edge--slow" d="M320 168 C 410 90, 460 110, 530 150" />
        <path class="si-login__edge" d="M320 168 C 300 250, 250 290, 170 318" />
        <path class="si-login__edge si-login__edge--slow" d="M320 168 C 380 250, 430 280, 510 300" />
        <path class="si-login__edge" d="M170 318 C 260 360, 380 350, 510 300" />
        <circle cx="120" cy="210" r="8" class="si-login__node" />
        <circle cx="320" cy="168" r="11" class="si-login__node si-login__node--core" />
        <circle cx="530" cy="150" r="8" class="si-login__node" />
        <circle cx="170" cy="318" r="7" class="si-login__node" />
        <circle cx="510" cy="300" r="8" class="si-login__node" />
      </svg>
      <div class="si-login__brand-copy">
        <div class="si-login__lockup">
          <InsightMark :size="44" />
          <p class="si-login__kicker">SPRING INSIGHT</p>
        </div>
        <h1>看清每一次调用</h1>
        <p class="si-login__lead">轻量监测中心 · 服务拓扑、链路瀑布、错误与延迟，一张图里定位慢与错。</p>
        <ul class="si-login__chips">
          <li>服务拓扑</li>
          <li>调用链路</li>
          <li>错误分析</li>
        </ul>
      </div>
    </section>

    <section class="si-login__panel">
      <form class="si-login__card" @submit.prevent="onSubmit">
        <div class="si-login__card-mark">
          <InsightMark :size="36" />
        </div>
        <h2>登录控制台</h2>
        <p class="si-login__hint">已启用鉴权，请使用管理员账号进入</p>
        <label>
          用户名
          <input v-model="username" autocomplete="username" required />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" required />
        </label>
        <p v-if="error" class="si-login__error">{{ error }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '进入控制台' }}</button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.si-login {
  display: grid;
  grid-template-columns: minmax(280px, 1.15fr) minmax(320px, 0.95fr);
  width: 100%;
  min-height: 100dvh;
  background: var(--si-paper, #f2f5f3);
}

.si-login__brand {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  display: flex;
  align-items: flex-end;
  padding: 2.5rem 2.25rem 2.75rem;
  background:
    radial-gradient(1200px 600px at -10% -20%, rgba(45, 212, 191, 0.22), transparent 55%),
    radial-gradient(800px 500px at 110% 90%, rgba(13, 148, 136, 0.18), transparent 50%),
    linear-gradient(165deg, #04141a 0%, #0a2422 48%, #07161c 100%);
  color: #ecfeff;
}

.si-login__grid {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image:
    linear-gradient(rgba(94, 234, 212, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(94, 234, 212, 0.07) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(ellipse at 30% 40%, #000 0%, transparent 78%);
}

.si-login__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  z-index: 1;
}

.si-login__orb--a {
  width: 220px;
  height: 220px;
  right: -40px;
  top: 12%;
  background: radial-gradient(circle, rgba(45, 212, 191, 0.35), transparent 68%);
}

.si-login__orb--b {
  width: 160px;
  height: 160px;
  left: 8%;
  bottom: 18%;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.28), transparent 70%);
}

.si-login__graph {
  position: absolute;
  inset: 8% 4% 28% 4%;
  width: auto;
  height: auto;
  z-index: 2;
  opacity: 0.95;
}

.si-login__edge {
  stroke: url(#siEdge);
  stroke-width: 1.6;
  stroke-dasharray: 8 10;
  animation: siDash 8s linear infinite;
}

.si-login__edge--slow {
  animation-duration: 12s;
  animation-direction: reverse;
}

.si-login__node {
  fill: #042f2e;
  stroke: #5eead4;
  stroke-width: 2;
  filter: drop-shadow(0 0 6px rgba(45, 212, 191, 0.85));
}

.si-login__node--core {
  fill: #0f766e;
  stroke: #ccfbf1;
}

.si-login__brand-copy {
  position: relative;
  z-index: 3;
  max-width: 34rem;
}

.si-login__kicker {
  margin: 0;
  letter-spacing: 0.22em;
  font-size: 0.72rem;
  font-weight: 700;
  color: #5eead4;
}

.si-login__lockup {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}

.si-login__brand-copy h1 {
  margin: 0 0 0.75rem;
  font-family: var(--font-display, Georgia, serif);
  font-size: clamp(1.8rem, 3.4vw, 2.6rem);
  line-height: 1.15;
  font-weight: 700;
}

.si-login__lead {
  margin: 0 0 1.15rem;
  max-width: 28rem;
  color: rgba(204, 251, 241, 0.78);
  font-size: 0.98rem;
  line-height: 1.65;
}

.si-login__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.si-login__chips li {
  padding: 0.28rem 0.7rem;
  border: 1px solid rgba(94, 234, 212, 0.28);
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.22);
  color: #ccfbf1;
  font-size: 0.78rem;
  font-weight: 600;
}

.si-login__panel {
  display: grid;
  place-items: center;
  padding: 2rem 1.5rem;
}

.si-login__card {
  width: min(400px, 100%);
  background: #fffcfa;
  border: 1px solid rgba(20, 83, 45, 0.12);
  border-radius: 16px;
  padding: 2rem 1.7rem 1.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  box-shadow: 0 16px 40px rgba(21, 36, 31, 0.08);
}

.si-login__card-mark {
  width: 2.5rem;
  height: 2.5rem;
  display: grid;
  place-items: center;
}

.si-login__card h2 {
  margin: 0;
  font-size: 1.45rem;
  font-weight: 700;
  color: #15241f;
}

.si-login__hint {
  margin: -0.25rem 0 0.2rem;
  color: #6b7f76;
  font-size: 0.9rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #3d524a;
}

input {
  border: 1px solid #d5ddd9;
  border-radius: 8px;
  padding: 0.62rem 0.75rem;
  font-size: 0.95rem;
  background: #fff;
}

input:focus {
  outline: 2px solid rgba(15, 118, 110, 0.28);
  border-color: #0f766e;
}

button {
  margin-top: 0.35rem;
  border: 0;
  border-radius: 8px;
  padding: 0.72rem 1rem;
  background: #0f766e;
  color: #fff;
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
  color: #b91c1c;
  font-size: 0.85rem;
}

@keyframes siDash {
  to {
    stroke-dashoffset: -180;
  }
}

@media (max-width: 900px) {
  .si-login {
    grid-template-columns: 1fr;
  }

  .si-login__brand {
    min-height: 34vh;
    padding: 1.5rem 1.35rem 1.4rem;
  }

  .si-login__graph {
    inset: 6% 8% 36% 8%;
  }

  .si-login__brand-copy h1 {
    font-size: 1.55rem;
  }
}
</style>
