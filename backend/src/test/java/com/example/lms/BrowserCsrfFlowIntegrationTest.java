package com.example.lms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BrowserCsrfFlowIntegrationTest {
  @Autowired MockMvc mvc;

  @Value("${lms.admin.password}")
  String adminPassword;

  @Test
  void browserCookieAndHeaderCsrfFlowAuthenticatesAndLogsOut() throws Exception {
    MvcResult csrf =
        mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();
    Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
    Assertions.assertNotNull(csrfCookie, "The CSRF endpoint must issue the readable SPA cookie");

    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"admin\",\"password\":\""
                            + adminPassword.replace("\"", "\\\"")
                            + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    Assertions.assertNotNull(session, "Successful session login must create a session");

    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"));

    Cookie refreshedCsrfCookie = login.getResponse().getCookie("XSRF-TOKEN");
    Cookie logoutCsrfCookie = refreshedCsrfCookie != null ? refreshedCsrfCookie : csrfCookie;
    mvc.perform(
            post("/api/auth/logout")
                .session(session)
                .cookie(logoutCsrfCookie)
                .header("X-XSRF-TOKEN", logoutCsrfCookie.getValue()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
  }
}
