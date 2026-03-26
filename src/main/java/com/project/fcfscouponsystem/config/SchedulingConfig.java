package com.project.fcfscouponsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * [스케줄링 활성화]
 * @Scheduled 어노테이션이 동작하려면 반드시 필요
 * CouponQueueProcessor의 @Scheduled(fixedDelay = 100)이 여기서 활성화됨
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
