package com.iconcile.expense.web.controller;

import com.iconcile.expense.service.CsvImportService;
import com.iconcile.expense.service.csv.CsvField;
import com.iconcile.expense.web.dto.ImportResultResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultResponse importCsv(@RequestParam("file") MultipartFile file) {
        return csvImportService.importCsv(file);
    }

    /** Machine-readable format spec, so the UI's format hint cannot drift from the parser. */
    @GetMapping("/import/format")
    public Map<String, Object> format() {
        return Map.of(
                "templateHeader", CsvField.templateHeader(),
                "acceptedDateFormats", List.of("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"),
                "requiredColumns", List.of("date", "amount", "vendor"),
                "optionalColumns", List.of("description", "category"),
                "columnAliases", CsvField.aliasCatalog(),
                "notes", List.of(
                        "The date format is locked to whichever supported format the first row uses.",
                        "Rows that fail validation are reported and skipped; the rest of the file still imports.",
                        "Amounts must be positive and have at most 2 decimal places.",
                        "Supplying a category overrides the automatic vendor rules for that row."));
    }
}
