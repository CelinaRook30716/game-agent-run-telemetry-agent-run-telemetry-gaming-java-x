package dev.learninggame;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("infrai")
record InfraiProperties(String apiKey, String baseUrl) {}

@Configuration
class InfraiConfig {
    @Bean
    OpenAIClient openAIClient(InfraiProperties properties) {
        return OpenAIOkHttpClient.builder()
            .apiKey(properties.apiKey())
            .baseUrl(properties.baseUrl())
            .build();
    }
}
