package com.planit.photomission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class PhotoMissionEvaluationRetryConfig {

    @Bean("photoMissionEvaluationRetryClock")
    public Clock photoMissionEvaluationRetryClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
