Assignment: Build a Mini Expense Manager
Please create a small full-stack application that allows users to track their daily expenses. The app should include the following features:



Core Features
Add Expense Manually
Fields: Date, Amount, Vendor Name, Description
Automatically assign a category based on vendor name (e.g., “Swiggy” → “Food”)
Upload via CSV
Support uploading a CSV file containing multiple expense entries
Parse and save these entries in the backend
Rule-Based Categorization
Maintain a vendor-to-category mapping
Apply the mapping automatically during expense entry
Anomaly Detection
If an expense is more than 3× the average amount for its category, flag it as an anomaly
Display these flagged anomalies distinctly in the UI
Dashboard Summary
Show monthly totals per category
Display top 5 vendors by total spend
Count and list anomalies
Preferred Tech Stack

Frontend: React + TypeScript
Backend: Java Spring Boot
Database: PostgreSQL
