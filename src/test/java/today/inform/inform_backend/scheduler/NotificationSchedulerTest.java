package today.inform.inform_backend.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.repository.NotificationRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("30일 이전의 알림 삭제 로직이 정상적으로 호출된다.")
    void cleanupOldNotifications_Success() {
        // given - 특별한 준비 사항 없음

        // when
        notificationScheduler.cleanupOldNotifications();

        // then
        // deleteByCreatedAtBefore 메서드가 LocalDateTime 인자와 함께 호출되었는지 확인
        verify(notificationRepository, times(1)).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("삭제 로직 중 예외가 발생해도 스케줄러가 중단되지 않고 로그를 남긴다.")
    void cleanupOldNotifications_HandlesException() {
        // given
        doThrow(new RuntimeException("DB Connection Error"))
                .when(notificationRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));

        // when & then
        // 예외가 밖으로 던져지지 않고 내부적으로 catch 되어야 함
        notificationScheduler.cleanupOldNotifications();
        
        verify(notificationRepository, times(1)).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }
}
