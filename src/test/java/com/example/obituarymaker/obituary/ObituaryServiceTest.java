package com.example.obituarymaker.obituary;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ObituaryServiceTest {

    private final GoogleSheetsClient sheetsClient = mock(GoogleSheetsClient.class);
    private final S3Publisher publisher = mock(S3Publisher.class);
    private final ObituaryService service = new ObituaryService(sheetsClient, publisher,
            Validation.buildDefaultValidatorFactory().getValidator());

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

    @Test
    void 부고장ID는_랜덤_UUID다() throws Exception {
        String id = service.create(sample());

        assertThat(UUID.fromString(id)).hasToString(id);
    }

    @Test
    void 공유_링크를_확정한_뒤_S3에_올리고_시트에_적는다() throws Exception {
        given(publisher.shareUrl(any())).willReturn("https://obituary.example.com/some-id/");
        Obituary obituary = sample();

        service.create(obituary);

        assertThat(obituary.getShareUrl()).isEqualTo("https://obituary.example.com/some-id/");
        assertThat(obituary.getCreatedAt()).isNotBlank();
        assertThat(obituary.getUpdatedAt()).isEmpty();

        InOrder order = inOrder(publisher, sheetsClient);
        order.verify(publisher).publish(obituary);
        order.verify(sheetsClient).append(obituary);
    }
}
