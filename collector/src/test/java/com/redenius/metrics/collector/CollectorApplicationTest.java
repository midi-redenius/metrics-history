package com.redenius.metrics.collector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=localhost:1")
class CollectorApplicationTest {

    @MockitoBean
    MetricRegistrar metricRegistrar;

    @Autowired
    ApplicationContext context;

    @Test
    void shouldStartWithoutWebServer() {
        assertThat(context.getBeanNamesForType(RestClient.Builder.class)).isNotEmpty();
        assertThat(context.containsBean("tomcatServletWebServerFactory")).isFalse();
    }
}
