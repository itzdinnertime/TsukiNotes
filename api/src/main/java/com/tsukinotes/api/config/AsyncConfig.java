package com.tsukinotes.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async, used to send emails in the background (on Spring Boot's default task executor). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
