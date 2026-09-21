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
    <div class="si-login__sheet">
      <section class="si-login__brand">
        <div class="si-login__lockup">
          <InsightMark :size="36" />
          <span>Spring Insight</span>
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
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  background: #070b0c;
}

.si-login__sheet {
  display: grid;
  grid-template-columns: minmax(240px, 0.92fr) minmax(280px, 1.08fr);
  width: min(860px, 100%);
  min-height: 460px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.45);
}

.si-login__brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.85rem;
  padding: 2.5rem 2.1rem;
  background:
    radial-gradient(420px 220px at 0% 0%, rgba(45, 212, 191, 0.08), transparent 60%),
    #1c2c30;
  color: #e7eef0;
}

.si-login__lockup {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.si-login__brand h1 {
  margin: 0.4rem 0 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: #d5e4e2;
}

.si-login__lead {
  margin: 0;
  max-width: 22rem;
  font-size: 0.92rem;
  line-height: 1.7;
  color: rgba(214, 226, 224, 0.72);
}

.si-login__panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 2.4rem 2.3rem;
  background: #121416;
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

@media (max-width: 760px) {
  .si-login__sheet {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .si-login__brand,
  .si-login__panel {
    padding: 1.6rem 1.35rem;
  }
}
</style>
