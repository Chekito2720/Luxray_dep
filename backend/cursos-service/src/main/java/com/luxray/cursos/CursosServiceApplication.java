package com.luxray.cursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.luxray.cursos", "com.luxray.common"})
@EnableDiscoveryClient
public class CursosServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CursosServiceApplication.class, args);
    }
}
