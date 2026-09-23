<template>
  <div class="si-page fade-in si-settings">
    <div class="si-page__header mb-3">
      <div>
        <h2 class="page-title mb-1">
          <i class="fa fa-cog me-2"></i>设置
        </h2>
        <p class="page-description mb-0">
          告警推送、链路 AI 解读与存储清理
          <template v-if="section !== 'data'">
            · 保存后即时生效，写入
            <code v-if="settingsPath">{{ settingsPath }}</code>
            <span v-else>runtime-settings.json</span>
          </template>
        </p>
      </div>
      <button
        v-if="section !== 'data'"
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

    <template v-else>
      <nav class="si-settings__tabs" aria-label="设置分节">
        <button
          v-for="item in sections"
          :key="item.id"
          type="button"
          class="si-settings__tab"
          :class="{
            'is-active': section === item.id,
            'is-danger': item.id === 'data'
          }"
          @click="section = item.id"
        >
          <span class="si-settings__tab-icon" aria-hidden="true">
            <i class="fa" :class="item.icon"></i>
          </span>
          <span class="si-settings__tab-text">
            <strong>{{ item.label }}</strong>
            <em>{{ item.desc }}</em>
          </span>
        </button>
      </nav>

      <div class="si-settings__panel">
        <!-- 告警 -->
        <section v-show="section === 'alert'" class="si-settings__card">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-bell me-2"></i>告警推送</h3>
              <p class="si-settings__card-desc">
                按错误率或错误次数扫描，超阈值时通过 Webhook 或邮件通知。
              </p>
            </div>
            <div class="form-check form-switch mb-0">
              <input id="alert-enabled" v-model="form.alert.enabled" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="alert-enabled">启用扫描</label>
            </div>
          </header>

          <div class="si-settings__fields">
            <div class="si-settings__field si-settings__field--full">
              <label class="form-label" for="alert-webhook">Webhook URL</label>
              <input
                id="alert-webhook"
                v-model="form.alert.webhookUrl"
                class="form-control"
                type="url"
                placeholder="https://… 或 http://host.docker.internal:…"
              />
            </div>

            <div class="si-settings__field">
              <label class="form-label" for="alert-metric">指标</label>
              <select id="alert-metric" v-model="form.alert.metric" class="form-select">
                <option value="error_rate">error_rate（%）</option>
                <option value="error_count">error_count</option>
              </select>
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="alert-threshold">阈值</label>
              <input id="alert-threshold" v-model.number="form.alert.threshold" class="form-control" type="number" min="0" step="0.1" />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="alert-window">窗口（分钟）</label>
              <input id="alert-window" v-model.number="form.alert.windowMinutes" class="form-control" type="number" min="1" />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="alert-cooldown">冷却（分钟）</label>
              <input id="alert-cooldown" v-model.number="form.alert.cooldownMinutes" class="form-control" type="number" min="0" />
            </div>
          </div>

          <div class="si-settings__block">
            <header class="si-settings__block-head">
              <div>
                <h4 class="si-settings__block-title"><i class="fa fa-envelope me-1"></i>邮件 SMTP</h4>
                <p class="si-settings__block-desc">可选；与 Webhook 可同时启用。</p>
              </div>
              <div class="form-check form-switch mb-0">
                <input id="email-enabled" v-model="form.alert.email.enabled" class="form-check-input" type="checkbox" />
                <label class="form-check-label" for="email-enabled">启用邮件</label>
              </div>
            </header>

            <div class="si-settings__fields">
              <div class="si-settings__field si-settings__field--wide">
                <label class="form-label" for="smtp-host">SMTP Host</label>
                <input id="smtp-host" v-model="form.alert.email.host" class="form-control" placeholder="smtp.example.com" />
              </div>
              <div class="si-settings__field si-settings__field--narrow">
                <label class="form-label" for="smtp-port">端口</label>
                <input id="smtp-port" v-model.number="form.alert.email.port" class="form-control" type="number" min="1" />
              </div>
              <div class="si-settings__field">
                <label class="form-label" for="smtp-user">用户名</label>
                <input id="smtp-user" v-model="form.alert.email.username" class="form-control" autocomplete="off" />
              </div>
              <div class="si-settings__field">
                <label class="form-label" for="smtp-pass">
                  密码
                  <span v-if="passwordConfigured" class="text-muted small">（已配置，留空不改）</span>
                </label>
                <input
                  id="smtp-pass"
                  v-model="form.alert.email.password"
                  class="form-control"
                  type="password"
                  autocomplete="new-password"
                  :placeholder="passwordConfigured ? '••••••••' : ''"
                />
              </div>
              <div class="si-settings__field">
                <label class="form-label" for="smtp-from">发件人 From</label>
                <input id="smtp-from" v-model="form.alert.email.from" class="form-control" type="email" />
              </div>
              <div class="si-settings__field">
                <label class="form-label" for="smtp-to">收件人 To（逗号分隔）</label>
                <input id="smtp-to" v-model="form.alert.email.to" class="form-control" />
              </div>
              <div class="si-settings__field si-settings__field--toggles">
                <div class="form-check form-switch">
                  <input id="email-starttls" v-model="form.alert.email.startTls" class="form-check-input" type="checkbox" />
                  <label class="form-check-label" for="email-starttls">STARTTLS</label>
                </div>
                <div class="form-check form-switch">
                  <input id="email-ssl" v-model="form.alert.email.ssl" class="form-check-input" type="checkbox" />
                  <label class="form-check-label" for="email-ssl">SSL</label>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- AI -->
        <section v-show="section === 'ai'" class="si-settings__card">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-magic me-2"></i>AI 解读</h3>
              <p class="si-settings__card-desc">
                对接 OpenAI 兼容接口，为一键解读链路、拓扑边与错误聚合提供模型能力。
              </p>
            </div>
            <div class="form-check form-switch mb-0">
              <input id="ai-enabled" v-model="form.ai.enabled" class="form-check-input" type="checkbox" />
              <label class="form-check-label" for="ai-enabled">启用 AI</label>
            </div>
          </header>

          <div class="si-settings__fields">
            <div class="si-settings__field">
              <label class="form-label" for="ai-provider">Provider</label>
              <input id="ai-provider" v-model="form.ai.provider" class="form-control" placeholder="openai-compatible" />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="ai-model">Model</label>
              <input id="ai-model" v-model="form.ai.model" class="form-control" placeholder="deepseek-chat" />
            </div>
            <div class="si-settings__field si-settings__field--full">
              <label class="form-label" for="ai-base">Base URL</label>
              <input id="ai-base" v-model="form.ai.baseUrl" class="form-control" placeholder="https://api.deepseek.com/v1" />
            </div>
            <div class="si-settings__field si-settings__field--full">
              <label class="form-label" for="ai-key">
                API Key
                <span v-if="apiKeyConfigured" class="text-muted small">（已配置，留空不改）</span>
              </label>
              <input
                id="ai-key"
                v-model="form.ai.apiKey"
                class="form-control"
                type="password"
                autocomplete="new-password"
                :placeholder="apiKeyConfigured ? '••••••••' : ''"
              />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="ai-timeout">超时（ms）</label>
              <input id="ai-timeout" v-model.number="form.ai.timeoutMs" class="form-control" type="number" min="1000" />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="ai-spans">最大输入 Span</label>
              <input id="ai-spans" v-model.number="form.ai.maxInputSpans" class="form-control" type="number" min="1" />
            </div>
            <div class="si-settings__field">
              <label class="form-label" for="ai-tokens">maxTokens</label>
              <input id="ai-tokens" v-model.number="form.ai.maxTokens" class="form-control" type="number" min="64" />
            </div>
          </div>
          <p class="si-settings__footnote">
            使用 Chat Completions 兼容接口即可对接各类大模型；API Key 仅保存在 Server 本机数据目录。
          </p>
        </section>

        <!-- 数据 -->
        <section v-show="section === 'data'" class="si-settings__card si-settings__card--danger">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-database me-2"></i>数据清理</h3>
              <p class="si-settings__card-desc">
                查看存储占用，并按范围删除 Span。清除不可恢复；按服务精确匹配时，相关 Trace 可能不完整。
              </p>
            </div>
          </header>

          <div class="si-settings__storage">
            <div class="si-settings__storage-item">
              <span class="si-settings__storage-label">存储模式</span>
              <strong>{{ storage.mode || '—' }}</strong>
            </div>
            <div class="si-settings__storage-item">
              <span class="si-settings__storage-label">已存 / 上限</span>
              <strong>{{ storage.stored }} / {{ storage.max }}</strong>
            </div>
            <div class="si-settings__storage-item">
              <span class="si-settings__storage-label">累计裁剪</span>
              <strong>{{ storage.evicted }}</strong>
            </div>
          </div>

          <div class="si-settings__actions">
            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>清空全部</h4>
                <p>删除当前已存的全部 Span。</p>
              </div>
              <button
                type="button"
                class="btn btn-outline-danger"
                :disabled="clearing || storage.stored === 0"
                :title="storage.stored === 0 ? '当前没有可清空的 Span' : undefined"
                @click="openClearDialog('all')"
              >
                清空全部
              </button>
            </article>

            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>按时间清理</h4>
                <p>只删除早于指定小时数的 Span，保留更近的数据。</p>
              </div>
              <div class="si-settings__action-row">
                <div class="si-settings__action-field">
                  <label class="form-label" for="clear-hours">早于（小时）</label>
                  <input id="clear-hours" v-model.number="clearOlderHours" class="form-control" type="number" min="1" />
                </div>
                <button
                  type="button"
                  class="btn btn-outline-warning si-settings__action-btn"
                  :disabled="clearing"
                  @click="openClearDialog('older_than')"
                >
                  按时间清理
                </button>
              </div>
            </article>

            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>按服务清理</h4>
                <p>按服务名精确匹配删除该服务的全部 Span。</p>
              </div>
              <div class="si-settings__action-row">
                <div class="si-settings__action-field">
                  <label class="form-label" for="clear-svc">服务名</label>
                  <input
                    id="clear-svc"
                    v-model="clearServiceName"
                    class="form-control"
                    list="si-service-names"
                    placeholder="例如 sca-order"
                  />
                  <datalist id="si-service-names">
                    <option v-for="n in serviceNames" :key="n" :value="n" />
                  </datalist>
                </div>
                <button
                  type="button"
                  class="btn btn-outline-warning si-settings__action-btn"
                  :disabled="clearing"
                  @click="openClearDialog('service')"
                >
                  按服务清理
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>

    <Teleport to="body">
      <div
        v-if="clearDialog.open"
        class="si-confirm"
        role="presentation"
        @click.self="closeClearDialog"
      >
        <div
          class="si-confirm__panel"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="si-clear-title"
          aria-describedby="si-clear-desc"
        >
          <div class="si-confirm__icon" aria-hidden="true">
            <i class="fa fa-exclamation-triangle"></i>
          </div>
          <h3 id="si-clear-title" class="si-confirm__title">{{ clearDialog.title }}</h3>
          <p id="si-clear-desc" class="si-confirm__body">{{ clearDialog.body }}</p>
          <p class="si-confirm__warn">此操作不可恢复</p>
          <div class="si-confirm__actions">
            <button type="button" class="btn btn-outline-secondary" :disabled="clearing" @click="closeClearDialog">
              取消
            </button>
            <button type="button" class="btn btn-danger" :disabled="clearing" @click="runClear">
              <i class="fa" :class="clearing ? 'fa-spinner fa-spin' : 'fa-trash'"></i>
              {{ clearing ? '清理中…' : '确认清理' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ApiService, type RuntimeSettingsSaveBody } from '../services/ApiService'

type SettingsSection = 'alert' | 'ai' | 'data'
type ClearScope = 'all' | 'older_than' | 'service'

const sections: { id: SettingsSection; label: string; icon: string; desc: string }[] = [
  { id: 'alert', label: '告警推送', icon: 'fa-bell', desc: 'Webhook / 邮件通知规则' },
  { id: 'ai', label: 'AI 解读', icon: 'fa-magic', desc: '配置模型，解读链路与错误' },
  { id: 'data', label: '数据清理', icon: 'fa-database', desc: '查看占用，删除已存 Span' }
]

const section = ref<SettingsSection>('alert')

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
const clearDialog = reactive({
  open: false,
  scope: 'all' as ClearScope,
  title: '',
  body: ''
})

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

async function openClearDialog(scope: ClearScope) {
  if (scope === 'all') {
    if (storage.stored === 0) {
      showMsg('当前没有可清空的 Span', false)
      return
    }
    clearDialog.scope = 'all'
    clearDialog.title = '清空全部 Span'
    clearDialog.body = `将删除当前已存的全部 ${storage.stored} 条 Span。仪表盘、拓扑与链路中的对应数据也会随之消失。`
  } else if (scope === 'older_than') {
    if (!clearOlderHours.value || clearOlderHours.value < 1) {
      showMsg('请填写有效的小时数', false)
      return
    }
    clearDialog.scope = 'older_than'
    clearDialog.title = '按时间清理'
    clearDialog.body = `将删除早于 ${clearOlderHours.value} 小时的 Span（保留更近的数据）。`
  } else {
    const name = clearServiceName.value.trim()
    if (!name) {
      showMsg('请填写服务名', false)
      return
    }
    clearDialog.scope = 'service'
    clearDialog.title = '按服务清理'
    clearDialog.body = `将删除服务「${name}」的全部 Span。相关 Trace 可能不完整。`
  }
  clearDialog.open = true
}

function closeClearDialog() {
  if (clearing.value) return
  clearDialog.open = false
}

async function runClear() {
  if (!clearDialog.open || clearing.value) return
  const scope = clearDialog.scope
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
    clearDialog.open = false
    await refreshStorage()
    showMsg(`已删除 ${result.deleted} 条，剩余 ${result.remaining}。请刷新仪表盘/拓扑查看。`, true)
  } catch (e: any) {
    showMsg(e?.response?.data?.message || e?.message || '清理失败', false)
  } finally {
    clearing.value = false
  }
}

function onClearDialogKey(e: KeyboardEvent) {
  if (!clearDialog.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    closeClearDialog()
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onClearDialogKey)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onClearDialogKey)
})
</script>

<style scoped>
.si-settings__tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
  margin-bottom: 1rem;
}

@media (max-width: 900px) {
  .si-settings__tabs {
    grid-template-columns: 1fr;
  }
}

.si-settings__tab {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  width: 100%;
  padding: 0.9rem 1rem;
  border: 1px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.si-settings__tab:hover {
  border-color: rgba(15, 118, 110, 0.28);
  background: rgba(15, 118, 110, 0.04);
}

.si-settings__tab.is-active {
  border-color: rgba(15, 118, 110, 0.45);
  background: rgba(15, 118, 110, 0.08);
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.12);
}

.si-settings__tab.is-danger.is-active {
  border-color: rgba(185, 28, 28, 0.35);
  background: rgba(185, 28, 28, 0.06);
  box-shadow: inset 0 0 0 1px rgba(185, 28, 28, 0.08);
}

.si-settings__tab-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 8px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--si-teal);
  flex-shrink: 0;
}

.si-settings__tab.is-danger .si-settings__tab-icon {
  background: rgba(185, 28, 28, 0.1);
  color: #b91c1c;
}

.si-settings__tab-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.si-settings__tab-text strong {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__tab-text em {
  font-style: normal;
  font-size: 0.78rem;
  line-height: 1.35;
  color: var(--si-muted);
}

.si-settings__panel {
  min-width: 0;
}

.si-settings__card {
  padding: 1.15rem 1.25rem 1.35rem;
  border-radius: 12px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.si-settings__card--danger {
  border-color: rgba(185, 28, 28, 0.22);
  background: rgba(185, 28, 28, 0.04);
}

.si-settings__card-head,
.si-settings__block-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.si-settings__card-title,
.si-settings__block-title {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__block-title {
  font-size: 0.95rem;
}

.si-settings__card-desc,
.si-settings__block-desc {
  margin: 0;
  max-width: 36rem;
  font-size: 0.86rem;
  line-height: 1.5;
  color: var(--si-muted);
}

.si-settings__block {
  margin-top: 1.15rem;
  padding-top: 1.1rem;
  border-top: 1px dashed rgba(15, 118, 110, 0.18);
}

.si-settings__fields {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.85rem 0.75rem;
}

@media (max-width: 991px) {
  .si-settings__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 575px) {
  .si-settings__fields {
    grid-template-columns: 1fr;
  }
}

.si-settings__field--full {
  grid-column: 1 / -1;
}

.si-settings__field--wide {
  grid-column: span 3;
}

.si-settings__field--narrow {
  grid-column: span 1;
}

@media (max-width: 991px) {
  .si-settings__field--wide {
    grid-column: 1 / -1;
  }
}

.si-settings__field--toggles {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.5rem;
  align-items: center;
  padding-top: 0.25rem;
}

.si-settings .form-label {
  font-size: 0.82rem;
  margin-bottom: 0.3rem;
  color: var(--si-ink-soft);
}

.si-settings__footnote {
  margin: 1rem 0 0;
  font-size: 0.8rem;
  color: var(--si-muted);
  line-height: 1.45;
}

.si-settings code {
  font-size: 0.8em;
  word-break: break-all;
}

/* —— 数据清理 —— */
.si-settings__storage {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
  margin-bottom: 1rem;
}

@media (max-width: 700px) {
  .si-settings__storage {
    grid-template-columns: 1fr;
  }
}

.si-settings__storage-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.7rem 0.85rem;
  border-radius: 8px;
  border: 1px solid rgba(185, 28, 28, 0.14);
  background: rgba(255, 252, 250, 0.75);
}

.si-settings__storage-label {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-settings__storage-item strong {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
  font-variant-numeric: tabular-nums;
}

.si-settings__actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.si-settings__action {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.85rem 1.25rem;
  align-items: center;
  padding: 0.95rem 1rem;
  border-radius: 10px;
  border: 1px solid rgba(185, 28, 28, 0.16);
  background: rgba(255, 252, 250, 0.85);
}

@media (max-width: 767px) {
  .si-settings__action {
    grid-template-columns: 1fr;
  }
}

.si-settings__action-copy h4 {
  margin: 0 0 0.2rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__action-copy p {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
  color: var(--si-muted);
}

.si-settings__action-row {
  display: grid;
  grid-template-columns: minmax(10rem, 16rem) 8.5rem;
  gap: 0.65rem;
  align-items: end;
  width: min(100%, 26rem);
  justify-self: end;
}

@media (max-width: 767px) {
  .si-settings__action-row {
    width: 100%;
    grid-template-columns: 1fr;
    justify-self: stretch;
  }
}

.si-settings__action-field {
  min-width: 0;
}

.si-settings__action-btn {
  white-space: nowrap;
  height: calc(1.5em + 0.75rem + 2px);
}

.si-settings__action > .btn {
  justify-self: end;
  min-width: 8.5rem;
}

@media (max-width: 767px) {
  .si-settings__action > .btn {
    justify-self: stretch;
    width: 100%;
  }

  .si-settings__action-btn {
    width: 100%;
  }
}
</style>

<style>
/* Teleport 到 body，需非 scoped */
.si-confirm {
  position: fixed;
  inset: 0;
  z-index: 1080;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  background: rgba(21, 36, 31, 0.45);
  backdrop-filter: blur(2px);
}

.si-confirm__panel {
  width: min(26rem, 100%);
  padding: 1.35rem 1.4rem 1.2rem;
  border-radius: 12px;
  background: var(--card-bg, #fffcfa);
  border: 1px solid rgba(185, 28, 28, 0.22);
  box-shadow: 0 18px 40px rgba(21, 36, 31, 0.18);
  text-align: center;
}

.si-confirm__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  margin-bottom: 0.75rem;
  border-radius: 999px;
  background: rgba(185, 28, 28, 0.1);
  color: #b91c1c;
  font-size: 1.25rem;
}

.si-confirm__title {
  margin: 0 0 0.5rem;
  font-family: var(--font-display, Fraunces, Georgia, serif);
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--si-ink, #15241f);
}

.si-confirm__body {
  margin: 0 0 0.45rem;
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--si-ink-soft, #3d524a);
  text-align: left;
}

.si-confirm__warn {
  margin: 0 0 1.15rem;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #b91c1c;
}

.si-confirm__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 0.55rem;
}
</style>
