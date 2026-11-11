package com.edu.csu.web.pet_shop_backend.common.utils;

import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 * access_token 短期有效，默认2小时，用于每次请求鉴权
 * refresh_token 长期有效，默认7天，用于刷新access_token
 */

@Component
public class JwtUtil {

    //配置项(application.yaml中)
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms:7200000}")  //默认2小时
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms:604800000}")  //默认7天
    private long refreshTokenExpirationMs;

    //Claim字符常量
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "userName";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";  // "access" | "refresh"

    //生成token
    public String generateAccessToken(Long userId, String username, UserRoleEnum role) {
        return buildToken(userId, username, role, "access", accessTokenExpirationMs);
    }

    public String generateRefreshToken(Long userId, String username, UserRoleEnum role) {
        return buildToken(userId, username, role, "refresh", refreshTokenExpirationMs);
    }

    private String buildToken(Long userId, String username, UserRoleEnum role, String type, long expirationMs) {
        Date now = new Date();
        Date expiry= new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.getValue())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getKey())
                .compact();
    }

    //解析token

    /**
     * 解析并验证token，返回Claims;验证失败抛出JwtException的子类异常
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //判断是否为access_token
    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get(CLAIM_TYPE,String.class));
    }

    //判断是否为refresh_token
    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get(CLAIM_TYPE,String.class));
    }

    public Long getUserId(Claims claims) {
        //JJWT默认将数字解析为Integer，用Number兼容long
        return ((Number)claims.get(CLAIM_USER_ID)).longValue();
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public UserRoleEnum getRole(Claims claims) {
        Object roleClaim = claims.get(CLAIM_ROLE);
        if (roleClaim instanceof Number number) {
            return UserRoleEnum.fromCode(number.intValue());
        }

        String roleValue = String.valueOf(roleClaim);
        try {
            return UserRoleEnum.fromValue(roleValue);
        } catch (IllegalArgumentException ignored) {
            return UserRoleEnum.valueOf(roleValue);
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
