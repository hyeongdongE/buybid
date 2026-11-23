package com.example.saleapp;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.repository.ChatMessageRepository;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.ChatRoomRepository;
import com.example.saleapp.repository.UsersRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsersRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository messages;
    @Autowired PasswordEncoder encoder;

    @Test
    void protectedApiReturns401WithoutAuthentication() throws Exception {
        mvc.perform(post("/api/products/buynow/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void signinRequiresCsrfAndUnknownCredentialsReturn401() throws Exception {
        var signin = post("/api/signin").contentType("application/json")
                .content("{\"email\":\"missing@example.test\",\"password\":\"test-password\"}");
        mvc.perform(signin).andExpect(status().isForbidden());
        mvc.perform(signin.with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void writesShortLivedAccessCookieAndRefreshCookieWithSevenDayTtl() throws Exception {
        var account = account();
        var response = mvc.perform(post("/api/signin").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"test-password\"}".formatted(account.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("accessToken=") && cookie.contains("Max-Age=900") && cookie.contains("HttpOnly"))
                .anyMatch(cookie -> cookie.startsWith("refreshToken=") && cookie.contains("Max-Age=604800") && cookie.contains("HttpOnly"));
    }

    @Test
    void refreshWithoutSessionReturns401() throws Exception {
        mvc.perform(post("/api/refresh").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void chatHistoryIsVisibleOnlyToTransactionParticipants() throws Exception {
        var buyer = account();
        var seller = account();
        var stranger = account();
        var room = rooms.save(ChatRoomEntity.builder().buyerId(buyer).sellarId(seller).build());
        var path = "/chat/history/" + room.getChatRoomId();
        mvc.perform(get(path).with(user(new CustomUserDetail(buyer)))).andExpect(status().isOk());
        mvc.perform(get(path).with(user(new CustomUserDetail(seller)))).andExpect(status().isOk());
        mvc.perform(get(path).with(user(new CustomUserDetail(stranger)))).andExpect(status().isForbidden());
    }

    @Test
    void browserCsrfTokenWorksAndRefreshRotatesOnlyOnce() throws Exception {
        var account = account();
        var page = mvc.perform(get("/signin")).andExpect(status().isOk()).andReturn().getResponse();
        var csrfCookie = page.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        var match = java.util.regex.Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"")
                .matcher(page.getContentAsString());
        assertThat(match.find()).isTrue();
        String masked = match.group(1);
        var login = mvc.perform(post("/api/signin").cookie(csrfCookie).header("X-XSRF-TOKEN", masked)
                        .contentType("application/json").content(
                                "{\"email\":\"%s\",\"password\":\"test-password\"}".formatted(account.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie access = login.getCookie("accessToken");
        Cookie refresh = login.getCookie("refreshToken");
        assertThat(login.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith("accessToken=") && value.contains("Path=/;") && value.contains("SameSite=Lax"))
                .anyMatch(value -> value.startsWith("refreshToken=") && value.contains("Path=/api;") && value.contains("SameSite=Lax"));
        mvc.perform(get("/productupload").cookie(access, csrfCookie)).andExpect(status().isOk());
        mvc.perform(get("/css/main.css").cookie(access, csrfCookie))
                .andExpect(status().isOk()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get("/api/session").cookie(refresh)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/refresh").cookie(refresh)).andExpect(status().isForbidden());
        var rotated = mvc.perform(post("/api/refresh").cookie(csrfCookie, refresh).header("X-XSRF-TOKEN", masked))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refreshToken");
        assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());
        mvc.perform(post("/api/refresh").cookie(csrfCookie, refresh).header("X-XSRF-TOKEN", masked))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/logout").cookie(rotated)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").cookie(csrfCookie, rotated).header("X-XSRF-TOKEN", masked))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/refresh").cookie(csrfCookie, rotated).header("X-XSRF-TOKEN", masked))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatHistoryReturnsBoundedPagesWithoutDuplicatingMessages() throws Exception {
        var buyer = account();
        var seller = account();
        var room = rooms.save(ChatRoomEntity.builder().buyerId(buyer).sellarId(seller).build());
        var inserted = messages.saveAll(java.util.stream.IntStream.rangeClosed(1, 52)
                .mapToObj(index -> ChatMessageEntity.builder().roomId(room).senderId(buyer)
                        .content("fixture " + index).createdAt(LocalDateTime.now()).build()).toList());
        String uri = "/chat/history/" + room.getChatRoomId();
        String cursor = inserted.get(2).getChatId().toString();
        mvc.perform(get(uri).with(user(new CustomUserDetail(buyer)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(50))
                .andExpect(jsonPath("$.messages[0].content").value("fixture 3"))
                .andExpect(jsonPath("$.nextBefore").value(Integer.parseInt(cursor)));
        mvc.perform(get(uri).param("before", cursor).with(user(new CustomUserDetail(buyer)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("fixture 1"))
                .andExpect(jsonPath("$.nextBefore").isEmpty());
    }

    private UsersEntity account() {
        var id = UUID.randomUUID().toString();
        return users.save(UsersEntity.builder().name(id).email(id + "@example.test")
                .password(encoder.encode("test-password")).role("ROLE_USER").created_at(LocalDateTime.now()).build());
    }
}
