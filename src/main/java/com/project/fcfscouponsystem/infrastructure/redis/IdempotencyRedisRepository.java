package com.project.fcfscouponsystem.infrastructure.redis;

import com.project.fcfscouponsystem.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * [멱등성 보장] Redis TTL 기반 구현
 *
 * 멱등성(Idempotency)이란?
 *   같은 요청을 여러 번 보내도 결과가 딱 한 번만 처리되는 성질
 *
 * 왜 필요한가?
 *   클라이언트가 네트워크 오류로 응답을 못 받아 같은 요청을 재전송할 때
 *   → 멱등성 없으면: 쿠폰이 두 번 발급될 수 있음
 *   → 멱등성 있으면: "이미 처리된 요청입니다"로 안전하게 응답
 *
 * 구현 방식:
 *   key: "idempotency:coupon:{userId}:{couponId}"
 *   value: "QUEUED"
 *   TTL: 24시간 (만료 후엔 재요청 허용)
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyRedisRepository implements IdempotencyRepository {

    private static final String KEY_PREFIX = "idempotency:coupon:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean exists(Long userId, Long couponId) {
        // Boolean.TRUE.equals()로 null-safe 체크
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(buildKey(userId, couponId))
        );
    }

    @Override
    public void save(Long userId, Long couponId) {
        // setIfAbsent(NX): 키가 없을 때만 저장 (동시 요청이 와도 한 번만 저장)
        // TTL: 24시간 후 자동 삭제 → 메모리 누수 방지
        redisTemplate.opsForValue().setIfAbsent(
                buildKey(userId, couponId),
                "QUEUED",
                TTL
        );
    }

    private String buildKey(Long userId, Long couponId) {
        return KEY_PREFIX + userId + ":" + couponId;
    }
}
