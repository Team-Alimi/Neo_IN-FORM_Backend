package today.inform.inform.admin.article.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import today.inform.inform.article.entity.ArticleStatus;
import today.inform.inform.article.entity.SourceType;

/**
 * ADM-06 생성 / ADM-05 수정 공통.
 *
 * @param articleId  <b>생성 시에만</b> 의미가 있습니다. 관리자가 게시글 번호를 직접 지정합니다.
 *                   비워 두면 시퀀스가 발급합니다
 * @param sourceType <b>생성 시에만</b> 지정합니다. 수정에서는 무시됩니다 —
 *                   {@code trg_articles_10_immutable} 이 변경을 IN001 로 막고,
 *                   이미 붙은 출처·첨부의 교차 검증 전제가 이 값이기 때문입니다
 * @param status     생성 시 초기 상태. 비워 두면 SCHOOL 은 검수 대기, CLUB 은 임시저장입니다.
 *                   <b>수정에서는 무시됩니다</b> — 상태 변경은 전이 규칙을 타야 하므로
 *                   {@code PATCH /admin/articles/status} 로만 합니다
 * <p><b>★ 이 요청은 "부분 수정" 이 아니라 "이 공지의 최종 모습" 입니다.</b>
 * 화면이 폼 전체를 보내는 것을 전제로 하며, {@code categoryIds} 와 {@code vendors} 는
 * <b>생략할 수 없습니다</b>. 생략을 허용하면 제목 오타 하나 고치려고 저장한 요청이
 * 분류와 출처를 통째로 지우고도 200 을 돌려주게 됩니다.
 * 비우려면 빈 배열을 <b>명시적으로</b> 보내야 합니다.
 *
 * @param categoryIds 복수입니다. 화면정의서의 "단일 선택" 은 잘못됐습니다 —
 *                    한 공지가 장학이면서 공모전일 수 있고, 크롤러 AI 분류도 복수를 전제합니다
 * @param vendors     출처. 학과 + 원본 URL 쌍입니다
 */
public record SaveArticleRequest(
        @Positive(message = "게시글 번호는 양수여야 합니다.")
        @Max(value = MAX_MANUAL_ID, message = "게시글 번호가 너무 큽니다.")
        Long articleId,

        SourceType sourceType,

        ArticleStatus status,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 500, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "본문을 입력해 주세요.")
        String content,

        LocalDate startsOn,

        LocalDate endsOn,

        @NotNull(message = "카테고리 목록은 생략할 수 없습니다. 없으면 빈 배열을 보내세요.")
        List<@NotNull(message = "카테고리 번호가 비어 있습니다.") Long> categoryIds,

        @NotNull(message = "출처 목록은 생략할 수 없습니다. 없으면 빈 배열을 보내세요.")
        List<@NotNull(message = "출처 항목이 비어 있습니다.") @Valid VendorLink> vendors) {

    /**
     * 수동 지정 가능한 최대 번호.
     *
     * <p>상한이 없으면 관리자가 실수로 큰 값을 넣었을 때 {@code setval} 이 시퀀스를 그만큼 밀어
     * <b>남은 번호를 통째로 태워 버립니다.</b> 시퀀스는 트랜잭션을 따르지 않아 되돌릴 수도 없습니다.
     * 1억이면 이 서비스의 수명 동안 충분합니다.
     */
    public static final long MAX_MANUAL_ID = 100_000_000L;

    /**
     * @param id        기존 출처 행의 번호({@code article_vendors.id}).
     *                  <b>새로 추가하는 출처는 비웁니다.</b>
     *                  이게 없으면 상세 응답을 그대로 되돌려 보냈을 때 서버가
     *                  "기존 행" 과 "새 행" 을 구분할 수 없어, 저장할 때마다 출처가 한 줄씩 늘어납니다
     * @param sourceUrl 원본 게시판 글 주소.
     *                  <b>관리자가 손으로 추가하는 출처는 없어도 됩니다</b> —
     *                  크롤러 수집분에만 트리거가 IN003 으로 요구합니다
     */
    public record VendorLink(
            Long id,

            @NotNull(message = "제공처를 선택해 주세요.")
            Long vendorId,

            @Size(max = 1000, message = "URL 이 너무 깁니다.")
            String sourceUrl) {
    }
}
