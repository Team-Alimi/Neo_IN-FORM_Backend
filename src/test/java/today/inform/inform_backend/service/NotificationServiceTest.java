package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.dto.NotificationResponse;
import today.inform.inform_backend.entity.Notification;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.NotificationRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자의 안 읽은 알림 개수를 정확히 반환한다")
    void getUnreadCount_Success() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserAndIsReadFalse(user)).thenReturn(5L);

        // when
        long unreadCount = notificationService.getUnreadCount(userId);

        // then
        assertThat(unreadCount).isEqualTo(5L);
        verify(notificationRepository, times(1)).countByUserAndIsReadFalse(user);
    }

    @Test
    @DisplayName("알림을 읽음 처리하면 isRead 상태가 true로 변경된다")
    void markAsRead_Success() {
        // given
        Integer userId = 1;
        Integer notificationId = 100;
        User user = User.builder().userId(userId).build();
        Notification notification = Notification.builder()
                .notificationId(notificationId)
                .user(user)
                .isRead(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(userId, notificationId);

        // then
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("알림 목록을 최신순으로 조회한다")
    void getNotifications_Success() {
        // given
        Integer userId = 1;
        User user = User.builder().userId(userId).build();
        Notification notification = Notification.builder()
                .notificationId(1)
                .user(user)
                .title("제목")
                .message("내용")
                .articleType(VendorType.SCHOOL)
                .articleId(10)
                .isRead(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.findAllByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(notification));

        // when
        List<NotificationResponse> responses = notificationService.getNotifications(userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTitle()).isEqualTo("제목");
        verify(notificationRepository, times(1)).findAllByUserOrderByCreatedAtDesc(user);
    }
}
