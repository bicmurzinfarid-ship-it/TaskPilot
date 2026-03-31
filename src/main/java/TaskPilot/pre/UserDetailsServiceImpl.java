package TaskPilot.pre;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Реализация UserDetailsService для Spring Security.
 *
 * Spring Security вызывает loadUserByUsername когда нужно проверить
 * учётные данные пользователя (при логине через стандартный механизм)
 * или когда JwtFilter устанавливает аутентификацию в SecurityContext.
 *
 * Мы берём пользователя из нашей БД через UserRepository и оборачиваем
 * его в стандартный объект UserDetails, который понимает Spring Security.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim();

        User user = userRepository.findByUsernameIgnoreCase(normalized)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // org.springframework.security.core.userdetails.User — это стандартный
        // класс Spring Security, не наш User. Передаём username, хэш пароля
        // и список ролей (пока пустой — роли добавим позже).
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())   // уже хэшированный BCrypt пароль из БД
                .roles("USER")
                .build();
    }
}
