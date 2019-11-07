package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;


@Data
@Component
@RequestScope
public class OauthDataHolder {
    @Nullable
    private OauthType oauthType;
    @Nullable
    private String token;

    public void setOauthTypeAndToken(@Nullable OauthType oauthType,
                                     @Nullable String token) {
        this.oauthType = oauthType;
        this.token = token;
    }
}
