package ru.netology.cloud.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.netology.cloud.exception.InvalidCredentialsException;
import ru.netology.cloud.service.AuthService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginReturnsAuthTokenField() throws Exception {
        when(authService.login("user@example.com", "password")).thenReturn("token-1");

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['auth-token']").value("token-1"));
    }

    @Test
    void loginWithBadCredentialsReturns400WithError() throws Exception {
        when(authService.login("user@example.com", "bad")).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"user@example.com\",\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bad credentials"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void loginWithEmptyBodyReturns400() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void logoutPassesHeaderToService() throws Exception {
        mvc.perform(post("/logout").header("auth-token", "Bearer token-1"))
                .andExpect(status().isOk());

        verify(authService).logout("Bearer token-1");
    }

    @Test
    void corsAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/login")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
