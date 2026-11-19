package ru.netology.cloud.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.netology.cloud.controller.AuthController;
import ru.netology.cloud.service.AuthService;

/** Проверяет auth-token и кладёт id пользователя в атрибут запроса. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "userId";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight приходит без заголовка auth-token
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        Long userId = authService.authenticate(request.getHeader(AuthController.AUTH_HEADER));
        request.setAttribute(USER_ID_ATTR, userId);
        return true;
    }
}
