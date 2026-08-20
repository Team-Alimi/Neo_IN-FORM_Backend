package today.inform.inform.admin.article.dto.request;

import java.time.LocalDate;
import today.inform.inform.article.entity.ArticleStatus;

/**
 * ADM-03 / ADM-12 관리자 목록 필터.
 *
 * <p>사용자 목록({@code ArticleSearchCondition})과 <b>일부러 분리했습니다.</b>
 * 노출 기준이 정반대입니다 — 사용자 목록은 배포된 것만 보고,
 * 관리자 목록은 <b>상태가 필수 조건</b>이라 미배포를 골라 보는 것이 목적입니다.
 * 한 객체로 합치면 "관리자면 이 조건 빼고" 분기가 생기고, 그 분기 하나를 빠뜨리면
 * 미배포 공지가 사용자에게 새어 나갑니다.
 *
 * @param status      <b>필수</b>입니다. 기본값은 검수 대기
 * @param needsCheck  ADM-12. 중복 의심이거나 정보가 빠진 공지만
 */
public record AdminArticleSearchCondition(
        ArticleStatus status,
        Long articleId,
        String title,
        Long vendorId,
        Long categoryId,
        LocalDate startsFrom,
        LocalDate endsTo,
        Boolean needsCheck) {

    public AdminArticleSearchCondition {
        if (status == null) {
            status = ArticleStatus.PENDING_REVIEW;
        }
    }

    public boolean isNeedsCheck() {
        return Boolean.TRUE.equals(needsCheck);
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
