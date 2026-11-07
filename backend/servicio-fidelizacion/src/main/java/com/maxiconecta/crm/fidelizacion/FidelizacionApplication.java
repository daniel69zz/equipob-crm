package com.maxiconecta.crm.fidelizacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FidelizacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FidelizacionApplication.class, args);
    }
}
