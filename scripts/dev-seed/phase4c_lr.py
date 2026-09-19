#!/usr/bin/env python3
"""
Phase 4C — Logistic Regression baseline for Libris overdue-risk prediction.

Implements from scratch (no sklearn): standardization, one-hot encoding,
logistic regression with gradient descent + L2 regularization.
Evaluates on temporal validation set with full metrics.
All preprocessing fit on train only, applied to both train and val.
"""
import csv
import math
import os
from collections import defaultdict
from datetime import datetime

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")

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
    def __init__(self, n_features, learning_rate=0.01, n_iter=5000, l2=0.01):
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
            total_loss = 0.0
            for i in range(n):
                z = self._dot(self.w, X[i]) + self.b
                p = sigmoid(z)
                error = p - y[i]
                xi = X[i]
                for j in range(nf):
                    grad_w[j] += error * xi[j]
                grad_b += error
                total_loss += -y[i] * math.log(p + 1e-15) - (1 - y[i]) * math.log(1 - p + 1e-15)
            for j in range(nf):
                grad_w[j] = grad_w[j] / n + self.l2 * self.w[j]
                self.w[j] -= self.lr * grad_w[j]
            self.b -= self.lr * grad_b / n
            if iteration % 500 == 0:
                avg_loss = total_loss / n
                reg = 0.5 * self.l2 * sum(w ** 2 for w in self.w)
                print(f"    Iter {iteration:>5}: loss={avg_loss + reg:.4f}")

    def predict_proba(self, X):
        result = []
        for x in X:
            z = self._dot(self.w, x) + self.b
            result.append(sigmoid(z))
        return result

    def get_feature_importance(self, feature_names):
        pairs = list(zip(feature_names, self.w))
        pairs.sort(key=lambda x: abs(x[1]), reverse=True)
        return pairs


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


def build_features(rows, scaler, encoder):
    X_num = scaler.transform(rows)
    X_cat = encoder.transform(rows)
    X = [xn + xc for xn, xc in zip(X_num, X_cat)]
    y = [int(r["label"]) for r in rows]
    return X, y


def main():
    print("Phase 4C — Logistic Regression Baseline")
    print(f"Run at: {datetime.now().isoformat()}")
    print()

    train_rows = load_csv("features_train.csv")
    val_rows = load_csv("features_val.csv")
    print(f"Loaded {len(train_rows)} train, {len(val_rows)} val rows")

    print("\nPreprocessing (fit on train only)...")
    scaler = StandardScaler()
    scaler.fit(train_rows)
    encoder = OneHotEncoder()
    encoder.fit(train_rows)

    X_train, y_train = build_features(train_rows, scaler, encoder)
    X_val, y_val = build_features(val_rows, scaler, encoder)

    n_features = len(X_train[0])
    print(f"  Features: {n_features} ({len(NUMERIC_COLS)} numeric + {len(encoder.categories)} one-hot)")

    print("\nTraining Logistic Regression...")
    model = LogisticRegression(n_features, learning_rate=0.1, n_iter=2000, l2=0.01)
    model.fit(X_train, y_train)

    train_scores = model.predict_proba(X_train)
    val_scores = model.predict_proba(X_val)

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    for split_name, rows, scores in [("train", train_rows, train_scores), ("val", val_rows, val_scores)]:
        out_rows = []
        for r, s in zip(rows, scores):
            out = dict(r)
            out["lr_score"] = f"{s:.6f}"
            out["lr_pred_045"] = 1 if s >= 0.45 else 0
            out_rows.append(out)
        path = os.path.join(OUTPUT_DIR, f"lr_{split_name}.csv")
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
            writer.writeheader()
            writer.writerows(out_rows)
        print(f"  Exported {path}")

    feature_names = [f"num_{c}" for c in NUMERIC_COLS] + [f"cat_{c}" for c in encoder.categories]
    importance = model.get_feature_importance(feature_names)

    threshold_analysis = []
    for thr in [0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70]:
        m = compute_metrics(y_val, val_scores, threshold=thr)
        threshold_analysis.append((thr, m))

    best_thr = 0.45
    best_m = compute_metrics(y_val, val_scores, threshold=best_thr)

    majority_acc = max(sum(y_val), len(y_val) - sum(y_val)) / len(y_val)

    heuristic_scores = []
    for r in val_rows:
        with open(os.path.join(OUTPUT_DIR, "heuristic_val.csv"), newline="") as f:
            reader = csv.DictReader(f)
            for hr in reader:
                if hr["loan_id"] == r["loan_id"]:
                    heuristic_scores.append(float(hr["risk_score"]))
                    break
    h_m = compute_metrics(y_val, heuristic_scores, threshold=0.45)

    report = []
    report.append("=" * 70)
    report.append("  PHASE 4C — LOGISTIC REGRESSION EVALUATION REPORT")
    report.append("=" * 70)
    report.append(f"  Run at: {datetime.now().isoformat()}")
    report.append("")

    report.append("  MODEL DESIGN")
    report.append("  ─────────────────")
    report.append("  Algorithm: Logistic Regression with L2 regularization")
    report.append(f"  Features: {n_features} ({len(NUMERIC_COLS)} numeric + {len(encoder.categories)} one-hot)")
    report.append(f"  Training: gradient descent, lr=0.1, iter=2000, L2=0.01")
    report.append("  Preprocessing: StandardScaler (fit on train), OneHotEncoder (fit on train)")
    report.append("  No production code modified. No future data used.")
    report.append("")

    report.append("  FEATURE IMPORTANCE (top 15 by |coefficient|)")
    report.append("  ─────────────────")
    for fname, fw in importance[:15]:
        direction = "+" if fw > 0 else "-"
        report.append(f"    {direction} {fname:35s} coef={fw:+.4f}")
    report.append("")

    report.append("=" * 70)
    report.append("  VALIDATION SET (2071 loans)")
    report.append("=" * 70)
    report.append("")

    report.append("  Threshold analysis:")
    report.append(f"    {'Thr':>4s}  {'Precision':>9s}  {'Recall':>6s}  {'F1':>6s}  {'Acc':>6s}  {'PPR':>5s}")
    for thr, m in threshold_analysis:
        report.append(
            f"    {thr:.2f}  {m['precision']:>9.3f}  {m['recall']:>6.3f}  "
            f"{m['f1']:>6.3f}  {m['accuracy']:>6.3f}  {(m['tp']+m['fp'])/len(y_val):>5.1%}"
        )

    report.append("")
    report.append(f"  Confusion matrix (threshold={best_thr}):")
    report.append(f"                    Predicted")
    report.append(f"                    On-time    Overdue")
    report.append(f"  Actual On-time    {best_m['tn']:>6}     {best_m['fp']:>6}")
    report.append(f"  Actual Overdue    {best_m['fn']:>6}     {best_m['tp']:>6}")

    report.append("")
    report.append(f"  Selected metrics (threshold={best_thr}):")
    report.append(f"    Precision:    {best_m['precision']:.3f}")
    report.append(f"    Recall:       {best_m['recall']:.3f}")
    report.append(f"    F1:           {best_m['f1']:.3f}")
    report.append(f"    Accuracy:     {best_m['accuracy']:.3f}")

    lr_roc = compute_roc_auc(y_val, val_scores)
    lr_pr = compute_pr_auc(y_val, val_scores)
    report.append(f"    ROC-AUC:      {lr_roc:.3f}")
    report.append(f"    PR-AUC:       {lr_pr:.3f}")

    report.append("")
    report.append("  Comparison with baselines:")
    report.append(f"    {'Model':20s} {'Accuracy':>9s} {'P':>6s} {'R':>6s} {'F1':>6s} {'ROC-AUC':>8s} {'PR-AUC':>8s}")
    report.append(f"    {'Majority-class':20s} {majority_acc:>9.3f} {'—':>6s} {'—':>6s} {'—':>6s} {'0.500':>8s} {'—':>8s}")
    report.append(f"    {'Heuristic (4B)':20s} {h_m['accuracy']:>9.3f} {h_m['precision']:>6.3f} {h_m['recall']:>6.3f} {h_m['f1']:>6.3f} {'0.639':>8s} {'0.542':>8s}")
    report.append(f"    {'LogisticReg (4C)':20s} {best_m['accuracy']:>9.3f} {best_m['precision']:>6.3f} {best_m['recall']:>6.3f} {best_m['f1']:>6.3f} {lr_roc:>8.3f} {lr_pr:>8.3f}")

    report.append("")
    report.append("  Score direction check:")
    val_overdue_scores = [s for l, s in zip(y_val, val_scores) if l == 1]
    val_ontime_scores = [s for l, s in zip(y_val, val_scores) if l == 0]
    od_mean = sum(val_overdue_scores) / len(val_overdue_scores)
    ot_mean = sum(val_ontime_scores) / len(val_ontime_scores)
    report.append(f"    Overdue mean score: {od_mean:.3f}")
    report.append(f"    On-time mean score: {ot_mean:.3f}")
    report.append(f"    Direction: {'CORRECT (higher=more overdue)' if od_mean > ot_mean else 'INVERTED — ERROR'}")

    report.append("")
    report.append("  Performance by borrower-history group:")
    groups = {
        "1-4 prior loans": lambda r: 1 <= int(r["priorLoanCount"]) <= 4,
        "5-9 prior loans": lambda r: 5 <= int(r["priorLoanCount"]) <= 9,
        "10+ prior loans": lambda r: int(r["priorLoanCount"]) >= 10,
    }
    for gname, gfilter in groups.items():
        g_rows = [r for r in val_rows if gfilter(r)]
        if not g_rows:
            continue
        g_indices = [i for i, r in enumerate(val_rows) if gfilter(r)]
        g_labels = [y_val[i] for i in g_indices]
        g_scores = [val_scores[i] for i in g_indices]
        g_m = compute_metrics(g_labels, g_scores, threshold=best_thr)
        g_pos = sum(g_labels)
        g_neg = len(g_labels) - g_pos
        if g_pos > 0 and g_neg > 0:
            g_roc = compute_roc_auc(g_labels, g_scores)
            report.append(
                f"    {gname:25s}: n={len(g_rows):>4}  "
                f"P={g_m['precision']:.3f}  R={g_m['recall']:.3f}  "
                f"F1={g_m['f1']:.3f}  ROC-AUC={g_roc:.3f}"
            )
        else:
            report.append(
                f"    {gname:25s}: n={len(g_rows):>4}  "
                f"P={g_m['precision']:.3f}  R={g_m['recall']:.3f}  "
                f"F1={g_m['f1']:.3f}  ROC-AUC=N/A (single class)"
            )

    report.append("")
    report.append("  Observed overdue rate by risk score bin:")
    report.append(f"    {'Bin':>12s} {'Count':>6s} {'Mean score':>11s} {'Observed':>9s}")
    bins = [(0.0, 0.2), (0.2, 0.35), (0.35, 0.45), (0.45, 0.55), (0.55, 0.7), (0.7, 1.01)]
    for lo, hi in bins:
        bin_indices = [i for i, s in enumerate(val_scores) if lo <= s < hi]
        if not bin_indices:
            continue
        bin_labels = [y_val[i] for i in bin_indices]
        bin_scores_list = [val_scores[i] for i in bin_indices]
        bin_count = len(bin_indices)
        bin_mean = sum(bin_scores_list) / bin_count
        bin_od = sum(bin_labels) / bin_count
        label = f"[{lo:.2f},{hi:.2f})"
        report.append(f"    {label:>12s} {bin_count:>6} {bin_mean:>10.3f} {bin_od:>8.1%}")

    report.append("")
    report.append("  Risk tier distribution:")
    tier_counts = defaultdict(int)
    tier_overdue = defaultdict(int)
    for r, s in zip(val_rows, val_scores):
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
    report.append("  Observed overdue rate by LR risk tier:")
    report.append(f"    {'Tier':8s} {'Count':>6s} {'Observed':>9s}")
    for tier in ["LOW", "MEDIUM", "HIGH"]:
        cnt = tier_counts[tier]
        od = tier_overdue[tier]
        rate = od / cnt if cnt > 0 else 0
        report.append(f"    {tier:8s} {cnt:>6} {rate:>8.1%}")

    report.append("")
    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)

    improvements = []
    if lr_roc > 0.639:
        improvements.append(f"ROC-AUC ({lr_roc:.3f} > 0.639)")
    if lr_pr > 0.542:
        improvements.append(f"PR-AUC ({lr_pr:.3f} > 0.542)")
    if best_m['f1'] > h_m['f1']:
        improvements.append(f"F1 ({best_m['f1']:.3f} > {h_m['f1']:.3f})")

    if improvements:
        report.append(f"  Logistic Regression improves over heuristic on: {', '.join(improvements)}")
    else:
        report.append("  Logistic Regression does not clearly outperform the heuristic.")
    report.append("  All metrics computed on temporal validation set (borrow_date >= 2025-10-01).")
    report.append("  No future data used. No production code modified.")
    report.append("=" * 70)

    report_text = "\n".join(report)
    report_path = os.path.join(OUTPUT_DIR, "phase4c_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text + "\n")
    print(f"\n  Report written to {report_path}")

    print("\n" + report_text)

    print("\nPhase 4C COMPLETE.")


if __name__ == "__main__":
    main()
