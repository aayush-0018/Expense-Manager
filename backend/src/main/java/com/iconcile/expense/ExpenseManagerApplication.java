package com.iconcile.expense;

import com.iconcile.expense.config.AnomalyProperties;
import com.iconcile.expense.config.CsvImportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AnomalyProperties.class, CsvImportProperties.class})
public class ExpenseManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseManagerApplication.class, args);
    }
}
