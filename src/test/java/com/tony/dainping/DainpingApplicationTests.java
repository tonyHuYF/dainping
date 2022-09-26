package com.tony.dainping;

import com.tony.dainping.entity.Shop;
import com.tony.dainping.service.impl.ShopServiceImpl;
import com.tony.dainping.utils.CacheClient;
import com.tony.dainping.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class DainpingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient client;


    @Test
    void testSaveRedis() throws InterruptedException {
        Shop shop = shopService.getById(1);
        client.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }
}
