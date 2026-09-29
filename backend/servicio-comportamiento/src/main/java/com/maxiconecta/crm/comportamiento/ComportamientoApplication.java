package com.maxiconecta.crm.comportamiento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ComportamientoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComportamientoApplication.class, args);
    }
}
