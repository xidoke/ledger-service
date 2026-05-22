package com.xidoke.ledger.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private MockHttpServletResponse run(String inboundHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (inboundHeader != null) {
            request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, inboundHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private String echoed(MockHttpServletResponse response) {
        return response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    }

    @Test
    void validClientIdIsEchoed() throws Exception {
        assertThat(echoed(run("req-abc_123"))).isEqualTo("req-abc_123");
    }

    @Test
    void missingHeaderGeneratesUuid() throws Exception {
        String id = echoed(run(null));
        assertThat(id).isNotBlank();
        UUID.fromString(id); // a generated UUID — throws if not
    }

    @Test
    void crlfInjectionIsRejectedAndReplacedWithUuid() throws Exception {
        String id = echoed(run("abc\r\nSet-Cookie: evil=1"));
        assertThat(id).doesNotContain("\r", "\n", "Set-Cookie");
        UUID.fromString(id); // untrusted input was replaced by a fresh UUID
    }

    @Test
    void overlongHeaderIsRejected() throws Exception {
        String overlong = "a".repeat(65);
        assertThat(echoed(run(overlong))).isNotEqualTo(overlong);
    }
}
