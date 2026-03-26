package com.project.fcfscouponsystem.infrastructure.kafka;

public record CouponIssueMessage(Long userId, Long couponId) {

    public String serialize() {
        return userId + "," + couponId;
    }
}
