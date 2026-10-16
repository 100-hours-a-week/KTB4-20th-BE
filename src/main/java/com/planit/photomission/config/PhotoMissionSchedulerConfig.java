package com.planit.photomission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class PhotoMissionSchedulerConfig {

    @Bean
    Clock photoMissionClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
