package TaskPilot.pre;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtFilter jwtFilter;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService, JwtFilter jwtFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtFilter = jwtFilter;
    }

    /**
     * Главная цепочка фильтров безопасности.
     *
     * SessionCreationPolicy.STATELESS — сервер не хранит сессии.
     * Каждый запрос должен содержать JWT-токен. Это стандарт для REST API.
     *
     * Открытые маршруты:
     * - POST /auth/login  — получить токен (регистрация сессии)
     * - POST /user        — создать аккаунт (регистрация пользователя)
     * - GET /h2-console   — консоль БД для разработки
     *
     * Все остальные маршруты требуют валидного JWT.
     *
     * jwtFilter добавляется перед стандартным UsernamePasswordAuthenticationFilter —
     * это значит наш фильтр отработает первым и установит аутентификацию.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.disable())) // для H2 console
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console", "/h2-console/**").permitAll() // H2 консоль
                        .requestMatchers("/auth/login").permitAll()     // вход — без токена
                        .requestMatchers("/logreg.html").permitAll()
                        .requestMatchers("/user").permitAll()           // регистрация — без токена// временно для теста
                        .requestMatchers("/chat-test.html").permitAll()
                        .requestMatchers("/ws", "/ws/**", "/ws/info/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider связывает UserDetailsService и PasswordEncoder.
     * AuthenticationManager использует его для проверки логина/пароля при входе.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager — точка входа для аутентификации.
     * Используется в AuthController.login() для проверки логина/пароля.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
