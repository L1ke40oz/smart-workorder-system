package com.smartworkorder.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration @EnableAsync @EnableScheduling
public class KnowledgeConfig {
    @Bean(name="knowledgeExecutor")
    public ThreadPoolTaskExecutor knowledgeExecutor() {
        var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(1);pool.setMaxPoolSize(1);pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("knowledge-");
        // A periodic database scan repairs dropped or failed indexing jobs.
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return pool;
    }
}
