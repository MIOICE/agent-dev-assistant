package com.gaozhaoyang.agent.casework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaseSerializationConfiguration {

    @Bean
    ObjectMapper caseSnapshotObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
