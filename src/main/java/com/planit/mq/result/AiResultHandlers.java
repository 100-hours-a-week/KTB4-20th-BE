package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 등록된 결과 Handler를 작업 종류로 찾는다. 같은 작업 종류의 Handler가 둘이면 기동하지 않는다. */
@Component
public class AiResultHandlers {

    private final Map<AiJobType, AiResultHandler> handlers = new EnumMap<>(AiJobType.class);

    public AiResultHandlers(List<AiResultHandler> handlers) {
        handlers.forEach(this::register);
    }

    /** @return 아직 Handler가 없는 작업 종류면 비어 있다 */
    public Optional<AiResultHandler> find(AiJobType jobType) {
        return Optional.ofNullable(handlers.get(jobType));
    }

    private void register(AiResultHandler handler) {
        AiResultHandler existing = handlers.putIfAbsent(handler.jobType(), handler);
        if (existing != null) {
            throw new IllegalStateException("같은 작업 종류의 결과 Handler가 둘 있음 jobType=%s, handlers=%s, %s"
                    .formatted(handler.jobType(), existing.getClass().getName(), handler.getClass().getName()));
        }
    }
}
