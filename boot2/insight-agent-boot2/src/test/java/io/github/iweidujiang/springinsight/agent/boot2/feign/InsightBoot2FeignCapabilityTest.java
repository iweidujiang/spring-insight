/**
 * 验证公开 Capability 可被 Feign 反射 enrich（匿名类会 IllegalAccessException）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.feign;

import feign.Capability;
import feign.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InsightBoot2FeignCapabilityTest {

    /**
     * 公开具名 Capability 应能成功包装 Client.Default。
     */
    @Test
    public void enrich_publicCapability_wrapsDefaultClient() {
        ObjectProvider<Object> empty = emptyProvider();
        @SuppressWarnings("unchecked")
        InsightBoot2FeignCapability cap = new InsightBoot2FeignCapability(
                (ObjectProvider) empty, (ObjectProvider) empty);
        Client original = new Client.Default(null, null);
        Object enriched = Capability.enrich(original, Client.class, Collections.<Capability>singletonList(cap));
        assertTrue(enriched instanceof TracingFeignClient, "expected TracingFeignClient, got " + enriched.getClass());
    }

    /**
     * 构造空 ObjectProvider，仅满足构造注入。
     *
     * @return 始终返回 null 的 Provider
     */
    private static ObjectProvider<Object> emptyProvider() {
        return new ObjectProvider<Object>() {
            @Override
            public Object getObject() {
                return null;
            }

            @Override
            public Object getObject(Object... args) {
                return null;
            }

            @Override
            public Object getIfAvailable() {
                return null;
            }

            @Override
            public Object getIfUnique() {
                return null;
            }

            @Override
            public void ifAvailable(Consumer<Object> consumer) {
            }

            @Override
            public void ifUnique(Consumer<Object> consumer) {
            }

            @Override
            public Stream<Object> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<Object> orderedStream() {
                return Stream.empty();
            }

            @Override
            public Iterator<Object> iterator() {
                return Collections.emptyIterator();
            }
        };
    }
}
