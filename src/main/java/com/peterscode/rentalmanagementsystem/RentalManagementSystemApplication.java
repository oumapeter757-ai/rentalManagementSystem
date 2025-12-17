package com.peterscode.rentalmanagementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RentalManagementSystemApplication {

    static void main(String[] args) {
        SpringApplication.run(RentalManagementSystemApplication.class, args);
    }
}
