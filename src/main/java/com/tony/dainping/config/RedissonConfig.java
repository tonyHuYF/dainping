package com.tony.dainping.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://175.178.108.7:6379").setPassword("BHuEqwbXF6euRKYq");
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
