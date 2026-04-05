package TaskPilot.pre;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Утилита для работы с JWT-токенами.
 *
 * Как работает JWT:
 * Токен состоит из трёх частей, разделённых точкой: header.payload.signature
 * - header: алгоритм подписи (HS256)
 * - payload: данные (username, время выдачи, время истечения)
 * - signature: HMAC-подпись header+payload с помощью секретного ключа
 *
 * Сервер проверяет подпись — если она совпадает, токен настоящий и не изменён.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    // @Value вытаскивает значения из application.properties
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        // Keys.hmacShaKeyFor создаёт ключ для алгоритма HS256/HS512
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * Генерирует JWT-токен для пользователя.
     * Вызывается при успешном входе (POST /auth/login).
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                          // кто владелец токена
                .issuedAt(new Date())                       // когда выдан
                .expiration(new Date(System.currentTimeMillis() + expiration)) // когда истекает
                .signWith(key)                              // подписываем ключом
                .compact();                                 // собираем строку
    }

    /**
     * Извлекает username из токена.
     * Используется в JwtFilter при каждом запросе.
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Проверяет, что токен валиден: подпись верна и срок не истёк.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            // Любое исключение = токен невалиден (подделан, истёк, неверный формат)
            return false;
        }
    }

    /**
     * Парсит и верифицирует токен. Бросает исключение если подпись неверна или токен истёк.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
