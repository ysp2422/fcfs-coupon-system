package com.project.fcfscouponsystem.domain.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long couponId) {
        super(String.format("쿠폰 선착순이 마감되었습니다. couponId: %d", couponId));
    }
}
