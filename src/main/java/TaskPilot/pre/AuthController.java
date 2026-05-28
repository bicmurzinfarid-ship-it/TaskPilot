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

    record LoginRequest(String username, String password) {}
}
