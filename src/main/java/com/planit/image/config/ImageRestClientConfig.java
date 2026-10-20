package com.planit.image.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ImageRestClientConfig {

    @Bean("kakaoProfileImageRestClient")
    RestClient kakaoProfileImageRestClient(
            ImageProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                properties.downloadConnectTimeout()
        );
        requestFactory.setReadTimeout(
                properties.downloadReadTimeout()
        );

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
