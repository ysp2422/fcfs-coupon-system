package com.project.fcfscouponsystem.infrastructure.redis;

import com.project.fcfscouponsystem.domain.CouponIssueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * [Lua Script 원자적 처리]
 *
 * 기존 문제:
 *   1) Redis Set에 userId 추가 (중복 체크)
 *   2) Redis Decrement (재고 차감)
 *   → 1번과 2번 사이에 서버가 죽으면 Set엔 있는데 재고는 안 줄어드는 불일치 발생
 *
 * 해결책:
 *   Lua Script로 두 연산을 하나의 트랜잭션처럼 묶음
 *   → Redis는 Lua Script를 원자적으로 실행 (중간에 다른 요청 끼어들기 불가)
 */
@Repository
@RequiredArgsConstructor
public class CouponIssueLuaRepository {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua Script:
     *   KEYS[1] = coupon:issued:users:{couponId}  → 발급된 유저 Set
     *   KEYS[2] = coupon:inventory:{couponId}      → 쿠폰 재고
     *   ARGV[1] = userId
     *
     *   반환값:
     *     0 → 성공 (중복 아님 + 재고 있음)
     *     1 → 중복 (이미 발급 요청한 유저)
     *     2 → 재고 소진
     */
    private static final String ISSUE_SCRIPT =
            // Step 1: userId를 Set에 추가 시도 (SADD는 새로 추가되면 1, 이미 있으면 0 반환)
            "local added = redis.call('SADD', KEYS[1], ARGV[1]) " +
            "if added == 0 then " +
            "  return 1 " +                      // 이미 존재 → 중복
            "end " +
            // Step 2: 재고 1 차감 (DECR은 차감 후 남은 수량 반환)
            "local remaining = redis.call('DECR', KEYS[2]) " +
            "if remaining < 0 then " +
            // 재고 소진 → Set에서 userId 제거하고 재고 복원 (원자적으로 롤백)
            "  redis.call('SREM', KEYS[1], ARGV[1]) " +
            "  redis.call('INCR', KEYS[2]) " +
            "  return 2 " +                      // 재고 소진
            "end " +
            "return 0";                          // 성공

    private static final DefaultRedisScript<Long> REDIS_SCRIPT =
            new DefaultRedisScript<>(ISSUE_SCRIPT, Long.class);

    /**
     * Lua Script 실행 → 중복 체크 + 재고 차감을 원자적으로 처리
     */
    public CouponIssueResult execute(Long couponId, Long userId) {
        String issuedUsersKey = "coupon:issued:users:" + couponId;
        String inventoryKey = "coupon:inventory:" + couponId;

        Long resultCode = redisTemplate.execute(
                REDIS_SCRIPT,
                List.of(issuedUsersKey, inventoryKey),  // KEYS
                String.valueOf(userId)                   // ARGV
        );

        return CouponIssueResult.from(resultCode);
    }
}
