package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResultHandlersTest {

    @DisplayName("작업 종류로 결과 Handler를 찾고, 없는 작업 종류는 비워 둔다")
    @Test
    void findsHandlerByJobType() {
        AiResultHandler schedule = handler(AiJobType.SCHEDULE_GENERATE);
        AiResultHandlers handlers = new AiResultHandlers(List.of(schedule, handler(AiJobType.PHOTO_MISSION_GENERATE)));

        assertThat(handlers.find(AiJobType.SCHEDULE_GENERATE)).containsSame(schedule);
        assertThat(handlers.find(AiJobType.PHOTO_JUDGE)).isEmpty();
    }

    @DisplayName("같은 작업 종류의 Handler가 둘이면 기동하지 않는다")
    @Test
    void rejectsDuplicateHandler() {
        assertThatThrownBy(() -> new AiResultHandlers(List.of(
                handler(AiJobType.SCHEDULE_GENERATE),
                handler(AiJobType.SCHEDULE_GENERATE)
        ))).isInstanceOf(IllegalStateException.class);
    }

    private static AiResultHandler handler(AiJobType jobType) {
        return new AiResultHandler() {
            @Override
            public AiJobType jobType() {
                return jobType;
            }

            @Override
            public void handle(AiResultEnvelope result, int retryCount) {
            }
        };
    }
}
