package com.project.fcfscouponsystem.service;

import com.project.fcfscouponsystem.controller.dto.CouponIssueResponse;
import com.project.fcfscouponsystem.repository.CouponQueueRepository;
import com.project.fcfscouponsystem.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [쿠폰 발급 서비스 - 대기열 기반으로 전면 개편]
 *
 * 변경된 흐름:
 *   기존: 요청 → Redis 검증 → Kafka 발행 (동기, 느림)
 *   변경: 요청 → 멱등성 체크 → 대기열 등록 → 202 반환 (비동기, 빠름)
 *         └→ CouponQueueProcessor가 백그라운드에서 실제 처리
 *
 * 이 방식의 장점:
 *   - 대량 트래픽에도 Controller가 즉시 응답 가능 (Redis 쓰기 한 번만)
 *   - 처리 속도는 백그라운드 프로세서가 조절 (시스템 부하 제어)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final IdempotencyRepository idempotencyRepository;  // 멱등성 체크
    private final CouponQueueRepository queueRepository;        // 대기열 관리

    public CouponIssueResponse issue(Long userId, Long couponId) {

        // [1단계] 멱등성 체크
        // 동일한 userId + couponId 요청이 이미 처리됐으면 즉시 반환
        // → 네트워크 오류로 클라이언트가 재시도해도 중복 발급 없음
        if (idempotencyRepository.exists(userId, couponId)) {
            log.info("멱등성 체크 - 이미 처리된 요청: userId={}, couponId={}", userId, couponId);
            return CouponIssueResponse.alreadyQueued();
        }

        // [2단계] 대기열 등록
        // Redis Sorted Set에 score=현재시간으로 추가 → 선착순 자동 보장
        // 실제 Lua Script 검증은 CouponQueueProcessor에서 처리
        queueRepository.enqueue(userId, couponId);

        // [3단계] 멱등성 키 저장 (24시간 TTL)
        // 대기열 등록 후 저장해야 재시도 시 "이미 등록됨"으로 응답 가능
        idempotencyRepository.save(userId, couponId);

        // [4단계] 현재 대기 순서 조회 후 반환
        // 클라이언트가 "n번째 대기 중" UI를 보여줄 수 있음
        Long position = queueRepository.getPosition(userId, couponId);
        log.info("대기열 등록 완료: userId={}, couponId={}, 순서={}", userId, couponId, position);

        return CouponIssueResponse.of(position);
    }
}
