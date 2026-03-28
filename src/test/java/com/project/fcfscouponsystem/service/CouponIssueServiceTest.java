package com.project.fcfscouponsystem.service;

import com.project.fcfscouponsystem.controller.dto.CouponIssueResponse;
import com.project.fcfscouponsystem.repository.CouponQueueRepository;
import com.project.fcfscouponsystem.repository.IdempotencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * [CouponIssueService 단위 테스트]
 *
 * 단위 테스트
 *   - 실제 Redis, Kafka 없이 테스트
 *   - Mock(가짜 객체)으로 의존성 대체
 *   - 빠르고 외부 환경에 영향받지 않음
 *
 * 사용 기술:
 *   - @ExtendWith(MockitoExtension): Mockito 사용 선언
 *   - @Mock: 가짜 객체 생성 (실제 Redis 연결 없이 동작 흉내)
 *   - @InjectMocks: @Mock으로 만든 가짜 객체를 Service에 주입
 *   - verify(): 특정 메서드가 실제로 호출됐는지 검증
 */
@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository; // 가짜 멱등성 저장소

    @Mock
    private CouponQueueRepository queueRepository; // 가짜 대기열 저장소

    @InjectMocks
    private CouponIssueService couponIssueService; // 위 가짜 객체들이 주입된 실제 서비스

    // ===== 테스트용 상수 =====
    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 100L;

    /**
     * [테스트 1] 중복 요청 시 멱등성 동작 확인
     *
     * 시나리오:
     *   - 유저가 이미 쿠폰을 요청한 적 있음 (idempotencyRepository.exists = true)
     *   - 같은 요청을 다시 보냄
     *
     * 기대 결과:
     *   - "이미 대기열에 등록된 요청" 메시지 반환
     *   - 대기열에 중복으로 등록되지 않음 (enqueue 호출 안 됨)
     */
    @Test
    @DisplayName("이미 처리된 요청이면 대기열 등록 없이 즉시 반환한다")
    void 멱등성_이미처리된요청_즉시반환() {
        // given: "이미 요청한 적 있다"고 가짜 객체가 응답하도록 설정
        when(idempotencyRepository.exists(USER_ID, COUPON_ID)).thenReturn(true);

        // when: 쿠폰 발급 요청
        CouponIssueResponse response = couponIssueService.issue(USER_ID, COUPON_ID);

        // then: 이미 등록된 응답 반환
        assertThat(response.message()).isEqualTo("이미 대기열에 등록된 요청입니다.");
        assertThat(response.queuePosition()).isNull();

        // 대기열에 추가로 등록하지 않았는지 검증 (중복 등록 방지)
        verify(queueRepository, never()).enqueue(USER_ID, COUPON_ID);
    }

    /**
     * [테스트 2] 정상 발급 요청 시 대기열 등록 확인
     *
     * 시나리오:
     *   - 처음 요청하는 유저
     *   - 대기열 등록 후 3번째 대기 중
     *
     * 기대 결과:
     *   - 대기열에 등록됨 (enqueue 호출)
     *   - 멱등성 키 저장됨 (save 호출)
     *   - 대기 순서(3) 응답에 포함
     */
    @Test
    @DisplayName("처음 요청이면 대기열에 등록하고 대기 순서를 반환한다")
    void 정상요청_대기열등록_대기순서반환() {
        // given: 처음 요청 (exists = false), 현재 3번째 대기 중
        when(idempotencyRepository.exists(USER_ID, COUPON_ID)).thenReturn(false);
        when(queueRepository.getPosition(USER_ID, COUPON_ID)).thenReturn(3L);

        // when: 쿠폰 발급 요청
        CouponIssueResponse response = couponIssueService.issue(USER_ID, COUPON_ID);

        // then: 대기 순서 포함된 응답 확인
        assertThat(response.queuePosition()).isEqualTo(3L);
        assertThat(response.message()).isEqualTo("3번째로 대기 중입니다.");

        // 대기열 등록이 실제로 호출됐는지 검증
        verify(queueRepository, times(1)).enqueue(USER_ID, COUPON_ID);

        // 멱등성 키 저장이 실제로 호출됐는지 검증 (다음 중복 요청 방어용)
        verify(idempotencyRepository, times(1)).save(USER_ID, COUPON_ID);
    }

    /**
     * [테스트 3] 멱등성 저장 순서 확인
     *
     * 시나리오:
     *   - 대기열 등록(enqueue) → 멱등성 저장(save) 순서여야 함
     *   - 순서가 반대면: 멱등성 저장 후 enqueue 실패 시
     *     → 다음 재시도 때 "이미 처리됨"으로 잘못 판단함
     *
     * 기대 결과:
     *   - enqueue 먼저 호출 후 save 호출
     */
    @Test
    @DisplayName("대기열 등록 후 멱등성 키를 저장한다 (순서 중요)")
    void 대기열등록_후_멱등성저장_순서확인() {
        // given
        when(idempotencyRepository.exists(USER_ID, COUPON_ID)).thenReturn(false);
        when(queueRepository.getPosition(USER_ID, COUPON_ID)).thenReturn(1L);

        // when
        couponIssueService.issue(USER_ID, COUPON_ID);

        // then: 호출 순서 검증 (enqueue → save 순이어야 함)
        var inOrder = inOrder(queueRepository, idempotencyRepository);
        inOrder.verify(queueRepository).enqueue(USER_ID, COUPON_ID);           // 1. 먼저 대기열 등록
        inOrder.verify(idempotencyRepository).save(USER_ID, COUPON_ID);        // 2. 그 다음 멱등성 저장
    }
}
