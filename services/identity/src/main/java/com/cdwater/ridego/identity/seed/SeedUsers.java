package com.cdwater.ridego.identity.seed;

import com.cdwater.ridego.contracts.Roles;
import com.cdwater.ridego.identity.auth.Account;
import com.cdwater.ridego.identity.auth.AccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "ridego.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedUsers implements ApplicationRunner {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SeedUsers(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("rider1", Roles.RIDER, null);
        seed("driver1", Roles.DRIVER, "driver-1");
    }

    private void seed(String username, String role, String driverId) {
        if (accounts.findByUsername(username).isPresent()) {
            return;
        }
        accounts.insert(new Account(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode("ridego"),
                role,
                driverId,
                Instant.now()
        ));
    }
}
