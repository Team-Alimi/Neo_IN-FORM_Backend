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
 *   <li>{@code NotificationEmailSender#sendPendingEmails} — NTF-05 알림 메일 발송 (5분)</li>
 * </ul>
 *
 * <h2>★ 중복 실행 안전성이 작업마다 다릅니다</h2>
 * <table border="1">
 *   <caption>인스턴스를 여러 대로 늘렸을 때</caption>
 *   <tr><th>작업</th><th>중복 실행</th><th>막는 장치</th></tr>
 *   <tr><td>알림 생성</td><td>안전</td>
 *       <td>유니크 키 {@code uk_notifications_dedup} 위의 {@code ON CONFLICT DO NOTHING}</td></tr>
 *   <tr><td>알림 정리</td><td>안전</td><td>같은 조건의 벌크 삭제라 결과가 같음</td></tr>
 *   <tr><td>조회수 반영</td><td><b>위험</b></td>
 *       <td>없음 — <b>증분</b>이라 두 인스턴스가 같은 델타를 각각 더합니다.
 *           ShedLock 이 필요하고, 스키마에 {@code shedlock} 테이블이 이미 있습니다</td></tr>
 *   <tr><td>메일 발송</td><td><b>위험</b></td>
 *       <td>자체 Redis 락({@code inform:notification-email:lock}).
 *           메일은 외부 부작용이라 되돌릴 수 없고, {@code email_sent_at} 은
 *           <b>보낸 뒤에</b> 찍히므로 그 사이의 창을 막지 못합니다.
 *           그래서 실행 전체를 락으로 감쌉니다</td></tr>
 * </table>
 *
 * <p>즉 "알림 쪽은 전부 멱등하다" 가 아닙니다 — <b>생성·정리는 멱등하고, 메일 발송은
 * 아닙니다.</b> 메일 발송에 락이 없으면 사용자에게 같은 메일이 두 통 갑니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
