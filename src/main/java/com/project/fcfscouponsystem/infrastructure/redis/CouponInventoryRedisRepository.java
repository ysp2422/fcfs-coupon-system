package com.project.fcfscouponsystem.infrastructure.redis;

import com.project.fcfscouponsystem.repository.CouponInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponInventoryRedisRepository implements CouponInventoryRepository {

    private static final String KEY_PREFIX = "coupon:inventory:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Long decrement(Long couponId) {
        return redisTemplate.opsForValue().decrement(KEY_PREFIX + couponId);
    }

    @Override
    public void increment(Long couponId) {
        redisTemplate.opsForValue().increment(KEY_PREFIX + couponId);
    }
}
