package com.project.fcfscouponsystem.controller.dto;

/**
 * [쿠폰 발급 응답 DTO]
 * 대기열 등록 후 클라이언트에게 반환하는 정보
 */
public record CouponIssueResponse(
        Long queuePosition,  // 현재 대기 순서 (1부터 시작)
        String message
) {
    public static CouponIssueResponse of(Long position) {
        return new CouponIssueResponse(position, position + "번째로 대기 중입니다.");
    }

    public static CouponIssueResponse alreadyQueued() {
        return new CouponIssueResponse(null, "이미 대기열에 등록된 요청입니다.");
    }
}
