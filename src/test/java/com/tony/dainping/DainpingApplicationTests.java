package com.tony.dainping;

import com.tony.dainping.entity.Shop;
import com.tony.dainping.service.impl.ShopServiceImpl;
import com.tony.dainping.utils.CacheClient;
import com.tony.dainping.utils.RedisConstants;
import com.tony.dainping.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class DainpingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient client;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);


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
}
