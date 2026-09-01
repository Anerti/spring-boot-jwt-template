package com.techindna.springbootjwttemplate;

import com.maxmind.geoip2.DatabaseReader;
import com.techindna.springbootjwttemplate.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class SpringBootJwtTemplateApplicationTests {

    @MockitoBean private DatabaseReader geoIpCityReader;

    @Test
    void contextLoads() {
    }

}
