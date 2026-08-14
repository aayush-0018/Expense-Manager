package com.iconcile.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /** Money is NUMERIC(14,2) / BigDecimal end to end - never a floating point type. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    /** Lowercased/cleaned form used for rule matching and vendor grouping. */
    @Column(name = "vendor_normalized", nullable = false)
    private String vendorNormalized;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "categorization_source", nullable = false)
    private CategorizationSource categorizationSource;

    /** Materialized by AnomalyService; never set directly by callers. */
    @Column(name = "is_anomaly", nullable = false)
    private boolean anomaly;

    @Column(name = "anomaly_reason")
    private String anomalyReason;

    @Column(name = "anomaly_evaluated_at")
    private OffsetDateTime anomalyEvaluatedAt;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Expense() {
    }

    public Expense(LocalDate expenseDate,
                   BigDecimal amount,
                   String vendorName,
                   String vendorNormalized,
                   String description,
                   Category category,
                   CategorizationSource categorizationSource) {
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.vendorName = vendorName;
        this.vendorNormalized = vendorNormalized;
        this.description = description;
        this.category = category;
        this.categorizationSource = categorizationSource;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorNormalized() {
        return vendorNormalized;
    }

    public void setVendorNormalized(String vendorNormalized) {
        this.vendorNormalized = vendorNormalized;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public CategorizationSource getCategorizationSource() {
        return categorizationSource;
    }

    public void setCategorizationSource(CategorizationSource categorizationSource) {
        this.categorizationSource = categorizationSource;
    }

    public boolean isAnomaly() {
        return anomaly;
    }

    public String getAnomalyReason() {
        return anomalyReason;
    }

    public OffsetDateTime getAnomalyEvaluatedAt() {
        return anomalyEvaluatedAt;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
