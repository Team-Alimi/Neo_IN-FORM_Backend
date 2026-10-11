/**
 * 게시글 조회와 AI 3줄 요약.
 *
 * <p>요약은 상세 요청 및 관리자 재생성에서만 시작합니다. 목록·인기·달력 조회는
 * 생성하지 않습니다. 별도 backend 키를 UPSTAGE_API_KEY 환경변수로 주입하며,
 * 키가 없으면 기존 캐시를 읽기만 합니다. Solar Pro 4 에 텍스트만 보내고 OCR 은 하지 않습니다.
 *
 * <p>제한 기본값은 ArticleSummaryProperties 가 소유합니다. 인스턴스당 worker 2개,
 * 대기열 20건, 호출 timeout 20초, retry 최대 2회(대기 2초/4초), 실패 cooldown 60초입니다.
 * Redis 에서 backend 키 전체의 최근 60초 RPM 100 / TPM 250,000 을 retry 까지 합산합니다.
 * 입력은 UTF-8 byte 수와 framing 여유로 보수적으로 예약하므로 실제 TPM 보다 일찍 제한될 수 있습니다.
 * timeout 이나 usage 누락은 예약을 유지합니다. 긴 원문은 임의로 자르지 않고 한도 초과 시 건너뜁니다.
 * 일일 호출·비용 상한은 없습니다. 전달받은 무료 키 사용 기한은 2027-05-01 입니다.
 *
 * <p>외부 응답은 세 문자열을 가진 lines JSON 으로 받아 빈 줄 없는 세 문장으로 검증합니다.
 * 명시적 날짜는 원문과 대조하며 일반적인 사실성 전체를 자동으로 증명하지는 않습니다.
 * 입력의 사실만 사용하도록 prompt 를 제한하고 fixture 로 내용 보존을 검증합니다.
 * 실패 결과는 캐시에 저장하지 않고 이후 상세 요청에서 다시 시도합니다.
 *
 * <p>worker 는 lock 획득 후 원문/version/updated_at 을 함께 읽습니다. 생성 결과는
 * id + 시작 version + 시작 updated_at + summary IS NULL 조건의 native UPDATE 로만 저장합니다.
 * 원문 네 컬럼 변경에 따른 무효화는 기존 DB trigger 의 책임입니다.
 */
package today.inform.inform.article.service;
