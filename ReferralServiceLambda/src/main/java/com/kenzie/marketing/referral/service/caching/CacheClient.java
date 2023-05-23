package com.kenzie.marketing.referral.service.caching;

import com.kenzie.marketing.referral.service.dependency.DaggerServiceComponent;

import redis.clients.jedis.Jedis;

import java.util.Optional;

public class CacheClient {

    public CacheClient() {}

    public void setValue(String key, String value){
        checkNonNullKey(key);
        Jedis cache = DaggerServiceComponent.create().provideJedis();
        cache.set(key, value);
        cache.close();
    }

    public String getValue(String key){
        checkNonNullKey(key);
        Jedis cache = DaggerServiceComponent.create().provideJedis();
        String value = cache.get(key);
        cache.close();
        return value;
    }

    public void invalidate(String key){
        checkNonNullKey(key);
        Jedis cache = DaggerServiceComponent.create().provideJedis();
        cache.del(key);
        cache.close();
    }

    private void checkNonNullKey(String key){
        if(key == null){
            throw  new IllegalArgumentException("Key must not be null");
        }
    }
    // Put your Cache Client Here

    // Since Jedis is being used multithreaded, you MUST get a new Jedis instances and close it inside every method.
    // Do NOT use a single instance across multiple of these methods

    // Use Jedis in each method by doing the following:
    // Jedis cache = DaggerServiceComponent.create().provideJedis();
    // ... use the cache
    // cache.close();

    // Remember to check for null keys!
}
