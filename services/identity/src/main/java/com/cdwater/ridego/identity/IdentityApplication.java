package com.cdwater.ridego.identity;

import com.cdwater.ridego.auth.JwtConfiguration;
import com.cdwater.ridego.web.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({JwtConfiguration.class, WebConfiguration.class})
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
