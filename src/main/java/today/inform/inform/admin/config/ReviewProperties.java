package today.inform.inform.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADM-12 "확인 필요 게시글" 판정 임계값.
 *
 * <p><b>DB 에 넣지 않는 것이 의도입니다.</b> 운영하며 조정할 값인데 컬럼이나 설정 테이블로 두면
 * 조정할 때마다 마이그레이션이 필요합니다. 설정으로 두면 배포만으로 바뀝니다.
 *
 * @param similarityThreshold 중복 의심 기준(0~100). 운영 초기 80 으로 시작합니다
 * @param minContentLength    본문 부실 판정 기준 글자수.
 *                            <b>크롤러 팀과 아직 확정하지 않았습니다</b>(Q12).
 *                            수집되는 본문의 실제 분포를 봐야 정할 수 있어서, 임시값을 씁니다
 */
@ConfigurationProperties(prefix = "inform.review")
public record ReviewProperties(int similarityThreshold, int minContentLength) {

    public ReviewProperties {
        if (similarityThreshold < 0 || similarityThreshold > 100) {
            throw new IllegalArgumentException("similarityThreshold 는 0~100 이어야 합니다: " + similarityThreshold);
        }
    }
}
