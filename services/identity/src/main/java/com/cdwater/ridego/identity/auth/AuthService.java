package com.cdwater.ridego.identity.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AccountRepository accounts;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AccountRepository accounts, JwtService jwtService) {
        this.accounts = accounts;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String username, String password) {
        Account account = accounts.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(
                jwtService.issue(account),
                account.id().toString(),
                account.role(),
                account.driverId()
        );
    }
}
