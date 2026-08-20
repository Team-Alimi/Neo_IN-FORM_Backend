package today.inform.inform.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화.
 *
 * <p>메인 클래스가 아니라 별도 설정으로 둡니다. 테스트에서 배치를 돌리고 싶지 않을 때
 * 이 클래스만 제외하면 되기 때문입니다 —
 * {@code @SpringBootApplication} 에 붙이면 컨텍스트를 띄우는 모든 테스트에서 배치가 돕니다.
 *
 * <p>현재 등록된 작업
 * <ul>
 *   <li>{@code ArticleViewCounter#flush} — Redis 에 모인 조회수 델타를 DB 에 반영</li>
 * </ul>
 *
 * <p>★ 인스턴스를 여러 대로 늘리면 ShedLock 이 필요합니다.
 * 스키마에 {@code shedlock} 테이블이 이미 있습니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
