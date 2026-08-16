package com.example.obituarymaker.obituary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 화면을 실제로 렌더링해서 build/preview/ 에 저장한다.
 * `./gradlew test` 후 그 폴더의 html을 브라우저로 열면 서버를 띄우지 않고도 눈으로 확인할 수 있다.
 */
@WebMvcTest(ObituaryController.class)
class TemplateRenderTest {

    private static final Path PREVIEW_DIR = Path.of("build", "preview");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObituaryService obituaryService;

    @Test
    void 첫_화면을_렌더링한다() throws Exception {
        String html = render("index.html", get("/"));

        assertThat(html).contains("부고장 만들기");
    }

    @Test
    void 입력_화면을_렌더링한다() throws Exception {
        String html = render("form.html", get("/obituaries/new"));

        assertThat(html).contains("고인 성함").contains("조의금 계좌");
    }

    @Test
    void 입력값이_틀린_화면을_렌더링한다() throws Exception {
        String html = render("form-error.html",
                post("/obituaries/preview").param("name", "").param("mournerPhone", "01012345678"));

        assertThat(html).contains("고인의 성함을 입력해 주세요.");
    }

    @Test
    void 확인_화면을_렌더링한다() throws Exception {
        String html = render("preview.html", post("/obituaries/preview")
                .param("name", "홍길동")
                .param("deathDate", "2026-08-14")
                .param("funeralHome", "서울추모공원")
                .param("room", "3호실")
                .param("departureDate", "2026-08-16")
                .param("mournerName", "홍철수")
                .param("mournerPhone", "010-1234-5678")
                .param("address", "서울시 서초구 원지동 산4-1")
                .param("account", "국민 123456-01-123456 홍철수"));

        assertThat(html).contains("이렇게 만들어집니다").contains("서울추모공원 3호실");
    }

    @Test
    void 완료_화면을_렌더링한다() throws Exception {
        given(obituaryService.shareUrl("a1b2c3d4")).willReturn("https://obituary.example.com/a1b2c3d4/");

        String html = render("complete.html", get("/obituaries/a1b2c3d4/complete"));

        assertThat(html).contains("https://obituary.example.com/a1b2c3d4/");
    }

    @Test
    void 선택_정보까지_적은_부고장을_렌더링한다() throws IOException {
        Obituary obituary = sample();
        obituary.setAddress("서울시 서초구 원지동 산4-1");
        obituary.setAccount("국민 123456-01-123456 홍철수");

        String html = save("view.html", renderObituary(obituary));

        assertThat(html)
                .contains("조의금 안내")
                .contains("지도에서 길 찾기")
                .contains("data-account=\"국민 123456-01-123456 홍철수\"")
                .contains("https://map.kakao.com/?q=%EC%84%9C%EC%9A%B8%EC%8B%9C")
                .doesNotContain("q=서울시");
    }

    @Test
    void 필수_정보만_적으면_선택_항목은_아예_안_보인다() throws IOException {
        String html = save("view-required-only.html", renderObituary(sample()));

        assertThat(html)
                .doesNotContain("조의금 안내")
                .doesNotContain("지도에서 길 찾기")
                .doesNotContain("위치");
    }

    private Obituary sample() {
        Obituary obituary = new Obituary();
        obituary.setName("홍길동");
        obituary.setDeathDate(LocalDate.of(2026, 8, 14));
        obituary.setFuneralHome("서울추모공원");
        obituary.setRoom("3호실");
        obituary.setDepartureDate(LocalDate.of(2026, 8, 16));
        obituary.setMournerName("홍철수");
        obituary.setMournerPhone("010-1234-5678");
        return obituary;
    }

    /** 부고장은 S3에 단독 파일로 올라가므로 S3Publisher와 똑같이 스프링 없이 렌더링한다. */
    private String renderObituary(Obituary obituary) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        return engine.process("obituary/view", new Context(Locale.KOREA, Map.of("obituary", obituary)));
    }

    private String render(String fileName, RequestBuilder request) throws Exception {
        return save(fileName, mockMvc.perform(request).andReturn().getResponse().getContentAsString());
    }

    /** 브라우저에서 file:// 로 열어도 보이도록 style.css를 같이 두고 상대 경로로 바꾼다. */
    private String save(String fileName, String html) throws IOException {
        Files.createDirectories(PREVIEW_DIR);
        Files.copy(Path.of("src/main/resources/static/style.css"), PREVIEW_DIR.resolve("style.css"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(PREVIEW_DIR.resolve(fileName), html.replace("href=\"/style.css\"", "href=\"style.css\""));
        return html;
    }
}
