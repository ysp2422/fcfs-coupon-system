package com.project.fcfscouponsystem.publisher;

public interface CouponIssueEventPublisher {

    /**
     * 쿠폰 발급 이벤트를 발행합니다.
     */
    void publish(Long userId, Long couponId);
}
