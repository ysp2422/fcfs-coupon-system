package com.project.fcfscouponsystem.domain.exception;

public class DuplicateIssueException extends RuntimeException {

    public DuplicateIssueException(Long userId, Long couponId) {
        super(String.format("이미 발급 요청된 유저입니다. userId: %d, couponId: %d", userId, couponId));
    }
}
