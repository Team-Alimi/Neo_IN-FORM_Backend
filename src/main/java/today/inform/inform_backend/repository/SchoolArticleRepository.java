package today.inform.inform_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

public interface SchoolArticleRepository extends JpaRepository<SchoolArticle, Integer>, SchoolArticleRepositoryCustom {
    List<SchoolArticle> findAllByDueDate(LocalDate dueDate);

    // 사용자용 - isPublished 체크
    java.util.Optional<SchoolArticle> findByArticleIdAndIsPublishedTrue(Integer articleId);
    boolean existsByArticleIdAndIsPublishedTrue(Integer articleId);

    // 미배포 게시글 조회 (관리자용)
    Page<SchoolArticle> findAllByIsPublishedFalseAndAdminStatusOrderByCreatedAtAsc(AdminStatus adminStatus, Pageable pageable);

    Page<SchoolArticle> findAllByIsPublishedFalseAndAdminStatusInOrderByCreatedAtAsc(List<AdminStatus> adminStatuses, Pageable pageable);

    long countByIsPublishedFalseAndAdminStatus(AdminStatus adminStatus);

    @Modifying
    @Query(value = "INSERT INTO school_articles (article_id, title, content, category_id, start_date, due_date, is_published, admin_status, created_at, updated_at) " +
                   "VALUES (:articleId, :title, :content, :categoryId, :startDate, :dueDate, :isPublished, :adminStatus, NOW(), NOW())", nativeQuery = true)
    void insertArticleDirectly(@Param("articleId") Integer articleId,
                               @Param("title") String title,
                               @Param("content") String content,
                               @Param("categoryId") Integer categoryId,
                               @Param("startDate") LocalDate startDate,
                               @Param("dueDate") LocalDate dueDate,
                               @Param("isPublished") boolean isPublished,
                               @Param("adminStatus") String adminStatus);
}