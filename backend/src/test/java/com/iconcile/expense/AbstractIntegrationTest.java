package com.iconcile.expense;

import com.iconcile.expense.repository.CsvImportBatchRepository;
import com.iconcile.expense.repository.ExpenseRepository;
import com.iconcile.expense.service.CategorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p>PostgreSQL rather than an in-memory stand-in because the parts most worth testing are the
 * parts H2 does not reproduce: {@code date_trunc}, {@code FILTER (WHERE ...)},
 * {@code mode() WITHIN GROUP}, and the {@code UPDATE ... FROM} that drives anomaly detection.
 *
 * <p>Point it at a scratch database with {@code TEST_DATASOURCE_URL}; it defaults to
 * {@code expenses_test} on localhost. Data is wiped before each test rather than rolled back,
 * because the anomaly sweep runs as its own statement and the assertions need to see committed
 * state.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected ExpenseRepository expenseRepository;

    @Autowired
    protected CsvImportBatchRepository csvImportBatchRepository;

    @Autowired
    protected CategorizationService categorizationService;

    @BeforeEach
    void resetDatabase() {
        expenseRepository.deleteAllInBatch();
        csvImportBatchRepository.deleteAllInBatch();
        categorizationService.refresh();
    }
}
