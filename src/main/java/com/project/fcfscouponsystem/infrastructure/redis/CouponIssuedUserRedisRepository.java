package com.project.fcfscouponsystem.infrastructure.redis;

import com.project.fcfscouponsystem.repository.CouponIssuedUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponIssuedUserRedisRepository implements CouponIssuedUserRepository {

    private static final String KEY_PREFIX = "coupon:issued:users:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean addIfAbsent(Long couponId, Long userId) {
        Long addedCount = redisTemplate.opsForSet().add(KEY_PREFIX + couponId, String.valueOf(userId));
        return addedCount != null && addedCount > 0;
    }

    @Override
    public void remove(Long couponId, Long userId) {
        redisTemplate.opsForSet().remove(KEY_PREFIX + couponId, String.valueOf(userId));
    }
}
