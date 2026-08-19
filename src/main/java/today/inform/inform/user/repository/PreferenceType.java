package today.inform.inform.user.repository;

/**
 * 사용자 개인화 junction 테이블 3종.
 *
 * <p>세 테이블이 {@code (user_id, 대상_id)} 복합 PK 로 모양이 같아 한 저장소에서 다룹니다.
 * 테이블/컬럼명은 이 enum 의 상수이며 <b>외부 입력이 들어오지 않습니다.</b>
 * (동적 SQL 이지만 인젝션 경로가 없습니다)
 */
public enum PreferenceType {

    /** 구독 학과·기관. SCHOOL 유형만 허용 — DB trigger 가 IN006 으로 막습니다. */
    VENDOR("user_vendors", "vendor_id", "vendors"),

    /** 관심 분야. 공지 카테고리와 같은 taxonomy 를 씁니다. */
    CATEGORY("user_interest_categories", "category_id", "categories"),

    /** 관심 동아리 유형. 비활성 유형 신규 선택은 IN008 로 막힙니다. */
    CLUB_TYPE("user_club_type_interests", "club_type_id", "club_types");

    private final String table;
    private final String column;
    private final String masterTable;

    PreferenceType(String table, String column, String masterTable) {
        this.table = table;
        this.column = column;
        this.masterTable = masterTable;
    }

    public String table() {
        return table;
    }

    public String column() {
        return column;
    }

    public String masterTable() {
        return masterTable;
    }
}
