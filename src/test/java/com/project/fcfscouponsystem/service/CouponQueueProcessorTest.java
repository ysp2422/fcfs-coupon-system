package com.project.fcfscouponsystem.service;

import com.project.fcfscouponsystem.domain.CouponIssueResult;
import com.project.fcfscouponsystem.infrastructure.redis.CouponIssueLuaRepository;
import com.project.fcfscouponsystem.publisher.CouponIssueEventPublisher;
import com.project.fcfscouponsystem.repository.CouponQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * [CouponQueueProcessor 단위 테스트]
 *
 * 재고 소진(OutOfStock) 케이스가 여기 있는 이유:
 *   - CouponIssueService는 대기열 등록만 담당
 *   - 실제 재고 차감 + 중복 검증은 Processor가 Lua Script로 처리
 *   → 재고 0 케이스는 Processor 테스트에서 확인해야 함
 */
@ExtendWith(MockitoExtension.class)
class CouponQueueProcessorTest {

    @Mock
    private CouponQueueRepository queueRepository; // 가짜 대기열 저장소

    @Mock
    private CouponIssueLuaRepository luaRepository; // 가짜 Lua Script 실행기

    @Mock
    private CouponIssueEventPublisher eventPublisher; // 가짜 Kafka 발행자

    @InjectMocks
    private CouponQueueProcessor couponQueueProcessor;

    /**
     * [테스트 1] 재고 소진 시 Kafka 발행 안 함
     *
     * 시나리오:
     *   - 대기열에 "1:100" (userId=1, couponId=100) 존재
     *   - Lua Script 실행 결과 = OUT_OF_STOCK (재고 없음)
     *
     * 기대 결과:
     *   - Kafka에 발행하지 않음 (eventPublisher.publish 호출 안 됨)
     */
    @Test
    @DisplayName("재고가 소진되면 Kafka에 발행하지 않는다")
    void 재고소진_Kafka발행안함() {
        // given: 대기열에 1개 있고, Lua Script는 재고 소진 반환
        when(queueRepository.dequeue(100)).thenReturn(Set.of("1:100"));
        when(luaRepository.execute(100L, 1L)).thenReturn(CouponIssueResult.OUT_OF_STOCK);

        // when: 프로세서 실행
        couponQueueProcessor.process();

        // then: Kafka 발행 안 됐는지 검증
        verify(eventPublisher, never()).publish(anyLong(), anyLong());
    }

    /**
     * [테스트 2] 정상 발급 시 Kafka 발행
     *
     * 시나리오:
     *   - 대기열에 "1:100" 존재
     *   - Lua Script 결과 = SUCCESS (재고 있음 + 중복 아님)
     *
     * 기대 결과:
     *   - Kafka에 발행됨 (eventPublisher.publish 호출 1번)
     */
    @Test
    @DisplayName("정상 발급이면 Kafka에 발행한다")
    void 정상발급_Kafka발행() {
        // given
        when(queueRepository.dequeue(100)).thenReturn(Set.of("1:100"));
        when(luaRepository.execute(100L, 1L)).thenReturn(CouponIssueResult.SUCCESS);

        // when
        couponQueueProcessor.process();

        // then: Kafka 발행 1번 호출됐는지 검증
        verify(eventPublisher, times(1)).publish(1L, 100L);
    }

    /**
     * [테스트 3] 중복 요청 시 Kafka 발행 안 함
     *
     * 시나리오:
     *   - 멱등성에서 못 걸러진 중복 요청이 대기열에 들어온 경우
     *   - Lua Script 결과 = DUPLICATE
     *
     * 기대 결과:
     *   - Kafka에 발행하지 않음 (중복 발급 방지)
     */
    @Test
    @DisplayName("중복 요청이면 Kafka에 발행하지 않는다")
    void 중복요청_Kafka발행안함() {
        // given
        when(queueRepository.dequeue(100)).thenReturn(Set.of("1:100"));
        when(luaRepository.execute(100L, 1L)).thenReturn(CouponIssueResult.DUPLICATE);

        // when
        couponQueueProcessor.process();

        // then
        verify(eventPublisher, never()).publish(anyLong(), anyLong());
    }

    /**
     * [테스트 4] 대기열이 비어있으면 아무것도 안 함
     *
     * 시나리오:
     *   - 대기열이 비어있음
     *
     * 기대 결과:
     *   - Lua Script, Kafka 발행 모두 호출 안 됨
     */
    @Test
    @DisplayName("대기열이 비어있으면 아무 처리도 하지 않는다")
    void 대기열비어있음_아무처리안함() {
        // given: 빈 대기열
        when(queueRepository.dequeue(100)).thenReturn(Set.of());

        // when
        couponQueueProcessor.process();

        // then: Lua Script도 Kafka도 호출 안 됨
        verify(luaRepository, never()).execute(anyLong(), anyLong());
        verify(eventPublisher, never()).publish(anyLong(), anyLong());
    }
}
