package com.tony.dainping.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);

    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R, T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback
            , Long time, TimeUnit unit) {
        //查询redis是否存在缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        //判断是否命中空值
        if (json != null) {
            //json 不为null即命中空值
            return null;
        }

        //如果不存在，去查询数据库
        R r = dbFallback.apply(id);

        if (r == null) {
            //数据不存在，传入空值，防止redis穿透问题
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            //存在，写入redis
            this.set(key, r, time, unit);
        }

        return r;
    }


    public <R, T> R queryWithLogicExpire(String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback
            , Long time, TimeUnit unit) {
        //查询redis是否存在缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //存在，直接返回（此时是空值）
            return null;
        }

        //命中，首先将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        //命中，判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return r;
        }

        //已过期，进行缓存重建
        //获取互斥锁
        //判断是否获取锁成功

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        if (tryLock(lockKey)) {
            //成功，缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis

                    setWithLogicExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //返回数据
        return r;
    }


    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
