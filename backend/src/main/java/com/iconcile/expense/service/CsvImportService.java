package com.iconcile.expense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iconcile.expense.config.CsvImportProperties;
import com.iconcile.expense.domain.CategorizationSource;
import com.iconcile.expense.domain.Category;
import com.iconcile.expense.domain.CsvImportBatch;
import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.repository.CategoryRepository;
import com.iconcile.expense.repository.CsvImportBatchRepository;
import com.iconcile.expense.repository.ExpenseRepository;
import com.iconcile.expense.service.CategorizationService.CategorizationResult;
import com.iconcile.expense.service.csv.CsvField;
import com.iconcile.expense.service.csv.CsvValueParser;
import com.iconcile.expense.service.csv.RowFieldException;
import com.iconcile.expense.util.VendorNameNormalizer;
import com.iconcile.expense.web.dto.ImportResultResponse;
import com.iconcile.expense.web.error.CsvParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bulk expense import from CSV.
 *
 * <p><b>Transaction boundary:</b> the whole import is one transaction. A partial import the user
 * cannot identify or undo is worse than a clean failure. That is not the same as being brittle -
 * rows are validated individually and bad ones are <em>skipped and reported</em>, so the common
 * "a few rows are malformed" case still succeeds with everything else landing.
 *
 * <p><b>Duplicates</b> are reported, never dropped. Two identical coffees on the same day are a
 * perfectly normal pair of expenses; silently discarding one would lose real data.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final char BOM = '\uFEFF';

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final CsvImportBatchRepository batchRepository;
    private final CategorizationService categorizationService;
    private final AnomalyService anomalyService;
    private final VendorNameNormalizer normalizer;
    private final CsvImportProperties properties;
    private final ObjectMapper objectMapper;

    public CsvImportService(ExpenseRepository expenseRepository,
                            CategoryRepository categoryRepository,
                            CsvImportBatchRepository batchRepository,
                            CategorizationService categorizationService,
                            AnomalyService anomalyService,
                            VendorNameNormalizer normalizer,
                            CsvImportProperties properties,
                            ObjectMapper objectMapper) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.batchRepository = batchRepository;
        this.categorizationService = categorizationService;
        this.anomalyService = anomalyService;
        this.normalizer = normalizer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResultResponse importCsv(MultipartFile file) {
        String filename = sanitizeFilename(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new CsvParseException("The uploaded file is empty");
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvParseException("Only .csv files are accepted, got '" + filename + "'");
        }

        ParsedFile parsed = parse(file, filename);

        CsvImportBatch batch = batchRepository.save(new CsvImportBatch(filename));
        List<ImportResultResponse.RowWarning> warnings = detectDuplicates(parsed.rows());

        Set<Long> affectedCategories = new LinkedHashSet<>();
        List<Expense> pending = new ArrayList<>(properties.getBatchSize());
        for (ParsedRow row : parsed.rows()) {
            Expense expense = new Expense(row.date(), row.amount(), row.vendorName(), row.vendorNormalized(),
                    row.description(), row.category(), row.source());
            expense.setImportBatchId(batch.getId());
            pending.add(expense);
            affectedCategories.add(row.category().getId());

            if (pending.size() >= properties.getBatchSize()) {
                flush(pending);
            }
        }
        flush(pending);

        // One sweep per category once every row is in, not one per row.
        anomalyService.reevaluateCategories(affectedCategories);

        String status = parsed.errors().isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        batch.setTotalRows(parsed.totalRows());
        batch.setImportedRows(parsed.rows().size());
        batch.setFailedRows(parsed.errors().size());
        batch.setStatus(status);
        batch.setErrorReport(toJson(parsed.errors()));
        batchRepository.save(batch);

        log.info("CSV import '{}': {} rows, {} imported, {} failed, date format {}",
                filename, parsed.totalRows(), parsed.rows().size(), parsed.errors().size(),
                parsed.dateFormat() == null ? "n/a" : parsed.dateFormat());

        return new ImportResultResponse(batch.getId(), filename, parsed.totalRows(), parsed.rows().size(),
                parsed.errors().size(), status, parsed.errors(), warnings);
    }

    // ---------------------------------------------------------------- parsing

    private ParsedFile parse(MultipartFile file, String filename) {
        CsvValueParser values = new CsvValueParser();
        List<ParsedRow> rows = new ArrayList<>();
        List<ImportResultResponse.RowError> errors = new ArrayList<>();
        LocalDate today = LocalDate.now();

        Map<String, Category> categoriesByName = new HashMap<>();
        Map<Long, Category> categoriesById = new HashMap<>();
        for (Category category : categoryRepository.findAll()) {
            categoriesByName.put(category.getName().toLowerCase(Locale.ROOT), category);
            categoriesById.put(category.getId(), category);
        }

        int totalRows = 0;
        try (Reader reader = openReader(file);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            Map<CsvField, Integer> columns = resolveColumns(parser.getHeaderNames());

            for (CSVRecord record : parser) {
                if (isBlankRecord(record)) {
                    continue;
                }
                totalRows++;
                if (totalRows > properties.getMaxRows()) {
                    throw new CsvParseException("File exceeds the maximum of " + properties.getMaxRows()
                            + " data rows");
                }
                long line = parser.getCurrentLineNumber();
                try {
                    rows.add(toRow(line, record, columns, values, today, categoriesByName, categoriesById));
                } catch (RowFieldException ex) {
                    errors.add(new ImportResultResponse.RowError(line, ex.getField(), ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new CsvParseException("Could not read '" + filename + "': " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new CsvParseException("Could not parse '" + filename + "' as CSV: " + ex.getMessage(), ex);
        }

        return new ParsedFile(totalRows, rows, errors, values.lockedDateFormat());
    }

    private ParsedRow toRow(long line,
                            CSVRecord record,
                            Map<CsvField, Integer> columns,
                            CsvValueParser values,
                            LocalDate today,
                            Map<String, Category> categoriesByName,
                            Map<Long, Category> categoriesById) {
        LocalDate date = values.parseDate(cell(record, columns, CsvField.DATE), today);
        BigDecimal amount = values.parseAmount(cell(record, columns, CsvField.AMOUNT));
        String vendorName = values.parseVendor(cell(record, columns, CsvField.VENDOR));
        String description = values.parseDescription(cell(record, columns, CsvField.DESCRIPTION));
        String normalized = normalizer.normalize(vendorName);

        String categoryCell = cell(record, columns, CsvField.CATEGORY);
        if (categoryCell != null && !categoryCell.isBlank()) {
            Category explicit = categoriesByName.get(categoryCell.trim().toLowerCase(Locale.ROOT));
            if (explicit == null) {
                throw new RowFieldException("category", "Unknown category '" + categoryCell.trim() + "'");
            }
            return new ParsedRow(line, date, amount, vendorName, normalized, description, explicit,
                    CategorizationSource.MANUAL_OVERRIDE);
        }

        CategorizationResult result = categorizationService.categorizeNormalized(normalized);
        Category category = categoriesById.get(result.categoryId());
        if (category == null) {
            throw new RowFieldException("category", "Category " + result.categoryId() + " no longer exists");
        }
        return new ParsedRow(line, date, amount, vendorName, normalized, description, category, result.source());
    }

    private Map<CsvField, Integer> resolveColumns(List<String> headers) {
        Map<CsvField, Integer> columns = new EnumMap<>(CsvField.class);
        for (int i = 0; i < headers.size(); i++) {
            CsvField field = CsvField.resolve(headers.get(i));
            if (field != null) {
                columns.putIfAbsent(field, i);
            }
        }
        List<String> missing = new ArrayList<>();
        for (CsvField field : CsvField.values()) {
            if (field.isRequired() && !columns.containsKey(field)) {
                missing.add(field.name().toLowerCase(Locale.ROOT) + " (accepted: "
                        + String.join(", ", field.aliases()) + ")");
            }
        }
        if (!missing.isEmpty()) {
            throw new CsvParseException("Missing required column(s): " + String.join("; ", missing)
                    + ". Found header: " + String.join(", ", headers));
        }
        return columns;
    }

    private static String cell(CSVRecord record, Map<CsvField, Integer> columns, CsvField field) {
        Integer index = columns.get(field);
        if (index == null || index >= record.size()) {
            return null;
        }
        return record.get(index);
    }

    private static boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Strips a UTF-8 BOM, which Excel writes and which would otherwise corrupt the first header. */
    private static Reader openReader(MultipartFile file) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        reader.mark(1);
        int first = reader.read();
        if (first != BOM) {
            reader.reset();
        }
        return reader;
    }

    // ------------------------------------------------------------- persistence

    private void flush(List<Expense> pending) {
        if (pending.isEmpty()) {
            return;
        }
        expenseRepository.saveAll(pending);
        expenseRepository.flush();
        pending.clear();
    }

    /**
     * Flags rows that look like an expense already on file, and rows that repeat within the
     * upload itself. One range query for the whole file rather than a lookup per row.
     */
    private List<ImportResultResponse.RowWarning> detectDuplicates(List<ParsedRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate min = rows.get(0).date();
        LocalDate max = rows.get(0).date();
        for (ParsedRow row : rows) {
            if (row.date().isBefore(min)) {
                min = row.date();
            }
            if (row.date().isAfter(max)) {
                max = row.date();
            }
        }

        Set<String> existing = new HashSet<>();
        for (ExpenseRepository.DuplicateKeyRow key : expenseRepository.findKeysInRange(min, max)) {
            existing.add(duplicateKey(key.getExpenseDate(), key.getAmount(), key.getVendorNormalized()));
        }

        List<ImportResultResponse.RowWarning> warnings = new ArrayList<>();
        Set<String> withinFile = new HashSet<>();
        for (ParsedRow row : rows) {
            String key = duplicateKey(row.date(), row.amount(), row.vendorNormalized());
            if (existing.contains(key)) {
                warnings.add(new ImportResultResponse.RowWarning(row.line(),
                        "Matches an expense already on file (" + row.vendorName() + ", " + row.date() + ")"));
            } else if (!withinFile.add(key)) {
                warnings.add(new ImportResultResponse.RowWarning(row.line(),
                        "Repeats an earlier row in this file (" + row.vendorName() + ", " + row.date() + ")"));
            }
        }
        return warnings;
    }

    private static String duplicateKey(LocalDate date, BigDecimal amount, String vendorNormalized) {
        return date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + vendorNormalized;
    }

    private String toJson(List<ImportResultResponse.RowError> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize the import error report", ex);
            return null;
        }
    }

    private static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "upload.csv";
        }
        // Only the leaf name is kept - the browser-supplied path is not trusted.
        String leaf = original.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        return leaf.length() > 200 ? leaf.substring(0, 200) : leaf;
    }

    private record ParsedRow(long line,
                             LocalDate date,
                             BigDecimal amount,
                             String vendorName,
                             String vendorNormalized,
                             String description,
                             Category category,
                             CategorizationSource source) {
    }

    private record ParsedFile(int totalRows,
                              List<ParsedRow> rows,
                              List<ImportResultResponse.RowError> errors,
                              String dateFormat) {
    }
}
