package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * 包装 Spring 容器中的 {@link RabbitTemplate}，为 send / convertAndSend 等生产方法打 CLIENT Span。
 *
 * @author 苏渡苇
 * @since 2026-11-28
 */
public final class InsightRabbitProxies {

    private InsightRabbitProxies() {
    }

    /**
     * 是否已由本类包装。
     *
     * @param bean 候选 Bean
     * @return true 表示已是本代理
     */
    public static boolean isInsightWrapped(Object bean) {
        if (!(bean instanceof Advised advised)) {
            return false;
        }
        for (Advisor advisor : advised.getAdvisors()) {
            if (advisor.getAdvice() instanceof InsightRabbitPublishInterceptor) {
                return true;
            }
        }
        return false;
    }

    /**
     * 包装 {@link RabbitTemplate}：挂载消息头注入 + 发布方法拦截。
     *
     * @param template 原始模板
     * @param listener 上报
     * @param props    配置
     * @return 代理后的模板
     */
    public static RabbitTemplate wrap(RabbitTemplate template,
                                      SpanReportingListener listener,
                                      InsightProperties props) {
        if (template == null || isInsightWrapped(template)) {
            return template;
        }
        template.addBeforePublishPostProcessors(message -> {
            TraceSpan span = InsightRabbitSupport.currentPublishSpan();
            if (span != null) {
                InsightRabbitSupport.injectTraceparent(message, span, props);
            }
            return message;
        });

        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(template);
        proxyFactory.setProxyTargetClass(true);
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames(
                "send",
                "convertAndSend",
                "sendAndReceive",
                "convertSendAndReceive",
                "convertSendAndReceiveAsType");
        proxyFactory.addAdvisor(new DefaultPointcutAdvisor(
                pointcut, new InsightRabbitPublishInterceptor(listener, props)));
        return (RabbitTemplate) proxyFactory.getProxy(template.getClass().getClassLoader());
    }

    /**
     * 发布方法拦截：创建 CLIENT Span，不压栈。
     */
    static final class InsightRabbitPublishInterceptor implements MethodInterceptor {

        private final SpanReportingListener listener;
        private final InsightProperties props;

        InsightRabbitPublishInterceptor(SpanReportingListener listener, InsightProperties props) {
            this.listener = listener;
            this.props = props;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if (props == null || !props.isEnabled() || !props.isRabbitTracingEnabled()) {
                return invocation.proceed();
            }
            Object[] args = invocation.getArguments();
            String[] dest = InsightRabbitSupport.resolveExchangeAndRoutingKey(args);
            Optional<TraceSpan> span = InsightRabbitSupport.beginPublish(dest[0], dest[1], props);
            if (span.isPresent()) {
                injectIntoMessageArgs(args, span.get());
            }
            long start = System.currentTimeMillis();
            try {
                Object result = invocation.proceed();
                InsightRabbitSupport.endPublish(span, null, props, listener, start);
                return result;
            } catch (Throwable error) {
                InsightRabbitSupport.endPublish(span, unwrap(error), props, listener, start);
                throw error;
            }
        }

        private void injectIntoMessageArgs(Object[] args, TraceSpan span) {
            if (args == null) {
                return;
            }
            for (Object arg : args) {
                if (arg instanceof Message message) {
                    InsightRabbitSupport.injectTraceparent(message, span, props);
                }
            }
        }

        private static Throwable unwrap(Throwable error) {
            if (error instanceof InvocationTargetException ite && ite.getCause() != null) {
                return ite.getCause();
            }
            return error;
        }
    }
}
