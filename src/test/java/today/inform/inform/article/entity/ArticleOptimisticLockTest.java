package today.inform.inform.article.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.article.repository.ArticleRepository;
import today.inform.inform.support.IntegrationTest;

/**
 * 관리자 두 명이 같은 공지를 동시에 고쳤을 때 뒤늦은 저장이 <b>거부되는지</b> 확인합니다.
 *
 * <p>낙관적 잠금은 "붙였다" 로 끝나지 않습니다. 매핑 조합에 따라 조용히 무력화될 수 있어서
 * 실제로 예외가 올라오는지 확인해 두지 않으면 붙인 줄로만 알고 지나갑니다.
 */
@Transactional
class ArticleOptimisticLockTest extends IntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("낡은 버전으로 저장하면 낙관적 잠금 예외가 난다")
    void staleUpdateRaisesOptimisticLockFailure() {
        Article article = articleRepository.save(
                Article.createSchoolArticle("제목", "내용", null, null, null));
        em.flush();

        // 다른 관리자가 먼저 저장한 상황을 흉내냅니다.
        em.createNativeQuery("UPDATE articles SET version = version + 1 WHERE id = :id")
                .setParameter("id", article.getId())
                .executeUpdate();

        article.edit("나중에 저장한 제목", "내용", null, null);

        // EntityManager 를 직접 호출하면 JPA 원본 예외가 그대로 올라옵니다.
        // 서비스의 @Transactional 커밋 경로에서는 Spring 이
        // ObjectOptimisticLockingFailureException 으로 감싸고,
        // GlobalExceptionHandler 가 그걸 409 CONCURRENT_MODIFICATION 으로 내려보냅니다.
        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("expected row count 1 but was 0");
    }
}
