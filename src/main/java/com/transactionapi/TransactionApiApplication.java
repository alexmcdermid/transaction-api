package com.transactionapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransactionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionApiApplication.class, args);
    }
}
