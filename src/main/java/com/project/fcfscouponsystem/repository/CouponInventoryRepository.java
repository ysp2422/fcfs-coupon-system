package com.project.fcfscouponsystem.repository;

public interface CouponInventoryRepository {

    /**
     * 재고를 1 차감하고, 차감 후 남은 수량을 반환합니다.
     */
    Long decrement(Long couponId);

    /**
     * 재고를 1 복원합니다. (롤백 용도)
     */
    void increment(Long couponId);
}
