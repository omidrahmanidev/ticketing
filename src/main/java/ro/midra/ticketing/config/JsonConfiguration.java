package ro.midra.ticketing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class JsonConfiguration {
    @Bean @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper outboxObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }
}
