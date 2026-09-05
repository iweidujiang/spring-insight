package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 为 {@link ThreadPoolTaskExecutor}（含 {@code @Async} 默认池）挂上 Trace 上下文透传。
 */
@Slf4j
@Configuration
@ConditionalOnClass(ThreadPoolTaskExecutor.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "context-propagation-enabled", havingValue = "true", matchIfMissing = true)
public class InsightAsyncAutoConfiguration {

    @Bean
    public static InsightTaskExecutorBeanPostProcessor insightTaskExecutorBeanPostProcessor() {
        return new InsightTaskExecutorBeanPostProcessor();
    }

    static final class InsightTaskExecutorBeanPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof ThreadPoolTaskExecutor executor)) {
                return bean;
            }
            TaskDecorator existing = readTaskDecorator(executor);
            if (existing instanceof InsightTaskDecorator) {
                return bean;
            }
            executor.setTaskDecorator(new InsightTaskDecorator(existing));
            log.debug("[Agent] 已为 ThreadPoolTaskExecutor 启用 Trace 上下文透传: bean={}", beanName);
            return bean;
        }

        /**
         * Spring 对 taskDecorator 未必提供 getter，反射兼容读取以便组合已有装饰器。
         */
        private static TaskDecorator readTaskDecorator(ThreadPoolTaskExecutor executor) {
            try {
                Method getter = executor.getClass().getMethod("getTaskDecorator");
                Object v = getter.invoke(executor);
                return v instanceof TaskDecorator td ? td : null;
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
            Class<?> c = executor.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField("taskDecorator");
                    f.setAccessible(true);
                    Object v = f.get(executor);
                    return v instanceof TaskDecorator td ? td : null;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
