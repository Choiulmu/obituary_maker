package com.example.obituarymaker.obituary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "admin-token=test-token")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObituaryService obituaryService;

    /** 운영자가 쓰는 curl 그대로: PATCH + form body. */
    private MockHttpServletRequestBuilder patchWithBody(String id, String body) {
        return patch("/admin/obituaries/" + id)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(body);
    }

    @Test
    void 토큰이_맞으면_넘어온_항목만_수정한다() throws Exception {
        mockMvc.perform(patchWithBody("a1b2c3d4", "room=5호실&departureDate=2026-08-17")
                        .header("X-Admin-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("수정했습니다."));

        then(obituaryService).should()
                .update("a1b2c3d4", Map.of("room", "5호실", "departureDate", "2026-08-17"));
    }

    @Test
    void 토큰이_없거나_틀리면_거절한다() throws Exception {
        mockMvc.perform(patchWithBody("a1b2c3d4", "room=5호실"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patchWithBody("a1b2c3d4", "room=5호실").header("X-Admin-Token", "wrong"))
                .andExpect(status().isUnauthorized());

        then(obituaryService).shouldHaveNoInteractions();
    }

    @Test
    void 없는_부고장이면_404다() throws Exception {
        willThrow(new NoSuchElementException("부고장을 찾을 수 없습니다."))
                .given(obituaryService).update(anyString(), anyMap());

        mockMvc.perform(patchWithBody("none", "room=5호실").header("X-Admin-Token", "test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 값이_잘못되면_400과_이유를_돌려준다() throws Exception {
        willThrow(new IllegalArgumentException("빈소를 입력해 주세요."))
                .given(obituaryService).update(anyString(), anyMap());

        mockMvc.perform(patchWithBody("a1b2c3d4", "room=").header("X-Admin-Token", "test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("빈소를 입력해 주세요."));
    }
}
