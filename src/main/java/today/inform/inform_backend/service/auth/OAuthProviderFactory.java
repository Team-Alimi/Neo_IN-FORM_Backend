package today.inform.inform_backend.service.auth;

import org.springframework.stereotype.Component;
import today.inform.inform_backend.entity.SocialType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthProviderFactory {

    private final Map<SocialType, OAuthProvider> providers;

    public OAuthProviderFactory(List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(OAuthProvider::getSocialType, Function.identity()));
    }

    public OAuthProvider getProvider(SocialType socialType) {
        OAuthProvider provider = providers.get(socialType);
        if (provider == null) {
            throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다: " + socialType);
        }
        return provider;
    }
}
