package com.planit.notification.precheck;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private static final DefaultRedisScript<List> FIND_SCRIPT =
            new DefaultRedisScript<>("""
                    local json = redis.call('JSON.GET', KEYS[1])

                    if not json then
                        return nil
                    end

                    local ttl = redis.call('TTL', KEYS[1])

                    return { json, tostring(ttl) }
                    """, List.class);

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

    public Optional<TripRemindPrecheckSnapshot> find(
            LocalDate targetDate
    ) {
        Objects.requireNonNull(targetDate, "여행 시작 대상일은 필수입니다");

        List<?> result = redisTemplate.execute(
                FIND_SCRIPT,
                List.of(key(targetDate))
        );

        if (result == null) {
            return Optional.empty();
        }

        return Optional.of(toSnapshot(result));
    }

    private TripRemindPrecheckSnapshot toSnapshot(List<?> result) {
        validateFindResult(result);

        String json = Objects.toString(result.get(0), null);
        long ttlSeconds = parseTtlSeconds(result.get(1));
        validateTtlSeconds(ttlSeconds);

        return new TripRemindPrecheckSnapshot(
                deserializeCache(json),
                ttlSeconds
        );
    }

    private void validateFindResult(List<?> result) {
        if (result.size() != 2) {
            throw new IllegalStateException(
                    "지원하지 않는 Valkey 사전 점검 조회 결과입니다."
            );
        }
    }

    private long parseTtlSeconds(Object ttl) {
        try {
            return Long.parseLong(Objects.toString(ttl));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Valkey 사전 점검 TTL을 해석할 수 없습니다.",
                    exception
            );
        }
    }

    private void validateTtlSeconds(long ttlSeconds) {
        if (ttlSeconds <= 0 || ttlSeconds > TTL_SECONDS) {
            throw new IllegalStateException(
                    "Valkey 사전 점검 TTL이 비정상입니다: " + ttlSeconds
            );
        }
    }

    private TripRemindPrecheckCache deserializeCache(String json) {
        try {
            return objectMapper.readValue(
                    json,
                    TripRemindPrecheckCache.class
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Valkey 사전 점검 JSON을 해석할 수 없습니다.",
                    exception
            );
        }
    }

    static String key(LocalDate targetDate) {
        return KEY_PREFIX + targetDate;
    }
}
