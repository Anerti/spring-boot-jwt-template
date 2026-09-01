package com.techindna.springbootjwttemplate.conf;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.maxmind.geoip2.DatabaseReader;
import com.techindna.springbootjwttemplate.config.TestcontainersConfig;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
public class FacadeIT {

    @MockitoBean private DatabaseReader geoIpCityReader;
}
