package com.iconcile.expense.repository;

import com.iconcile.expense.domain.Expense;
import com.iconcile.expense.repository.projection.DashboardProjections.CategoryStatsRow;
import com.iconcile.expense.repository.projection.DashboardProjections.CategoryTotalRow;
import com.iconcile.expense.repository.projection.DashboardProjections.MonthlyCategoryTotalRow;
import com.iconcile.expense.repository.projection.DashboardProjections.SummaryRow;
import com.iconcile.expense.repository.projection.DashboardProjections.VendorTotalRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /**
     * Re-evaluates the anomaly flag for every expense in one category, in a single
     * set-based statement.
     *
     * <p>The baseline is a <em>leave-one-out</em> average: each expense is compared against the
     * mean of the <em>other</em> expenses in its category, so a large expense cannot inflate its
     * own baseline and hide itself. A category needs at least {@code minSampleSize} other
     * expenses before anything is flagged, otherwise a two-row category flags on noise.
     *
     * <p>{@code NULLIF} guards the divide when a category holds a single expense: the division
     * yields NULL, and {@code FALSE AND NULL} is FALSE, so nothing is flagged.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH stats AS (
                SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS cnt
                FROM expense
                WHERE category_id = :categoryId
                  AND expense_date >= :sinceDate
            ), calc AS (
                SELECT e2.id AS eid,
                       COALESCE(
                           (s.cnt - 1) >= :minSampleSize
                           AND e2.amount > :multiplier * ((s.total - e2.amount) / NULLIF(s.cnt - 1, 0)),
                           false
                       ) AS new_flag
                FROM expense e2
                CROSS JOIN stats s
                WHERE e2.category_id = :categoryId
                  AND e2.expense_date >= :sinceDate
            )
            UPDATE expense e
            SET is_anomaly = calc.new_flag,
                anomaly_reason = CASE WHEN calc.new_flag THEN 'AMOUNT_GT_3X_CATEGORY_AVG' ELSE NULL END,
                anomaly_evaluated_at = now()
            FROM calc
            WHERE e.id = calc.eid
              AND (e.is_anomaly IS DISTINCT FROM calc.new_flag OR e.anomaly_evaluated_at IS NULL)
            """, nativeQuery = true)
    int reevaluateAnomaliesForCategory(@Param("categoryId") Long categoryId,
                                       @Param("multiplier") BigDecimal multiplier,
                                       @Param("minSampleSize") int minSampleSize,
                                       @Param("sinceDate") LocalDate sinceDate);

    @Query(value = """
            SELECT COALESCE(SUM(e.amount), 0)                        AS "totalAmount",
                   COUNT(*)                                          AS "expenseCount",
                   COUNT(*) FILTER (WHERE e.is_anomaly)              AS "anomalyCount"
            FROM expense e
            WHERE e.expense_date BETWEEN :fromDate AND :toDate
            """, nativeQuery = true)
    SummaryRow summarize(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query(value = """
            SELECT to_char(date_trunc('month', e.expense_date), 'YYYY-MM') AS "yearMonth",
                   c.id                                                    AS "categoryId",
                   c.name                                                  AS "categoryName",
                   c.color_hex                                             AS "colorHex",
                   SUM(e.amount)                                           AS "totalAmount"
            FROM expense e
            JOIN category c ON c.id = e.category_id
            WHERE e.expense_date BETWEEN :fromDate AND :toDate
            GROUP BY 1, c.id, c.name, c.color_hex
            ORDER BY 1 ASC, c.name ASC
            """, nativeQuery = true)
    List<MonthlyCategoryTotalRow> monthlyTotalsByCategory(@Param("fromDate") LocalDate fromDate,
                                                          @Param("toDate") LocalDate toDate);

    /**
     * Vendors are grouped on {@code vendor_normalized} so that "SWIGGY", "Swiggy " and
     * "swiggy" collapse into one bar; the label shown is the most common raw spelling.
     */
    @Query(value = """
            SELECT mode() WITHIN GROUP (ORDER BY e.vendor_name) AS "vendorName",
                   mode() WITHIN GROUP (ORDER BY c.name)        AS "topCategory",
                   SUM(e.amount)                                AS "totalAmount",
                   COUNT(*)                                     AS "expenseCount"
            FROM expense e
            JOIN category c ON c.id = e.category_id
            WHERE e.expense_date BETWEEN :fromDate AND :toDate
            GROUP BY e.vendor_normalized
            ORDER BY SUM(e.amount) DESC, COUNT(*) DESC, e.vendor_normalized ASC
            LIMIT :maxResults
            """, nativeQuery = true)
    List<VendorTotalRow> topVendorsBySpend(@Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate,
                                           @Param("maxResults") int maxResults);

    @Query(value = """
            SELECT e.category_id  AS "categoryId",
                   SUM(e.amount)  AS "totalAmount",
                   COUNT(*)       AS "expenseCount"
            FROM expense e
            WHERE e.expense_date >= :sinceDate
            GROUP BY e.category_id
            """, nativeQuery = true)
    List<CategoryStatsRow> categoryStats(@Param("sinceDate") LocalDate sinceDate);

    @Query(value = """
            SELECT c.id           AS "categoryId",
                   c.name         AS "categoryName",
                   SUM(e.amount)  AS "totalAmount"
            FROM expense e
            JOIN category c ON c.id = e.category_id
            WHERE e.expense_date BETWEEN :fromDate AND :toDate
            GROUP BY c.id, c.name
            ORDER BY SUM(e.amount) DESC
            """, nativeQuery = true)
    List<CategoryTotalRow> categoryTotals(@Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    /**
     * Identity keys for existing expenses inside a date window, used to warn about likely
     * duplicates during CSV import. One query for the whole file rather than one per row.
     */
    @Query("""
           SELECT e.id AS id,
                  e.expenseDate AS expenseDate,
                  e.amount AS amount,
                  e.vendorNormalized AS vendorNormalized
           FROM Expense e
           WHERE e.expenseDate BETWEEN :fromDate AND :toDate
           """)
    List<DuplicateKeyRow> findKeysInRange(@Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    /** Projection for {@link #findKeysInRange}. */
    interface DuplicateKeyRow {
        Long getId();

        LocalDate getExpenseDate();

        BigDecimal getAmount();

        String getVendorNormalized();
    }
}
