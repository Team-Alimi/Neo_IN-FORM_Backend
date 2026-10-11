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
 *   <li>{@code ArticleViewCounter#flush} — Redis 에 모인 조회수 델타를 DB 에 반영 (1분)</li>
 *   <li>{@code NotificationScheduler#createDeadlineNotifications} — 마감 D-1 알림 (09:00 KST)</li>
 *   <li>{@code NotificationScheduler#deleteOldNotifications} — 30일 경과 알림 정리 (04:00 KST)</li>
 * </ul>
 *
 * <p>★ 인스턴스를 여러 대로 늘리면 <b>조회수 반영에만</b> ShedLock 이 필요합니다.
 * 스키마에 {@code shedlock} 테이블이 이미 있습니다.
 *
 * <p>알림 쪽 두 작업은 멱등해서 중복 실행이 안전합니다 — 생성은 유니크 키
 * ({@code uk_notifications_dedup})가 막고, 정리는 같은 조건의 DELETE 입니다.
 * 반면 조회수는 <b>증분</b>이라 두 인스턴스가 같은 델타를 각각 더합니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
