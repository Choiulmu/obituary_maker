package com.example.obituarymaker.obituary;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObituaryTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Obituary sample() {
        Obituary obituary = new Obituary();
        obituary.setId("a1b2c3d4");
        obituary.setCreatedAt("2026-08-15 14:32:10");
        obituary.setName("홍길동");
        obituary.setDeathDate(LocalDate.of(2026, 8, 14));
        obituary.setFuneralHome("서울추모공원");
        obituary.setRoom("3호실");
        obituary.setDepartureDate(LocalDate.of(2026, 8, 16));
        obituary.setMournerName("홍철수");
        obituary.setMournerPhone("010-1234-5678");
        obituary.setShareUrl("https://obituary.example.com/a1b2c3d4/");
        return obituary;
    }

    @Test
    void 시트_행은_A부터_M까지_13칸이고_선택항목은_빈칸으로_남는다() {
        List<Object> row = sample().toRow();

        assertThat(row).hasSize(13);
        assertThat(row.get(0)).isEqualTo("a1b2c3d4");
        assertThat(row.get(2)).isEqualTo("");
        assertThat(row.get(4)).isEqualTo("2026-08-14");
        assertThat(row.get(10)).isEqualTo("");
        assertThat(row.get(12)).isEqualTo("https://obituary.example.com/a1b2c3d4/");
    }

    @Test
    void 시트_행을_다시_읽으면_같은_값이_된다() {
        Obituary read = Obituary.fromRow(sample().toRow(), 2);

        assertThat(read.getRowNumber()).isEqualTo(2);
        assertThat(read.toRow()).isEqualTo(sample().toRow());
    }

    @Test
    void 뒤쪽_빈칸이_잘려온_행도_읽을_수_있다() {
        Obituary read = Obituary.fromRow(List.of("a1b2c3d4", "2026-08-15 14:32:10"), 2);

        assertThat(read.getId()).isEqualTo("a1b2c3d4");
        assertThat(read.getAccount()).isEmpty();
        assertThat(read.getDeathDate()).isNull();
    }

    @Test
    void 넘어온_항목만_바뀐다() {
        Obituary obituary = sample();

        obituary.applyChanges(Map.of("room", "5호실", "departureDate", "2026-08-17"));

        assertThat(obituary.getRoom()).isEqualTo("5호실");
        assertThat(obituary.getDepartureDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(obituary.getName()).isEqualTo("홍길동");
    }

    @Test
    void 공백은_적은_그대로_저장한다() {
        Obituary obituary = sample();

        obituary.applyChanges(Map.of("room", " 5 호실 "));

        assertThat(obituary.getRoom()).isEqualTo(" 5 호실 ");
        assertThat(obituary.toRow().get(6)).isEqualTo(" 5 호실 ");
    }

    @Test
    void 길이는_공백까지_세서_제한한다() {
        Obituary obituary = sample();
        obituary.setRoom(" ".repeat(18) + "3호실");

        assertThat(validator.validate(obituary))
                .extracting(v -> v.getMessage())
                .containsExactly("빈소는 20자까지 적을 수 있습니다.");
    }

    @Test
    void 상주는_한_명만_적을_수_있다() {
        Obituary obituary = sample();
        obituary.setMournerName("홍철수 홍영희");

        assertThat(validator.validate(obituary))
                .extracting(v -> v.getMessage())
                .containsExactly("상주는 한 분만, 띄어쓰기 없이 적어 주세요.");
    }

    @Test
    void 모르는_항목은_바꾸지_않고_거절한다() {
        assertThatThrownBy(() -> sample().applyChanges(Map.of("id", "hacked")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 필수항목을_빈_값으로_바꾸면_검증에_걸린다() {
        Obituary obituary = sample();
        obituary.applyChanges(Map.of("room", "", "deathDate", ""));

        assertThat(validator.validate(obituary)).hasSize(2);
    }

    @Test
    void 선택항목은_빈_값으로_지울_수_있다() {
        Obituary obituary = sample();
        obituary.setAccount("국민 123456-01-123456 홍철수");
        obituary.applyChanges(Map.of("account", ""));

        assertThat(obituary.getAccount()).isEmpty();
        assertThat(validator.validate(obituary)).isEmpty();
    }

    @Test
    void 너무_긴_값은_검증에_걸린다() {
        Obituary obituary = sample();
        obituary.setName("가".repeat(21));
        obituary.setAccount("나".repeat(51));

        assertThat(validator.validate(obituary))
                .extracting(v -> v.getMessage())
                .containsExactlyInAnyOrder(
                        "고인의 성함은 20자까지 적을 수 있습니다.",
                        "조의금 계좌는 50자까지 적을 수 있습니다.");
    }

    @Test
    void 길이_제한까지는_통과한다() {
        Obituary obituary = sample();
        obituary.setName("가".repeat(20));
        obituary.setFuneralHome("나".repeat(30));
        obituary.setRoom("다".repeat(20));
        obituary.setMournerName("라".repeat(10));
        obituary.setAddress("마".repeat(100));
        obituary.setAccount("바".repeat(50));

        assertThat(validator.validate(obituary)).isEmpty();
    }

    @Test
    void 연락처_형식이_틀리면_검증에_걸린다() {
        Obituary obituary = sample();
        obituary.setMournerPhone("01012345678");

        assertThat(validator.validate(obituary)).hasSize(1);
    }

    @Test
    void 화면에는_날짜를_한글로_보여준다() {
        assertThat(sample().getDeathDateText()).isEqualTo("2026년 8월 14일");
    }
}
