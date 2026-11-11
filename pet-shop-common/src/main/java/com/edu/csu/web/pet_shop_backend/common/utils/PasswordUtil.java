package com.edu.csu.web.pet_shop_backend.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtil {

    private static final BCryptPasswordEncoder Encoder = new BCryptPasswordEncoder();

    public String encode(String rawpassword) {
        return Encoder.encode(rawpassword);
    }

    public boolean matches(String rawpassword, String encodedPassword) {
        return Encoder.matches(rawpassword, encodedPassword);
    }
}
