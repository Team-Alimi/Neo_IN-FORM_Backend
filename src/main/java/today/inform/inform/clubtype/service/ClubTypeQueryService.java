package today.inform.inform.clubtype.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform.clubtype.dto.response.ClubTypeResponse;
import today.inform.inform.clubtype.repository.ClubTypeRepository;

/**
 * 동아리 유형 목록 (사용자용).
 *
 * <p><b>활성 항목만 내보냅니다.</b> 이 목록이 곧 온보딩 선택 화면이고,
 * 비활성 유형은 DB 트리거가 신규 선택을 IN008 로 거부합니다.
 * 걸러 내지 않으면 <b>고를 수 있는 것처럼 보여 주고 저장에서 400</b> 을 주게 됩니다.
 * ({@code CategoryQueryService} 와 같은 판단입니다)
 */
@Service
@RequiredArgsConstructor
public class ClubTypeQueryService {

    private final ClubTypeRepository clubTypeRepository;

    @Transactional(readOnly = true)
    public List<ClubTypeResponse> findActive() {
        return ClubTypeResponse.from(clubTypeRepository.findActive());
    }
}
