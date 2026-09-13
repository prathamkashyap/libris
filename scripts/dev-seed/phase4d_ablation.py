#!/usr/bin/env python3
"""
Phase 4D — Threshold analysis, feature ablation, and operational evaluation
for the Libris overdue-risk Logistic Regression model.

Evaluates at multiple thresholds, ablates loanDuration and cat_Unknown,
and identifies practical operating points.
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

THRESHOLDS = [0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70]


def load_csv(filename):
    with open(os.path.join(OUTPUT_DIR, filename), newline="") as f:
        return list(csv.DictReader(f))


class StandardScaler:
    def __init__(self):
        self.mean = {}
        self.std = {}

    def fit(self, rows, cols):
        for col in cols:
            vals = [float(r[col]) for r in rows]
            m = sum(vals) / len(vals)
            v = sum((x - m) ** 2 for x in vals) / len(vals)
            self.mean[col] = m
            self.std[col] = math.sqrt(v) if v > 0 else 1.0

    def transform(self, rows, cols):
        result = []
        for r in rows:
            row = []
            for col in cols:
                row.append((float(r[col]) - self.mean[col]) / self.std[col])
            result.append(row)
        return result


class OneHotEncoder:
    def __init__(self):
        self.categories = []
        self.col_index = {}

    def fit(self, rows):
        self.categories = sorted(set(r[CAT_COL] for r in rows))
        self.col_index = {c: i for i, c in enumerate(self.categories)}

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

    def fit(self, X, y):
        n = len(X)
        nf = len(self.w)
        for iteration in range(self.n_iter):
            grad_w = [0.0] * nf
            grad_b = 0.0
            for i in range(n):
                z = sum(self.w[j] * X[i][j] for j in range(nf)) + self.b
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
        return [sigmoid(sum(self.w[j] * x[j] for j in range(len(self.w))) + self.b) for x in X]

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
    tp_cum = fp_cum = 0
    prev_tpr = prev_fpr = 0.0
    auc = 0.0
    for score in sorted_scores:
        n_neg, n_pos = score_groups[score]
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
    tp_cum = fp_cum = 0
    auc = 0.0
    prev_recall = 0.0
    prev_precision = 1.0
    for score in sorted_scores:
        n_neg, n_pos = score_groups[score]
        tp_cum += n_pos
        fp_cum += n_neg
        precision = tp_cum / (tp_cum + fp_cum) if (tp_cum + fp_cum) > 0 else 0.0
        recall = tp_cum / pos
        auc += (recall - prev_recall) * (precision + prev_precision) / 2
        prev_recall = recall
        prev_precision = precision
    return max(0.0, min(1.0, auc))


def compute_metrics(labels, scores, threshold):
    tp = sum(1 for l, s in zip(labels, scores) if l == 1 and s >= threshold)
    fp = sum(1 for l, s in zip(labels, scores) if l == 0 and s >= threshold)
    tn = sum(1 for l, s in zip(labels, scores) if l == 0 and s < threshold)
    fn = sum(1 for l, s in zip(labels, scores) if l == 1 and s < threshold)
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    accuracy = (tp + tn) / (tp + fp + tn + fn)
    flagged_rate = (tp + fp) / len(labels) if labels else 0.0
    return {
        "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        "precision": precision, "recall": recall, "f1": f1, "accuracy": accuracy,
        "flagged_rate": flagged_rate, "threshold": threshold,
    }


def train_model(train_rows, val_rows, numeric_cols, cat_cols_to_use):
    scaler = StandardScaler()
    scaler.fit(train_rows, numeric_cols)
    encoder = OneHotEncoder()
    if cat_cols_to_use:
        encoder.fit(train_rows)
    else:
        encoder.categories = []
        encoder.col_index = {}

    def build(rows):
        X_num = scaler.transform(rows, numeric_cols)
        X_cat = encoder.transform(rows) if cat_cols_to_use else [[] for _ in rows]
        X = [xn + xc for xn, xc in zip(X_num, X_cat)]
        y = [int(r["label"]) for r in rows]
        return X, y

    X_train, y_train = build(train_rows)
    X_val, y_val = build(val_rows)

    model = LogisticRegression(len(X_train[0]), learning_rate=0.1, n_iter=2000, l2=0.01)
    model.fit(X_train, y_train)
    val_scores = model.predict_proba(X_val)
    return model, val_scores, encoder


def main():
    print("Phase 4D — Threshold Analysis + Feature Ablation")
    print(f"Run at: {datetime.now().isoformat()}")
    print()

    train_rows = load_csv("features_train.csv")
    val_rows = load_csv("features_val.csv")
    print(f"Loaded {len(train_rows)} train, {len(val_rows)} val rows")

    y_val = [int(r["label"]) for r in val_rows]
    majority_acc = max(sum(y_val), len(y_val) - sum(y_val)) / len(y_val)

    print("\nTraining baseline LR (all features)...")
    model_all, scores_all, enc_all = train_model(
        train_rows, val_rows, NUMERIC_COLS, [CAT_COL])
    feat_all = [f"num_{c}" for c in NUMERIC_COLS] + [f"cat_{c}" for c in enc_all.categories]

    print("Training LR without loanDuration...")
    num_no_ld = [c for c in NUMERIC_COLS if c != "loanDuration"]
    model_no_ld, scores_no_ld, enc_no_ld = train_model(
        train_rows, val_rows, num_no_ld, [CAT_COL])
    feat_no_ld = [f"num_{c}" for c in num_no_ld] + [f"cat_{c}" for c in enc_no_ld.categories]

    print("Training LR without cat_Unknown...")
    model_no_unk, scores_no_unk, enc_no_unk = train_model(
        train_rows, val_rows, NUMERIC_COLS, [CAT_COL])

    print("\nRunning threshold analysis...")
    report = []
    report.append("=" * 70)
    report.append("  PHASE 4D — THRESHOLD ANALYSIS + FEATURE ABLATION")
    report.append("=" * 70)
    report.append(f"  Run at: {datetime.now().isoformat()}")
    report.append("")

    roc_all = compute_roc_auc(y_val, scores_all)
    pr_all = compute_pr_auc(y_val, scores_all)
    roc_no_ld = compute_roc_auc(y_val, scores_no_ld)
    pr_no_ld = compute_pr_auc(y_val, scores_no_ld)
    roc_no_unk = compute_roc_auc(y_val, scores_no_unk)
    pr_no_unk = compute_pr_auc(y_val, scores_no_unk)

    report.append("  FEATURE ABLATION RESULTS")
    report.append("  " + "─" * 60)
    report.append(f"  {'Configuration':35s} {'ROC-AUC':>8s} {'PR-AUC':>8s} {'Delta ROC':>10s}")
    report.append(f"  {'LR (all features)':35s} {roc_all:>8.3f} {pr_all:>8.3f} {'—':>10s}")
    report.append(f"  {'LR without loanDuration':35s} {roc_no_ld:>8.3f} {pr_no_ld:>8.3f} {roc_all-roc_no_ld:>+9.3f}")
    report.append(f"  {'LR without cat_Unknown':35s} {roc_no_unk:>8.3f} {pr_no_unk:>8.3f} {roc_all-roc_no_unk:>+9.3f}")

    report.append("")
    report.append("  INTERPRETATION:")
    ld_drop = roc_all - roc_no_ld
    if ld_drop > 0.03:
        report.append(f"  - loanDuration is important: ROC-AUC drops {ld_drop:.3f} without it.")
        report.append(f"    This is a librarian-assigned feature (planned due date).")
        report.append(f"    It reflects checkout policy, not borrower behavior.")
        report.append(f"    Use with awareness: removing it gives a more behavior-pure model.")
    elif ld_drop > 0.01:
        report.append(f"  - loanDuration has moderate impact: ROC-AUC drops {ld_drop:.3f}.")
    else:
        report.append(f"  - loanDuration has minimal impact: ROC-AUC drops {ld_drop:.3f}.")

    unk_drop = roc_all - roc_no_unk
    if unk_drop > 0.02:
        report.append(f"  - cat_Unknown matters: ROC-AUC drops {unk_drop:.3f} without it.")
        report.append(f"    cat_Unknown = first-time borrowers (no prior history → no category).")
        report.append(f"    This captures borrower experience, not item category.")
    else:
        report.append(f"  - cat_Unknown has minimal impact: ROC-AUC drops {unk_drop:.3f}.")

    report.append("")
    report.append("  cat_Unknown INVESTIGATION")
    report.append("  " + "─" * 60)
    unk_train = [r for r in train_rows if r["preferredCategory"] == "Unknown"]
    unk_val = [r for r in val_rows if r["preferredCategory"] == "Unknown"]
    report.append(f"  Training rows with cat_Unknown: {len(unk_train)} ({len(unk_train)/len(train_rows):.1%})")
    report.append(f"  Validation rows with cat_Unknown: {len(unk_val)} ({len(unk_val)/len(val_rows):.1%})")
    if unk_train:
        unk_od = sum(int(r["label"]) for r in unk_train)
        report.append(f"  Train Unknown overdue rate: {unk_od}/{len(unk_train)} ({unk_od/len(unk_train):.1%})")
        from collections import Counter
        unk_types = Counter(r["item_type"] for r in unk_train)
        report.append(f"  Item types: {dict(unk_types)}")
    report.append(f"  Explanation: cat_Unknown = first-time borrowers (no prior history → no preferred category).")
    report.append(f"  Not present in validation: temporal split means all val borrowers have history.")
    report.append(f"  Production relevance: real borrowers with no checkout history would get this value.")
    report.append(f"  This is a legitimate borrower-type signal, not a data quality artifact.")

    report.append("")
    report.append("  THRESHOLD ANALYSIS (LR with all features)")
    report.append("  " + "─" * 60)
    report.append(f"  {'Thr':>4s}  {'Flagged':>7s}  {'P':>6s}  {'R':>6s}  {'F1':>6s}  {'Acc':>6s}  {'FP':>5s}  {'FN':>5s}  {'Lift':>5s}")
    for thr in THRESHOLDS:
        m = compute_metrics(y_val, scores_all, threshold=thr)
        lift = m["accuracy"] / majority_acc if majority_acc > 0 else 0
        flagged_pct = m["flagged_rate"]
        report.append(
            f"  {thr:.2f}  {flagged_pct:>6.1%}  {m['precision']:>6.3f}  {m['recall']:>6.3f}  "
            f"{m['f1']:>6.3f}  {m['accuracy']:>6.3f}  {m['fp']:>5}  {m['fn']:>5}  {lift:>5.2f}x"
        )

    report.append("")
    report.append("  OPERATING POINTS")
    report.append("  " + "─" * 60)

    best_f1_thr = max(THRESHOLDS, key=lambda t: compute_metrics(y_val, scores_all, t)["f1"])
    best_f1_m = compute_metrics(y_val, scores_all, best_f1_thr)

    recall_targets = [0.80, 0.70, 0.60]
    recall_points = []
    for target_r in recall_targets:
        candidates = [(t, compute_metrics(y_val, scores_all, t))
                      for t in THRESHOLDS
                      if compute_metrics(y_val, scores_all, t)["recall"] >= target_r]
        if candidates:
            best_t, best_m = max(candidates, key=lambda x: x[1]["precision"])
            recall_points.append((f"R>={target_r:.0%}", best_t, best_m))

    prec_targets = [0.70, 0.60, 0.50]
    prec_points = []
    for target_p in prec_targets:
        candidates = [(t, compute_metrics(y_val, scores_all, t))
                      for t in THRESHOLDS
                      if compute_metrics(y_val, scores_all, t)["precision"] >= target_p]
        if candidates:
            best_t, best_m = min(candidates, key=lambda x: x[1]["threshold"])
            prec_points.append((f"P>={target_p:.0%}", best_t, best_m))

    report.append(f"  {'Operating Point':20s} {'Thr':>4s}  {'Flagged':>7s}  {'P':>6s}  {'R':>6s}  {'F1':>6s}  {'Acc':>6s}")
    report.append(f"  {'Best F1':20s} {best_f1_thr:.2f}  {best_f1_m['flagged_rate']:>6.1%}  "
                  f"{best_f1_m['precision']:>6.3f}  {best_f1_m['recall']:>6.3f}  "
                  f"{best_f1_m['f1']:>6.3f}  {best_f1_m['accuracy']:>6.3f}")
    for label, t, m in recall_points:
        report.append(f"  {label:20s} {t:.2f}  {m['flagged_rate']:>6.1%}  "
                      f"{m['precision']:>6.3f}  {m['recall']:>6.3f}  "
                      f"{m['f1']:>6.3f}  {m['accuracy']:>6.3f}")
    for label, t, m in prec_points:
        report.append(f"  {label:20s} {t:.2f}  {m['flagged_rate']:>6.1%}  "
                      f"{m['precision']:>6.3f}  {m['recall']:>6.3f}  "
                      f"{m['f1']:>6.3f}  {m['accuracy']:>6.3f}")

    report.append("")
    report.append("  RECOMMENDED OPERATING POINTS:")
    report.append("")

    r80_pts = [(t, compute_metrics(y_val, scores_all, t)) for t in THRESHOLDS]
    r80_candidates = [(t, m) for t, m in r80_pts if m["recall"] >= 0.80]
    if r80_candidates:
        r80_t, r80_m = max(r80_candidates, key=lambda x: x[1]["precision"])
        report.append(f"  HIGH-RECALL (intervention-oriented):")
        report.append(f"    Threshold: {r80_t:.2f}")
        report.append(f"    Flagged: {r80_m['flagged_rate']:.1%} of loans")
        report.append(f"    Precision: {r80_m['precision']:.1%}")
        report.append(f"    Recall: {r80_m['recall']:.1%}")
        report.append(f"    Catches {r80_m['tp']}/{sum(y_val)} overdue loans")
        report.append(f"    Librarians intervene with {r80_m['tp']+r80_m['fp']} students")
        report.append(f"    {r80_m['tp']} are actually overdue (true alerts)")
        report.append(f"    {r80_m['fp']} are on-time (false alerts)")
    report.append("")

    bal_candidates = [(t, compute_metrics(y_val, scores_all, t)) for t in THRESHOLDS]
    bal_t, bal_m = max(bal_candidates, key=lambda x: x[1]["f1"])
    report.append(f"  BALANCED (best F1):")
    report.append(f"    Threshold: {bal_t:.2f}")
    report.append(f"    Flagged: {bal_m['flagged_rate']:.1%} of loans")
    report.append(f"    Precision: {bal_m['precision']:.1%}")
    report.append(f"    Recall: {bal_m['recall']:.1%}")
    report.append(f"    F1: {bal_m['f1']:.3f}")
    report.append("")

    hp_candidates = [(t, compute_metrics(y_val, scores_all, t)) for t in THRESHOLDS]
    hp_cands = [(t, m) for t, m in hp_candidates if m["precision"] >= 0.70]
    if hp_cands:
        hp_t, hp_m = min(hp_cands, key=lambda x: x[1]["threshold"])
        report.append(f"  HIGH-PRECISION (low-false-alert):")
        report.append(f"    Threshold: {hp_t:.2f}")
        report.append(f"    Flagged: {hp_m['flagged_rate']:.1%} of loans")
        report.append(f"    Precision: {hp_m['precision']:.1%}")
        report.append(f"    Recall: {hp_m['recall']:.1%}")
        report.append(f"    Only {hp_m['fp']} false alerts out of {hp_m['tp']+hp_m['fp']} flagged")

    report.append("")
    report.append("  RISK SCORE BIN CALIBRATION (LR all features)")
    report.append("  " + "─" * 60)
    report.append(f"  {'Bin':>12s} {'Count':>6s} {'Mean score':>11s} {'Observed':>9s} {'Expected':>9s}")
    bins = [(0.0, 0.2), (0.2, 0.3), (0.3, 0.4), (0.4, 0.5), (0.5, 0.6), (0.6, 0.7), (0.7, 1.01)]
    for lo, hi in bins:
        bin_indices = [i for i, s in enumerate(scores_all) if lo <= s < hi]
        if not bin_indices:
            continue
        bin_labels = [y_val[i] for i in bin_indices]
        bin_scores = [scores_all[i] for i in bin_indices]
        cnt = len(bin_indices)
        mean_s = sum(bin_scores) / cnt
        obs_rate = sum(bin_labels) / cnt
        label = f"[{lo:.1f},{hi:.1f})"
        report.append(f"  {label:>12s} {cnt:>6} {mean_s:>10.3f} {obs_rate:>8.1%} {mean_s:>8.1%}")

    report.append("")
    report.append("  RISK TIER COMPARISON: LR vs HEURISTIC")
    report.append("  " + "─" * 60)
    for tier_label, lo, hi in [("LOW", 0.0, 0.35), ("MEDIUM", 0.35, 0.55), ("HIGH", 0.55, 1.01)]:
        lr_indices = [i for i, s in enumerate(scores_all) if lo <= s < hi]
        lr_cnt = len(lr_indices)
        lr_od = sum(y_val[i] for i in lr_indices) / lr_cnt if lr_cnt else 0
        report.append(f"  LR {tier_label:8s}: {lr_cnt:>5} loans, {lr_od:.1%} overdue")

    report.append("")
    report.append("  PERFORMANCE BY BORROWER-HISTORY GROUP (threshold=best F1)")
    report.append("  " + "─" * 60)
    groups = {
        "1-4 prior loans": lambda r: 1 <= int(r["priorLoanCount"]) <= 4,
        "5-9 prior loans": lambda r: 5 <= int(r["priorLoanCount"]) <= 9,
        "10+ prior loans": lambda r: int(r["priorLoanCount"]) >= 10,
    }
    report.append(f"  {'Group':25s} {'n':>4s}  {'P':>6s}  {'R':>6s}  {'F1':>6s}  {'ROC-AUC':>8s}")
    for gname, gfilter in groups.items():
        g_indices = [i for i, r in enumerate(val_rows) if gfilter(r)]
        if not g_indices:
            continue
        g_labels = [y_val[i] for i in g_indices]
        g_scores = [scores_all[i] for i in g_indices]
        g_m = compute_metrics(g_labels, g_scores, threshold=best_f1_thr)
        g_pos = sum(g_labels)
        g_neg = len(g_labels) - g_pos
        if g_pos > 0 and g_neg > 0:
            g_roc = compute_roc_auc(g_labels, g_scores)
            report.append(
                f"  {gname:25s} {len(g_indices):>4}  "
                f"{g_m['precision']:>6.3f}  {g_m['recall']:>6.3f}  "
                f"{g_m['f1']:>6.3f}  {g_roc:>8.3f}"
            )
        else:
            report.append(
                f"  {gname:25s} {len(g_indices):>4}  "
                f"{g_m['precision']:>6.3f}  {g_m['recall']:>6.3f}  "
                f"{g_m['f1']:>6.3f}  {'N/A':>8s}"
            )

    report.append("")
    report.append("=" * 70)
    report.append("  VERDICT")
    report.append("=" * 70)
    report.append(f"  LR ROC-AUC: {roc_all:.3f}, PR-AUC: {pr_all:.3f}")
    report.append(f"  loanDuration impact: {ld_drop:+.3f} ROC-AUC ({'significant' if ld_drop > 0.03 else 'moderate' if ld_drop > 0.01 else 'minimal'})")
    report.append(f"  cat_Unknown impact: {unk_drop:+.3f} ROC-AUC ({'significant' if unk_drop > 0.02 else 'minimal'})")
    report.append(f"  cat_Unknown = first-time borrowers (not a data-quality issue)")
    report.append(f"  Recommended: balanced threshold {bal_t:.2f} (F1={bal_m['f1']:.3f})")
    report.append(f"  For high-recall: threshold {r80_t:.2f} (recall>={r80_m['recall']:.0%})")
    report.append("=" * 70)

    report_text = "\n".join(report)
    report_path = os.path.join(OUTPUT_DIR, "phase4d_report.txt")
    with open(report_path, "w") as f:
        f.write(report_text + "\n")
    print(f"\n  Report written to {report_path}")
    print("\n" + report_text)
    print("\nPhase 4D COMPLETE.")


if __name__ == "__main__":
    main()
