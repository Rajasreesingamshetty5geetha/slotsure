package com.slotsure.slot.config;

import com.uber.h3core.H3Core;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;

@Configuration
public class SlotConfig {

    @Bean
    public H3Core h3Core() throws IOException {
        return H3Core.newInstance();
    }

    @Bean
    public RedisScript<Long> holdScript() {
        return script("scripts/hold.lua");
    }

    @Bean
    public RedisScript<Long> releaseScript() {
        return script("scripts/release.lua");
    }

    private static RedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
