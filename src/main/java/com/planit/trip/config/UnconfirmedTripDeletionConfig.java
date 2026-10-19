package com.planit.trip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class UnconfirmedTripDeletionConfig {

    @Bean
    Clock unconfirmedTripDeletionClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
