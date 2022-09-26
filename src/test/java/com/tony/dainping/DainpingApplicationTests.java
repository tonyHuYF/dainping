package com.tony.dainping;

import com.tony.dainping.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DainpingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;


    @Test
    void testSaveRedis() throws InterruptedException {
        shopService.saveShopToRedis(1L, 10L);
    }
}
