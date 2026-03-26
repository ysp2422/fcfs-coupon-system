package com.project.fcfscouponsystem.repository;

import java.util.Set;

public interface CouponQueueRepository {

    /**
     * 대기열에 유저 추가 (score = 현재 타임스탬프 → 선착순 보장)
     */
    void enqueue(Long userId, Long couponId);

    /**
     * 대기열에서 앞에서부터 count개 꺼냄 (배치 처리용)
     * @return "userId:couponId" 형태의 문자열 Set
     */
    Set<String> dequeue(int count);

    /**
     * 현재 대기 순서 조회 (0부터 시작, 0이면 맨 앞)
     */
    Long getPosition(Long userId, Long couponId);
}
