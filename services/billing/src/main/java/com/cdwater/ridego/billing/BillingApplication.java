package com.cdwater.ridego.billing;

import com.cdwater.ridego.messaging.MessagingConfiguration;
import com.cdwater.ridego.web.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({MessagingConfiguration.class, WebConfiguration.class})
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
