package com.iconcile.expense.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iconcile.expense.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ExpenseApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private long createExpense(String date, String amount, String vendor) throws Exception {
        String response = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", date, "amount", amount, "vendorName", vendor))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("POST creates an expense, categorizes it, and returns a Location header")
    void createsAndCategorizes() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "date", "2026-06-01",
                                "amount", "450.00",
                                "vendorName", "Swiggy",
                                "description", "Team lunch"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/expenses/")))
                .andExpect(jsonPath("$.amount", is("450.00")))
                .andExpect(jsonPath("$.category.name", is("Food")))
                .andExpect(jsonPath("$.categorizationSource", is("RULE")))
                .andExpect(jsonPath("$.isAnomaly", is(false)));
    }

    @Test
    @DisplayName("an unrecognised vendor falls back to Uncategorized")
    void unknownVendorFallsBack() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", "2026-06-01", "amount", "99.00", "vendorName", "Corner Shop"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.name", is("Uncategorized")))
                .andExpect(jsonPath("$.categorizationSource", is("DEFAULT")));
    }

    @Test
    @DisplayName("an explicit categoryId overrides the rules")
    void explicitCategoryOverridesRules() throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long travelId = -1;
        for (var node : objectMapper.readTree(categories)) {
            if ("Travel".equals(node.get("name").asText())) {
                travelId = node.get("id").asLong();
            }
        }

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "date", "2026-06-01", "amount", "450.00",
                                "vendorName", "Swiggy", "categoryId", travelId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.name", is("Travel")))
                .andExpect(jsonPath("$.categorizationSource", is("MANUAL_OVERRIDE")));
    }

    @Test
    @DisplayName("invalid payloads return 400 with per-field messages")
    void validationFailureNamesTheFields() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":null,\"amount\":\"-5\",\"vendorName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.path", is("/api/expenses")));
    }

    @Test
    @DisplayName("an amount with three decimals is refused rather than rounded")
    void rejectsOverPreciseAmounts() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", "2026-06-01", "amount", "10.005", "vendorName", "Swiggy"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field", is("amount")));
    }

    @Test
    @DisplayName("a future-dated expense is refused, matching what CSV import already enforced")
    void rejectsFutureDates() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", tomorrow, "amount", "100.00", "vendorName", "Swiggy"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("date")));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().toString(), "amount", "100.00",
                                "vendorName", "Swiggy"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a year typo before 2000 is refused, so it cannot drag every date range backwards")
    void rejectsAncientDates() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", "1999-12-31", "amount", "100.00", "vendorName", "Swiggy"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("the update path enforces the same date rules as create")
    void updateEnforcesDateRules() throws Exception {
        long id = createExpense("2026-06-01", "450.00", "Swiggy");

        mockMvc.perform(put("/api/expenses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", LocalDate.now().plusDays(1).toString(),
                                "amount", "450.00", "vendorName", "Swiggy"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field", is("date")));
    }

    @Test
    @DisplayName("a missing expense returns the standard 404 body")
    void missingExpenseReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/expenses/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("PUT updates and DELETE removes")
    void updatesAndDeletes() throws Exception {
        long id = createExpense("2026-06-01", "450.00", "Swiggy");

        mockMvc.perform(put("/api/expenses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", "2026-06-02", "amount", "500.00", "vendorName", "Zomato"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is("500.00")))
                .andExpect(jsonPath("$.vendorName", is("Zomato")));

        mockMvc.perform(delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/expenses/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the list endpoint filters, pages and reports totals")
    void listFiltersAndPages() throws Exception {
        createExpense("2026-06-01", "100.00", "Swiggy");
        createExpense("2026-06-02", "200.00", "Uber");
        createExpense("2026-07-01", "300.00", "Swiggy");

        mockMvc.perform(get("/api/expenses").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.hasNext", is(true)));

        mockMvc.perform(get("/api/expenses").param("vendor", "swiggy"))
                .andExpect(jsonPath("$.totalElements", is(2)));

        mockMvc.perform(get("/api/expenses").param("from", "2026-07-01"))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("an unknown sort key is refused instead of being passed through to the query")
    void rejectsUnknownSortKeys() throws Exception {
        mockMvc.perform(get("/api/expenses").param("sort", "password,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("anomalyOnly narrows the list to flagged rows")
    void filtersToAnomalies() throws Exception {
        createExpense("2026-06-01", "100.00", "Swiggy");
        createExpense("2026-06-02", "100.00", "Swiggy");
        createExpense("2026-06-03", "100.00", "Swiggy");
        createExpense("2026-06-04", "1000.00", "Swiggy");

        mockMvc.perform(get("/api/expenses").param("anomalyOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].amount", is("1000.00")))
                .andExpect(jsonPath("$.content[0].isAnomaly", is(true)));
    }

    @Test
    @DisplayName("CSV upload returns the import report")
    void csvUploadReturnsReport() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                """
                date,amount,vendor
                2026-06-01,450.00,Swiggy
                2026-06-02,oops,Uber
                """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/expenses/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED_WITH_ERRORS")))
                .andExpect(jsonPath("$.importedRows", is(1)))
                .andExpect(jsonPath("$.failedRows", is(1)))
                .andExpect(jsonPath("$.errors[0].field", is("amount")));
    }

    @Test
    @DisplayName("a file missing a required column returns 422, not 500")
    void unusableCsvReturnsUnprocessable() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "date,description\n2026-06-01,Lunch\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/expenses/import").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("CSV_UNPROCESSABLE")));
    }

    @Test
    @DisplayName("the import format endpoint documents what the parser actually accepts")
    void exposesImportFormat() throws Exception {
        mockMvc.perform(get("/api/expenses/import/format"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateHeader", is("date,amount,vendor,description")))
                .andExpect(jsonPath("$.requiredColumns", hasSize(3)))
                .andExpect(jsonPath("$.acceptedDateFormats", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("dashboard endpoints reject malformed month parameters")
    void rejectsMalformedMonth() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").param("month", "August"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("yyyy-MM")));
    }

    @Test
    @DisplayName("dashboard endpoints return their documented shapes")
    void dashboardShapes() throws Exception {
        createExpense("2026-06-01", "100.00", "Swiggy");
        createExpense("2026-06-02", "900.00", "Uber");

        mockMvc.perform(get("/api/dashboard/summary").param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount", is("1000.00")))
                .andExpect(jsonPath("$.expenseCount", is(2)));

        mockMvc.perform(get("/api/dashboard/monthly-by-category").param("from", "2026-05").param("to", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(2)));

        mockMvc.perform(get("/api/dashboard/top-vendors").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendors", hasSize(2)));

        mockMvc.perform(get("/api/dashboard/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }
}
