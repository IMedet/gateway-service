package kz.qonaqzhai.gatewayservice.security;

import kz.qonaqzhai.gatewayservice.config.InternalGatewaySignatureProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class InternalRequestSigner {

    private final InternalGatewaySignatureProperties props;

    public InternalRequestSigner(InternalGatewaySignatureProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public String timestampHeaderName() {
        return props.getTimestampHeader();
    }

    public String signatureHeaderName() {
        return props.getSignatureHeader();
    }

    public String sign(String method, String fullPath, String timestamp) {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Internal gateway secret is not configured");
        }
        String canonical = method + "\n" + fullPath + "\n" + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign internal request", e);
        }
    }
}
