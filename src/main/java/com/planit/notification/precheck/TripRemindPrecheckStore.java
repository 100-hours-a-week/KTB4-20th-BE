package com.planit.notification.precheck;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Repository
public class TripRemindPrecheckStore {

    static final long TTL_SECONDS = 72_000L;
    static final String KEY_PREFIX = "planit:trip-remind:precheck:";

    private static final DefaultRedisScript<String> CREATE_SCRIPT =
            new DefaultRedisScript<>("""
                    local result = redis.call(
                        'JSON.SET',
                        KEYS[1],
                        '$',
                        ARGV[1],
                        'NX'
                    )

                    if not result then
                        return 'ALREADY_EXISTS'
                    end

                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))

                    return 'CREATED'
                    """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TripRemindPrecheckStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TripRemindPrecheckCreateResult create(
            LocalDate targetDate,
            TripRemindPrecheckCache cache
    ) {
        Objects.requireNonNull(targetDate, "여행 시작 대상일은 필수입니다");
        Objects.requireNonNull(cache, "저장할 사전 점검 데이터는 필수입니다");

        String result = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(key(targetDate)),
                objectMapper.writeValueAsString(cache),
                Long.toString(TTL_SECONDS)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Valkey 사전 점검 저장 결과가 없습니다."
            );
        }

        try {
            return TripRemindPrecheckCreateResult.valueOf(result);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "지원하지 않는 Valkey 사전 점검 저장 결과입니다: "
                            + result,
                    exception
            );
        }
    }

    static String key(LocalDate targetDate) {
        return KEY_PREFIX + targetDate;
    }
}
