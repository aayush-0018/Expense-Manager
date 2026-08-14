package com.iconcile.expense.web.controller;

import com.iconcile.expense.service.ExpenseService;
import com.iconcile.expense.web.dto.ExpenseRequest;
import com.iconcile.expense.web.dto.ExpenseResponse;
import com.iconcile.expense.web.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Sortable columns. A whitelist rather than passing the raw parameter through: an
     * unrestricted sort key is a way to probe the entity model and to force expensive scans.
     */
    private static final Set<String> SORTABLE = Set.of(
            "expenseDate", "amount", "vendorName", "createdAt", "anomaly");

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String vendor,
            @RequestParam(defaultValue = "false") boolean anomalyOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "expenseDate,desc") String sort) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), parseSort(sort));
        return PageResponse.of(
                expenseService.search(from, to, categoryId, vendor, anomalyOnly, pageable),
                ExpenseResponse::from);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable Long id) {
        return ExpenseResponse.from(expenseService.get(id));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse created = ExpenseResponse.from(expenseService.create(request));
        return ResponseEntity.created(URI.create("/api/expenses/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return ExpenseResponse.from(expenseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            throw new IllegalArgumentException("Cannot sort by '" + property + "'; allowed: " + SORTABLE);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // Secondary key on id keeps paging stable when two rows share a sort value.
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
