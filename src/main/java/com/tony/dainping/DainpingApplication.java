package com.tony.dainping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class DainpingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DainpingApplication.class, args);
    }

}
