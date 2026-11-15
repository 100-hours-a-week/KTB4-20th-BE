package com.planit.mq.monitor;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * 큐 감시 설정. RabbitMQ Management API를 조회해 큐가 차면 새 요청을 막는다.
 *
 * @param managementBaseUrl Management API 주소
 * @param virtualHost       조회할 큐가 있는 vhost
 * @param username          `monitoring` 태그만 가진 조회 전용 계정
 * @param connectTimeout    Management API 연결 대기 시간
 * @param readTimeout       Management API 응답 대기 시간
 * @param taskBlockRatio    작업 큐가 최대 길이의 이 비율 이상 차면 차단한다
 * @param taskUnblockRatio  작업 큐가 최대 길이의 이 비율 이하로 줄면 차단을 푼다
 * @param dlqBlockRatio     요청 DLQ가 최대 길이의 이 비율 이상 차면 차단한다
 * @param dlqUnblockRatio   요청 DLQ가 최대 길이의 이 비율 이하로 줄면 차단을 푼다
 */
@Validated
@ConfigurationProperties(prefix = "planit.mq.monitor")
public record MessageQueueMonitorProperties(
        @NotNull URI managementBaseUrl,
        @NotBlank String virtualHost,
        @NotBlank String username,
        @NotNull String password,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double taskBlockRatio,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double taskUnblockRatio,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double dlqBlockRatio,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double dlqUnblockRatio
) {

    /** 해제 기준이 차단 기준보다 낮아야 경계 근처에서 플래그가 매번 켜졌다 꺼지지 않는다.
     *  잘못된 설정값 변경 시 값 검증을 위함.
     * */
    @AssertTrue(message = "차단 해제 비율은 차단 비율보다 낮아야 합니다")
    public boolean isUnblockRatioBelowBlockRatio() {
        return isBelow(taskUnblockRatio, taskBlockRatio) && isBelow(dlqUnblockRatio, dlqBlockRatio);
    }

    private static boolean isBelow(Double unblock, Double block) {
        return unblock == null || block == null || unblock < block;
    }
}
