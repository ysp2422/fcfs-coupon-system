package com.project.fcfscouponsystem.repository;

public interface CouponIssuedUserRepository {

    /**
     * 유저를 발급 목록에 추가합니다.
     * @return 추가 성공 시 true, 이미 존재하면 false
     */
    boolean addIfAbsent(Long couponId, Long userId);

    /**
     * 유저를 발급 목록에서 제거합니다. (롤백 용도)
     */
    void remove(Long couponId, Long userId);
}
