package com.project.fcfscouponsystem.service;

import com.project.fcfscouponsystem.domain.CouponIssueResult;
import com.project.fcfscouponsystem.infrastructure.redis.CouponIssueLuaRepository;
import com.project.fcfscouponsystem.publisher.CouponIssueEventPublisher;
import com.project.fcfscouponsystem.repository.CouponQueueRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * [대기열 백그라운드 프로세서]
 *
 * 역할:
 *   대기열(Redis Sorted Set)에 쌓인 요청을 주기적으로 꺼내서 실제 처리
 *   → Controller는 요청을 대기열에 넣고 즉시 202를 반환 (빠른 응답)
 *   → 이 클래스가 백그라운드에서 실제 발급 로직 처리 (부하 분산)
 *
 * [서킷 브레이커 적용]
 *   Redis 또는 Kafka 장애 시:
 *   - 10번 중 5번 이상 실패 → 서킷 OPEN (요청 즉시 차단, fallback 실행)
 *   - 10초 후 HALF-OPEN → 3번 시도해서 성공하면 서킷 CLOSED (정상 복구)
 *   → 장애가 전체 서버로 전파되는 것을 막음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponQueueProcessor {

    // 한 번에 처리할 배치 크기 (너무 크면 Redis 부하, 너무 작으면 처리 느림)
    private static final int BATCH_SIZE = 100;

    private final CouponQueueRepository queueRepository;
    private final CouponIssueLuaRepository luaRepository;
    private final CouponIssueEventPublisher eventPublisher;

    /**
     * 100ms마다 대기열에서 BATCH_SIZE개씩 꺼내서 처리
     * fixedDelay: 이전 실행이 끝난 후 100ms 대기 → 중복 실행 방지
     */
    @Scheduled(fixedDelay = 100)
    @CircuitBreaker(name = "redis", fallbackMethod = "processFallback")
    public void process() {
        // 대기열에서 앞에서부터 100개 꺼냄 (꺼내는 동시에 삭제 → 중복 처리 없음)
        Set<String> entries = queueRepository.dequeue(BATCH_SIZE);

        if (entries.isEmpty()) return;

        log.debug("대기열 처리 시작: {}건", entries.size());

        for (String entry : entries) {
            processEntry(entry);
        }
    }

    /**
     * 단건 처리: Lua Script로 원자적 검증 → Kafka 발행
     */
    @CircuitBreaker(name = "kafka", fallbackMethod = "publishFallback")
    private void processEntry(String entry) {
        // entry 형식: "userId:couponId"
        String[] parts = entry.split(":");
        Long userId = Long.parseLong(parts[0]);
        Long couponId = Long.parseLong(parts[1]);

        // [Lua Script] 중복 체크 + 재고 차감을 원자적으로 한 번에 처리
        CouponIssueResult result = luaRepository.execute(couponId, userId);

        switch (result) {
            case SUCCESS ->
                // 검증 통과 → Kafka로 Worker 서버에 발급 이벤트 전달
                eventPublisher.publish(userId, couponId);

            case DUPLICATE ->
                // 이미 처리된 요청 (멱등성에서 못 걸러진 경우)
                log.warn("중복 처리 시도 무시: userId={}, couponId={}", userId, couponId);

            case OUT_OF_STOCK ->
                // 재고 소진 → 로그만 남기고 종료 (Kafka 발행 안 함)
                log.info("재고 소진으로 발급 불가: userId={}, couponId={}", userId, couponId);
        }
    }

    /**
     * [서킷 브레이커 fallback] Redis 장애 시 실행
     * process() 메서드의 시그니처 + Throwable 파라미터 추가 필요
     */
    private void processFallback(Throwable t) {
        log.error("Redis 서킷 브레이커 OPEN - 대기열 처리 중단. 원인: {}", t.getMessage());
        // 실제 운영에서는 Slack/PagerDuty 알림 전송
    }

    /**
     * [서킷 브레이커 fallback] Kafka 장애 시 실행
     */
    private void publishFallback(String entry, Throwable t) {
        log.error("Kafka 서킷 브레이커 OPEN - 발행 실패: entry={}, 원인: {}", entry, t.getMessage());
        // TODO: Dead Letter Queue 또는 DB에 실패 이력 저장 후 재처리
    }
}
