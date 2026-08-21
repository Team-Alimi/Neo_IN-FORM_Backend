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
 * <p><b>★ 부분 수정입니다</b>(명세 4.8). 보낸 필드만 반영합니다 —
 * 제목·본문·기간도 마찬가지로 {@code null} 이면 그대로 둡니다.
 * 그래서 {@code @NotBlank} 가 없습니다. 생성에서는 필수지만 그건 서비스가 따로 확인합니다
 * ({@code AdminArticleWriteService#requireCreatable}) — 한 DTO 를 두 동사가 공유하기 때문입니다.
 *
 * <p><b>기간은 지울 수 없습니다.</b> {@code null} 이 "그대로 두기" 라서
 * "비워 달라" 를 표현할 방법이 없습니다. 목록 세 가지는 빈 배열로 구분되지만 날짜는 그런 값이 없습니다.
 * 지금까지 요구된 적이 없어 그대로 두지만, 필요해지면 별도 플래그가 있어야 합니다.
 * {@code categoryIds}·{@code vendors}·{@code attachments} 는
 * <b>보내면 전체 교체, 안 보내면 유지</b>입니다 — 생략과 빈 배열은 <b>다른 뜻</b>입니다.
 * 생략은 "건드리지 마라", 빈 배열은 "전부 지워라" 입니다.
 * 그래서 제목 오타 하나 고치려고 저장한 요청이 분류를 통째로 지우는 일이 없습니다.
 *
 * @param categoryIds 복수입니다. 화면정의서의 "단일 선택" 은 잘못됐습니다 —
 *                    한 공지가 장학이면서 공모전일 수 있고, 크롤러 AI 분류도 복수를 전제합니다
 * @param vendors     출처. 학과 + 원본 URL 쌍입니다
 * @param attachments 첨부. <b>{@code POST /admin/files} 로 먼저 올린 뒤</b> 그 {@code file_url} 을
 *                    여기로 넘겨야 공지에 붙습니다. 업로드만으로는 아무 데도 연결되지 않습니다
 */
public record SaveArticleRequest(
        @Positive(message = "게시글 번호는 양수여야 합니다.")
        @Max(value = MAX_MANUAL_ID, message = "게시글 번호가 너무 큽니다.")
        Long articleId,

        SourceType sourceType,

        ArticleStatus status,

        @Size(max = 500, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        String content,

        LocalDate startsOn,

        LocalDate endsOn,

        List<@NotNull(message = "카테고리 번호가 비어 있습니다.") Long> categoryIds,

        List<@NotNull(message = "출처 항목이 비어 있습니다.") @Valid VendorLink> vendors,

        @Size(max = 20, message = "첨부는 20개를 넘을 수 없습니다.")
        List<@NotNull(message = "첨부 항목이 비어 있습니다.") @Valid AttachmentLink> attachments) {

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
    /**
     * 첨부 한 건.
     *
     * @param id        기존 첨부 행의 번호({@code attachments.id}).
     *                  <b>새로 붙이는 파일은 비웁니다.</b> {@code VendorLink} 와 같은 이유입니다 —
     *                  상세 응답을 그대로 되돌려 보냈을 때 "기존" 과 "신규" 를 구분하기 위한 표식입니다
     * @param fileUrl   {@code POST /admin/files} 가 돌려준 주소.
     *                  우리 스토리지 주소면 S3 첨부로, 아니면 원본 사이트 링크(EXTERNAL)로 저장됩니다
     * @param sizeBytes 0 보다 커야 합니다 — {@code ck_attachments_size} 가 강제합니다
     */
    public record AttachmentLink(
            Long id,

            @NotBlank(message = "파일 주소가 비어 있습니다.")
            @Size(max = 1000, message = "파일 주소가 너무 깁니다.")
            String fileUrl,

            @Size(max = 255, message = "파일 이름이 너무 깁니다.")
            String originalName,

            @Size(max = 100, message = "파일 형식이 너무 깁니다.")
            String contentType,

            @Positive(message = "파일 크기는 양수여야 합니다.")
            Long sizeBytes) {
    }

    public record VendorLink(
            Long id,

            @NotNull(message = "제공처를 선택해 주세요.")
            Long vendorId,

            @Size(max = 1000, message = "URL 이 너무 깁니다.")
            String sourceUrl) {
    }
}
