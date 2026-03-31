package TaskPilot.pre;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
    }

    public User createUser(User user) {
        if (user.getId() != null) {
            throw new IllegalArgumentException("Id should be empty");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email shouldn't be empty");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username shouldn't be empty");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password shouldn't be empty");
        }

        String normalizedUsername = user.getUsername().trim();
        String normalizedEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);

        // Проверяем уникальность без учёта регистра
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already taken");
        }
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Username already taken");
        }

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);

        // Хэшируем пароль перед сохранением
        user.setPassword(passwordEncoder.encode(user.getPassword().trim()));

        return userRepository.save(user);
    }
}
