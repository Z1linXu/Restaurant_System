package com.restaurant.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.restaurant.system.**.mapper")
public class RestaurantSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantSystemApplication.class, args);
    }
}
