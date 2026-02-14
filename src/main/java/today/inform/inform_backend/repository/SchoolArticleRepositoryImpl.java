package today.inform.inform_backend.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform_backend.entity.QSchoolArticle;
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

import static today.inform.inform_backend.entity.QCategory.category;
import static today.inform.inform_backend.entity.QSchoolArticle.schoolArticle;

import today.inform.inform_backend.entity.VendorType;
import static today.inform.inform_backend.entity.QBookmark.bookmark;

@Repository
@RequiredArgsConstructor
public class SchoolArticleRepositoryImpl implements SchoolArticleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SchoolArticle> findHotArticles(LocalDate today, int limit) {
        return queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(bookmark).on(
                        bookmark.articleId.eq(schoolArticle.articleId)
                        .and(bookmark.articleType.eq(VendorType.SCHOOL))
                )
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        schoolArticle.dueDate.goe(today).or(schoolArticle.dueDate.isNull()) // 마감되지 않은 글
                )
                .groupBy(schoolArticle.articleId)
                .orderBy(
                        bookmark.count().desc(),
                        schoolArticle.createdAt.desc()
                )
                .limit(limit)
                .fetch();
    }

    @Override
    public Page<SchoolArticle> findAllByIdsWithFiltersAndSorting(
            List<Integer> articleIds,
            Integer categoryId,
            String keyword,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    ) {
        List<SchoolArticle> content = queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        schoolArticle.articleId.in(articleIds),
                        categoryEq(categoryId),
                        titleContains(keyword)
                )
                .orderBy(
                        createStatusOrder(today, upcomingLimit, endingSoonLimit),
                        schoolArticle.createdAt.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(schoolArticle.count())
                .from(schoolArticle)
                .where(
                        schoolArticle.articleId.in(articleIds),
                        categoryEq(categoryId),
                        titleContains(keyword)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<SchoolArticle> findAllWithFiltersAndSorting(
            Integer categoryId,
            String keyword,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    ) {
        // 1. 데이터 조회 쿼리
        List<SchoolArticle> content = queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin() // Fetch Join으로 N+1 방지
                .where(
                        categoryEq(categoryId),
                        titleContains(keyword)
                )
                .orderBy(
                        createStatusOrder(today, upcomingLimit, endingSoonLimit),
                        schoolArticle.createdAt.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2. 카운트 쿼리 (별도 실행으로 최적화)
        Long total = queryFactory
                .select(schoolArticle.count())
                .from(schoolArticle)
                .where(
                        categoryEq(categoryId),
                        titleContains(keyword)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<SchoolArticle> findCalendarArticles(List<String> categoryNames, Integer userId, LocalDate viewStart, LocalDate viewEnd) {
        return queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        calendarCategoryFilter(categoryNames, userId),
                        schoolArticle.startDate.loe(viewEnd),
                        schoolArticle.dueDate.goe(viewStart)
                )
                .fetch();
    }

    @Override
    public Page<SchoolArticle> findDailyCalendarArticles(LocalDate selectedDate, List<String> categoryNames, Integer userId, Pageable pageable) {
        List<SchoolArticle> content = queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        calendarCategoryFilter(categoryNames, userId),
                        schoolArticle.startDate.loe(selectedDate),
                        schoolArticle.dueDate.goe(selectedDate)
                )
                .orderBy(schoolArticle.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(schoolArticle.count())
                .from(schoolArticle)
                .where(
                        calendarCategoryFilter(categoryNames, userId),
                        schoolArticle.startDate.loe(selectedDate),
                        schoolArticle.dueDate.goe(selectedDate)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression calendarCategoryFilter(List<String> categoryNames, Integer userId) {
        // MY 필터가 포함되어 있으면 다른 카테고리는 무시
        if (categoryNames != null && categoryNames.contains("MY")) {
            if (userId == null) return schoolArticle.articleId.eq(-1); // 로그인 안 했으면 결과 없음
            return schoolArticle.articleId.in(
                    queryFactory.select(bookmark.articleId)
                            .from(bookmark)
                            .where(bookmark.user.userId.eq(userId),
                                   bookmark.articleType.eq(VendorType.SCHOOL))
            );
        }

        // 일반 카테고리 필터링
        if (categoryNames == null || categoryNames.isEmpty()) {
            return category.categoryName.eq("대회•공모전");
        }

        return category.categoryName.in(categoryNames);
    }

    // --- 조건절 메서드 (재사용 및 가독성 향상) ---

    private BooleanExpression categoryEq(Integer categoryId) {
        return categoryId != null ? schoolArticle.category.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression titleContains(String keyword) {
        return keyword != null ? schoolArticle.title.contains(keyword) : null;
    }

    // --- 정렬 로직 (기존 CASE WHEN을 자바 코드로 구현) ---
    private OrderSpecifier<Integer> createStatusOrder(LocalDate today, LocalDate upcomingLimit, LocalDate endingSoonLimit) {
        NumberExpression<Integer> statusPriority = new CaseBuilder()
                .when(schoolArticle.startDate.loe(today).and(schoolArticle.dueDate.gt(endingSoonLimit).or(schoolArticle.dueDate.isNull()))).then(1)   // OPEN (General)
                .when(schoolArticle.dueDate.goe(today).and(schoolArticle.dueDate.loe(endingSoonLimit))).then(2)  // ENDING_SOON
                .when(schoolArticle.startDate.gt(today).and(schoolArticle.startDate.loe(upcomingLimit))).then(3) // UPCOMING
                .when(schoolArticle.dueDate.lt(today)).then(5)  // CLOSED
                .otherwise(4); // NORMAL

        return statusPriority.asc();
    }
}
