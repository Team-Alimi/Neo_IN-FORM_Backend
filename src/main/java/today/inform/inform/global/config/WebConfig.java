package today.inform.inform.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

/**
 * 모든 REST 컨트롤러에 {@code /api/v1} 접두사를 붙인다.
 *
 * <p>컨트롤러마다 경로에 직접 쓰면 빠뜨리는 곳이 생기고, {@code server.servlet.context-path}
 * 로 설정하면 actuator 헬스체크까지 접두사가 붙어 인프라 설정이 꼬인다.
 *
 * <p>API 버전이 {@code v1} 인 이유: 프로젝트는 v2 지만 <b>공개 API 로서는 첫 버전</b>이고
 * v1 서비스는 중단 상태라 하위 호환 대상이 없다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
