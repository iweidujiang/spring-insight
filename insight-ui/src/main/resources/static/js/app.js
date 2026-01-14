// Spring Insight UI - 主应用JavaScript文件

class SpringInsightApp {
    constructor() {
        this.isConnected = false;
        this.stompClient = null;
        this.refreshInterval = null;
        this.toastContainer = null;
        this.loadingCount = 0;

        this.init();
    }

    init() {
        this.initToastContainer();
        this.initEventListeners();
        this.initWebSocket();
        this.initAutoRefresh();

        // 显示欢迎消息
        setTimeout(() => {
            this.showToast('Spring Insight 已就绪', 'success');
        }, 1000);
    }

    // ========== Toast消息 ==========

    initToastContainer() {
        this.toastContainer = document.getElementById('toast-container');
        if (!this.toastContainer) {
            this.toastContainer = document.createElement('div');
            this.toastContainer.id = 'toast-container';
            this.toastContainer.className = 'position-fixed top-0 end-0 p-3';
            this.toastContainer.style.zIndex = '1060';
            document.body.appendChild(this.toastContainer);
        }
    }

    showToast(message, type = 'info', duration = 5000) {
        const toastId = 'toast-' + Date.now();

        const iconMap = {
            'success': 'check-circle',
            'error': 'exclamation-circle',
            'warning': 'exclamation-triangle',
            'info': 'info-circle'
        };

        const toastHtml = `
            <div id="${toastId}" class="toast align-items-center text-bg-${type} border-0" role="alert" aria-live="assertive" aria-atomic="true">
                <div class="d-flex">
                    <div class="toast-body">
                        <i class="fas fa-${iconMap[type]} me-2"></i>
                        ${message}
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
                </div>
            </div>
        `;

        const toastElement = document.createElement('div');
        toastElement.innerHTML = toastHtml;
        const toast = toastElement.firstElementChild;

        this.toastContainer.appendChild(toast);

        const bsToast = new bootstrap.Toast(toast, {
            autohide: true,
            delay: duration
        });

        bsToast.show();

        // 移除隐藏的toast
        toast.addEventListener('hidden.bs.toast', () => {
            toast.remove();
        });

        return toast;
    }

    // ========== 加载状态 ==========

    showLoading(selector = 'body') {
        this.loadingCount++;

        const container = document.querySelector(selector);
        if (!container) return;

        // 如果已经有加载指示器，不再添加
        if (container.querySelector('.loading-overlay')) return;

        const overlay = document.createElement('div');
        overlay.className = 'loading-overlay position-absolute top-0 start-0 w-100 h-100 d-flex justify-content-center align-items-center';
        overlay.style.backgroundColor = 'rgba(255, 255, 255, 0.8)';
        overlay.style.zIndex = '1050';

        overlay.innerHTML = `
            <div class="text-center">
                <div class="spinner-border text-primary" style="width: 3rem; height: 3rem;" role="status">
                    <span class="visually-hidden">加载中...</span>
                </div>
                <div class="mt-2 text-muted">加载中...</div>
            </div>
        `;

        container.style.position = 'relative';
        container.appendChild(overlay);
    }

    hideLoading(selector = 'body') {
        this.loadingCount = Math.max(0, this.loadingCount - 1);

        if (this.loadingCount > 0) return;

        const container = document.querySelector(selector);
        if (!container) return;

        const overlay = container.querySelector('.loading-overlay');
        if (overlay) {
            overlay.remove();
        }
    }

    // ========== 确认对话框 ==========

    showConfirm(message, title = '确认操作') {
        return new Promise((resolve) => {
            const modalId = 'confirm-modal-' + Date.now();

            const modalHtml = `
                <div class="modal fade" id="${modalId}" tabindex="-1" aria-labelledby="${modalId}-label" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title" id="${modalId}-label">${title}</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body">
                                ${message}
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                                <button type="button" class="btn btn-primary" id="${modalId}-confirm">确认</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            const modalElement = document.createElement('div');
            modalElement.innerHTML = modalHtml;
            const modal = modalElement.firstElementChild;

            document.body.appendChild(modal);

            const bsModal = new bootstrap.Modal(modal);
            bsModal.show();

            const confirmBtn = document.getElementById(`${modalId}-confirm`);
            confirmBtn.addEventListener('click', () => {
                bsModal.hide();
                resolve(true);
            });

            modal.addEventListener('hidden.bs.modal', () => {
                modal.remove();
                resolve(false);
            });
        });
    }

    // ========== WebSocket ==========

    initWebSocket() {
        // 检查浏览器是否支持WebSocket
        if (!window.WebSocket && !window.SockJS) {
            console.warn('浏览器不支持WebSocket，将使用轮询更新');
            return;
        }

        try {
            const socket = new SockJS('/insight-ui/ws');
            this.stompClient = Stomp.over(socket);

            // 禁用调试日志
            this.stompClient.debug = null;

            this.stompClient.connect({}, (frame) => {
                this.isConnected = true;
                this.showToast('实时数据连接已建立', 'success');
                this.updateConnectionStatus(true);

                // 订阅统计更新
                this.stompClient.subscribe('/topic/stats', (message) => {
                    this.handleStatsUpdate(JSON.parse(message.body));
                });

                // 订阅拓扑图更新
                this.stompClient.subscribe('/topic/topology', (message) => {
                    this.handleTopologyUpdate(JSON.parse(message.body));
                });

                // 订阅链路更新
                this.stompClient.subscribe('/topic/traces', (message) => {
                    this.handleTracesUpdate(JSON.parse(message.body));
                });

                // 订阅告警
                this.stompClient.subscribe('/topic/alerts', (message) => {
                    this.handleAlertUpdate(JSON.parse(message.body));
                });

            }, (error) => {
                this.isConnected = false;
                this.updateConnectionStatus(false);
                console.error('WebSocket连接失败:', error);

                // 5秒后重连
                setTimeout(() => this.initWebSocket(), 5000);
            });

        } catch (error) {
            console.error('初始化WebSocket失败:', error);
        }
    }

    updateConnectionStatus(connected) {
        const statusElement = document.getElementById('ws-status');
        if (statusElement) {
            if (connected) {
                statusElement.innerHTML = '<i class="fas fa-plug text-success"></i> 实时连接';
                statusElement.title = 'WebSocket连接正常';
            } else {
                statusElement.innerHTML = '<i class="fas fa-plug text-danger"></i> 连接断开';
                statusElement.title = 'WebSocket连接断开，使用轮询更新';
            }
        }
    }

    handleStatsUpdate(message) {
        if (message.type === 'STATS_UPDATE') {
            // 更新仪表盘统计卡片
            this.updateDashboardStats(message.data);
        }
    }

    handleTopologyUpdate(message) {
        if (message.type === 'TOPOLOGY_UPDATE' && window.updateTopologyChart) {
            window.updateTopologyChart(message.data);
        }
    }

    handleTracesUpdate(message) {
        if (message.type === 'TRACES_UPDATE' && window.updateRecentTraces) {
            window.updateRecentTraces(message.data);
        }
    }

    handleAlertUpdate(message) {
        if (message.type === 'ERROR_ALERT') {
            const alert = message.data;

            // 显示toast警告
            this.showToast(
                `[${alert.serviceName}] ${alert.errorMessage}`,
                alert.level === 'critical' ? 'error' : 'warning'
            );

            // 播放提示音（如果有）
            this.playAlertSound();

            // 更新页面上的错误计数
            this.updateErrorCount();
        }
    }

    playAlertSound() {
        // 可以在这里添加声音提示
        console.log('播放告警提示音');
    }

    updateDashboardStats(data) {
        // 更新collector统计
        if (data.collectorStats) {
            const stats = data.collectorStats;

            // 更新页面上的统计显示
            const elements = {
                'total-requests': stats.totalReceivedRequests,
                'total-spans': stats.totalReceivedSpans,
                'success-rate': stats.successRate.toFixed(2) + '%',
                'running-hours': stats.runningHours + '小时'
            };

            for (const [id, value] of Object.entries(elements)) {
                const element = document.getElementById(id);
                if (element) {
                    this.animateValueChange(element, value);
                }
            }
        }
    }

    animateValueChange(element, newValue) {
        const oldValue = element.textContent.trim();
        if (oldValue === newValue.toString()) return;

        element.style.color = '#28a745';
        element.textContent = newValue;

        setTimeout(() => {
            element.style.color = '';
        }, 1000);
    }

    updateErrorCount() {
        // 更新错误计数
        const errorCountElement = document.getElementById('error-count');
        if (errorCountElement) {
            const current = parseInt(errorCountElement.textContent) || 0;
            errorCountElement.textContent = current + 1;

            // 添加动画效果
            errorCountElement.classList.add('pulse-animation');
            setTimeout(() => {
                errorCountElement.classList.remove('pulse-animation');
            }, 1000);
        }
    }

    // ========== 自动刷新 ==========

    initAutoRefresh() {
        // 从配置读取刷新间隔
        const interval = window.REFRESH_INTERVAL || 30000; // 默认30秒

        this.refreshInterval = setInterval(() => {
            if (!this.isConnected) {
                // 如果WebSocket断开，使用AJAX轮询
                this.pollForUpdates();
            }
        }, interval);
    }

    pollForUpdates() {
        // AJAX轮询获取最新数据
        fetch('/insight-ui/api/realtime-stats')
            .then(response => response.json())
            .then(data => {
                this.handleStatsUpdate({
                    type: 'STATS_UPDATE',
                    data: data
                });
            })
            .catch(error => {
                console.error('轮询更新失败:', error);
            });
    }

    // ========== 事件监听 ==========

    initEventListeners() {
        // 全局错误处理
        window.addEventListener('error', (event) => {
            this.showToast(`JavaScript错误: ${event.message}`, 'error');
        });

        // AJAX请求拦截
        this.interceptAjaxRequests();

        // 页面可见性变化
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.onPageHidden();
            } else {
                this.onPageVisible();
            }
        });

        // 表单提交确认
        document.addEventListener('submit', (event) => {
            if (event.target.dataset.confirm) {
                event.preventDefault();
                this.showConfirm(event.target.dataset.confirm, '确认提交')
                    .then(confirmed => {
                        if (confirmed) {
                            event.target.submit();
                        }
                    });
            }
        });

        // 链接点击确认
        document.addEventListener('click', (event) => {
            const link = event.target.closest('a[data-confirm]');
            if (link) {
                event.preventDefault();
                this.showConfirm(link.dataset.confirm, '确认操作')
                    .then(confirmed => {
                        if (confirmed) {
                            window.location.href = link.href;
                        }
                    });
            }
        });
    }

    interceptAjaxRequests() {
        const originalFetch = window.fetch;

        window.fetch = async (...args) => {
            const startTime = Date.now();

            try {
                this.showLoading();
                const response = await originalFetch(...args);
                const endTime = Date.now();

                // 记录请求耗时
                console.debug(`API请求: ${args[0]}, 耗时: ${endTime - startTime}ms`);

                return response;
            } catch (error) {
                this.showToast(`请求失败: ${error.message}`, 'error');
                throw error;
            } finally {
                this.hideLoading();
            }
        };
    }

    onPageHidden() {
        // 页面隐藏时暂停更新
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }

        if (this.stompClient && this.isConnected) {
            this.stompClient.disconnect();
        }
    }

    onPageVisible() {
        // 页面显示时恢复更新
        this.initAutoRefresh();
        if (!this.isConnected) {
            this.initWebSocket();
        }
    }

    // ========== 工具方法 ==========

    formatDuration(ms) {
        if (ms < 1000) return ms + 'ms';
        if (ms < 60000) return (ms / 1000).toFixed(2) + 's';
        return (ms / 60000).toFixed(2) + 'min';
    }

    formatTime(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    formatDate(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleDateString('zh-CN') + ' ' + this.formatTime(timestamp);
    }

    copyToClipboard(text) {
        navigator.clipboard.writeText(text)
            .then(() => {
                this.showToast('已复制到剪贴板', 'success');
            })
            .catch(() => {
                this.showToast('复制失败', 'error');
            });
    }

    // ========== 公开API ==========

    refreshData() {
        this.showToast('正在刷新数据...', 'info');

        // 发送刷新命令
        if (this.stompClient && this.isConnected) {
            this.stompClient.send('/app/command', {}, JSON.stringify({
                type: 'REFRESH'
            }));
        } else {
            // AJAX刷新
            fetch('/insight-ui/api/refresh', { method: 'POST' })
                .then(() => {
                    this.showToast('数据刷新完成', 'success');
                    location.reload();
                })
                .catch(error => {
                    this.showToast(`刷新失败: ${error.message}`, 'error');
                });
        }
    }

    exportData(format = 'json') {
        this.showLoading();

        fetch(`/insight-ui/api/export?format=${format}`)
            .then(response => response.blob())
            .then(blob => {
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `spring-insight-${new Date().toISOString().slice(0, 10)}.${format}`;
                a.click();
                window.URL.revokeObjectURL(url);
                this.showToast('导出成功', 'success');
            })
            .catch(error => {
                this.showToast(`导出失败: ${error.message}`, 'error');
            })
            .finally(() => {
                this.hideLoading();
            });
    }
}

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    window.app = new SpringInsightApp();
});