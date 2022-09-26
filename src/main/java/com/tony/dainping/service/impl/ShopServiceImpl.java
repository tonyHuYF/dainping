package com.tony.dainping.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.Result;
import com.tony.dainping.entity.Shop;
import com.tony.dainping.mapper.ShopMapper;
import com.tony.dainping.service.IShopService;
import com.tony.dainping.utils.CacheClient;
import com.tony.dainping.utils.RedisConstants;
import com.tony.dainping.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private CacheClient client;

    @Override
    public Result queryShopById(Long id) {

        //        缓存穿透
//        Shop shop = client.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解除缓存击穿
        Shop shop = queryWithMutex(id);

        //逻辑过期解除缓存击穿
//        Shop shop = queryWithLogicExpire(id);

//        //逻辑过期解除缓存击穿
//        Shop shop = client.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    //逻辑过期解除缓存击穿
    public Shop queryWithLogicExpire(Long id) {
        //查询redis是否存在缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //存在，直接返回（此时是空值）
            return null;
        }

        //命中，首先将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        //命中，判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return shop;
        }

        //已过期，进行缓存重建
        //获取互斥锁
        //判断是否获取锁成功

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        if (tryLock(lockKey)) {
            //成功，缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShopToRedis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //返回数据
        return shop;
    }


    //互斥锁解除缓存击穿
    public Shop queryWithMutex(Long id) {
        //查询redis是否存在缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, Shop.class);
        }

        //判断是否命中空值
        if (json != null) {
            //json 不为null即命中空值
            return null;
        }

        //实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        //获取互斥锁
        Shop shop = null;
        try {
            //判断是否成功获取锁
            if (!tryLock(lockKey)) {
                //失败，则休眠并重试
                Thread.sleep(100);
                return queryWithMutex(id);
            }

            //成功，则根据Id查询数据库
            shop = getById(id);
            //模拟重建延迟
            Thread.sleep(200);

            if (shop == null) {
                //数据不存在，传入空值，防止redis穿透问题
                //存在，写入redis
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            } else {
                //存在，写入redis
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }
        return shop;
    }


    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    //防止缓存穿透
    public Shop queryWithPassThrough(Long id) {
        //查询redis是否存在缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, Shop.class);
        }

        //判断是否命中空值
        if (json != null) {
            //json 不为null即命中空值
            return null;
        }

        //如果不存在，去查询数据库
        Shop shop = getById(id);

        if (shop == null) {
            //数据不存在，传入空值，防止redis穿透问题
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }

        return shop;
    }

    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //查询数据库，查出店铺
        Shop shop = getById(id);

        //模拟延迟
        Thread.sleep(200);

        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));

    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        //修改数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }

        updateById(shop);

        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
