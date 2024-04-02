package io.quarkus.search.app.cache;

import java.lang.reflect.Method;

import io.quarkus.cache.CacheKeyGenerator;

public class MethodNameCacheKeyGenerator implements CacheKeyGenerator {
    @Override
    public Object generate(Method method, Object... methodParams) {
        return method.getName();
    }
}
