package com.java.edtech;

import com.java.edtech.common.config.JacksonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JacksonConfig.class)
public class EdTechApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdTechApplication.class, args);
    }

}
