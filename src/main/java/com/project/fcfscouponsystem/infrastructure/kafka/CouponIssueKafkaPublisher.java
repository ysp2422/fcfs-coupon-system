package com.project.fcfscouponsystem.infrastructure.kafka;

import com.project.fcfscouponsystem.publisher.CouponIssueEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaPublisher implements CouponIssueEventPublisher {

    private static final String TOPIC = "coupon_issue_requests";

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(Long userId, Long couponId) {
        String payload = new CouponIssueMessage(userId, couponId).serialize();

        kafkaTemplate.send(TOPIC, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka 전송 성공: {}", payload);
                    } else {
                        // TODO: 전송 실패 시 보상 트랜잭션 (Redis 롤백 등) 처리 필요
                        log.error("Kafka 전송 실패: {}", payload, ex);
                    }
                });
    }
}
