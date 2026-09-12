package com.mcpgateway.client;

import com.google.protobuf.ByteString;
import com.mcpcipher.grpc.cipher.v1.CipherServiceGrpc;
import com.mcpcipher.grpc.cipher.v1.DecryptRequest;
import com.mcpcipher.grpc.cipher.v1.EncryptRequest;
import com.mcpcipher.grpc.cipher.v1.EncryptResponse;
import com.mcpgateway.property.CipherProperties;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Encrypts and decrypts through mcp-cipher.
 *
 * <p>This service never holds a key. It sends a value and gets back ciphertext plus the id
 * of the key that sealed it, and it stores both; opening the value later means asking
 * again. The database and this configuration are therefore both useless on their own.
 *
 * <p>Plaintext appears in exactly two places: the argument to {@link #encrypt} and the
 * return of {@link #decrypt}. It is never logged, never put in an exception message, and
 * never returned to an HTTP caller.
 */
@Slf4j
@Component
public class CipherClient {

    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-cipher-token", Metadata.ASCII_STRING_MARSHALLER);

    private final CipherProperties properties;
    private final ManagedChannel channel;
    private final CipherServiceGrpc.CipherServiceBlockingStub stub;

    public CipherClient(CipherProperties properties) {
        this.properties = properties;

        // Plaintext transport. Defensible only while both processes are on one machine,
        // which is also why mcp-cipher listens on loopback by default. Crossing a network
        // means TLS here and there.
        this.channel = NettyChannelBuilder.forTarget(properties.getAddress())
                .usePlaintext()
                .build();

        CipherServiceGrpc.CipherServiceBlockingStub base =
                CipherServiceGrpc.newBlockingStub(channel);

        this.stub = properties.hasToken()
                ? base.withInterceptors(tokenInterceptor(properties.getToken()))
                : base;
    }

    /**
     * Seals a value, returning the ciphertext and the key that sealed it.
     *
     * <p>The context is the secret's kind, and it is bound into the ciphertext: a value
     * sealed as an SSH key cannot be opened as a model API key, even by a caller holding
     * both. It must be the same string every consumer reports, or the value simply will not
     * open — which is what happened when this method used one context and the response
     * reported another.
     */
    public Sealed encrypt(String plaintext, String context) {
        try {
            EncryptResponse response = call().encrypt(EncryptRequest.newBuilder()
                    .setPlaintext(ByteString.copyFrom(plaintext, StandardCharsets.UTF_8))
                    .setContext(context)
                    .build());

            return new Sealed(response.getCiphertext().toByteArray(), response.getKeyId());
        } catch (StatusRuntimeException ex) {
            throw translate(ex, "encrypt");
        }
    }

    /**
     * Opens a value that was sealed with {@code keyId}.
     *
     * <p>The key id travels with the ciphertext rather than being assumed, which is what
     * lets mcp-cipher rotate its active key without this service reading stale values
     * incorrectly — or at all.
     */
    public String decrypt(byte[] ciphertext, String keyId, String context) {
        try {
            return call().decrypt(DecryptRequest.newBuilder()
                            .setCiphertext(ByteString.copyFrom(ciphertext))
                            .setContext(context)
                            .setKeyId(keyId == null ? "" : keyId)
                            .build())
                    .getPlaintext()
                    .toStringUtf8();
        } catch (StatusRuntimeException ex) {
            throw translate(ex, "decrypt");
        }
    }

    /** Whether the cipher answers at all, for {@code /actuator} style reporting. */
    public boolean isReachable() {
        try {
            call().keys(com.mcpcipher.grpc.cipher.v1.KeysRequest.getDefaultInstance());
            return true;
        } catch (StatusRuntimeException ex) {
            return false;
        }
    }

    private CipherServiceGrpc.CipherServiceBlockingStub call() {
        return stub.withDeadlineAfter(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Turns a gRPC status into something a caller can act on.
     *
     * <p>{@code UNAVAILABLE} and a deadline are a dependency being down: a {@code 503}, and
     * worth retrying. Everything else means the cipher understood and refused, which no
     * amount of retrying changes.
     */
    private RuntimeException translate(StatusRuntimeException ex, String operation) {
        Status.Code code = ex.getStatus().getCode();

        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
            log.error("mcp-cipher is unreachable during {}", operation, ex);
            return new CipherUnavailableException(
                    "The encryption service is unavailable, please try again shortly");
        }

        log.error("mcp-cipher refused a {} request: {}", operation, code);
        return new CipherUnavailableException(
                "The encryption service refused the request (" + code + ")");
    }

    private static ClientInterceptor tokenInterceptor(String token) {
        return new ClientInterceptor() {
            @Override
            public <Q, S> ClientCall<Q, S> interceptCall(
                    MethodDescriptor<Q, S> method, CallOptions options, Channel next) {

                return new ForwardingClientCall.SimpleForwardingClientCall<>(
                        next.newCall(method, options)) {
                    @Override
                    public void start(Listener<S> listener, Metadata headers) {
                        headers.put(TOKEN_HEADER, token);
                        super.start(listener, headers);
                    }
                };
            }
        };
    }

    @PreDestroy
    void close() {
        channel.shutdown();
        try {
            // Long enough for a call in flight to finish; short enough that a stuck
            // connection does not hold shutdown open.
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    /** Ciphertext and the key that produced it. Both are stored; neither is a secret. */
    public record Sealed(byte[] ciphertext, String keyId) {
    }
}
