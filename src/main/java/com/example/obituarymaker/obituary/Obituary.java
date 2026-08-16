package com.example.obituarymaker.obituary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Obituary {

    private static final DateTimeFormatter VIEW_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private String id;
    private String createdAt;
    private String updatedAt = "";

    @NotBlank(message = "고인의 성함을 입력해 주세요.")
    @Size(max = 20, message = "고인의 성함은 20자까지 적을 수 있습니다.")
    private String name;

    @NotNull(message = "별세일을 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deathDate;

    @NotBlank(message = "장례식장 이름을 입력해 주세요.")
    @Size(max = 30, message = "장례식장 이름은 30자까지 적을 수 있습니다.")
    private String funeralHome;

    @NotBlank(message = "빈소를 입력해 주세요.")
    @Size(max = 20, message = "빈소는 20자까지 적을 수 있습니다.")
    private String room;

    @NotNull(message = "발인일을 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departureDate;

    @NotBlank(message = "상주 성함을 입력해 주세요.")
    @Size(max = 10, message = "상주 성함은 10자까지 적을 수 있습니다.")
    @Pattern(regexp = "[가-힣A-Za-z]*", message = "상주는 한 분만, 띄어쓰기 없이 적어 주세요.")
    private String mournerName;

    @NotBlank(message = "상주 연락처를 입력해 주세요.")
    @Pattern(regexp = "|0\\d{1,2}-\\d{3,4}-\\d{4}", message = "연락처를 010-1234-5678 형식으로 입력해 주세요.")
    private String mournerPhone;

    @Size(max = 100, message = "장례식장 주소는 100자까지 적을 수 있습니다.")
    private String address = "";

    //TODO: 조의금 계좌(한국 은행 목록 - 라이브러리 없나)

    @Size(max = 50, message = "조의금 계좌는 50자까지 적을 수 있습니다.")
    private String account = "";

    private String shareUrl;

    /** 시트에서 읽어온 행 번호(1-based). 시트 열이 아니라 갱신 위치를 기억하기 위한 값이다. */
    private int rowNumber;

    public String getDeathDateText() {
        return deathDate == null ? "" : deathDate.format(VIEW_DATE);
    }

    public String getDepartureDateText() {
        return departureDate == null ? "" : departureDate.format(VIEW_DATE);
    }

    public List<Object> toRow() {
        return List.of(
                text(id), text(createdAt), text(updatedAt), text(name), date(deathDate),
                text(funeralHome), text(room), date(departureDate), text(mournerName), text(mournerPhone),
                text(address), text(account), text(shareUrl));
    }

    public static Obituary fromRow(List<Object> row, int rowNumber) {
        Obituary obituary = new Obituary();
        obituary.rowNumber = rowNumber;
        obituary.id = cell(row, 0);
        obituary.createdAt = cell(row, 1);
        obituary.updatedAt = cell(row, 2);
        obituary.name = cell(row, 3);
        obituary.deathDate = parseDate(cell(row, 4));
        obituary.funeralHome = cell(row, 5);
        obituary.room = cell(row, 6);
        obituary.departureDate = parseDate(cell(row, 7));
        obituary.mournerName = cell(row, 8);
        obituary.mournerPhone = cell(row, 9);
        obituary.address = cell(row, 10);
        obituary.account = cell(row, 11);
        obituary.shareUrl = cell(row, 12);
        return obituary;
    }

    /**
     * 운영자 수정 API용. 넘어온 필드만 덮어쓴다.
     * 값이 빈 문자열이면 선택 항목은 지워지고, 필수 항목은 이어지는 검증에서 걸린다.
     */
    public void applyChanges(Map<String, String> changes) {
        changes.forEach((field, rawValue) -> {
            String value = text(rawValue);
            switch (field) {
                case "name" -> name = value;
                case "deathDate" -> deathDate = parseDate(value);
                case "funeralHome" -> funeralHome = value;
                case "room" -> room = value;
                case "departureDate" -> departureDate = parseDate(value);
                case "mournerName" -> mournerName = value;
                case "mournerPhone" -> mournerPhone = value;
                case "address" -> address = value;
                case "account" -> account = value;
                default -> throw new IllegalArgumentException("고칠 수 없는 항목입니다: " + field);
            }
        });
    }

    /** 날짜만 파싱 전에 공백을 잘라낸다. 나머지 값은 사용자가 넣은 그대로 저장한다. */
    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private static String cell(List<Object> row, int index) {
        return index < row.size() ? row.get(index).toString() : "";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
