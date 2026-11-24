package com.mohitdeo.saga.orchestrator.config;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Workers that drive sagas. If the queue is full, the saga waits for the recovery job instead. */
    @Bean
    TaskExecutor sagaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("saga-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Builds a RestClient with connect and read timeouts, so a hung participant never blocks a saga worker forever. */
    @Bean
    ParticipantRestClients participantRestClients(SagaProperties props) {
        SagaProperties.Participants p = props.participants();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(p.readTimeout());
        return new ParticipantRestClients(
                RestClient.builder().baseUrl(p.orderUrl()).requestFactory(factory).build(),
                RestClient.builder().baseUrl(p.inventoryUrl()).requestFactory(factory).build(),
                RestClient.builder().baseUrl(p.paymentUrl()).requestFactory(factory).build());
    }

    public record ParticipantRestClients(RestClient orders, RestClient inventory, RestClient payments) {
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
