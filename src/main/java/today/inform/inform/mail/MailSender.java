package today.inform.inform.mail;

/**
 * 메일 발송 경계. <b>인터페이스로 둔 이유는 {@code FileStorage} 와 같습니다 — 테스트입니다.</b>
 *
 * <p>검증하고 싶은 것은 "누구에게 보내는가"(수신 토글·탈퇴자 제외·중복 방지)이고,
 * 그건 SES 없이도 전부 확인할 수 있습니다. 실제 SES 왕복을 통합 테스트에 넣으면
 * 자격증명 없이는 빌드가 돌지 않고, 돌더라도 진짜 메일이 날아갑니다.
 */
public interface MailSender {

    /**
     * 발송 기능이 켜져 있는지. 꺼져 있으면 호출부가 <b>발송을 건너뛰어야</b> 합니다.
     *
     * <p>{@code MAIL_FROM} 이 비어 있으면 {@code false} 입니다. AI 요약의
     * {@code ArticleSummaryClient#isEnabled} 와 같은 장치입니다 — 설정이 빠진 환경에서
     * 예외로 기동을 막는 대신 조용히 비활성으로 떨어뜨리고, 그 사실을 로그로 남깁니다.
     */
    boolean isEnabled();

    /**
     * 한 통 보냅니다.
     *
     * @param to      수신 주소
     * @param subject 제목
     * @param body    본문 평문. <b>HTML 이 아닙니다</b> — 공지 제목이 그대로 들어가므로
     *                HTML 로 조립하면 태그가 섞인 제목에서 깨지거나 주입이 됩니다
     * @throws MailSendFailure 발송 실패. 호출부는 이 알림을 "아직 안 보냄" 으로 남겨
     *                         다음 주기에 재시도하게 해야 합니다
     */
    void send(String to, String subject, String body);

    /** 발송 실패. 되돌릴 수 없는 외부 호출이라 실패를 삼키지 않고 올립니다. */
    class MailSendFailure extends RuntimeException {
        public MailSendFailure(String message, Throwable cause) {
            // ★ 주소·본문·자격증명을 메시지에 담지 않습니다. 로그로 새어 나갑니다.
            super(message, cause);
        }
    }
}
