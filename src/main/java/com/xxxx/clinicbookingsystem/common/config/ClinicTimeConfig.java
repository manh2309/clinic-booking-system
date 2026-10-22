package com.xxxx.clinicbookingsystem.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClinicTimeConfig {
    @Bean
    Clock clinicClock(@Value("${clinic.time-zone:Asia/Ho_Chi_Minh}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
