package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum OauthType {
    PRE_STEP("pre-step"),
    INTEGRATED("integrated");

    private static final Map<String, OauthType> container = new HashMap<>();

    static {
        for (OauthType type : values()) {
            container.put(type.getValue(), type);
        }
    }

    private String value;

    OauthType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Optional<OauthType> getByValue(String name) {
        return Optional.ofNullable(container.get(name));
    }
}
