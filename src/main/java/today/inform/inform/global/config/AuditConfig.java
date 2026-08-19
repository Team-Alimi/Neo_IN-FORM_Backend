package today.inform.inform.global.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import today.inform.inform.global.security.CurrentUserProvider;

/**
 * Spring Boot 가 자동 구성하는 JpaTransactionManager 를
 * 감사 행위자를 주입하는 구현으로 교체한다.
 *
 * <p>자동 구성은 {@code @ConditionalOnMissingBean} 이므로 이 빈이 우선한다.
 */
@Configuration
public class AuditConfig {

    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            CurrentUserProvider currentUserProvider) {
        return new AuditAwareTransactionManager(entityManagerFactory, currentUserProvider);
    }
}
