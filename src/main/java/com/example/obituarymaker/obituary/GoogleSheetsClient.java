package com.example.obituarymaker.obituary;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class GoogleSheetsClient {

    private static final String SHEET = "obituaries";
    private static final int FIRST_DATA_ROW = 2;

    private final Sheets sheets;
    private final String spreadsheetId;

    public GoogleSheetsClient(Sheets sheets, @Value("${spreadsheet-id}") String spreadsheetId) {
        this.sheets = sheets;
        this.spreadsheetId = spreadsheetId;
    }

    public void append(Obituary obituary) throws IOException {
        sheets.spreadsheets().values()
                .append(spreadsheetId, SHEET + "!A:M", new ValueRange().setValues(List.of(obituary.toRow())))
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    public Optional<Obituary> findById(String id) throws IOException {
        List<List<Object>> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && id.equals(row.get(0).toString())) {
                return Optional.of(Obituary.fromRow(row, FIRST_DATA_ROW + i));
            }
        }
        return Optional.empty();
    }

    public void update(Obituary obituary) throws IOException {
        String range = "%s!A%d:M%d".formatted(SHEET, obituary.getRowNumber(), obituary.getRowNumber());
        sheets.spreadsheets().values()
                .update(spreadsheetId, range, new ValueRange().setValues(List.of(obituary.toRow())))
                .setValueInputOption("RAW")
                .execute();
    }

    private List<List<Object>> readAll() throws IOException {
        List<List<Object>> values = sheets.spreadsheets().values()
                .get(spreadsheetId, SHEET + "!A" + FIRST_DATA_ROW + ":M")
                .execute()
                .getValues();
        return values == null ? List.of() : values;
    }
}
