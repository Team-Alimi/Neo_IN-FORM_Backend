package today.inform.inform_backend.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import today.inform.inform_backend.entity.ClubArticle;

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
    public Page<ClubArticle> findAllWithFilters(Integer vendorId, Pageable pageable) {
        List<ClubArticle> content = queryFactory
                .selectFrom(clubArticle)
                .leftJoin(clubArticle.vendor, vendor).fetchJoin()
                .where(vendorIdEq(vendorId))
                .orderBy(clubArticle.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(clubArticle.count())
                .from(clubArticle)
                .where(vendorIdEq(vendorId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression vendorIdEq(Integer vendorId) {
        return vendorId != null ? clubArticle.vendor.vendorId.eq(vendorId) : null;
    }
}
