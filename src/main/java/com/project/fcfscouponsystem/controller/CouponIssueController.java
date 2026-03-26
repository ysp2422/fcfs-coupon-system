package com.project.fcfscouponsystem.controller;

import com.project.fcfscouponsystem.common.ApiResponse;
import com.project.fcfscouponsystem.controller.dto.CouponIssueRequest;
import com.project.fcfscouponsystem.controller.dto.CouponIssueResponse;
import com.project.fcfscouponsystem.service.CouponIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [쿠폰 발급 컨트롤러]
 *
 * 기존: 200 OK (동기 처리 완료)
 * 변경: 202 Accepted (대기열 등록 완료, 실제 처리는 백그라운드에서)
 *
 * 202 Accepted의 의미:
 *   "요청은 받았는데, 아직 처리는 안 됐어요. 나중에 알려드릴게요."
 *   → 대기열 기반 비동기 처리에 적합한 HTTP 상태코드
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponIssueController {

    private final CouponIssueService couponIssueService;

    /**
     * 쿠폰 발급 요청
     * @param request - { "userId": 1, "couponId": 100 }  (@RequestBody로 JSON 수신)
     * @return 202 Accepted + 대기 순서
     *         { "success": true, "message": "...", "data": { "queuePosition": 5, ... } }
     */
    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issueCoupon(
            @RequestBody CouponIssueRequest request) {

        // Service에서 대기열 등록 후 순서 반환
        // @ResponseBody는 @RestController에 포함되어 자동으로 JSON 직렬화
        CouponIssueResponse response = couponIssueService.issue(request.userId(), request.couponId());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)  // 202: 요청 접수됨, 처리는 비동기로
                .body(ApiResponse.ok(response.message(), response));
    }
}
