package com.cdwater.ridego.location;

import com.cdwater.ridego.messaging.MessagingConfiguration;
import com.cdwater.ridego.web.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({WebConfiguration.class, MessagingConfiguration.class})
public class LocationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationApplication.class, args);
    }
}
