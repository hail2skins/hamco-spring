package com.hamco.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/** Fake transport verifies Mailjet's request without allowing a network connection. */
class MailjetMailTest {
    @Test void sendsEscapedPayloadAndRejectsNonSuccess() throws Exception {
        HttpClient transport = mock(HttpClient.class);
        HttpResponse<?> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) ok);
        var mail = new MailjetMail("api-key", "api-secret", "from@example.test", "HAMCO", new ObjectMapper(), transport);
        mail.send("to@example.test", "Verify account", "https://example.test/verify?token=a\"b");
        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        var request = captured.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.mailjet.com/v3.1/send");
        assertThat(request.headers().firstValue("Authorization")).hasValue("Basic YXBpLWtleTphcGktc2VjcmV0");
        String body = bodyOf(request);
        assertThat(body).contains("to@example.test", "Verify account", "\\\"b");
        assertThat(body).contains("from@example.test");

        HttpResponse<?> failed = mock(HttpResponse.class);
        when(failed.statusCode()).thenReturn(503);
        when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) failed);
        assertThatThrownBy(() -> mail.send("to@example.test", "Reset", "https://example.test/reset"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("503");
    }

    private static String bodyOf(HttpRequest request) {
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] next = new byte[buffer.remaining()]; buffer.get(next); bytes.writeBytes(next);
            }
            @Override public void onError(Throwable error) { throw new AssertionError(error); }
            @Override public void onComplete() {}
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
