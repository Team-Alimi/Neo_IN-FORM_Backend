package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import today.inform.inform_backend.dto.ClubArticleListResponse;
import today.inform.inform_backend.dto.ClubArticleResponse;
import today.inform.inform_backend.dto.ClubArticleDetailResponse;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.entity.ClubArticle;
import today.inform.inform_backend.entity.User;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.AttachmentRepository;
import today.inform.inform_backend.repository.BookmarkRepository;
import today.inform.inform_backend.repository.ClubArticleRepository;
import today.inform.inform_backend.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClubArticleServiceTest {

    @InjectMocks
    private ClubArticleService clubArticleService;

    @Mock
    private ClubArticleRepository clubArticleRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Test
    @DisplayName("동아리 공지사항 목록을 페이징하여 조회한다.")
    void getClubArticles_Success() {
        // given
        Integer userId = 1;
        Vendor vendor = Vendor.builder()
                .vendorId(1)
                .vendorName("GDGOC")
                .vendorInitial("cl_gdgoc")
                .vendorType(VendorType.CLUB)
                .build();

        ClubArticle article = ClubArticle.builder()
                .articleId(1)
                .title("GOAT 행사")
                .vendor(vendor)
                .startDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(7))
                .build();

        Page<ClubArticle> page = new PageImpl<>(List.of(article), PageRequest.of(0, 4), 1);
        given(clubArticleRepository.findAllWithFilters(any(), any())).willReturn(page);

        Attachment attachment = Attachment.builder()
                .id(1)
                .articleId(1)
                .articleType(VendorType.CLUB)
                .attachmentUrl("https://image.com/1.jpg")
                .build();
        given(attachmentRepository.findAllByArticleIdInAndArticleType(anyList(), eq(VendorType.CLUB)))
                .willReturn(List.of(attachment));

        // 유저 및 북마크 Mock
        User user = User.builder().userId(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(bookmarkRepository.findAllByUserAndArticleTypeAndArticleIdIn(any(), any(), any()))
                .willReturn(List.of());

        // when
        ClubArticleListResponse response = clubArticleService.getClubArticles(1, 4, null, userId);

        // then
        assertThat(response.getClub_articles()).hasSize(1);
        assertThat(response.getClub_articles().get(0).getIs_bookmarked()).isFalse();
        assertThat(response.getClub_articles().get(0).getAttachment_url()).isEqualTo("https://image.com/1.jpg");
    }

    @Test
    @DisplayName("동아리 공지사항 상세 내용을 조회한다.")
    void getClubArticleDetail_Success() {
        // given
        Vendor vendor = Vendor.builder()
                .vendorId(1)
                .vendorName("GDGOC")
                .vendorInitial("cl_gdgoc")
                .build();

        ClubArticle article = ClubArticle.builder()
                .articleId(1)
                .title("상세 제목")
                .content("상세 내용")
                .vendor(vendor)
                .build();

        given(clubArticleRepository.findByIdWithVendor(1)).willReturn(Optional.of(article));

        Attachment attachment = Attachment.builder()
                .id(10)
                .attachmentUrl("https://image.com/detail.jpg")
                .build();
        given(attachmentRepository.findAllByArticleIdAndArticleType(1, VendorType.CLUB))
                .willReturn(List.of(attachment));

        // when
        ClubArticleDetailResponse response = clubArticleService.getClubArticleDetail(1, null);

        // then
        assertThat(response.getTitle()).isEqualTo("상세 제목");
        assertThat(response.getContent()).isEqualTo("상세 내용");
        assertThat(response.getAttachments()).hasSize(1);
        assertThat(response.getAttachments().get(0).getFile_url()).isEqualTo("https://image.com/detail.jpg");
        assertThat(response.getVendors().getVendor_name()).isEqualTo("GDGOC");
    }
}