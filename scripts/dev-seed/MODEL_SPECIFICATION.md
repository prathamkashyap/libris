# Libris Overdue-Risk Model — Specification v1.0

## Model Identity

| Field | Value |
|---|---|
| Name | Libris Overdue-Risk Predictor |
| Version | 1.0.0-dev |
| Algorithm | Logistic Regression (L2 regularized) |
| Status | Development prototype — validated on synthetic data |
| Date | 2026-09-13 |
| Validation ROC-AUC | 0.735 |
| Validation PR-AUC | 0.617 |
| Validation F1 | 0.602 (threshold=0.25) |

---

## Intended Use

Predict whether a library book loan will become overdue before its due date. The model produces a risk score [0, 1] for each active loan, enabling librarians to prioritize interventions.

**This model is a development prototype.** It has been validated on synthetic data only. It does not establish real-world predictive performance.

---

## Model Architecture

### Algorithm

Logistic Regression with L2 regularization (λ=0.01).

### Preprocessing

1. **Numeric features**: Standardized (zero mean, unit variance). Parameters fit on training set only.
2. **Categorical features**: One-hot encoded. Categories fit on training set only.

### Training

- Optimizer: Gradient descent
- Learning rate: 0.1
- Iterations: 2,000
- Regularization: L2 (λ=0.01)

### Input Features (12 total)

| # | Feature | Type | Description | Point-in-Time Safe |
|---|---|---|---|---|
| 1 | `priorLoanCount` | Numeric | Number of completed loans before this one | Yes |
| 2 | `priorOverdueCount` | Numeric | Number of overdue loans before this one | Yes |
| 3 | `historicalOverdueRate` | Numeric | priorOverdueCount / priorLoanCount (0 for first-time) | Yes |
| 4 | `avgDaysToReturn` | Numeric | Average days to return for prior loans (-1 if none) | Yes |
| 5 | `preferredCategory` | Categorical | Most-borrowed item category (Unknown if first-time) | Yes |
| 6 | `daysSinceLastBorrow` | Numeric | Days since borrower's last checkout (-1 if none) | Yes |
| 7 | `borrowDayOfWeek` | Numeric | Day of week (0=Sun, 6=Sat) | Yes |
| 8 | `isWeekendBorrow` | Numeric | 1 if Sat/Sun, 0 otherwise | Yes |
| 9 | `semesterFactor` | Numeric | Academic period factor (0.6–1.0) | Yes |
| 10 | `loanDuration` | Numeric | Planned loan duration in days (due_date − borrow_date) | Yes |
| 11 | `itemPopularityScore` | Numeric | Number of prior borrows of this item | Yes |
| 12 | `categoryOverdueRate` | Numeric | Borrower-specific overdue rate for this item's category, computed from the borrower's prior loans in that category only (PIT-safe) | Yes |

### Feature Classification

| Category | Features |
|---|---|
| **Borrower behavior** | priorLoanCount, priorOverdueCount, historicalOverdueRate, avgDaysToReturn, daysSinceLastBorrow |
| **Policy/context** | loanDuration, semesterFactor, borrowDayOfWeek, isWeekendBorrow |
| **Item/ catalog** | preferredCategory, itemPopularityScore, categoryOverdueRate (borrower-specific) |

**Note on `loanDuration`**: This is a librarian-assigned planned duration, not borrower behavior. Removing it drops ROC-AUC by 0.066. It reflects checkout policy rather than intrinsic borrower risk.

---

## Output

### Risk Score

Logistic Regression output in `[0, 1]`, interpreted as relative estimated overdue risk. Higher values indicate higher predicted overdue probability. Formal probability calibration has not been independently established.

### Risk Tiers

| Tier | Score Range | Interpretation |
|---|---|---|
| LOW | [0.00, 0.35) | Low overdue risk |
| MEDIUM | [0.35, 0.55) | Moderate overdue risk |
| HIGH | [0.55, 1.00] | High overdue risk |

### Observed Overdue Rates by Tier (Validation)

| Tier | Count | Observed Overdue |
|---|---|---|
| LOW | 1,275 | 24.0% |
| MEDIUM | 581 | 52.0% |
| HIGH | 215 | 72.6% |

---

## Operating Points

### Default: Balanced (Threshold 0.25)

| Metric | Value |
|---|---|
| Threshold | 0.25 |
| Flagged | 1,205 / 2,071 (58.2%) |
| Precision | 49.2% |
| Recall | 77.6% |
| F1 | 0.602 |
| Flagged per overdue caught | 2.03 |

### Alternative: High-Recall (Threshold 0.20)

| Metric | Value |
|---|---|
| Threshold | 0.20 |
| Flagged | 1,465 / 2,071 (70.7%) |
| Precision | 45.3% |
| Recall | 86.8% |
| Flagged per overdue caught | 2.21 |

### Alternative: High-Precision (Threshold 0.50)

| Metric | Value |
|---|---|
| Threshold | 0.50 |
| Flagged | 326 / 2,071 (15.7%) |
| Precision | 71.2% |
| Recall | 30.4% |
| Flagged per overdue caught | 1.12 |

---

## Performance Summary

### Overall (Validation Set, n=2,071)

| Metric | Value |
|---|---|
| ROC-AUC | 0.735 |
| PR-AUC | 0.617 |
| Majority-class accuracy | 63.1% |

### Temporal Robustness (Complete Months, Oct 2025–Jun 2026)

| Metric | Min | Max | Mean | CV |
|---|---|---|---|---|
| F1 | 0.541 | 0.675 | 0.603 | 0.067 |
| ROC-AUC | 0.677 | 0.825 | 0.736 | — |
| PR-AUC | 0.531 | 0.736 | 0.633 | — |

### Subgroup Performance (Threshold 0.25)

**By borrower history:**

| Group | n | F1 | ROC-AUC |
|---|---|---|---|
| 1-4 prior loans | 256 | 0.765 | 0.700 |
| 5-9 prior loans | 975 | 0.592 | 0.699 |
| 10+ prior loans | 840 | 0.498 | 0.724 |

**By item type:**

| Type | n | F1 | ROC-AUC |
|---|---|---|---|
| Books | 1,862 | 0.610 | 0.742 |
| Magazines | 135 | 0.551 | 0.657 |
| Newspapers | 74 | 0.484 | 0.698 |

**By category (best/worst):**

| Category | n | F1 |
|---|---|---|
| Biography (best) | 95 | 0.703 |
| Literature (worst) | 101 | 0.523 |

---

## Feature Importance (Top 5)

| Rank | Feature | Coefficient | Direction |
|---|---|---|---|
| 1 | loanDuration | -0.802 | shorter → more overdue |
| 2 | cat_Unknown | +0.413 | first-time borrower effect |
| 3 | priorLoanCount | -0.400 | fewer loans → higher risk |
| 4 | avgDaysToReturn | +0.210 | slower returners → higher risk |
| 5 | priorOverdueCount | +0.178 | more prior overdue → higher risk |

**Note on `cat_Unknown`**: This coefficient was present in training but absent from the validation partition (0 val rows had this category). Its importance should not be interpreted as validated predictive importance. It captures the first-time borrower effect in training data only.

---

## Limitations

### Data Limitations

1. **Synthetic data only**: All performance metrics are based on simulated development data. Real-world performance may differ materially.
2. **No demographic data**: The model does not use age, gender, or other protected attributes. Subgroup analysis is limited to borrower history, item type, and category.
3. **Category coverage**: `preferredCategory` is `Unknown` for first-time borrowers (no prior history to determine preference).

### Model Limitations

1. **`loanDuration` dependency**: The strongest feature reflects librarian-assigned checkout policy, not borrower behavior. Performance drops 0.066 ROC-AUC without it.
2. **First-time borrowers**: The model has limited signal for borrowers with no prior history. First-time borrowers receive a default risk score based on category-level patterns.
3. **Linear model**: Logistic Regression assumes linear decision boundaries. Non-linear interactions between features are not captured.

### Evaluation Limitations

1. **Temporal split only**: No cross-validation or out-of-sample testing beyond the temporal split.
2. **Single seed**: Results are based on random seed=42. Variance across seeds is not quantified.
3. **No calibration tuning**: Observed overdue rates in risk bins show some deviation from predicted probabilities.

---

## Provenance

| Component | Source |
|---|---|
| Training data | `scripts/dev-seed/output/features_train.csv` (7,261 loans) |
| Validation data | `scripts/dev-seed/output/features_val.csv` (2,071 loans) |
| Feature builder | `scripts/dev-seed/phase4a_features.py` |
| Model trainer | `scripts/dev-seed/phase4c_lr.py` |
| Evaluation | `scripts/dev-seed/phase4d_ablation.py`, `phase4e_robustness.py` |
| Model config | L2=0.01, lr=0.1, iter=2000, threshold=0.25 |

### Reproducibility

```bash
# Regenerate synthetic data
cd scripts/dev-seed
python3 seed_generator.py

# Rebuild features
python3 phase4a_features.py

# Retrain and evaluate
python3 phase4c_lr.py
python3 phase4d_ablation.py
python3 phase4e_robustness.py
```

---

## Decision Record

| Date | Decision | Rationale |
|---|---|---|
| 2026-09-13 | LR selected as primary model | +15% ROC-AUC over heuristic, interpretable, stable |
| 2026-09-13 | Threshold 0.25 selected as default | Best F1 (0.602), 77.6% recall |
| 2026-09-13 | No XGBoost added | Marginal benefit not justified on synthetic data |
| 2026-09-13 | loanDuration retained | -0.066 ROC-AUC drop without it; documented as policy feature |
| 2026-09-19 | categoryOverdueRate clarified as borrower-specific | Implementation computes rate from borrower's prior loans in the same category, not global category rate; spec updated to match |
| 2026-09-19 | current_active_loans excluded | Not in original feature set; information subsumed by priorLoanCount and daysSinceLastBorrow; no implementation gap |

---

## Next Steps

1. **When real historical data becomes available:**
   - Re-run the same point-in-time feature builder
   - Evaluate LR against real data
   - Compare against heuristic baseline
   - Consider XGBoost only if LR underperforms on real data

2. **Before production deployment:**
   - Validate on real library data
   - Confirm threshold with library staff
   - Integrate risk scoring into loan checkout workflow
   - Add monitoring for model drift

3. **Documentation gaps:**
   - Library workflow integration design
   - Staff intervention protocol
   - Model retraining schedule
   - A/B testing plan
