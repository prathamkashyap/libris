#!/usr/bin/env python3
"""
Phase 4F — Probability Calibration for Libris overdue-risk prediction (EXPERIMENTAL/DEFERRED).

Applies Platt (sigmoid) calibration to the Phase 4C logistic regression model.
Uses a chronological split within the training period:
  - model training: borrow_date < CALIBRATION_CUTOFF
  - calibration fitting: CALIBRATION_CUTOFF <= borrow_date < TRAIN_CUTOFF
  - evaluation: borrow_date >= TRAIN_CUTOFF (original validation set)

The final temporal test set is never used to fit calibration parameters.

EXPERIMENTAL STATUS (2026-09-19):
This calibration method was evaluated on synthetic development data and DEGRADED
probability quality metrics (Brier score: 0.1991→0.2307, log loss: 0.5797→0.6539).
Calibration is currently DEFERRED pending real-world circulation data and
operational probability requirements. See MODEL_SPECIFICATION.md for detailed results.

This script is retained for research/educational purposes but is NOT part of
the current production pipeline. Use the uncalibrated logistic regression
probabilities from Phase 4C as the current baseline.

Exports:
  - phase4f_report.txt (calibration evaluation report)

No production code, schema, tests, or frontend are modified.
"""
import csv
import math
import os
from collections import defaultdict
from datetime import date, datetime

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

TRAIN_CUTOFF = date(2025, 10, 1)
CALIBRATION_CUTOFF = date(2025, 7, 1)

NUMERIC_COLS = [
    "priorLoanCount", "priorOverdueCount", "historicalOverdueRate",
    "avgDaysToReturn", "daysSinceLastBorrow", "borrowDayOfWeek",
    "isWeekendBorrow", "semesterFactor", "loanDuration",
    "itemPopularityScore", "categoryOverdueRate",
]
CAT_COL = "preferredCategory"


def load_csv(filename):
    filepath = os.path.join(OUTPUT_DIR, filename)
    with open(filepath, newline="") as f:
        return list(csv.DictReader(f))


class StandardScaler:
    def __init__(self):
        self.mean = {}
        self.std = {}

    def fit(self, rows):
        for col in NUMERIC_COLS:
            vals = [float(r[col]) for r in rows]
            m = sum(vals) / len(vals)
            v = sum((x - m) ** 2 for x in vals) / len(vals)
            self.mean[col] = m
            self.std[col] = math.sqrt(v) if v > 0 else 1.0

    def transform(self, rows):
        result = []
        for r in rows:
            row = []
            for col in NUMERIC_COLS:
                row.append((float(r[col]) - self.mean[col]) / self.std[col])
            result.append(row)
        return result


class OneHotEncoder:
    def __init__(self):
        self.categories = []
        self.col_index = {}

    def fit(self, rows):
        cats = sorted(set(r[CAT_COL] for r in rows))
        self.categories = cats
        self.col_index = {c: i for i, c in enumerate(cats)}

    def transform(self, rows):
        result = []
        for r in rows:
            row = [0.0] * len(self.categories)
            if r[CAT_COL] in self.col_index:
                row[self.col_index[r[CAT_COL]]] = 1.0
            result.append(row)
        return result


def sigmoid(z):
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    else:
        ez = math.exp(z)
        return ez / (1.0 + ez)


class LogisticRegression:
    def __init__(self, n_features, learning_rate=0.1, n_iter=2000, l2=0.01):
        self.w = [0.0] * n_features
        self.b = 0.0
        self.lr = learning_rate
        self.n_iter = n_iter
        self.l2 = l2

    def _dot(self, w, x):
        s = 0.0
        for j in range(len(w)):
            s += w[j] * x[j]
        return s

    def fit(self, X, y):
        n = len(X)
        nf = len(self.w)
        for iteration in range(self.n_iter):
            grad_w = [0.0] * nf
            grad_b = 0.0
            for i in range(n):
                z = self._dot(self.w, X[i]) + self.b
                p = sigmoid(z)
                error = p - y[i]
                xi = X[i]
                for j in range(nf):
                    grad_w[j] += error * xi[j]
                grad_b += error
            for j in range(nf):
                grad_w[j] = grad_w[j] / n + self.l2 * self.w[j]
                self.w[j] -= self.lr * grad_w[j]
            self.b -= self.lr * grad_b / n

    def predict_proba(self, X):
        result = []
        for x in X:
            z = self._dot(self.w, x) + self.b
            result.append(sigmoid(z))
        return result


def build_features(rows, scaler, encoder):
    X_num = scaler.transform(rows)
    X_cat = encoder.transform(rows)
    X = [xn + xc for xn, xc in zip(X_num, X_cat)]
    y = [int(r["label"]) for r in rows]
    return X, y


def compute_roc_auc(labels, scores):
    pos = sum(labels)
    neg = len(labels) - pos
    if pos == 0 or neg == 0:
        return 0.5
    score_groups = defaultdict(lambda: [0, 0])
    for label, score in zip(labels, scores):
        score_groups[score][label] += 1
    sorted_scores = sorted(score_groups.keys(), reverse=True)
    tp_cum = 0
    fp_cum = 0
    prev_tpr = 0.0
    prev_fpr = 0.0
    auc = 0.0
    for score in sorted_scores:
        counts = score_groups[score]
        n_neg = counts[0]
        n_pos = counts[1]
        tp_cum += n_pos
        fp_cum += n_neg
        tpr = tp_cum / pos
        fpr = fp_cum / neg
        auc += (fpr - prev_fpr) * (tpr + prev_tpr) / 2
        prev_tpr = tpr
        prev_fpr = fpr
    return max(0.0, min(1.0, auc))


def compute_pr_auc(labels, scores):
    pos = sum(labels)
    if pos == 0:
        return 0.0
    score_groups = defaultdict(lambda: [0, 0])
    for label, score in zip(labels, scores):
        score_groups[score][label] += 1
    sorted_scores = sorted(score_groups.keys(), reverse=True)
    tp_cum = 0
    fp_cum = 0
    auc = 0.0
    prev_recall = 0.0
    prev_precision = 1.0
    for score in sorted_scores:
        counts = score_groups[score]
        n_neg = counts[0]
        n_pos = counts[1]
        tp_cum += n_pos
        fp_cum += n_neg
        precision = tp_cum / (tp_cum + fp_cum) if (tp_cum + fp_cum) > 0 else 0.0
        recall = tp_cum / pos
        auc += (recall - prev_recall) * (precision + prev_precision) / 2
        prev_recall = recall
        prev_precision = precision
    return max(0.0, min(1.0, auc))


def compute_metrics(labels, scores, threshold=0.5):
    tp = sum(1 for l, s in zip(labels, scores) if l == 1 and s >= threshold)
    fp = sum(1 for l, s in zip(labels, scores) if l == 0 and s >= threshold)
    tn = sum(1 for l, s in zip(labels, scores) if l == 0 and s < threshold)
    fn = sum(1 for l, s in zip(labels, scores) if l == 1 and s < threshold)
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    accuracy = (tp + tn) / (tp + fp + tn + fn) if (tp + fp + tn + fn) > 0 else 0.0
    return {
        "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        "precision": precision, "recall": recall, "f1": f1, "accuracy": accuracy,
        "threshold": threshold,
    }


def brier_score(labels, probs):
    n = len(labels)
    if n == 0:
        return 0.0
    return sum((p - l) ** 2 for p, l in zip(probs, labels)) / n


def log_loss(labels, probs, eps=1e-15):
    n = len(labels)
    if n == 0:
        return 0.0
    total = 0.0
    for l, p in zip(labels, probs):
        p = max(eps, min(1 - eps, p))
        total += -l * math.log(p) - (1 - l) * math.log(1 - p)
    return total / n


def platt_scale_fit(scores, labels, lr=0.01, n_iter=1000):
    """Fit Platt scaling: learn a, b such that P(y=1) = sigmoid(a * score + b).

    Uses the training/calibration split scores and labels.
    Returns (a, b) parameters.
    """
    a = 0.0
    b = 0.0
    n = len(scores)
    for _ in range(n_iter):
        grad_a = 0.0
        grad_b = 0.0
        for s, y in zip(scores, labels):
            t = a * s + b
            p = sigmoid(t)
            error = p - y
            grad_a += error * s
            grad_b += error
        a -= lr * grad_a / n
        b -= lr * grad_b / n
    return a, b


def platt_scale_apply(scores, a, b):
    """Apply Platt scaling: return sigmoid(a * score + b) for each score."""
    return [sigmoid(a * s + b) for s in scores]


def reliability_bins(labels, probs, n_bins=10):
    """Compute reliability diagram data: bin edges, mean predicted, mean observed."""
    bins = defaultdict(lambda: {"pred_sum": 0.0, "obs_sum": 0.0, "count": 0})
    for p, l in zip(probs, labels):
        bin_idx = min(int(p * n_bins), n_bins - 1)
        bins[bin_idx]["pred_sum"] += p
        bins[bin_idx]["obs_sum"] += l
        bins[bin_idx]["count"] += 1
    result = []
    for i in range(n_bins):
        b = bins[i]
        if b["count"] > 0:
            result.append({
                "bin_lo": i / n_bins,
                "bin_hi": (i + 1) / n_bins,
                "mean_pred": b["pred_sum"] / b["count"],
                "mean_obs": b["obs_sum"] / b["count"],
                "count": b["count"],
            })
    return result


def main():
    print("Phase 4F — Probability Calibration")
    print(f"Run at: {datetime.now().isoformat()}")
    print(f"Model training cutoff: < {CALIBRATION_CUTOFF}")
    print(f"Calibration cutoff: {CALIBRATION_CUTOFF} to {TRAIN_CUTOFF}")
    print(f"Evaluation cutoff: >= {TRAIN_CUTOFF}")
    print()

    all_rows = load_csv("features_all.csv")
    print(f"Loaded {len(all_rows)} total completed loans")

    model_train = [r for r in all_rows if r["borrow_date"] < CALIBRATION_CUTOFF.isoformat()]
    calib_rows = [r for r in all_rows
                  if CALIBRATION_CUTOFF.isoformat() <= r["borrow_date"] < TRAIN_CUTOFF.isoformat()]
    val_rows = [r for r in all_rows if r["borrow_date"] >= TRAIN_CUTOFF.isoformat()]

    print(f"  Model training:  {len(model_train)} loans (borrow_date < {CALIBRATION_CUTOFF})")
    print(f"  Calibration:     {len(calib_rows)} loans ({CALIBRATION_CUTOFF} <= borrow_date < {TRAIN_CUTOFF})")
    print(f"  Validation:      {len(val_rows)} loans (borrow_date >= {TRAIN_CUTOFF})")

    print("\nPreprocessing (fit on model-training set only)...")
    scaler = StandardScaler()
    scaler.fit(model_train)
    encoder = OneHotEncoder()
    encoder.fit(model_train)

    X_model_train, y_model_train = build_features(model_train, scaler, encoder)
    X_calib, y_calib = build_features(calib_rows, scaler, encoder)
    X_val, y_val = build_features(val_rows, scaler, encoder)

    n_features = len(X_model_train[0])
    print(f"  Features: {n_features}")

    print("\nTraining LR on model-training set...")
    model = LogisticRegression(n_features, learning_rate=0.1, n_iter=2000, l2=0.01)
    model.fit(X_model_train, y_model_train)

    train_scores = model.predict_proba(X_model_train)
    calib_scores = model.predict_proba(X_calib)
    val_scores = model.predict_proba(X_val)

    print("\nFitting Platt scaling on calibration set...")
    a, b = platt_scale_fit(calib_scores, y_calib)
    print(f"  Platt parameters: a={a:.4f}, b={b:.4f}")

    val_calibrated = platt_scale_apply(val_scores, a, b)

    print("\nComputing metrics...")

    uncal_roc = compute_roc_auc(y_val, val_scores)
    uncal_pr = compute_pr_auc(y_val, val_scores)
    uncal_brier = brier_score(y_val, val_scores)
    uncal_ll = log_loss(y_val, val_scores)
    uncal_m = compute_metrics(y_val, val_scores, threshold=0.45)

    cal_roc = compute_roc_auc(y_val, val_calibrated)
    cal_pr = compute_pr_auc(y_val, val_calibrated)
    cal_brier = brier_score(y_val, val_calibrated)
    cal_ll = log_loss(y_val, val_calibrated)
    cal_m = compute_metrics(y_val, val_calibrated, threshold=0.45)

    train_calibrated = platt_scale_apply(train_scores, a, b)
    train_labels = [int(r["label"]) for r in model_train]
    train_brier_cal = brier_score(train_labels, train_calibrated)
    train_brier_uncal = brier_score(train_labels, train_scores)

    calib_labels = y_calib
    calib_calibrated = platt_scale_apply(calib_scores, a, b)
    calib_brier_cal = brier_score(calib_labels, calib_calibrated)
    calib_brier_uncal = brier_score(calib_labels, calib_scores)

    report = []
    report.append("=" * 70)
    report.append("  PHASE 4F — PROBABILITY CALIBRATION REPORT")
    report.append("=" * 70)
    report.append(f"  Run at: {datetime.now().isoformat()}")
    report.append("")

    report.append("  CALIBRATION DESIGN")
    report.append("  " + "-" * 50)
    report.append("  Method: Platt (sigmoid) scaling")
    report.append(f"  Model training: borrow_date < {CALIBRATION_CUTOFF} ({len(model_train)} loans)")
    report.append(f"  Calibration fit: {CALIBRATION_CUTOFF} <= borrow_date < {TRAIN_CUTOFF} ({len(calib_rows)} loans)")
    report.append(f"  Evaluation: borrow_date >= {TRAIN_CUTOFF} ({len(val_rows)} loans)")
    report.append("  The temporal test set is never used to fit calibration parameters.")
    report.append("")

    report.append("  Platt Parameters")
    report.append("  " + "-" * 50)
    report.append(f"    a = {a:.6f}")
    report.append(f"    b = {b:.6f}")
    report.append(f"    Formula: P(overdue) = sigmoid(a * raw_score + b)")
    report.append("")

    report.append("  CALIBRATION QUALITY (Brier Score — lower is better)")
    report.append("  " + "-" * 50)
    report.append(f"    {'Split':18s} {'Uncalibrated':>13s} {'Calibrated':>13s} {'Improvement':>13s}")
    report.append(f"    {'Model train':18s} {train_brier_uncal:>13.4f} {train_brier_cal:>13.4f} {train_brier_uncal - train_brier_cal:>+13.4f}")
    report.append(f"    {'Calibration':18s} {calib_brier_uncal:>13.4f} {calib_brier_cal:>13.4f} {calib_brier_uncal - calib_brier_cal:>+13.4f}")
    report.append(f"    {'Validation':18s} {uncal_brier:>13.4f} {cal_brier:>13.4f} {uncal_brier - cal_brier:>+13.4f}")
    report.append("")

    report.append("  LOG LOSS (lower is better)")
    report.append("  " + "-" * 50)
    report.append(f"    Uncalibrated: {uncal_ll:.4f}")
    report.append(f"    Calibrated:   {cal_ll:.4f}")
    report.append(f"    Improvement:  {uncal_ll - cal_ll:+.4f}")
    report.append("")

    report.append("  RANKING METRICS (threshold-independent — should be unchanged)")
    report.append("  " + "-" * 50)
    report.append(f"    {'Metric':12s} {'Uncalibrated':>13s} {'Calibrated':>13s}")
    report.append(f"    {'ROC-AUC':12s} {uncal_roc:>13.3f} {cal_roc:>13.3f}")
    report.append(f"    {'PR-AUC':12s} {uncal_pr:>13.3f} {cal_pr:>13.3f}")
    report.append("")

    report.append("  CLASSIFICATION METRICS (threshold=0.45)")
    report.append("  " + "-" * 50)
    report.append(f"    {'Metric':12s} {'Uncalibrated':>13s} {'Calibrated':>13s}")
    report.append(f"    {'Precision':12s} {uncal_m['precision']:>13.3f} {cal_m['precision']:>13.3f}")
    report.append(f"    {'Recall':12s} {uncal_m['recall']:>13.3f} {cal_m['recall']:>13.3f}")
    report.append(f"    {'F1':12s} {uncal_m['f1']:>13.3f} {cal_m['f1']:>13.3f}")
    report.append(f"    {'Accuracy':12s} {uncal_m['accuracy']:>13.3f} {cal_m['accuracy']:>13.3f}")
    report.append("")

    report.append("  RELIABILITY DIAGRAM (calibrated probabilities, validation set)")
    report.append("  " + "-" * 50)
    report.append(f"    {'Bin':>12s} {'Mean Pred':>10s} {'Mean Obs':>10s} {'Gap':>8s} {'Count':>6s}")
    rel = reliability_bins(y_val, val_calibrated)
    for entry in rel:
        gap = entry["mean_pred"] - entry["mean_obs"]
        label = f"[{entry['bin_lo']:.1f},{entry['bin_hi']:.1f})"
        report.append(f"    {label:>12s} {entry['mean_pred']:>10.3f} {entry['mean_obs']:>10.3f} {gap:>+8.3f} {entry['count']:>6d}")
    report.append("")

    report.append("  RISK TIER DISTRIBUTION (calibrated)")
    report.append("  " + "-" * 50)
    tier_counts = defaultdict(int)
    tier_overdue = defaultdict(int)
    for r, s in zip(val_rows, val_calibrated):
        if s >= 0.55:
            tier = "HIGH"
        elif s >= 0.35:
            tier = "MEDIUM"
        else:
            tier = "LOW"
        tier_counts[tier] += 1
        tier_overdue[tier] += int(r["label"])
    for tier in ["LOW", "MEDIUM", "HIGH"]:
        cnt = tier_counts[tier]
        od = tier_overdue[tier]
        rate = od / cnt if cnt > 0 else 0
        report.append(f"    {tier:8s}: {cnt:>5} loans, {od:>4} overdue ({rate:.1%})")
    report.append("")

    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)

    if cal_brier < uncal_brier:
        report.append(f"  Calibration IMPROVED Brier score: {uncal_brier:.4f} -> {cal_brier:.4f}")
    elif cal_brier > uncal_brier:
        report.append(f"  Calibration DEGRADED Brier score: {uncal_brier:.4f} -> {cal_brier:.4f}")
    else:
        report.append(f"  Calibration had no effect on Brier score: {uncal_brier:.4f}")

    if abs(uncal_roc - cal_roc) < 0.001:
        report.append(f"  ROC-AUC preserved: {uncal_roc:.3f} (ranking unchanged)")
    else:
        report.append(f"  WARNING: ROC-AUC changed: {uncal_roc:.3f} -> {cal_roc:.3f}")

    report.append("  Platt calibration produces probabilities in [0, 1].")
    report.append("  The final temporal test set was never used to fit calibration.")
    report.append("  Uncalibrated metrics preserved for comparison.")
    report.append("  Offline/development only. No production integration.")
    report.append("=" * 70)

    report_text = "\n".join(report)
    report_path = os.path.join(OUTPUT_DIR, "phase4f_report.txt")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(report_path, "w") as f:
        f.write(report_text + "\n")
    print(f"\n  Report written to {report_path}")

    print("\n" + report_text)

    print("\nPhase 4F COMPLETE.")


if __name__ == "__main__":
    main()
