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
            List<Integer> categoryIds,
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
                        categoryIn(categoryIds),
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
                        categoryIn(categoryIds),
                        titleContains(keyword)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<SchoolArticle> findAllWithFiltersAndSorting(
            List<Integer> categoryIds,
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
                        categoryIn(categoryIds),
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
                        categoryIn(categoryIds),
                        titleContains(keyword)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<SchoolArticle> findCalendarArticles(List<Integer> categoryIds, Boolean isMyOnly, Integer userId, LocalDate viewStart, LocalDate viewEnd) {
        return queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        calendarCategoryFilter(categoryIds, isMyOnly, userId),
                        schoolArticle.startDate.loe(viewEnd),
                        schoolArticle.dueDate.goe(viewStart)
                )
                .fetch();
    }

    @Override
    public Page<SchoolArticle> findDailyCalendarArticles(LocalDate selectedDate, List<Integer> categoryIds, Boolean isMyOnly, Integer userId, Pageable pageable) {
        List<SchoolArticle> content = queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        calendarCategoryFilter(categoryIds, isMyOnly, userId),
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
                        calendarCategoryFilter(categoryIds, isMyOnly, userId),
                        schoolArticle.startDate.loe(selectedDate),
                        schoolArticle.dueDate.goe(selectedDate)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression calendarCategoryFilter(List<Integer> categoryIds, Boolean isMyOnly, Integer userId) {
        // 1. MY 필터 (본인 북마크) 처리
        if (Boolean.TRUE.equals(isMyOnly)) {
            if (userId == null) return schoolArticle.articleId.eq(-1); // 비로그인 시 결과 없음
            return schoolArticle.articleId.in(
                    queryFactory.select(bookmark.articleId)
                            .from(bookmark)
                            .where(bookmark.user.userId.eq(userId),
                                   bookmark.articleType.eq(VendorType.SCHOOL))
            );
        }

        // 2. 카테고리 ID 다중 필터 처리 (전달된 ID가 없으면 전체 조회)
        return (categoryIds != null && !categoryIds.isEmpty()) 
                ? schoolArticle.category.categoryId.in(categoryIds) 
                : null;
    }

    // --- 조건절 메서드 (재사용 및 가독성 향상) ---

    private BooleanExpression categoryIn(List<Integer> categoryIds) {
        return (categoryIds != null && !categoryIds.isEmpty()) ? schoolArticle.category.categoryId.in(categoryIds) : null;
    }

    private BooleanExpression titleContains(String keyword) {
        return keyword != null ? schoolArticle.title.contains(keyword) : null;
    }

    // --- 정렬 로직 (사용자 체감 시급성 기준 우선순위 조정) ---
    private OrderSpecifier<Integer> createStatusOrder(LocalDate today, LocalDate upcomingLimit, LocalDate endingSoonLimit) {
        NumberExpression<Integer> statusPriority = new CaseBuilder()
                // 1. 마감 임박 (ENDING_SOON): 이미 시작되었고 마감일이 오늘~5일 이내인 경우
                .when(schoolArticle.startDate.loe(today)
                        .and(schoolArticle.dueDate.goe(today))
                        .and(schoolArticle.dueDate.loe(endingSoonLimit))).then(1)
                
                // 2. 진행중 (OPEN): 이미 시작되었고 마감일이 5일보다 많이 남았거나 없는 경우
                .when(schoolArticle.startDate.loe(today)
                        .and(schoolArticle.dueDate.gt(endingSoonLimit).or(schoolArticle.dueDate.isNull()))).then(2)
                
                // 3. 시작예정 (UPCOMING): 아직 시작일이 되지 않은 경우
                .when(schoolArticle.startDate.gt(today)).then(3)
                
                // 4. 종료 (CLOSED): 마감일이 오늘 이전인 경우
                .when(schoolArticle.dueDate.lt(today)).then(4)
                
                .otherwise(5);

        return statusPriority.asc();
    }
}
