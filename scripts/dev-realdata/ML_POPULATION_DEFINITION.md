# Libris ML Population Definition

## Purpose

Defines the exact population of loans eligible for ML model training and evaluation.
This ensures consistency between the frozen synthetic benchmark and future real-data evaluation.

## ML Population

### Inclusion Criteria

A loan is eligible for the ML dataset if ALL of the following are true:

1. **Completed loan**: `return_date IS NOT NULL`
2. **Has student**: `student_id IS NOT NULL`
3. **Has item reference**: Exactly one of `book_id`, `magazine_id`, `newspaper_id` is non-null

### Exclusion Criteria

| Exclusion | Reason |
|---|---|
| Active loans (`return_date IS NULL`) | Label cannot be computed — item not yet returned |
| Walk-in loans (`student_id IS NULL`) | Cannot compute borrower-history features |
| Missing item reference | Cannot determine item type or category |

### Label Definition

```
label = 1  IF  return_date > COALESCE(due_date, borrow_date + 14 days)
label = 0  OTHERWISE
```

This matches the canonical `OverdueCalculator.effectiveDueDate()` semantics exactly.

## Feature Requirements

### Required Fields per Loan

| Feature | Required Source Fields |
|---|---|
| priorLoanCount | `student_id`, `borrow_date` |
| priorOverdueCount | `student_id`, `borrow_date`, `return_date`, `due_date` |
| historicalOverdueRate | Derived from above two |
| avgDaysToReturn | `student_id`, `borrow_date`, `return_date` |
| preferredCategory | Item's `category` column (see category mapping) |
| daysSinceLastBorrow | `student_id`, `borrow_date` |
| borrowDayOfWeek | `borrow_date` |
| isWeekendBorrow | `borrow_date` |
| semesterFactor | `borrow_date` |
| loanDuration | `borrow_date`, `due_date` |
| itemPopularityScore | Item FK, `borrow_date` |
| categoryOverdueRate | Item's `category`, `borrow_date`, overdue status |

### Category Mapping Strategy

| Item Type | Category Source | Fallback |
|---|---|---|
| Books | `books.category` (V4, nullable) | `'Uncategorized'` |
| Magazines | `magazines.category` (V1, nullable) | `'Uncategorized'` |
| Newspapers | No category column | `'Newspaper'` (item type as category) |

### Due Date Handling

| Scenario | Effective Due Date |
|---|---|
| `due_date IS NOT NULL` | `due_date` |
| `due_date IS NULL` | `borrow_date + 14 days` |

This matches `OverdueCalculator.effectiveDueDate()` exactly.

## Point-in-Time Requirement

All features must be computed using ONLY records where:
```
record.borrow_date < current_loan.borrow_date
```

This means:
- Borrower history features: only count prior loans for the same student
- Item popularity features: only count prior borrows of the same item
- Category overdue rates: only use prior loans in the same category
- The current loan's own data is NOT used to compute its features

## Temporal Split

### Principle

Train on earlier loans, validate on later loans. Never the reverse.

### Split Boundary

- **NOT fixed** — must be determined from the real data's temporal range
- Must ensure sufficient samples in both train and validation
- Must ensure at least 6 months of validation data for robustness analysis
- Boundary must be documented BEFORE training

### Recommended Approach

1. Determine the earliest and latest `borrow_date` in the eligible population
2. Choose a split point that gives ~80% train / ~20% validation
3. Ensure the validation period has at least 6 complete months
4. Document the split boundary in the model specification

## Data Sufficiency Thresholds

| Metric | Minimum | Ideal |
|---|---|---|
| Eligible completed loans | 500 | 2,000+ |
| Distinct borrowers | 100 | 500+ |
| Months with data | 6 | 12+ |
| Overdue examples | 50 | 200+ |
| On-time examples | 50 | 500+ |
| Borrowers with >=5 loans | 50 | 200+ |
| Category coverage | 80% | 95%+ |
| Due date coverage | 90% | 99%+ |

## Monitoring

Run `readiness_monitor.py` periodically to check data sufficiency:

```bash
python3 scripts/dev-realdata/readiness_monitor.py --host HOST --port PORT --db DB
```

When all readiness checks pass, proceed to Phase 5C (real-data PIT dataset construction).

## Frozen Benchmark Reference

| Metric | Synthetic Value |
|---|---|
| ROC-AUC | 0.735 |
| PR-AUC | 0.617 |
| F1 @ threshold 0.25 | 0.602 |
| Temporal F1 CV | 0.067 |

Real-data performance should be compared against these frozen benchmarks.
