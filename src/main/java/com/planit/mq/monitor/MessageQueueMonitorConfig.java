package com.planit.mq.monitor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class MessageQueueMonitorConfig {

    public static final String MONITOR_EXECUTOR = "mqMonitorExecutor";

    /**
     * 큐 상태 조회 실행기. 조회는 대부분 응답을 기다리는 시간이므로 큐마다 가상 스레드 하나로 동시에 조회한다.
     */
    @Bean(name = MONITOR_EXECUTOR, destroyMethod = "close")
    public ExecutorService mqMonitorExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
