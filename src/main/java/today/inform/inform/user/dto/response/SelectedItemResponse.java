package today.inform.inform.user.dto.response;

/** 사용자가 선택한 마스터 항목. 화면에 이름을 바로 그릴 수 있게 name 을 함께 내려줍니다. */
public record SelectedItemResponse(Long id, String name) {
}
