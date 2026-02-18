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
import today.inform.inform_backend.entity.SchoolArticle;

import java.time.LocalDate;
import java.util.List;

import static today.inform.inform_backend.entity.QCategory.category;
import static today.inform.inform_backend.entity.QSchoolArticle.schoolArticle;
import static today.inform.inform_backend.entity.QSchoolArticleVendor.schoolArticleVendor;

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
                        schoolArticle.dueDate.goe(today).or(schoolArticle.dueDate.isNull())
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
            LocalDate startDate,
            LocalDate endDate,
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
                        titleContains(keyword),
                        dateRangeFilter(startDate, endDate)
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
                        titleContains(keyword),
                        dateRangeFilter(startDate, endDate)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<SchoolArticle> findAllWithFiltersAndSorting(
            List<Integer> categoryIds,
            List<Integer> vendorIds,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today,
            LocalDate upcomingLimit,
            LocalDate endingSoonLimit,
            Pageable pageable
    ) {
        List<SchoolArticle> content = queryFactory
                .selectFrom(schoolArticle)
                .leftJoin(schoolArticle.category, category).fetchJoin()
                .where(
                        categoryIn(categoryIds),
                        vendorIn(vendorIds),
                        titleContains(keyword),
                        dateRangeFilter(startDate, endDate)
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
                        categoryIn(categoryIds),
                        vendorIn(vendorIds),
                        titleContains(keyword),
                        dateRangeFilter(startDate, endDate)
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
                        dateRangeFilter(viewStart, viewEnd)
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
                        dateRangeFilter(selectedDate, selectedDate)
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
                        dateRangeFilter(selectedDate, selectedDate)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression calendarCategoryFilter(List<Integer> categoryIds, Boolean isMyOnly, Integer userId) {
        if (Boolean.TRUE.equals(isMyOnly)) {
            if (userId == null) return schoolArticle.articleId.eq(-1);
            return schoolArticle.articleId.in(
                    queryFactory.select(bookmark.articleId)
                            .from(bookmark)
                            .where(bookmark.user.userId.eq(userId),
                                   bookmark.articleType.eq(VendorType.SCHOOL))
            );
        }

        return (categoryIds != null && !categoryIds.isEmpty()) 
                ? schoolArticle.category.categoryId.in(categoryIds) 
                : null;
    }

    private BooleanExpression dateRangeFilter(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return null;
        
        BooleanExpression filter = null;
        
        if (startDate != null) {
            // 공지사항의 종료일이 필터의 시작일보다 같거나 커야 함 (기간이 겹침)
            filter = schoolArticle.dueDate.goe(startDate).or(schoolArticle.dueDate.isNull());
        }
        
        if (endDate != null) {
            // 공지사항의 시작일이 필터의 종료일보다 같거나 작아야 함 (기간이 겹침)
            BooleanExpression endFilter = schoolArticle.startDate.loe(endDate).or(schoolArticle.startDate.isNull());
            filter = (filter == null) ? endFilter : filter.and(endFilter);
        }
        
        return filter;
    }

    private BooleanExpression categoryIn(List<Integer> categoryIds) {
        return (categoryIds != null && !categoryIds.isEmpty()) ? schoolArticle.category.categoryId.in(categoryIds) : null;
    }

    private BooleanExpression vendorIn(List<Integer> vendorIds) {
        if (vendorIds == null || vendorIds.isEmpty()) return null;
        
        return schoolArticle.articleId.in(
                queryFactory.select(schoolArticleVendor.article.articleId)
                        .from(schoolArticleVendor)
                        .where(schoolArticleVendor.vendor.vendorId.in(vendorIds))
        );
    }

    private BooleanExpression titleContains(String keyword) {
        return keyword != null ? schoolArticle.title.contains(keyword) : null;
    }

    private OrderSpecifier<Integer> createStatusOrder(LocalDate today, LocalDate upcomingLimit, LocalDate endingSoonLimit) {
        NumberExpression<Integer> statusPriority = new CaseBuilder()
                .when(schoolArticle.startDate.loe(today)
                        .and(schoolArticle.dueDate.goe(today))
                        .and(schoolArticle.dueDate.loe(endingSoonLimit))).then(1)
                .when(schoolArticle.startDate.loe(today)
                        .and(schoolArticle.dueDate.gt(endingSoonLimit).or(schoolArticle.dueDate.isNull()))).then(2)
                .when(schoolArticle.startDate.gt(today)).then(3)
                .when(schoolArticle.dueDate.lt(today)).then(4)
                .otherwise(5);

        return statusPriority.asc();
    }
}
