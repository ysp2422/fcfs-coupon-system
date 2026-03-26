package com.project.fcfscouponsystem.infrastructure.redis;

import com.project.fcfscouponsystem.repository.CouponQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * [대기열 시스템] Redis Sorted Set 기반 구현
 *
 * Redis Sorted Set 특징:
 *   - member: 저장할 값 (여기서는 "userId:couponId")
 *   - score: 정렬 기준 (여기서는 요청 타임스탬프)
 *   → score가 낮을수록 먼저 온 요청 = 선착순 자동 보장
 *
 * 흐름:
 *   요청 → ZADD (대기열 등록) → 202 반환 (대기 순서 포함)
 *   백그라운드 프로세서 → ZPOPMIN (앞에서부터 꺼내서 처리)
 */
@Repository
@RequiredArgsConstructor
public class CouponQueueRedisRepository implements CouponQueueRepository {

    // 전체 요청을 하나의 글로벌 대기열로 관리
    private static final String QUEUE_KEY = "coupon:queue";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void enqueue(Long userId, Long couponId) {
        // member: "userId:couponId", score: 현재 시간(ms) → 먼저 온 요청이 낮은 score
        redisTemplate.opsForZSet().add(
                QUEUE_KEY,
                userId + ":" + couponId,
                System.currentTimeMillis()
        );
    }

    @Override
    public Set<String> dequeue(int count) {
        // ZPOPMIN: score가 낮은 것부터 (= 먼저 온 것부터) count개 꺼냄
        // popMin은 꺼내면서 동시에 삭제 → 중복 처리 방지
        var typedTuples = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (typedTuples == null) return Set.of();

        // TypedTuple에서 value(member)만 추출
        return typedTuples.stream()
                .map(tuple -> tuple.getValue())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Long getPosition(Long userId, Long couponId) {
        // ZRANK: 해당 member의 순위 반환 (0부터 시작, 0 = 맨 앞)
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId + ":" + couponId);
        return rank != null ? rank + 1 : null; // 1부터 시작하도록 +1
    }
}
