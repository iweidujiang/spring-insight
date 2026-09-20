<template>
  <div class="si-page fade-in si-settings">
    <div class="si-page__header mb-3">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-cog me-2"></i>设置
        </h2>
        <p class="page-description mb-0">
          告警与 AI 在监测中心配置；保存后即时生效，写入数据目录
          <code v-if="settingsPath">{{ settingsPath }}</code>
          <span v-else>runtime-settings.json</span>
        </p>
      </div>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="loading || saving"
        @click="onSave"
      >
        <i class="fa" :class="saving ? 'fa-spinner fa-spin' : 'fa-save'"></i>
        {{ saving ? '保存中…' : '保存并生效' }}
      </button>
    </div>

    <div v-if="message" class="alert" :class="messageOk ? 'alert-success' : 'alert-danger'" role="status">
      {{ message }}
    </div>

    <div v-if="loading" class="loading-spinner text-center py-5">
      <i class="fa fa-spinner fa-spin fa-2x"></i>
      <p class="mt-2 text-muted mb-0">加载设置…</p>
    </div>

    <div v-else class="si-settings__grid">
      <section class="card stat-card si-settings__card">
        <div class="card-body">
          <h5 class="card-title"><i class="fa fa-bell me-2"></i>告警</h5>
          <div class="form-check form-switch mb-3">
            <input id="alert-enabled" v-model="form.alert.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="alert-enabled">启用告警扫描</label>
          </div>
          <div class="mb-3">
            <label class="form-label">Webhook URL</label>
            <input v-model="form.alert.webhookUrl" class="form-control" type="url" placeholder="https://… 或 http://host.docker.internal:…" />
          </div>
          <div class="row g-2 mb-3">
            <div class="col-md-4">
              <label class="form-label">指标</label>
              <select v-model="form.alert.metric" class="form-control">
                <option value="error_rate">error_rate（%）</option>
                <option value="error_count">error_count</option>
              </select>
            </div>
            <div class="col-md-4">
              <label class="form-label">阈值</label>
              <input v-model.number="form.alert.threshold" class="form-control" type="number" min="0" step="0.1" />
            </div>
            <div class="col-md-4">
              <label class="form-label">窗口（分钟）</label>
              <input v-model.number="form.alert.windowMinutes" class="form-control" type="number" min="1" />
            </div>
            <div class="col-md-4">
              <label class="form-label">冷却（分钟）</label>
              <input v-model.number="form.alert.cooldownMinutes" class="form-control" type="number" min="0" />
            </div>
          </div>

          <hr class="si-settings__hr" />
          <h6 class="mb-2"><i class="fa fa-envelope me-1"></i>邮件 SMTP</h6>
          <div class="form-check form-switch mb-3">
            <input id="email-enabled" v-model="form.alert.email.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="email-enabled">启用邮件通道</label>
          </div>
          <div class="row g-2">
            <div class="col-md-8">
              <label class="form-label">SMTP Host</label>
              <input v-model="form.alert.email.host" class="form-control" placeholder="smtp.example.com" />
            </div>
            <div class="col-md-4">
              <label class="form-label">端口</label>
              <input v-model.number="form.alert.email.port" class="form-control" type="number" min="1" />
            </div>
            <div class="col-md-6">
              <label class="form-label">用户名</label>
              <input v-model="form.alert.email.username" class="form-control" autocomplete="off" />
            </div>
            <div class="col-md-6">
              <label class="form-label">
                密码
                <span v-if="passwordConfigured" class="text-muted small">（已配置，留空不修改）</span>
              </label>
              <input
                v-model="form.alert.email.password"
                class="form-control"
                type="password"
                autocomplete="new-password"
                :placeholder="passwordConfigured ? '••••••••' : ''"
              />
            </div>
            <div class="col-md-6">
              <label class="form-label">发件人 From</label>
              <input v-model="form.alert.email.from" class="form-control" type="email" />
            </div>
            <div class="col-md-6">
              <label class="form-label">收件人 To（逗号分隔）</label>
              <input v-model="form.alert.email.to" class="form-control" />
            </div>
            <div class="col-md-6">
              <div class="form-check form-switch mt-2">
                <input id="email-starttls" v-model="form.alert.email.startTls" class="form-check-input" type="checkbox" />
                <label class="form-check-label" for="email-starttls">STARTTLS</label>
              </div>
            </div>
            <div class="col-md-6">
              <div class="form-check form-switch mt-2">
                <input id="email-ssl" v-model="form.alert.email.ssl" class="form-check-input" type="checkbox" />
                <label class="form-check-label" for="email-ssl">SSL</label>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="card stat-card si-settings__card">
        <div class="card-body">
          <h5 class="card-title"><i class="fa fa-magic me-2"></i>AI 解释</h5>
          <div class="form-check form-switch mb-3">
            <input id="ai-enabled" v-model="form.ai.enabled" class="form-check-input" type="checkbox" />
            <label class="form-check-label" for="ai-enabled">启用链路 AI 解释</label>
          </div>
          <div class="mb-3">
            <label class="form-label">Provider</label>
            <input v-model="form.ai.provider" class="form-control" placeholder="openai-compatible" />
          </div>
          <div class="mb-3">
            <label class="form-label">Base URL</label>
            <input v-model="form.ai.baseUrl" class="form-control" placeholder="https://api.deepseek.com/v1" />
          </div>
          <div class="mb-3">
            <label class="form-label">Model</label>
            <input v-model="form.ai.model" class="form-control" placeholder="deepseek-chat" />
          </div>
          <div class="mb-3">
            <label class="form-label">
              API Key
              <span v-if="apiKeyConfigured" class="text-muted small">（已配置，留空不修改）</span>
            </label>
            <input
              v-model="form.ai.apiKey"
              class="form-control"
              type="password"
              autocomplete="new-password"
              :placeholder="apiKeyConfigured ? '••••••••' : ''"
            />
          </div>
          <div class="row g-2">
            <div class="col-md-4">
              <label class="form-label">超时（ms）</label>
              <input v-model.number="form.ai.timeoutMs" class="form-control" type="number" min="1000" />
            </div>
            <div class="col-md-4">
              <label class="form-label">最大输入 Span</label>
              <input v-model.number="form.ai.maxInputSpans" class="form-control" type="number" min="1" />
            </div>
            <div class="col-md-4">
              <label class="form-label">maxTokens</label>
              <input v-model.number="form.ai.maxTokens" class="form-control" type="number" min="64" />
            </div>
          </div>
          <p class="text-muted small mt-3 mb-0">
            使用 OpenAI 兼容的 Chat Completions 接口即可对接各类大模型；API Key 仅保存在 Server 本机数据目录。
          </p>
        </div>
      </section>

      <section class="card stat-card si-settings__card si-settings__card--danger">
        <div class="card-body">
          <h5 class="card-title"><i class="fa fa-database me-2"></i>数据</h5>
          <p class="text-muted small mb-3">
            当前存储模式 <strong>{{ storage.mode || '—' }}</strong>：
            已存 {{ storage.stored }} / 上限 {{ storage.max }}，累计裁剪 {{ storage.evicted }}。
            清除不可恢复；按服务只删服务名精确匹配的 Span，相关 Trace 可能不完整。
          </p>
          <div class="d-flex flex-wrap gap-2 mb-3">
            <button type="button" class="btn btn-outline-danger btn-sm" :disabled="clearing" @click="confirmClear('all')">
              清空全部
            </button>
          </div>
          <div class="row g-2 align-items-end mb-3">
            <div class="col-md-5">
              <label class="form-label">早于 N 小时</label>
              <input v-model.number="clearOlderHours" class="form-control" type="number" min="1" />
            </div>
            <div class="col-md-4">
              <button type="button" class="btn btn-outline-warning btn-sm w-100" :disabled="clearing" @click="confirmClear('older_than')">
                按时间清理
              </button>
            </div>
          </div>
          <div class="row g-2 align-items-end">
            <div class="col-md-7">
              <label class="form-label">服务名（精确匹配）</label>
              <input v-model="clearServiceName" class="form-control" list="si-service-names" placeholder="例如 sca-order" />
              <datalist id="si-service-names">
                <option v-for="n in serviceNames" :key="n" :value="n" />
              </datalist>
            </div>
            <div class="col-md-4">
              <button type="button" class="btn btn-outline-warning btn-sm w-100" :disabled="clearing" @click="confirmClear('service')">
                按服务清理
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiService, type RuntimeSettingsSaveBody } from '../services/ApiService'

const loading = ref(true)
const saving = ref(false)
const clearing = ref(false)
const message = ref('')
const messageOk = ref(false)
const settingsPath = ref('')
const passwordConfigured = ref(false)
const apiKeyConfigured = ref(false)
const clearOlderHours = ref(24)
const clearServiceName = ref('')
const serviceNames = ref<string[]>([])
const storage = reactive({ mode: '', stored: 0, max: 0, evicted: 0 })

const form = reactive<RuntimeSettingsSaveBody>({
  alert: {
    enabled: false,
    webhookUrl: '',
    metric: 'error_rate',
    threshold: 10,
    windowMinutes: 15,
    cooldownMinutes: 30,
    email: {
      enabled: false,
      host: '',
      port: 587,
      username: '',
      password: '',
      from: '',
      to: '',
      startTls: true,
      ssl: false
    }
  },
  ai: {
    enabled: false,
    provider: 'openai-compatible',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    model: 'gpt-4o-mini',
    timeoutMs: 30000,
    maxInputSpans: 40,
    maxTokens: 800
  }
})

function showMsg(text: string, ok: boolean) {
  message.value = text
  messageOk.value = ok
}

async function refreshStorage() {
  const s = await ApiService.getStorageSummary()
  storage.mode = s.mode || ''
  storage.stored = Number(s.stored ?? 0)
  storage.max = Number(s.max ?? 0)
  storage.evicted = Number(s.evicted ?? 0)
}

async function load() {
  loading.value = true
  message.value = ''
  try {
    const data = await ApiService.getSettings()
    settingsPath.value = data.settingsPath || ''
    form.alert.enabled = !!data.alert?.enabled
    form.alert.webhookUrl = data.alert?.webhookUrl || ''
    form.alert.metric = data.alert?.metric || 'error_rate'
    form.alert.threshold = Number(data.alert?.threshold ?? 10)
    form.alert.windowMinutes = Number(data.alert?.windowMinutes ?? 15)
    form.alert.cooldownMinutes = Number(data.alert?.cooldownMinutes ?? 30)
    const email = data.alert?.email
    form.alert.email.enabled = !!email?.enabled
    form.alert.email.host = email?.host || ''
    form.alert.email.port = Number(email?.port ?? 587)
    form.alert.email.username = email?.username || ''
    form.alert.email.password = ''
    form.alert.email.from = email?.from || ''
    form.alert.email.to = email?.to || ''
    form.alert.email.startTls = email?.startTls !== false
    form.alert.email.ssl = !!email?.ssl
    passwordConfigured.value = !!email?.passwordConfigured

    form.ai.enabled = !!data.ai?.enabled
    form.ai.provider = data.ai?.provider || 'openai-compatible'
    form.ai.baseUrl = data.ai?.baseUrl || ''
    form.ai.apiKey = ''
    form.ai.model = data.ai?.model || ''
    form.ai.timeoutMs = Number(data.ai?.timeoutMs ?? 30000)
    form.ai.maxInputSpans = Number(data.ai?.maxInputSpans ?? 40)
    form.ai.maxTokens = Number(data.ai?.maxTokens ?? 800)
    apiKeyConfigured.value = !!data.ai?.apiKeyConfigured

    await refreshStorage()
    serviceNames.value = await ApiService.getServiceNames()
  } catch (e: any) {
    showMsg(e?.message || '加载设置失败', false)
  } finally {
    loading.value = false
  }
}

async function onSave() {
  saving.value = true
  message.value = ''
  try {
    const saved = await ApiService.saveSettings({
      alert: { ...form.alert, email: { ...form.alert.email } },
      ai: { ...form.ai }
    })
    passwordConfigured.value = !!saved.alert?.email?.passwordConfigured
    apiKeyConfigured.value = !!saved.ai?.apiKeyConfigured
    form.alert.email.password = ''
    form.ai.apiKey = ''
    settingsPath.value = saved.settingsPath || settingsPath.value
    showMsg('已保存并生效', true)
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '保存失败'
    showMsg(msg, false)
  } finally {
    saving.value = false
  }
}

async function confirmClear(scope: 'all' | 'older_than' | 'service') {
  let tip = ''
  if (scope === 'all') {
    tip = `确定清空全部 ${storage.stored} 条 Span？此操作不可恢复。`
  } else if (scope === 'older_than') {
    if (!clearOlderHours.value || clearOlderHours.value < 1) {
      showMsg('请填写有效的小时数', false)
      return
    }
    tip = `确定删除早于 ${clearOlderHours.value} 小时的 Span？此操作不可恢复。`
  } else {
    const name = clearServiceName.value.trim()
    if (!name) {
      showMsg('请填写服务名', false)
      return
    }
    tip = `确定删除服务「${name}」的全部 Span？相关 Trace 可能不完整，且不可恢复。`
  }
  if (!window.confirm(tip)) {
    return
  }
  clearing.value = true
  message.value = ''
  try {
    const result = await ApiService.clearStorage(
      scope === 'all'
        ? { scope: 'all' }
        : scope === 'older_than'
          ? { scope: 'older_than', olderThanHours: clearOlderHours.value }
          : { scope: 'service', serviceName: clearServiceName.value.trim() }
    )
    await refreshStorage()
    showMsg(`已删除 ${result.deleted} 条，剩余 ${result.remaining}。请刷新仪表盘/拓扑查看。`, true)
  } catch (e: any) {
    showMsg(e?.response?.data?.message || e?.message || '清理失败', false)
  } finally {
    clearing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.si-settings__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1.25rem;
}

.si-settings__card .card-title {
  margin-bottom: 1rem;
}

.si-settings__card--danger {
  border-color: rgba(185, 28, 28, 0.25);
  box-shadow: inset 0 0 0 1px rgba(185, 28, 28, 0.06);
}

.si-settings__hr {
  border: 0;
  border-top: 1px solid rgba(15, 118, 110, 0.12);
  margin: 1rem 0;
}

.si-settings .form-label {
  font-size: 0.85rem;
  margin-bottom: 0.25rem;
}

.si-settings code {
  font-size: 0.8em;
  word-break: break-all;
}
</style>
