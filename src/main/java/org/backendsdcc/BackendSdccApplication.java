package org.backendsdcc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BackendSdccApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(BackendSdccApplication.class, args);
    }

}
