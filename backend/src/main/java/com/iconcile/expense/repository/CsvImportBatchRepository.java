package com.iconcile.expense.repository;

import com.iconcile.expense.domain.CsvImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvImportBatchRepository extends JpaRepository<CsvImportBatch, Long> {
}
