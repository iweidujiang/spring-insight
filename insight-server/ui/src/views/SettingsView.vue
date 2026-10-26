<template>
  <div class="si-page fade-in si-settings">
    <SiPageHeader class="mb-3" title="设置" icon="fa-cog">
      <el-button
        v-if="section !== 'data' && section !== 'slo'"
        type="primary"
        :loading="saving"
        :disabled="loading || saving"
        @click="onSave"
      >
        <i v-if="!saving" class="fa fa-save me-1" aria-hidden="true"></i>
        {{ saving ? '保存中…' : '保存并生效' }}
      </el-button>
    </SiPageHeader>

    <el-alert
      v-if="message"
      class="mb-3"
      :type="messageOk ? 'success' : 'error'"
      :title="message"
      :closable="false"
      show-icon
      role="status"
    />

    <SiLoading v-if="loading" text="加载设置…" />

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
                按下面条件扫描，全部满足时按启用的渠道发送；可同时配置多个渠道。
              </p>
            </div>
            <SiSwitchField id="alert-enabled" v-model="form.alert.enabled" label="启用扫描" flush />
          </header>

          <div class="si-conditions">
            <div class="si-conditions__head">
              <h4 class="si-settings__block-title"><i class="fa fa-filter me-2"></i>触发条件</h4>
              <p class="si-settings__block-desc">多条条件之间为「且」：全部成立才推送。</p>
            </div>
            <div
              v-for="(row, index) in form.alert.conditions"
              :key="'cond-' + index"
              class="si-condition"
            >
              <span v-if="index > 0" class="si-condition__and" aria-hidden="true">且</span>
              <div class="si-condition__row">
                <el-select
                  :id="'alert-metric-' + index"
                  v-model="row.metric"
                  :aria-label="'条件 ' + (index + 1) + ' 指标'"
                >
                  <el-option
                    v-for="opt in metricOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
                <el-select
                  :id="'alert-op-' + index"
                  v-model="row.operator"
                  :aria-label="'条件 ' + (index + 1) + ' 运算符'"
                >
                  <el-option
                    v-for="opt in operatorOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
                <el-input
                  :id="'alert-threshold-' + index"
                  v-model.number="row.threshold"
                  type="number"
                  :min="0"
                  :step="0.1"
                  :aria-label="'条件 ' + (index + 1) + ' 阈值'"
                  :placeholder="thresholdUnit(row.metric)"
                />
                <el-button
                  type="danger"
                  plain
                  size="small"
                  class="si-condition__remove"
                  :disabled="form.alert.conditions.length <= 1"
                  :title="form.alert.conditions.length <= 1 ? '至少保留一条条件' : '删除条件'"
                  @click="removeCondition(index)"
                >
                  <i class="fa fa-trash" aria-hidden="true"></i>
                  <span class="visually-hidden">删除</span>
                </el-button>
              </div>
            </div>
            <el-button size="small" class="si-conditions__add" @click="addCondition">
              <i class="fa fa-plus me-1" aria-hidden="true"></i>添加条件
            </el-button>
          </div>

          <el-form class="si-settings__fields" label-position="top" @submit.prevent>
            <SiFormField label="窗口（分钟）" field-id="alert-window">
              <el-input id="alert-window" v-model.number="form.alert.windowMinutes" type="number" :min="1" />
            </SiFormField>
            <SiFormField label="冷却（分钟）" field-id="alert-cooldown">
              <el-input id="alert-cooldown" v-model.number="form.alert.cooldownMinutes" type="number" :min="0" />
            </SiFormField>
          </el-form>

          <SiFormBlock title="静默时段" icon="fa-moon-o" description="时段内不推送（可跨午夜，如 22:00→08:00）。">
            <template #switch>
              <SiSwitchField id="quiet-enabled" v-model="form.alert.quietHoursEnabled" label="启用静默" flush />
            </template>
            <SiFormField label="开始（HH:mm）" field-id="quiet-start">
              <el-input id="quiet-start" v-model="form.alert.quietHoursStart" placeholder="22:00" />
            </SiFormField>
            <SiFormField label="结束（HH:mm）" field-id="quiet-end">
              <el-input id="quiet-end" v-model="form.alert.quietHoursEnd" placeholder="08:00" />
            </SiFormField>
            <SiFormField label="时区（可选）" field-id="quiet-zone" span="wide">
              <el-input
                id="quiet-zone"
                v-model="form.alert.quietHoursZone"
                placeholder="留空=服务器默认，例 Asia/Shanghai"
              />
            </SiFormField>
          </SiFormBlock>

          <SiFormBlock
            title="告警升级"
            icon="fa-level-up"
            description="持续未恢复超过设定时长后，向指定渠道再发一次升级通知（同一告警只升一次）。"
          >
            <template #switch>
              <SiSwitchField id="esc-enabled" v-model="form.alert.escalation!.enabled" label="启用升级" flush />
            </template>
            <SiFormField label="持续未恢复（分钟）" field-id="esc-after">
              <el-input
                id="esc-after"
                v-model.number="form.alert.escalation!.afterMinutes"
                type="number"
                :min="1"
              />
            </SiFormField>
            <SiFormField label="升级渠道" field-id="esc-channel" span="wide">
              <el-select id="esc-channel" v-model="form.alert.escalation!.channelId" clearable placeholder="请选择渠道" class="w-100">
                <el-option
                  v-for="ch in channels"
                  :key="'esc-' + ch.id"
                  :label="`${ch.name || ch.id}（${ch.type}）`"
                  :value="ch.id"
                />
              </el-select>
            </SiFormField>
          </SiFormBlock>

          <div class="si-channels">
            <div class="si-channels__head">
              <h4 class="si-settings__block-title"><i class="fa fa-share-alt me-2"></i>通知渠道</h4>
              <p class="si-settings__block-desc">钉钉、飞书需 Webhook 和加签密钥；企业微信只需 Webhook；通用 Webhook 可另填令牌。</p>
            </div>
            <article v-for="channel in channels" :key="channel.id" class="si-channel">
              <div class="si-channel__head">
                <button type="button" class="si-channel__toggle" @click="toggleChannel(channel.id)">
                  <i class="fa" :class="openChannels[channel.id] ? 'fa-chevron-down' : 'fa-chevron-right'"></i>
                  <i class="fa" :class="channelIcon(channel.type)"></i>
                  <strong>{{ channel.name }}</strong>
                </button>
                <SiSwitchField :id="'channel-enabled-' + channel.id" v-model="channel.enabled" label="启用" flush />
              </div>
              <el-form
                v-show="openChannels[channel.id]"
                class="si-channel__body si-settings__fields"
                label-position="top"
                @submit.prevent
              >
                <template v-if="channel.type === 'email'">
                  <SiFormField label="SMTP Host" :field-id="'smtp-host-' + channel.id" span="wide">
                    <el-input :id="'smtp-host-' + channel.id" v-model="channel.smtpHost" placeholder="smtp.example.com" />
                  </SiFormField>
                  <SiFormField label="端口" :field-id="'smtp-port-' + channel.id" span="narrow">
                    <el-input :id="'smtp-port-' + channel.id" v-model.number="channel.smtpPort" type="number" :min="1" />
                  </SiFormField>
                  <SiFormField label="用户名" :field-id="'smtp-user-' + channel.id">
                    <el-input :id="'smtp-user-' + channel.id" v-model="channel.smtpUsername" autocomplete="off" />
                  </SiFormField>
                  <SiFormField label="密码" :field-id="'smtp-pass-' + channel.id">
                    <template v-if="channel.passwordConfigured" #hint>
                      <span class="text-muted small">（已配置，留空不改）</span>
                    </template>
                    <el-input
                      :id="'smtp-pass-' + channel.id"
                      v-model="channel.smtpPassword"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      :placeholder="channel.passwordConfigured ? '••••••••' : ''"
                    />
                  </SiFormField>
                  <SiFormField label="发件人 From" :field-id="'smtp-from-' + channel.id">
                    <el-input :id="'smtp-from-' + channel.id" v-model="channel.smtpFrom" type="email" />
                  </SiFormField>
                  <SiFormField label="收件人 To（逗号分隔）" :field-id="'smtp-to-' + channel.id">
                    <el-input :id="'smtp-to-' + channel.id" v-model="channel.smtpTo" />
                  </SiFormField>
                  <SiFormField span="toggles">
                    <SiSwitchField :id="'email-starttls-' + channel.id" v-model="channel.startTls" label="STARTTLS" />
                    <SiSwitchField :id="'email-ssl-' + channel.id" v-model="channel.ssl" label="SSL" />
                  </SiFormField>
                </template>
                <template v-else>
                  <SiFormField label="Webhook URL" :field-id="'hook-' + channel.id" span="full">
                    <el-input :id="'hook-' + channel.id" v-model="channel.webhookUrl" type="url" placeholder="https://…" />
                  </SiFormField>
                  <SiFormField v-if="channel.type === 'dingtalk' || channel.type === 'feishu'" label="加签密钥" :field-id="'secret-' + channel.id" span="wide">
                    <template v-if="channel.secretConfigured" #hint>
                      <span class="text-muted small">（已配置，留空不改）</span>
                    </template>
                    <el-input
                      :id="'secret-' + channel.id"
                      v-model="channel.secret"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      :placeholder="channel.secretConfigured ? '••••••••' : ''"
                    />
                  </SiFormField>
                  <SiFormField v-if="channel.type === 'webhook'" label="令牌（可选）" :field-id="'token-' + channel.id" span="wide">
                    <template v-if="channel.tokenConfigured" #hint>
                      <span class="text-muted small">（已配置，留空不改）</span>
                    </template>
                    <el-input
                      :id="'token-' + channel.id"
                      v-model="channel.token"
                      type="password"
                      show-password
                      autocomplete="new-password"
                      :placeholder="channel.tokenConfigured ? '••••••••' : 'Authorization: Bearer'"
                    />
                  </SiFormField>
                  <SiFormField v-if="channel.type !== 'webhook'" label="控制台地址" :field-id="'server-' + channel.id" span="wide">
                    <el-input :id="'server-' + channel.id" v-model="channel.serverUrl" placeholder="http://127.0.0.1:9966" />
                  </SiFormField>
                </template>
                <div class="si-channel__actions">
                  <el-button size="small" :loading="testingId === channel.id" @click="testChannel(channel, false)">
                    <i v-if="testingId !== channel.id" class="fa fa-eye me-1" aria-hidden="true"></i>预览
                  </el-button>
                  <el-button type="primary" plain size="small" :loading="testingId === channel.id" @click="testChannel(channel, true)">
                    <i v-if="testingId !== channel.id" class="fa fa-paper-plane me-1" aria-hidden="true"></i>测试发送
                  </el-button>
                </div>
                <pre v-if="channelResults[channel.id]" class="si-settings__preview">{{ channelResults[channel.id] }}</pre>
              </el-form>
            </article>
          </div>
        </section>

        <!-- AI -->
        <section v-show="section === 'ai'" class="si-settings__card">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-magic me-2"></i>AI 解读</h3>
              <p class="si-settings__card-desc">
                对接 OpenAI 兼容接口。解读含摘要、可点证据与建议；可选在告警推送时附带短解读。
              </p>
            </div>
            <SiSwitchField id="ai-enabled" v-model="form.ai.enabled" label="启用 AI" flush />
          </header>

          <div
            class="si-settings__ai-status"
            :class="aiDraftReady ? 'is-ready' : 'is-blocked'"
            role="status"
          >
            <span class="si-settings__ai-badge">{{ aiDraftLabel }}</span>
            <div class="si-settings__ai-status-body">
              <p class="mb-0">{{ aiDraftReason }}</p>
              <p v-if="form.ai.model" class="mb-0 si-settings__ai-model">
                当前模型：<code>{{ form.ai.model }}</code>
                <template v-if="form.ai.provider"> · {{ form.ai.provider }}</template>
              </p>
              <p v-if="aiServerReason && aiServerReason !== aiDraftReason" class="mb-0 text-muted small">
                已保存状态{{ aiServerLabel ? `（${aiServerLabel}）` : '' }}：{{ aiServerReason }}
              </p>
            </div>
          </div>

          <el-form class="si-settings__fields" label-position="top" @submit.prevent>
            <SiFormField label="Provider" field-id="ai-provider">
              <el-input id="ai-provider" v-model="form.ai.provider" placeholder="openai-compatible" />
            </SiFormField>
            <SiFormField label="Model" field-id="ai-model">
              <el-input id="ai-model" v-model="form.ai.model" placeholder="deepseek-chat" />
            </SiFormField>
            <SiFormField label="Base URL" field-id="ai-base" span="full">
              <el-input id="ai-base" v-model="form.ai.baseUrl" placeholder="https://api.deepseek.com/v1" />
            </SiFormField>
            <SiFormField label="API Key" field-id="ai-key" span="full">
              <template v-if="apiKeyConfigured" #hint>
                <span class="text-muted small">（已配置，留空不改）</span>
              </template>
              <el-input
                id="ai-key"
                v-model="form.ai.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="apiKeyConfigured ? '••••••••' : ''"
              />
            </SiFormField>
            <SiFormField label="超时（ms）" field-id="ai-timeout">
              <el-input id="ai-timeout" v-model.number="form.ai.timeoutMs" type="number" :min="1000" />
            </SiFormField>
            <SiFormField label="最大输入 Span" field-id="ai-spans">
              <el-input id="ai-spans" v-model.number="form.ai.maxInputSpans" type="number" :min="1" />
            </SiFormField>
            <SiFormField label="maxTokens" field-id="ai-tokens">
              <el-input id="ai-tokens" v-model.number="form.ai.maxTokens" type="number" :min="64" />
            </SiFormField>
            <SiFormField span="full">
              <SiSwitchField id="ai-attach-alerts" v-model="form.ai.attachToAlerts" label="告警附带 AI 解读" />
              <template #note>
                <p class="form-text mb-0">开启后，Webhook / 邮件会附带短摘要与建议（须同时启用 AI；失败不阻断推送）。</p>
              </template>
            </SiFormField>
            <SiFormField label="告警 AI 每小时上限" field-id="ai-alert-max">
              <el-input
                id="ai-alert-max"
                v-model.number="form.ai.alertMaxPerHour"
                type="number"
                :min="1"
                :disabled="!form.ai.attachToAlerts"
              />
            </SiFormField>
            <SiFormField label="时段 AI 每小时上限" field-id="ai-period-max">
              <el-input
                id="ai-period-max"
                v-model.number="form.ai.periodMaxPerHour"
                type="number"
                :min="1"
              />
              <template #note>
                <p class="form-text mb-0">仪表盘「时段摘要 → AI 小结」按钮触发；超出后本小时内降级，不调模型。</p>
              </template>
            </SiFormField>
          </el-form>
          <p class="si-settings__footnote">
            使用 Chat Completions 兼容接口即可对接各类大模型；API Key 仅保存在 Server 本机数据目录。
          </p>
        </section>

        <!-- SLO -->
        <section v-show="section === 'slo'" class="si-settings__card">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-bullseye me-2"></i>服务 SLO</h3>
              <p class="si-settings__card-desc">
                按服务设定可用性目标与观察窗口，控制台据此计算错误预算；配置写入本机 runtime-settings.json。
              </p>
            </div>
          </header>

          <el-form class="si-settings__slo-form si-settings__grid" label-position="top" @submit.prevent>
            <SiFormField label="服务名" field-id="slo-service">
              <el-input
                id="slo-service"
                v-model="sloDraft.serviceName"
                clearable
                list="slo-service-list"
                placeholder="如 sca-user"
              />
              <datalist id="slo-service-list">
                <option v-for="name in serviceNames" :key="'slo-' + name" :value="name" />
              </datalist>
            </SiFormField>
            <SiFormField label="目标可用性" field-id="slo-target">
              <el-input
                id="slo-target"
                v-model.number="sloDraft.targetAvailability"
                type="number"
                :min="0.9"
                :max="1"
                :step="0.0001"
                placeholder="0.999"
              />
              <p class="si-settings__hint mb-0">例如 0.999 = 99.9%</p>
            </SiFormField>
            <SiFormField label="窗口（小时）" field-id="slo-window">
              <el-input
                id="slo-window"
                v-model.number="sloDraft.windowHours"
                type="number"
                :min="1"
                placeholder="720"
              />
              <p class="si-settings__hint mb-0">默认 720（约 30 天）</p>
            </SiFormField>
          </el-form>
          <div class="d-flex gap-2 mb-3">
            <el-button
              type="primary"
              :loading="sloSaving"
              :disabled="sloSaving || !sloDraft.serviceName.trim()"
              @click="saveSloDraft"
            >
              <i v-if="!sloSaving" class="fa fa-plus me-1" aria-hidden="true"></i>
              {{ sloEditing ? '更新 SLO' : '添加 SLO' }}
            </el-button>
            <el-button
              v-if="sloEditing"
              :disabled="sloSaving"
              @click="resetSloDraft"
            >
              取消编辑
            </el-button>
          </div>

          <div v-if="!sloRows.length" class="text-muted small">尚未配置 SLO，添加后可在仪表盘查看错误预算。</div>
          <ul v-else class="si-settings__slo-list list-unstyled mb-0">
            <li v-for="row in sloRows" :key="row.serviceName" class="si-settings__slo-item">
              <div>
                <strong>{{ row.serviceName }}</strong>
                <span class="text-muted ms-2">
                  目标 {{ (row.targetAvailability * 100).toFixed(2) }}% · 窗口 {{ row.windowHours }}h
                </span>
              </div>
              <div class="si-settings__slo-actions">
                <el-button size="small" @click="editSlo(row)">编辑</el-button>
                <el-button type="danger" plain size="small" @click="removeSlo(row.serviceName)">删除</el-button>
              </div>
            </li>
          </ul>
        </section>

        <!-- 容量与保留 -->
        <section v-show="section === 'data'" class="si-settings__card si-settings__card--danger">
          <header class="si-settings__card-head">
            <div>
              <h3 class="si-settings__card-title"><i class="fa fa-database me-2"></i>容量与保留</h3>
              <p class="si-settings__card-desc">
                查看占用与自动保留策略，并按范围删除 Span。清除不可恢复；按服务精确匹配时，相关 Trace 可能不完整。
              </p>
            </div>
          </header>

          <div class="si-settings__capacity">
            <el-alert
              v-if="capacityWarn"
              class="mb-2"
              :type="usagePercent >= 95 ? 'error' : 'warning'"
              :title="capacityWarn"
              :closable="false"
              show-icon
              role="status"
            />
            <div class="si-settings__usage">
              <div class="si-settings__usage-head">
                <span>已存 / 上限</span>
                <strong>{{ storage.stored }} / {{ storage.max }}（{{ usagePercentLabel }}）</strong>
              </div>
              <div
                class="si-settings__usage-bar"
                role="progressbar"
                :aria-valuenow="usagePercent"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-label="`存储占用 ${usagePercentLabel}`"
              >
                <div
                  class="si-settings__usage-fill"
                  :class="{ 'is-warn': usagePercent >= 80, 'is-danger': usagePercent >= 95 }"
                  :style="{ width: usagePercent + '%' }"
                />
              </div>
            </div>

            <div class="si-settings__storage">
              <div class="si-settings__storage-item">
                <span class="si-settings__storage-label">存储模式</span>
                <strong>{{ storage.mode || '—' }}</strong>
              </div>
              <div class="si-settings__storage-item">
                <span class="si-settings__storage-label">累计裁剪</span>
                <strong>{{ storage.evicted }}</strong>
              </div>
              <div class="si-settings__storage-item si-settings__storage-item--wide">
                <span class="si-settings__storage-label">保留策略</span>
                <strong>{{ retentionLabel }}</strong>
              </div>
              <div v-if="storagePathLabel" class="si-settings__storage-item si-settings__storage-item--wide">
                <span class="si-settings__storage-label">落盘路径</span>
                <strong class="si-settings__path">{{ storagePathLabel }}</strong>
              </div>
            </div>
          </div>

          <div class="si-settings__actions">
            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>清空全部</h4>
                <p>删除当前已存的全部 Span。</p>
              </div>
              <el-button
                type="danger"
                plain
                :disabled="clearing || storage.stored === 0"
                :title="storage.stored === 0 ? '当前没有可清空的 Span' : undefined"
                @click="openClearDialog('all')"
              >
                清空全部
              </el-button>
            </article>

            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>按时间清理</h4>
                <p>只删除早于指定小时数的 Span，保留更近的数据。</p>
              </div>
              <div class="si-settings__action-row">
                <SiFormField class="si-settings__action-field" label="早于（小时）" field-id="clear-hours">
                  <el-input id="clear-hours" v-model.number="clearOlderHours" type="number" :min="1" />
                </SiFormField>
                <el-button
                  type="warning"
                  plain
                  class="si-settings__action-btn"
                  :disabled="clearing"
                  @click="openClearDialog('older_than')"
                >
                  按时间清理
                </el-button>
              </div>
            </article>

            <article class="si-settings__action">
              <div class="si-settings__action-copy">
                <h4>按服务清理</h4>
                <p>按服务名精确匹配删除该服务的全部 Span。</p>
              </div>
              <div class="si-settings__action-row">
                <SiFormField class="si-settings__action-field" label="服务名" field-id="clear-svc">
                  <el-input
                    id="clear-svc"
                    v-model="clearServiceName"
                    clearable
                    placeholder="例如 sca-order"
                  />
                  <datalist id="si-service-names">
                    <option v-for="n in serviceNames" :key="n" :value="n" />
                  </datalist>
                </SiFormField>
                <el-button
                  type="warning"
                  plain
                  class="si-settings__action-btn"
                  :disabled="clearing"
                  @click="openClearDialog('service')"
                >
                  按服务清理
                </el-button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>

    <el-dialog
      v-model="clearDialog.open"
      :title="clearDialog.title"
      width="420px"
      align-center
      :close-on-click-modal="!clearing"
      @close="closeClearDialog"
    >
      <p>{{ clearDialog.body }}</p>
      <p class="si-confirm__warn mb-0">此操作不可恢复</p>
      <template #footer>
        <el-button :disabled="clearing" @click="closeClearDialog">取消</el-button>
        <el-button type="danger" :loading="clearing" @click="runClear">
          <i v-if="!clearing" class="fa fa-trash me-1" aria-hidden="true"></i>
          {{ clearing ? '清理中…' : '确认清理' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import SiFormBlock from '../components/SiFormBlock.vue'
import SiFormField from '../components/SiFormField.vue'
import SiLoading from '../components/SiLoading.vue'
import SiPageHeader from '../components/SiPageHeader.vue'
import SiSwitchField from '../components/SiSwitchField.vue'
import {
  ApiService,
  type AlertChannelView,
  type AlertConditionView,
  type RuntimeSettingsSaveBody,
  type ServiceSlo
} from '../services/ApiService'
import { capacityWarnMessage } from '../utils/timeRange'

type SettingsSection = 'alert' | 'ai' | 'slo' | 'data'
type ClearScope = 'all' | 'older_than' | 'service'

const sections: { id: SettingsSection; label: string; icon: string; desc: string }[] = [
  { id: 'alert', label: '告警推送', icon: 'fa-bell', desc: '扫描规则与通知渠道' },
  { id: 'ai', label: 'AI 解读', icon: 'fa-magic', desc: '配置模型，解读链路与错误' },
  { id: 'slo', label: '服务 SLO', icon: 'fa-bullseye', desc: '可用性目标与错误预算' },
  { id: 'data', label: '容量与保留', icon: 'fa-database', desc: '占用、保留策略与清理' }
]

const route = useRoute()
const section = ref<SettingsSection>('alert')

const loading = ref(true)
const saving = ref(false)
const clearing = ref(false)
const message = ref('')
const messageOk = ref(false)
const settingsPath = ref('')
const apiKeyConfigured = ref(false)
const aiServerReason = ref('')
const aiServerLabel = ref('')
const clearOlderHours = ref(24)
const clearServiceName = ref('')
const serviceNames = ref<string[]>([])
const sloRows = ref<ServiceSlo[]>([])
const sloSaving = ref(false)
const sloEditing = ref(false)
const sloEditOriginal = ref('')
const sloDraft = reactive<ServiceSlo>({
  serviceName: '',
  targetAvailability: 0.999,
  windowHours: 720
})
const storage = reactive({
  mode: '',
  stored: 0,
  max: 0,
  evicted: 0,
  maxAgeHours: 0,
  retentionEnabled: false,
  usageRatio: 0,
  filePath: '',
  sqlitePath: ''
})

const usagePercent = computed(() => {
  const ratio = Number(storage.usageRatio)
  if (Number.isFinite(ratio) && ratio >= 0) {
    return Math.min(100, Math.round(ratio * 100))
  }
  if (storage.max > 0) {
    return Math.min(100, Math.round((storage.stored / storage.max) * 100))
  }
  return 0
})
const usagePercentLabel = computed(() => `${usagePercent.value}%`)
const capacityWarn = computed(() => capacityWarnMessage(usagePercent.value))
const retentionLabel = computed(() =>
  storage.retentionEnabled && storage.maxAgeHours > 0
    ? `自动保留最近 ${storage.maxAgeHours} 小时（超时裁剪）`
    : '未按时间裁剪，仅条数上限'
)
const storagePathLabel = computed(() => {
  if (storage.mode === 'file' && storage.filePath) return storage.filePath
  if (storage.mode === 'sqlite' && storage.sqlitePath) return storage.sqlitePath
  return ''
})
const clearDialog = reactive({
  open: false,
  scope: 'all' as ClearScope,
  title: '',
  body: ''
})

type ChannelType = 'webhook' | 'email' | 'dingtalk' | 'feishu' | 'wecom'

interface ChannelDraft {
  id: string
  type: ChannelType
  enabled: boolean
  name: string
  webhookUrl: string
  secret: string
  token: string
  serverUrl: string
  secretConfigured: boolean
  tokenConfigured: boolean
  passwordConfigured: boolean
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  smtpPassword: string
  smtpFrom: string
  smtpTo: string
  startTls: boolean
  ssl: boolean
}

const CHANNEL_TYPES: ChannelType[] = ['webhook', 'email', 'dingtalk', 'feishu', 'wecom']

const testingId = ref('')
const channelResults = reactive<Record<string, string>>({})
const openChannels = reactive<Record<string, boolean>>({ webhook: true })
const channels = ref<ChannelDraft[]>([])

const aiDraftReady = computed(() => {
  if (!form.ai.enabled) return false
  const keyOk = !!(form.ai.apiKey?.trim() || apiKeyConfigured.value)
  const baseOk = !!form.ai.baseUrl?.trim()
  const provider = (form.ai.provider || 'openai-compatible').trim().toLowerCase()
  const providerOk =
    !provider ||
    provider === 'openai-compatible' ||
    provider === 'openai' ||
    provider === 'deepseek'
  return keyOk && baseOk && providerOk
})

const aiDraftLabel = computed(() => {
  if (aiDraftReady.value) return '就绪'
  if (!form.ai.enabled) return '未启用'
  return '未就绪'
})

const aiDraftReason = computed(() => {
  if (!form.ai.enabled) {
    return '未启用：打开上方开关并填写 base-url / api-key 后保存。'
  }
  if (!(form.ai.apiKey?.trim() || apiKeyConfigured.value)) {
    return '已启用但未配置 api-key（保存后才会生效）。'
  }
  if (!form.ai.baseUrl?.trim()) {
    return '已启用但未配置 base-url。'
  }
  const provider = (form.ai.provider || '').trim().toLowerCase()
  if (
    provider &&
    provider !== 'openai-compatible' &&
    provider !== 'openai' &&
    provider !== 'deepseek'
  ) {
    return '当前仅支持 provider=openai-compatible（DeepSeek / OpenAI / 兼容网关）。'
  }
  const model = form.ai.model?.trim()
  return model ? `草稿就绪：保存后可调用模型 ${model}` : '草稿就绪：保存后可调用模型。'
})

function blankChannel(type: ChannelType): ChannelDraft {
  const names: Record<ChannelType, string> = {
    webhook: 'Webhook',
    email: '邮件',
    dingtalk: '钉钉',
    feishu: '飞书',
    wecom: '企业微信'
  }
  return {
    id: type,
    type,
    enabled: false,
    name: names[type],
    webhookUrl: '',
    secret: '',
    token: '',
    serverUrl: '',
    secretConfigured: false,
    tokenConfigured: false,
    passwordConfigured: false,
    smtpHost: '',
    smtpPort: 587,
    smtpUsername: '',
    smtpPassword: '',
    smtpFrom: '',
    smtpTo: '',
    startTls: true,
    ssl: false
  }
}

channels.value = CHANNEL_TYPES.map((type) => blankChannel(type))

function channelIcon(type: ChannelType) {
  if (type === 'email') return 'fa-envelope'
  if (type === 'dingtalk') return 'fa-commenting'
  if (type === 'feishu') return 'fa-paper-plane'
  if (type === 'wecom') return 'fa-weixin'
  return 'fa-link'
}

function toggleChannel(id: string) {
  openChannels[id] = !openChannels[id]
}

function applyChannels(rows: AlertChannelView[] | undefined) {
  const byType = new Map((rows || []).map((row) => [row.type, row]))
  channels.value = CHANNEL_TYPES.map((type) => {
    const row = byType.get(type)
    const draft = blankChannel(type)
    if (!row) return draft
    draft.enabled = !!row.enabled
    draft.name = row.name || draft.name
    draft.webhookUrl = row.webhookUrl || ''
    draft.serverUrl = row.serverUrl || ''
    draft.secretConfigured = !!row.secretConfigured
    draft.tokenConfigured = !!row.tokenConfigured
    draft.passwordConfigured = !!row.passwordConfigured
    draft.smtpHost = row.smtpHost || ''
    draft.smtpPort = Number(row.smtpPort ?? 587)
    draft.smtpUsername = row.smtpUsername || ''
    draft.smtpFrom = row.smtpFrom || ''
    draft.smtpTo = row.smtpTo || ''
    draft.startTls = row.startTls !== false
    draft.ssl = !!row.ssl
    return draft
  })
}

function syncLegacyFromChannels() {
  const webhook = channels.value.find((item) => item.type === 'webhook')
  const email = channels.value.find((item) => item.type === 'email')
  form.alert.webhookUrl = webhook?.webhookUrl || ''
  form.alert.email.enabled = !!email?.enabled
  form.alert.email.host = email?.smtpHost || ''
  form.alert.email.port = email?.smtpPort || 587
  form.alert.email.username = email?.smtpUsername || ''
  form.alert.email.password = email?.smtpPassword || ''
  form.alert.email.from = email?.smtpFrom || ''
  form.alert.email.to = email?.smtpTo || ''
  form.alert.email.startTls = email?.startTls !== false
  form.alert.email.ssl = !!email?.ssl
}

const metricOptions = [
  { value: 'error_rate', label: '错误率(%)' },
  { value: 'error_count', label: '错误次数' },
  { value: 'latency_p95', label: 'p95延迟(ms)' },
  { value: 'span_count', label: '请求量' }
] as const

const operatorOptions = [
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lte', label: '小于等于' },
  { value: 'eq', label: '等于' }
] as const

function blankCondition(metric = 'error_rate', operator = 'gte', threshold = 10): AlertConditionView {
  return { metric, operator, threshold }
}

function thresholdUnit(metric: string) {
  if (metric === 'latency_p95') return '毫秒'
  if (metric === 'error_count' || metric === 'span_count') return '次'
  return '%'
}

function normalizeCondition(row: Partial<AlertConditionView> | undefined): AlertConditionView {
  const metric = row?.metric || 'error_rate'
  const allowedOps = new Set<string>(operatorOptions.map((item) => item.value))
  const operator = row?.operator && allowedOps.has(row.operator) ? row.operator : 'gte'
  const threshold = Number(row?.threshold)
  return blankCondition(metric, operator, Number.isFinite(threshold) ? threshold : 10)
}

function applyConditions(rows: AlertConditionView[] | undefined, metric?: string, threshold?: number) {
  if (rows && rows.length > 0) {
    form.alert.conditions = rows.map((row) => normalizeCondition(row))
  } else {
    form.alert.conditions = [blankCondition(metric || 'error_rate', 'gte', Number(threshold ?? 10))]
  }
  syncLegacyFromConditions()
}

function syncLegacyFromConditions() {
  const first = form.alert.conditions[0] || blankCondition()
  form.alert.metric = first.metric
  form.alert.threshold = first.threshold
}

function addCondition() {
  form.alert.conditions.push(blankCondition())
}

function removeCondition(index: number) {
  if (form.alert.conditions.length <= 1) return
  form.alert.conditions.splice(index, 1)
  syncLegacyFromConditions()
}

const form = reactive<RuntimeSettingsSaveBody>({
  alert: {
    enabled: false,
    webhookUrl: '',
    metric: 'error_rate',
    threshold: 10,
    conditions: [blankCondition()],
    windowMinutes: 15,
    cooldownMinutes: 30,
    quietHoursEnabled: false,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00',
    quietHoursZone: '',
    escalation: {
      enabled: false,
      afterMinutes: 30,
      channelId: ''
    },
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
    maxTokens: 800,
    attachToAlerts: false,
    alertMaxPerHour: 10,
    periodMaxPerHour: 20
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
  storage.maxAgeHours = Number(s.maxAgeHours ?? 0)
  storage.retentionEnabled = !!s.retentionEnabled
  storage.usageRatio = Number(s.usageRatio ?? 0)
  storage.filePath = s.filePath || ''
  storage.sqlitePath = s.sqlitePath || ''
}

async function refreshAiStatus() {
  try {
    const st = await ApiService.getAiStatus()
    aiServerLabel.value = st.statusLabel || ''
    aiServerReason.value = st.reason || ''
  } catch {
    aiServerLabel.value = ''
    aiServerReason.value = ''
  }
}

function resetSloDraft() {
  sloEditing.value = false
  sloEditOriginal.value = ''
  sloDraft.serviceName = ''
  sloDraft.targetAvailability = 0.999
  sloDraft.windowHours = 720
}

function editSlo(row: ServiceSlo) {
  sloEditing.value = true
  sloEditOriginal.value = row.serviceName
  sloDraft.serviceName = row.serviceName
  sloDraft.targetAvailability = row.targetAvailability
  sloDraft.windowHours = row.windowHours
  section.value = 'slo'
}

async function loadSlos() {
  sloRows.value = await ApiService.listSlos()
}

async function saveSloDraft() {
  const name = sloDraft.serviceName.trim()
  if (!name) {
    showMsg('请填写服务名', false)
    return
  }
  const target = Number(sloDraft.targetAvailability)
  if (!(target > 0 && target <= 1)) {
    showMsg('目标可用性须在 (0, 1] 内，例如 0.999', false)
    return
  }
  const windowHours = Number(sloDraft.windowHours) > 0 ? Number(sloDraft.windowHours) : 720
  sloSaving.value = true
  message.value = ''
  try {
    const body: ServiceSlo = { serviceName: name, targetAvailability: target, windowHours }
    if (sloEditing.value && sloEditOriginal.value) {
      await ApiService.updateSlo(sloEditOriginal.value, body)
      showMsg(`已更新 ${name} 的 SLO`, true)
    } else {
      await ApiService.createSlo(body)
      showMsg(`已添加 ${name} 的 SLO`, true)
    }
    resetSloDraft()
    await loadSlos()
  } catch (e: any) {
    showMsg(e?.response?.data?.message || e?.message || '保存 SLO 失败', false)
  } finally {
    sloSaving.value = false
  }
}

async function removeSlo(serviceName: string) {
  if (!serviceName || !window.confirm(`确定删除 ${serviceName} 的 SLO？`)) return
  try {
    await ApiService.deleteSlo(serviceName)
    if (sloEditOriginal.value === serviceName) resetSloDraft()
    await loadSlos()
    showMsg(`已删除 ${serviceName}`, true)
  } catch (e: any) {
    showMsg(e?.response?.data?.message || e?.message || '删除失败', false)
  }
}

async function load() {
  loading.value = true
  message.value = ''
  try {
    const data = await ApiService.getSettings()
    settingsPath.value = data.settingsPath || ''
    form.alert.enabled = !!data.alert?.enabled
    form.alert.webhookUrl = data.alert?.webhookUrl || ''
    applyConditions(data.alert?.conditions, data.alert?.metric, data.alert?.threshold)
    form.alert.windowMinutes = Number(data.alert?.windowMinutes ?? 15)
    form.alert.cooldownMinutes = Number(data.alert?.cooldownMinutes ?? 30)
    form.alert.quietHoursEnabled = !!data.alert?.quietHoursEnabled
    form.alert.quietHoursStart = data.alert?.quietHoursStart || '22:00'
    form.alert.quietHoursEnd = data.alert?.quietHoursEnd || '08:00'
    form.alert.quietHoursZone = data.alert?.quietHoursZone || ''
    form.alert.escalation = {
      enabled: !!data.alert?.escalation?.enabled,
      afterMinutes: Number(data.alert?.escalation?.afterMinutes ?? 30) || 30,
      channelId: data.alert?.escalation?.channelId || ''
    }
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
    applyChannels(data.channels || data.alert?.channels)

    form.ai.enabled = !!data.ai?.enabled
    form.ai.provider = data.ai?.provider || 'openai-compatible'
    form.ai.baseUrl = data.ai?.baseUrl || ''
    form.ai.apiKey = ''
    form.ai.model = data.ai?.model || ''
    form.ai.timeoutMs = Number(data.ai?.timeoutMs ?? 30000)
    form.ai.maxInputSpans = Number(data.ai?.maxInputSpans ?? 40)
    form.ai.maxTokens = Number(data.ai?.maxTokens ?? 800)
    form.ai.attachToAlerts = !!data.ai?.attachToAlerts
    form.ai.alertMaxPerHour = Number(data.ai?.alertMaxPerHour ?? 10)
    form.ai.periodMaxPerHour = Number(data.ai?.periodMaxPerHour ?? 20)
    apiKeyConfigured.value = !!data.ai?.apiKeyConfigured

    await refreshStorage()
    await refreshAiStatus()
    serviceNames.value = await ApiService.getServiceNames()
    await loadSlos()
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
    syncLegacyFromChannels()
    syncLegacyFromConditions()
    const saved = await ApiService.saveSettings({
      alert: {
        ...form.alert,
        email: { ...form.alert.email },
        conditions: form.alert.conditions.map((row) => normalizeCondition(row))
      },
      ai: { ...form.ai },
      channels: channels.value.map((channel) => ({
        id: channel.id,
        type: channel.type,
        enabled: channel.enabled,
        name: channel.name,
        webhookUrl: channel.webhookUrl,
        secret: channel.secret,
        token: channel.token,
        serverUrl: channel.serverUrl,
        smtpHost: channel.smtpHost,
        smtpPort: channel.smtpPort,
        smtpUsername: channel.smtpUsername,
        smtpPassword: channel.smtpPassword,
        smtpFrom: channel.smtpFrom,
        smtpTo: channel.smtpTo,
        startTls: channel.startTls,
        ssl: channel.ssl
      }))
    })
    apiKeyConfigured.value = !!saved.ai?.apiKeyConfigured
    form.alert.email.password = ''
    form.ai.apiKey = ''
    applyConditions(saved.alert?.conditions, saved.alert?.metric, saved.alert?.threshold)
    applyChannels(saved.channels || saved.alert?.channels)
    settingsPath.value = saved.settingsPath || settingsPath.value
    await refreshAiStatus()
    showMsg('已保存并生效', true)
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '保存失败'
    showMsg(msg, false)
  } finally {
    saving.value = false
  }
}

async function testChannel(channel: ChannelDraft, send: boolean) {
  testingId.value = channel.id
  channelResults[channel.id] = ''
  try {
    const res = await ApiService.testAlertChannel({
      send,
      id: channel.id,
      type: channel.type,
      webhookUrl: channel.webhookUrl,
      secret: channel.secret,
      token: channel.token,
      serverUrl: channel.serverUrl,
      smtpHost: channel.smtpHost,
      smtpPort: channel.smtpPort,
      smtpUsername: channel.smtpUsername,
      smtpPassword: channel.smtpPassword,
      smtpFrom: channel.smtpFrom,
      smtpTo: channel.smtpTo,
      startTls: channel.startTls,
      ssl: channel.ssl
    })
    channelResults[channel.id] = JSON.stringify(res, null, 2)
    if (send) {
      showMsg(res.ok ? `${channel.name} 测试发送成功` : `${channel.name} 测试发送失败：${res.error || '未成功'}`, !!res.ok)
    } else {
      showMsg(`已生成${channel.name}预览（未发送）`, true)
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '测试失败'
    showMsg(msg, false)
  } finally {
    testingId.value = ''
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
  const q = String(route.query.section || '')
  if (q === 'alert' || q === 'ai' || q === 'slo' || q === 'data') {
    section.value = q
  }
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
  margin-bottom: 1rem;
}

@media (min-width: 900px) {
  .si-settings__tabs {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 899px) {
  .si-settings__tabs {
    grid-template-columns: 1fr;
  }
}

.si-settings__slo-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.si-settings__slo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--si-border, #dee2e6);
  border-radius: 0.5rem;
  background: var(--si-surface, #fff);
}

.si-settings__tab {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  padding: 0.85rem 1rem;
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
  flex-shrink: 0;
  color: var(--si-teal);
  font-size: 1.05rem;
  line-height: 1;
}

.si-settings__tab.is-danger .si-settings__tab-icon {
  color: var(--danger-color);
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
.si-settings__block-head,
:deep(.si-settings__block-head) {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.si-settings__card-title,
.si-settings__block-title,
:deep(.si-settings__block-title) {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__block-title,
:deep(.si-settings__block-title) {
  font-size: 0.95rem;
}

.si-settings__card-desc,
.si-settings__block-desc,
:deep(.si-settings__block-desc) {
  margin: 0;
  max-width: 36rem;
  font-size: 0.86rem;
  line-height: 1.5;
  color: var(--si-muted);
}

.si-settings__block,
:deep(.si-settings__block) {
  margin-top: 1.15rem;
  padding-top: 1.1rem;
  border-top: 1px dashed rgba(15, 118, 110, 0.18);
}

.si-settings__fields,
:deep(.si-settings__fields) {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.85rem 0.75rem;
}

@media (max-width: 991px) {
  .si-settings__fields,
:deep(.si-settings__fields) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 575px) {
  .si-settings__fields,
:deep(.si-settings__fields) {
    grid-template-columns: 1fr;
  }
}

.si-settings__field--full,
:deep(.si-settings__field--full) {
  grid-column: 1 / -1;
}

.si-settings__webhook-row {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 0.45rem;
}

.si-settings__webhook-row .form-control {
  flex: 1 1 16rem;
  min-width: 12rem;
}

.si-settings__webhook-row .btn {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  flex-shrink: 0;
}

.si-conditions {
  margin: 0.35rem 0 1rem;
}

.si-conditions__head {
  margin-bottom: 0.65rem;
}

.si-condition {
  position: relative;
  margin-bottom: 0.55rem;
}

.si-condition__and {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.7rem;
  margin: 0 0 0.35rem 0.15rem;
  padding: 0.1rem 0.45rem;
  border: 1px solid var(--card-border);
  border-radius: 999px;
  background: var(--si-paper);
  color: var(--si-muted);
  font-size: 0.72rem;
  font-weight: 650;
  line-height: 1.2;
}

.si-condition__row {
  display: grid;
  grid-template-columns: minmax(7rem, 1.4fr) minmax(6rem, 1fr) minmax(5rem, 0.9fr) auto;
  gap: 0.45rem;
  align-items: center;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--card-border);
  border-radius: 8px;
  background: var(--si-paper);
  color: var(--si-ink);
}

.si-condition__remove {
  justify-self: end;
  white-space: nowrap;
}

.si-conditions__add {
  margin-top: 0.15rem;
}

@media (max-width: 575px) {
  .si-condition__row {
    grid-template-columns: 1fr 1fr;
  }

  .si-condition__remove {
    grid-column: 1 / -1;
    justify-self: start;
  }
}

.si-channels {
  margin-top: 1.15rem;
  padding-top: 1.1rem;
  border-top: 1px dashed var(--card-border);
}

.si-channels__head {
  margin-bottom: 0.75rem;
}

.si-channel {
  margin-bottom: 0.65rem;
  border: 1px solid var(--card-border);
  border-radius: 8px;
  background: var(--si-paper);
  color: var(--si-ink);
}

.si-channel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.55rem 0.75rem;
}

.si-channel__toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--si-ink);
  font-weight: 650;
}

.si-channel__body {
  padding: 0 0.75rem 0.75rem;
}

.si-channel__actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.35rem;
}

.si-channel__body > .si-settings__preview {
  grid-column: 1 / -1;
}

.si-settings__preview {
  max-height: 14rem;
  overflow: auto;
  padding: 0.65rem 0.75rem;
  border-radius: 6px;
  border: 1px solid var(--card-border);
  background: var(--si-paper, #fafaf8);
  font-size: 0.75rem;
  line-height: 1.45;
  color: var(--si-ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.si-settings__field--wide,
:deep(.si-settings__field--wide) {
  grid-column: span 3;
}

.si-settings__field--narrow,
:deep(.si-settings__field--narrow) {
  grid-column: span 1;
}

@media (max-width: 991px) {
  .si-settings__field--wide,
:deep(.si-settings__field--wide) {
    grid-column: 1 / -1;
  }
}

.si-settings__field--toggles,
:deep(.si-settings__field--toggles) {
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

.si-settings__ai-status {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin: 0 0 1rem;
  padding: 0.7rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.si-settings__ai-status.is-ready {
  border-color: rgba(15, 118, 110, 0.35);
  background: rgba(15, 118, 110, 0.08);
}

.si-settings__ai-status.is-blocked {
  border-color: rgba(180, 83, 9, 0.3);
  background: var(--si-amber-bg);
}

.si-settings__ai-badge {
  flex-shrink: 0;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  background: rgba(0, 0, 0, 0.06);
  color: var(--si-ink);
}

.si-settings__ai-status.is-ready .si-settings__ai-badge {
  background: rgba(15, 118, 110, 0.18);
  color: #0f766e;
}

.si-settings__ai-status.is-blocked .si-settings__ai-badge {
  background: rgba(180, 83, 9, 0.18);
  color: #9a3412;
}

.si-settings__ai-status-body {
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--si-ink);
}

.si-settings__ai-model {
  margin-top: 0.25rem !important;
  font-size: 0.8rem;
  color: var(--si-muted);
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

/* —— 容量与保留 —— */
.si-settings__capacity {
  margin-bottom: 1rem;
}

.si-settings__usage {
  margin-bottom: 0.75rem;
  padding: 0.85rem 1rem;
  border-radius: 8px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.si-settings__usage-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.35rem 1rem;
  margin-bottom: 0.55rem;
  font-size: 0.82rem;
  color: var(--si-ink-soft);
}

.si-settings__usage-head strong {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__usage-bar {
  height: 0.45rem;
  border-radius: 999px;
  background: var(--si-track-bg);
  overflow: hidden;
}

.si-settings__usage-fill {
  height: 100%;
  border-radius: inherit;
  background: #0f766e;
  transition: width 0.25s ease;
}

.si-settings__usage-fill.is-warn {
  background: #b45309;
}

.si-settings__usage-fill.is-danger {
  background: #b91c1c;
}

.si-settings__storage {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
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
  border: 1px solid var(--card-border);
  background: var(--card-bg);
}

.si-settings__storage-item--wide {
  grid-column: 1 / -1;
}

.si-settings__storage-label {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--si-muted);
}

.si-settings__storage-item strong {
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--si-ink);
}

.si-settings__path {
  font-size: 0.9rem !important;
  word-break: break-all;
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
  border: 1px solid var(--card-border);
  background: var(--card-bg);
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
