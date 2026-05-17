package TaskPilot.pre;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public void uploadAvatar(MultipartFile file) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains("."))
            ext = original.substring(original.lastIndexOf('.'));

        Path dir = Paths.get("./uploads/avatars");
        Files.createDirectories(dir);

        // Удаляем старый файл если есть
        if (user.getAvatarPath() != null) {
            Path old = Paths.get(user.getAvatarPath());
            Files.deleteIfExists(old);
        }

        Path dest = dir.resolve(user.getId() + ext);
        Files.write(dest, file.getBytes());
        user.setAvatarPath(dest.toString());
        userRepository.save(user);
    }

    public byte[] getAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        if (user.getAvatarPath() == null) return null;
        try {
            Path path = Paths.get(user.getAvatarPath());
            if (!Files.exists(path)) return null;
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
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
