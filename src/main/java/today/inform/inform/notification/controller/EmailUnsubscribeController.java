package today.inform.inform.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import today.inform.inform.notification.service.EmailUnsubscribeService;
import today.inform.inform.notification.service.NotificationEmailPaths;

/**
 * NTF-05 메일 수신거부. <b>로그인이 필요 없습니다</b>({@code SecurityConfig} 에 등록).
 *
 * <h2>★ GET 은 확인 페이지, POST 가 실제 해제입니다</h2>
 * 한 번의 {@code GET} 으로 끄면 안 됩니다. 메일 클라이언트(Gmail·Outlook)와
 * 회사 메일 게이트웨이의 링크 검사기는 <b>사용자가 누르지 않아도</b> 본문의 링크를
 * 미리 받아 봅니다. {@code GET} 에서 상태를 바꾸면
 * <b>메일이 도착한 것만으로 알림이 꺼집니다</b> — 사용자는 자기가 끈 적도 없는데
 * 알림이 안 온다고 느끼고, 원인은 어디에도 안 남습니다.
 *
 * <p>그래서 {@code GET} 은 버튼이 있는 페이지만 그리고, 누르면 그 버튼이
 * 같은 경로로 {@code POST} 합니다. 프리페치는 {@code GET} 만 하므로 아무 일도 안 납니다.
 *
 * <h2>★ JSON 이 아니라 HTML 입니다</h2>
 * 이 주소는 메일 본문의 링크이고, 그것을 여는 것은 브라우저입니다.
 * {@code ApiResponse} 를 돌려주면 사용자가 원시 JSON 을 보게 됩니다.
 * 이 프로젝트에서 사람이 직접 보는 화면을 내보내는 유일한 컨트롤러입니다.
 *
 * <h2>★ 실패 이유를 구분해 보여주지 않습니다</h2>
 * 만료와 위조를 나눠 알려 주면 토큰을 바꿔 가며 찔러 보는 쪽에 단서를 줍니다.
 * 어느 경우든 사용자가 할 일은 하나입니다 — 설정 화면에서 끄는 것.
 */
@RestController
@RequestMapping(NotificationEmailPaths.UNSUBSCRIBE_RELATIVE)
@RequiredArgsConstructor
public class EmailUnsubscribeController {

    private final EmailUnsubscribeService emailUnsubscribeService;

    /**
     * 확인 페이지. <b>아무것도 바꾸지 않습니다.</b>
     *
     * @param token 메일 링크에 담긴 서명 토큰
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@RequestParam(required = false) String token) {
        if (!emailUnsubscribeService.isTokenValid(token)) {
            return html(HttpStatus.BAD_REQUEST, invalidPage());
        }
        return html(HttpStatus.OK, confirmPage(token));
    }

    /**
     * 실제 수신거부. 확인 페이지의 버튼이 보냅니다.
     *
     * <p>토큰을 폼 필드로 받습니다. {@code @RequestParam} 은 쿼리스트링과
     * {@code application/x-www-form-urlencoded} 본문을 모두 받습니다.
     */
    @PostMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam(required = false) String token) {
        if (!emailUnsubscribeService.unsubscribe(token)) {
            return html(HttpStatus.BAD_REQUEST, invalidPage());
        }
        return html(HttpStatus.OK, donePage());
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private static String confirmPage(String token) {
        return page("메일 알림 수신거부", """
                <h1>메일 알림을 끄시겠어요?</h1>
                <p>마감 하루 전 알림 메일을 더 이상 받지 않습니다.<br>
                   서비스 안의 알림은 그대로 유지됩니다.</p>
                <form method="post">
                  <input type="hidden" name="token" value="%s">
                  <button type="submit">메일 알림 끄기</button>
                </form>
                <p class="note">언제든 INFORM 설정에서 다시 켜실 수 있습니다.</p>
                """.formatted(escape(token)));
    }

    private static String donePage() {
        return page("메일 알림 수신거부 완료", """
                <h1>처리되었습니다</h1>
                <p>이제 알림 메일을 받지 않습니다.<br>
                   서비스 안의 알림은 그대로 유지됩니다.</p>
                <p class="note">다시 받으시려면 INFORM 설정에서 메일 알림을 켜 주세요.</p>
                """);
    }

    private static String invalidPage() {
        return page("메일 알림 수신거부", """
                <h1>링크를 사용할 수 없습니다</h1>
                <p>링크가 만료되었거나 올바르지 않습니다.</p>
                <p class="note">INFORM 설정에서 메일 알림을 끄실 수 있습니다.</p>
                """);
    }

    /**
     * 페이지 껍데기.
     *
     * <p>CSS 를 인라인으로 둡니다. 외부 파일을 불러오면 그 요청에도 인증 예외를
     * 열어야 하고, 메일에서 열리는 한 장짜리 페이지에 정적 자원 배포 경로가 따라붙습니다.
     */
    private static String page(String title, String content) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { font-family: system-ui, -apple-system, sans-serif;
                           max-width: 32rem; margin: 4rem auto; padding: 0 1.5rem;
                           line-height: 1.7; color: #1a1a1a; }
                    h1 { font-size: 1.35rem; margin-bottom: 1rem; }
                    button { font: inherit; padding: 0.7rem 1.4rem; margin: 0.5rem 0;
                             border: 0; border-radius: 0.5rem; cursor: pointer;
                             background: #1a1a1a; color: #fff; }
                    .note { color: #666; font-size: 0.9rem; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(escape(title), content);
    }

    /**
     * HTML 특수문자 치환.
     *
     * <p>토큰은 숫자·점·base64url 로만 이루어져 있어 사실 치환할 것이 없고,
     * 서명 검증을 통과한 뒤에야 페이지에 들어갑니다. 그래도 거치는 이유는
     * <b>"입력을 HTML 에 그대로 넣는 자리" 를 만들지 않기 위해서</b>입니다.
     * 토큰 형식이 나중에 바뀌어도 이 자리는 안전한 채로 남습니다.
     */
    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
