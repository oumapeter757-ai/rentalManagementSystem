
package com.peterscode.rentalmanagementsystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Setter
@Getter
public class AppConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

}