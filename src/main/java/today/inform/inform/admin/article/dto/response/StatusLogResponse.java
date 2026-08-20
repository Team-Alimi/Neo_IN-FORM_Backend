package today.inform.inform.admin.article.dto.response;

import java.time.OffsetDateTime;
import today.inform.inform.article.entity.ArticleStatus;

/**
 * ADM-11 상태 변경 이력.
 *
 * @param fromStatus  최초 생성이면 {@code null} 입니다
 * @param changedBy   <b>{@code null} 이면 크롤러·스케줄러가 한 변경</b>입니다.
 *                    관리자 화면에서 "시스템" 으로 표시합니다
 * @param changedByName 관리자 이름. 시스템 변경이면 {@code null}
 */
public record StatusLogResponse(
        Long id,
        ArticleStatus fromStatus,
        ArticleStatus toStatus,
        Long changedBy,
        String changedByName,
        String memo,
        OffsetDateTime createdAt) {
}
