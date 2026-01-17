package com.cdwater.ridego.identity.auth;

import com.cdwater.ridego.auth.JwtCodec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AccountRepository accounts;
    private final JwtCodec jwtCodec;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AccountRepository accounts, JwtCodec jwtCodec) {
        this.accounts = accounts;
        this.jwtCodec = jwtCodec;
    }

    public LoginResponse login(String username, String password) {
        Account account = accounts.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtCodec.issue(
                account.id().toString(), account.role(), account.driverId());
        return new LoginResponse(
                token,
                account.id().toString(),
                account.role(),
                account.driverId()
        );
    }
}
