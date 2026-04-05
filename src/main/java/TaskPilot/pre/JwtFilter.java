package TaskPilot.pre;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT-фильтр — выполняется ровно один раз на каждый HTTP-запрос.
 *
 * Схема работы:
 * 1. Клиент делает запрос с заголовком: Authorization: Bearer <токен>
 * 2. Фильтр извлекает токен из заголовка
 * 3. Проверяет подпись и срок действия через JwtUtil
 * 4. Загружает пользователя из БД через UserDetailsService
 * 5. Помещает аутентификацию в SecurityContext
 * 6. Spring Security видит аутентифицированного пользователя и пропускает запрос
 *
 * Если токена нет или он невалиден — просто передаём запрос дальше без аутентификации.
 * Spring Security сам вернёт 401/403 для защищённых маршрутов.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Читаем заголовок Authorization
        String authHeader = request.getHeader("Authorization");

        // Токен должен начинаться с "Bearer " — это стандарт RFC 6750
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Токена нет — передаём запрос дальше (незащищённые маршруты пройдут)
            filterChain.doFilter(request, response);
            return;
        }

        // Обрезаем "Bearer " (7 символов) — остаётся сам токен
        String token = authHeader.substring(7);

        // Проверяем токен и устанавливаем аутентификацию
        if (jwtUtil.isTokenValid(token)) {
            String username = jwtUtil.extractUsername(token);

            // Устанавливаем только если в SecurityContext ещё нет аутентификации
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // UsernamePasswordAuthenticationToken — стандартный объект аутентификации
                // Третий параметр — список прав (roles), берём из UserDetails
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                // Добавляем детали запроса (IP, sessionId) — полезно для логов
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Сообщаем Spring Security что пользователь аутентифицирован
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Передаём запрос следующему фильтру/контроллеру
        filterChain.doFilter(request, response);
    }
}
