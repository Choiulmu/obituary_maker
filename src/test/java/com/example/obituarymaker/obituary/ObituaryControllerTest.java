package com.example.obituarymaker.obituary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ObituaryController.class)
class ObituaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObituaryService obituaryService;

    private MultiValueMap<String, String> filledForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", "홍길동");
        form.add("deathDate", "2026-08-14");
        form.add("funeralHome", "서울추모공원");
        form.add("room", "3호실");
        form.add("departureDate", "2026-08-16");
        form.add("mournerName", "홍철수");
        form.add("mournerPhone", "010-1234-5678");
        form.add("address", "");
        form.add("account", "국민 123456-01-123456 홍철수");
        return form;
    }

    @Test
    void 입력_폼이_열린다() throws Exception {
        mockMvc.perform(get("/obituaries/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("obituary/form"))
                .andExpect(content().string(containsString("고인 성함")));
    }

    @Test
    void 입력값이_맞으면_미리보기를_보여준다() throws Exception {
        mockMvc.perform(post("/obituaries/preview").params(filledForm()))
                .andExpect(status().isOk())
                .andExpect(view().name("obituary/preview"))
                .andExpect(content().string(containsString("2026년 8월 14일")))
                .andExpect(content().string(containsString("서울추모공원 3호실")));
    }

    @Test
    void 빠진_값이_있으면_폼에_쉬운_안내를_보여준다() throws Exception {
        MultiValueMap<String, String> form = filledForm();
        form.set("name", "");
        form.set("mournerPhone", "01012345678");

        mockMvc.perform(post("/obituaries/preview").params(form))
                .andExpect(status().isOk())
                .andExpect(view().name("obituary/form"))
                .andExpect(content().string(containsString("고인의 성함을 입력해 주세요.")))
                .andExpect(content().string(
                        containsString("연락처를 010-1234-5678 형식으로 입력해 주세요.")));
    }

    @Test
    void 부고장을_만들면_완료_화면으로_보낸다() throws Exception {
        given(obituaryService.create(any())).willReturn("a1b2c3d4");

        mockMvc.perform(post("/obituaries").params(filledForm()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/obituaries/a1b2c3d4/complete"));
    }

    @Test
    void 만들지_못하면_폼에_다시_시도하라고_알려준다() throws Exception {
        given(obituaryService.create(any())).willThrow(new RuntimeException("sheets down"));

        mockMvc.perform(post("/obituaries").params(filledForm()))
                .andExpect(status().isOk())
                .andExpect(view().name("obituary/form"))
                .andExpect(content().string(
                        containsString("부고장을 만들지 못했습니다.")));
    }

    @Test
    void 완료_화면에_공유_링크가_보인다() throws Exception {
        given(obituaryService.shareUrl("a1b2c3d4")).willReturn("https://obituary.example.com/a1b2c3d4/");

        mockMvc.perform(get("/obituaries/a1b2c3d4/complete"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("https://obituary.example.com/a1b2c3d4/")));
    }
}
