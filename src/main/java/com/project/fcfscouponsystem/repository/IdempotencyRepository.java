package com.project.fcfscouponsystem.repository;

public interface IdempotencyRepository {

    /**
     * 해당 요청이 이미 처리됐는지 확인
     */
    boolean exists(Long userId, Long couponId);

    /**
     * 처리된 요청으로 저장 (TTL 24시간)
     */
    void save(Long userId, Long couponId);
}
