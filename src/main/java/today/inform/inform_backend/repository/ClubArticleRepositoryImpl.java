package today.inform.inform_backend.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform_backend.entity.ClubArticle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static today.inform.inform_backend.entity.QClubArticle.clubArticle;
import static today.inform.inform_backend.entity.QVendor.vendor;

@Repository
@RequiredArgsConstructor
public class ClubArticleRepositoryImpl implements ClubArticleRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<ClubArticle> findByIdWithVendor(Integer articleId) {
        return Optional.ofNullable(queryFactory
                .selectFrom(clubArticle)
                .leftJoin(clubArticle.vendor, vendor).fetchJoin()
                .where(clubArticle.articleId.eq(articleId))
                .fetchOne());
    }

    @Override
    public Page<ClubArticle> findAllWithFilters(Integer vendorId, String keyword, LocalDate today, LocalDate dMinus5,
            Pageable pageable) {

        // 정렬 우선순위 생성: 오늘 기준 남은 기간이 5일 이하(0~5일)이고 만료되지 않은 경우 우선순위 1
        NumberExpression<Integer> dueDatePriority = new CaseBuilder()
                .when(clubArticle.dueDate.goe(today).and(clubArticle.dueDate.loe(dMinus5))).then(1)
                .otherwise(2);

        List<ClubArticle> content = queryFactory
                .selectFrom(clubArticle)
                .leftJoin(clubArticle.vendor, vendor).fetchJoin()
                .where(
                        vendorIdEq(vendorId),
                        titleContains(keyword))
                .orderBy(
                        dueDatePriority.asc(), // 우선순위 1순위(5일 남은 것) 먼저 정렬
                        clubArticle.dueDate.asc(), // 마감일이 가까운 순으로 추가 정렬
                        clubArticle.createdAt.desc() // 마지막으로 최신 작성순 정렬
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(clubArticle.count())
                .from(clubArticle)
                .where(
                        vendorIdEq(vendorId),
                        titleContains(keyword))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression vendorIdEq(Integer vendorId) {
        return vendorId != null ? clubArticle.vendor.vendorId.eq(vendorId) : null;
    }

    private BooleanExpression titleContains(String keyword) {
        return keyword != null ? clubArticle.title.contains(keyword) : null;
    }
}
