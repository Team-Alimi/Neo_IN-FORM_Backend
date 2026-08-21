package today.inform.inform.admin.article.dto.request;

import java.time.LocalDate;
import java.util.List;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * ADM-03 관리자 목록 필터 (명세 4.8 {@code GET /admin/articles}).
 *
 * <p>사용자 목록({@code ArticleSearchCondition})과 <b>일부러 분리했습니다.</b>
 * 노출 기준이 정반대입니다 — 사용자 목록은 배포된 것만 보고,
 * 관리자 목록은 미배포를 골라 보는 것이 목적입니다.
 * 한 객체로 합치면 "관리자면 이 조건 빼고" 분기가 생기고, 그 분기 하나를 빠뜨리면
 * 미배포 공지가 사용자에게 새어 나갑니다.
 *
 * @param statuses    복수 지정 가능. <b>생략하면 휴지통을 뺀 전체</b>입니다(명세).
 *                    휴지통만 따로 빼는 이유는 그게 "지운 것" 이라 기본 화면에 섞이면 안 되기 때문입니다.
 *                    {@code status=DRAFT} 로 부르면 그대로 CLB-04(임시저장 목록)가 됩니다
 * @param needsReview 원본 수정으로 재검수 대기인 것만. 상태와 <b>독립된 축</b>이라
 *                    상태 필터와 함께 걸 수 있습니다
 * @param needsCheck  ADM-12 "확인 필요" — 중복 의심이거나 정보가 빠진 것.
 *                    명세 표에는 없지만 대시보드 카드에서 넘어오는 목록이라 유지합니다
 */
public record AdminArticleSearchCondition(
        List<ArticleStatus> statuses,
        SourceType sourceType,
        Long articleId,
        String keyword,
        Long vendorId,
        Long categoryId,
        LocalDate startsFrom,
        LocalDate endsTo,
        Boolean needsReview,
        Boolean needsCheck) {

    public boolean hasStatusFilter() {
        return statuses != null && !statuses.isEmpty();
    }

    public boolean isNeedsReview() {
        return Boolean.TRUE.equals(needsReview);
    }

    public boolean isNeedsCheck() {
        return Boolean.TRUE.equals(needsCheck);
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public List<String> statusNames() {
        return statuses.stream().map(Enum::name).toList();
    }
}
