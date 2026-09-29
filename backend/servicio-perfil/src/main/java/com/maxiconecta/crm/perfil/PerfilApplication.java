package com.maxiconecta.crm.perfil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PerfilApplication {

    public static void main(String[] args) {
        SpringApplication.run(PerfilApplication.class, args);
    }
}
