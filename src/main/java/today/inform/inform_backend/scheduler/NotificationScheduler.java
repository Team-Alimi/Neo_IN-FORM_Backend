package today.inform.inform_backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.entity.Bookmark;
import today.inform.inform_backend.entity.SchoolArticle;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.SchoolArticleRepository;
import today.inform.inform_backend.service.NotificationService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final SchoolArticleRepository schoolArticleRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationService notificationService;

    /**
     * 매일 오전 9시에 마감 1일 전 공지사항 알림 생성
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendDeadlineReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("Starting deadline reminder scheduler for date: {}", tomorrow);

        List<SchoolArticle> articlesDueTomorrow = schoolArticleRepository.findAllByDueDate(tomorrow);
        List<today.inform.inform_backend.entity.Notification> notificationsToCreate = new ArrayList<>();
        
        for (SchoolArticle article : articlesDueTomorrow) {
            List<Bookmark> bookmarks = bookmarkRepository.findAllByArticleTypeAndArticleId(VendorType.SCHOOL, article.getArticleId());
            
            for (Bookmark bookmark : bookmarks) {
                String title = "⏰ 마감 임박 알림";
                String message = String.format("[%s] 공지사항 마감까지 1일 남았습니다. 잊지 말고 확인해 보세요!", article.getTitle());
                
                notificationsToCreate.add(today.inform.inform_backend.entity.Notification.builder()
                        .user(bookmark.getUser())
                        .title(title)
                        .message(message)
                        .articleType(VendorType.SCHOOL)
                        .articleId(article.getArticleId())
                        .isRead(false)
                        .build());
            }
        }

        if (!notificationsToCreate.isEmpty()) {
            notificationService.createNotificationsBulk(notificationsToCreate);
        }
        
        log.info("Successfully processed reminders for {} articles. Total notifications created: {}", 
                articlesDueTomorrow.size(), notificationsToCreate.size());
    }
}
