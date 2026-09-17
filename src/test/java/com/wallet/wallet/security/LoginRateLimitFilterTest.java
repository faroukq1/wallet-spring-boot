package com.wallet.wallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRateLimitFilterTest {

    private final FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("198.51.100.7");
        return request;
    }

    @Test
    void allowsUpToLimitThenRejectsWith429() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(3);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), response, chain);
            assertEquals(200, response.getStatus(), "Attempt " + (i + 1) + " should be allowed");
        }

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), rejected, chain);
        assertEquals(429, rejected.getStatus(), "The 4th attempt should be rejected");
        assertEquals("application/json", rejected.getContentType());
    }

    @Test
    void nonLoginRequests_areNotRateLimited() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts/1");
            request.setRemoteAddr("198.51.100.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void loopbackClients_areExempt() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(1);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("127.0.0.1");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }
    }
}
