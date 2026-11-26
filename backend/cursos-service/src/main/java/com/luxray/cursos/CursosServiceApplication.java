package com.luxray.cursos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.luxray.cursos", "com.luxray.common"})
@EnableDiscoveryClient
@EnableScheduling
public class CursosServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CursosServiceApplication.class, args);
    }
}
