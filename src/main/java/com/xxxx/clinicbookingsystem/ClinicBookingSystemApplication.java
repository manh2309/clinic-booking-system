package com.xxxx.clinicbookingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ClinicBookingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBookingSystemApplication.class, args);
    }

}
