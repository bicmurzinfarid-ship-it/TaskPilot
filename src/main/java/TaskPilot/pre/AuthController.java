package TaskPilot.pre;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Вход в систему.
     *
     * Запрос:  POST /auth/login
     *          Body: { "username": "ivan", "password": "secret" }
     *
     * Ответ:   200 OK
     *          Body: { "token": "eyJhbG..." }
     *
     *          401 Unauthorized — если пароль неверный или пользователь не найден
     *
     * Как работает:
     * 1. authenticationManager.authenticate() берёт username+password
     * 2. Внутри вызывает наш UserDetailsServiceImpl.loadUserByUsername()
     * 3. Сравнивает введённый пароль с BCrypt-хэшем из БД
     * 4. Если совпало — возвращаем JWT-токен
     * 5. Если нет — бросает BadCredentialsException → 401
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String login = request.username() == null ? "" : request.username().trim();
        String rawPassword = request.password() == null ? "" : request.password().trim();

        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmailIgnoreCase(login))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Неверный логин или пароль"));
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "userId", user.getId()));
    }

    /**
     * DTO для тела запроса входа.
     * Record — компактный способ объявить класс только с полями и геттерами (Java 16+).
     */
    record LoginRequest(String username, String password) {}
}
