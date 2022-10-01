package com.tony.dainping;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.tony.dainping.dto.UserDTO;
import com.tony.dainping.entity.Shop;
import com.tony.dainping.entity.User;
import com.tony.dainping.service.impl.ShopServiceImpl;
import com.tony.dainping.service.impl.UserServiceImpl;
import com.tony.dainping.utils.CacheClient;
import com.tony.dainping.utils.RedisConstants;
import com.tony.dainping.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class DainpingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient client;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Test
    void testNextId() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();
    }

    @Test
    void testSaveRedis() throws InterruptedException {
        Shop shop = shopService.getById(1);
        client.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testToken() {
        List<User> list = userService.query().list();

        list.forEach(user -> {
            //保存用户到redis中，以随机码token作为Key
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));


            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        });

    }

    @Test
    void getToken() {
        Set<String> keys = stringRedisTemplate.keys("login:user:*");
        keys.forEach(p -> {
            System.out.println(p.toString().replace("login:user:", ""));
        });
    }

    @Test
    void loadShop() {
        //查询店铺
        List<Shop> list = shopService.list();
        //分组，分装
        Map<Long, List<Shop>> shopMap = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //导入redsi

        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            String key = "shop:geo:" + entry.getKey();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();

            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())));

            }

            stringRedisTemplate.opsForGeo().add(key, locations);

        }


    }
}
