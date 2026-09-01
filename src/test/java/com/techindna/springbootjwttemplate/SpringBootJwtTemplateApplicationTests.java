package com.techindna.springbootjwttemplate;

import com.techindna.springbootjwttemplate.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class SpringBootJwtTemplateApplicationTests {

    @Test
    void contextLoads() {
    }

}
