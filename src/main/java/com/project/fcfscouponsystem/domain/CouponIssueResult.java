package com.project.fcfscouponsystem.domain;

/**
 * [Lua Script 결과 코드]
 * Redis Lua Script가 반환하는 숫자를 의미있는 enum으로 변환
 * Lua Script와 Java 코드 간의 계약(Contract)을 명확하게 정의
 */
public enum CouponIssueResult {

    SUCCESS(0L),       // 중복 없음 + 재고 있음 → 정상 발급 가능
    DUPLICATE(1L),     // 이미 발급 요청한 유저
    OUT_OF_STOCK(2L);  // 재고 소진

    private final Long code;

    CouponIssueResult(Long code) {
        this.code = code;
    }

    public static CouponIssueResult from(Long code) {
        for (CouponIssueResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        throw new IllegalArgumentException("알 수 없는 Lua Script 결과 코드: " + code);
    }
}
