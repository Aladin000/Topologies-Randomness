#!/usr/bin/env python3

from __future__ import annotations

import math
import pathlib
from dataclasses import dataclass
from typing import Iterable

import numpy as np
import pandas as pd

try:
    from scipy.stats import t as student_t
except ImportError:  # pragma: no cover - scipy is available in the analysis env.
    student_t = None


ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
OUT = ROOT / "analysis" / "figures" / "statistical_validity"
OUT.mkdir(parents=True, exist_ok=True)

BASE_KEYS = ["topology", "nodeCount", "k", "viewFraction", "fanOut"]
BFT_KEYS = BASE_KEYS + ["failureProbability"]
RUN_KEYS = ["graphSeed", "simulationSeed", "trial"]

TOPOLOGIES = ["Ring", "SmallWorld", "Random", "ScaleFree"]
TOPOLOGY_LABELS = {
    "Ring": "Ring",
    "SmallWorld": "Small-World",
    "Random": "Random",
    "ScaleFree": "Scale-Free",
}

METRICS = [
    "T_end",
    "Omega",
    "M",
    "alpha",
    "R_run",
    "F_eff",
    "L_0.5",
    "L_0.9",
    "L_1.0",
]

DERIVED_METRICS = ["T_end", "R_run"]

LATENCY_THRESHOLDS = {"L_0.5", "L_0.9", "L_1.0"}

METRIC_LABELS = {
    "T_end": "T_end",
    "Omega": "Omega",
    "M": "M",
    "alpha": "alpha",
    "R_run": "R",
    "F_eff": "F_eff",
    "L_0.5": "L_0.5",
    "L_0.9": "L_0.9",
    "L_1.0": "L_1.0",
}

METRIC_FORMATS = {
    "T_end": ".2f",
    "Omega": ",.0f",
    "M": ".2f",
    "alpha": ".6f",
    "R_run": ".3f",
    "F_eff": ".2f",
    "L_0.5": ".2f",
    "L_0.9": ".2f",
    "L_1.0": ".2f",
}


@dataclass(frozen=True)
class SuiteSpec:
    name: str
    label: str
    files: tuple[str, ...]
    config_keys: list[str]
    dedupe_keys: list[str]


SUITES = [
    SuiteSpec(
        name="rq1",
        label="RQ1 topology suite",
        files=("rq1.csv",),
        config_keys=BASE_KEYS,
        dedupe_keys=BASE_KEYS + RUN_KEYS,
    ),
    SuiteSpec(
        name="rq2",
        label="RQ2 peer-selection suite",
        files=("rq2.csv",),
        config_keys=BASE_KEYS,
        dedupe_keys=BASE_KEYS + RUN_KEYS,
    ),
    SuiteSpec(
        name="rq3",
        label="RQ3 failure-free trade-off space",
        files=("rq1.csv", "rq2.csv"),
        config_keys=BASE_KEYS,
        dedupe_keys=BASE_KEYS + RUN_KEYS,
    ),
    SuiteSpec(
        name="rq1b",
        label="RQ1b topology suite with non-forwarding",
        files=("rq1b.csv",),
        config_keys=BFT_KEYS,
        dedupe_keys=BFT_KEYS + RUN_KEYS,
    ),
    SuiteSpec(
        name="rq2b",
        label="RQ2b peer-selection suite with non-forwarding",
        files=("rq2b.csv",),
        config_keys=BFT_KEYS,
        dedupe_keys=BFT_KEYS + RUN_KEYS,
    ),
    SuiteSpec(
        name="rq3b",
        label="RQ3b non-forwarding trade-off space",
        files=("rq1b.csv", "rq2b.csv"),
        config_keys=BFT_KEYS,
        dedupe_keys=BFT_KEYS + RUN_KEYS,
    ),
]


def t_critical(n: int) -> float:
    """Two-sided 95% t critical value for n observations."""
    if n <= 1:
        return math.nan
    if student_t is not None:
        return float(student_t.ppf(0.975, n - 1))
    # Conservative fallback for the expected n=30 case.
    if n <= 30:
        return 2.045
    return 1.96


def _fmt(value: float, fmt: str = ".2f") -> str:
    if value is None or pd.isna(value):
        return "N/A"
    return format(float(value), fmt)


def _fmt_interval(mean: float, half_width: float, metric: str) -> str:
    fmt = METRIC_FORMATS[metric]
    if pd.isna(mean):
        return "N/A"
    if pd.isna(half_width):
        return _fmt(mean, fmt)
    return f"{_fmt(mean, fmt)} +/- {_fmt(half_width, fmt)}"


def _md_table(headers: list[str], rows: Iterable[Iterable[object]],
              aligns: list[str] | None = None) -> str:
    if aligns is None:
        aligns = ["l"] + ["r"] * (len(headers) - 1)
    separator = "|"
    for align in aligns:
        if align == "r":
            separator += " ---: |"
        elif align == "c":
            separator += " :---: |"
        else:
            separator += " :--- |"
    lines = ["| " + " | ".join(headers) + " |", separator]
    for row in rows:
        lines.append("| " + " | ".join(str(cell) for cell in row) + " |")
    return "\n".join(lines)


def topology_label(topology: str) -> str:
    return TOPOLOGY_LABELS.get(topology, topology)


def load_suite(spec: SuiteSpec) -> tuple[pd.DataFrame, dict[str, int]]:
    frames = []
    raw_rows = 0
    for filename in spec.files:
        path = DATA / filename
        if not path.exists():
            raise FileNotFoundError(f"Missing required data file: {path}")
        df = pd.read_csv(path)
        frames.append(df)
        raw_rows += len(df)

    df = pd.concat(frames, ignore_index=True)
    validate_columns(df, spec)
    before = len(df)
    df = df.drop_duplicates(subset=spec.dedupe_keys).reset_index(drop=True)
    return df, {
        "raw_rows": raw_rows,
        "deduplicated_rows": len(df),
        "duplicates_removed": before - len(df),
    }


def validate_columns(df: pd.DataFrame, spec: SuiteSpec) -> None:
    required = set(spec.config_keys + RUN_KEYS + METRICS)
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"{spec.name} is missing required columns: {missing}")


def prepare_metrics(df: pd.DataFrame) -> pd.DataFrame:
    prepared = df.copy()
    for metric in LATENCY_THRESHOLDS:
        prepared.loc[prepared[metric] < 0, metric] = np.nan
    return prepared


def design_audit(df: pd.DataFrame, spec: SuiteSpec,
                 row_counts: dict[str, int]) -> tuple[dict[str, object], pd.DataFrame]:
    config_counts = (
        df.groupby(spec.config_keys, dropna=False)
        .agg(
            n_trials=("trial", "count"),
            n_graphs=("graphSeed", "nunique"),
            n_simulation_seeds=("simulationSeed", "nunique"),
        )
        .reset_index()
    )

    graph_counts = (
        df.groupby(spec.config_keys + ["graphSeed"], dropna=False)
        .size()
        .reset_index(name="trials_per_graph")
    )

    config_counts["valid_1500_trials"] = config_counts["n_trials"].eq(1500)
    config_counts["valid_30_graphs"] = config_counts["n_graphs"].eq(30)

    summary = {
        "suite": spec.name,
        "label": spec.label,
        **row_counts,
        "configurations": len(config_counts),
        "min_trials_per_config": int(config_counts["n_trials"].min()),
        "max_trials_per_config": int(config_counts["n_trials"].max()),
        "configs_not_1500_trials": int((~config_counts["valid_1500_trials"]).sum()),
        "min_graphs_per_config": int(config_counts["n_graphs"].min()),
        "max_graphs_per_config": int(config_counts["n_graphs"].max()),
        "configs_not_30_graphs": int((~config_counts["valid_30_graphs"]).sum()),
        "min_trials_per_graph": int(graph_counts["trials_per_graph"].min()),
        "max_trials_per_graph": int(graph_counts["trials_per_graph"].max()),
        "graph_blocks_not_50_trials": int((graph_counts["trials_per_graph"] != 50).sum()),
    }
    return summary, config_counts


def graph_level_means(df: pd.DataFrame, spec: SuiteSpec) -> tuple[pd.DataFrame, pd.DataFrame]:
    prepared = prepare_metrics(df)
    per_graph = (
        prepared.groupby(spec.config_keys + ["graphSeed"], dropna=False)[METRICS]
        .mean()
        .reset_index()
    )

    per_config_counts = (
        df.groupby(spec.config_keys, dropna=False)
        .agg(
            n_trials=("trial", "count"),
            n_graphs_total=("graphSeed", "nunique"),
        )
        .reset_index()
    )
    return per_graph, per_config_counts


def ci_for_values(values: pd.Series) -> dict[str, float | int]:
    clean = values.dropna().astype(float)
    n = int(clean.size)
    if n == 0:
        return {
            "mean": math.nan,
            "sd_graph": math.nan,
            "se_graph": math.nan,
            "ci_low": math.nan,
            "ci_high": math.nan,
            "ci_half_width": math.nan,
            "n_graphs_used": 0,
        }

    mean = float(clean.mean())
    if n == 1:
        return {
            "mean": mean,
            "sd_graph": math.nan,
            "se_graph": math.nan,
            "ci_low": math.nan,
            "ci_high": math.nan,
            "ci_half_width": math.nan,
            "n_graphs_used": n,
        }

    sd = float(clean.std(ddof=1))
    se = sd / math.sqrt(n)
    half = t_critical(n) * se
    return {
        "mean": mean,
        "sd_graph": sd,
        "se_graph": se,
        "ci_low": mean - half,
        "ci_high": mean + half,
        "ci_half_width": half,
        "n_graphs_used": n,
    }


def graph_level_ci(df: pd.DataFrame, spec: SuiteSpec) -> pd.DataFrame:
    per_graph, per_config_counts = graph_level_means(df, spec)
    records: list[dict[str, object]] = []

    for config_values, group in per_graph.groupby(spec.config_keys, dropna=False):
        if not isinstance(config_values, tuple):
            config_values = (config_values,)
        config_record = dict(zip(spec.config_keys, config_values))

        for metric in METRICS:
            stats = ci_for_values(group[metric])
            mean = stats["mean"]
            half = stats["ci_half_width"]
            relative = math.nan
            if not pd.isna(mean) and not pd.isna(half) and abs(float(mean)) > 0:
                relative = abs(float(half) / float(mean))

            records.append({
                "suite": spec.name,
                "metric": metric,
                "metric_label": METRIC_LABELS[metric],
                **config_record,
                **stats,
                "relative_ci_half_width": relative,
            })

    ci = pd.DataFrame.from_records(records)
    ci = ci.merge(per_config_counts, on=spec.config_keys, how="left")

    if "failureProbability" not in ci.columns:
        ci["failureProbability"] = np.nan

    ordered_columns = (
        ["suite", "metric", "metric_label"]
        + BFT_KEYS
        + [
            "mean", "ci_low", "ci_high", "ci_half_width",
            "relative_ci_half_width", "sd_graph", "se_graph",
            "n_graphs_used", "n_graphs_total", "n_trials",
        ]
    )
    return ci[ordered_columns]


def fanout_averaged_ci(df: pd.DataFrame, spec: SuiteSpec) -> pd.DataFrame:
    """CI for fanOut effects averaged over viewFraction in RQ2/RQ2b."""
    if spec.name not in {"rq2", "rq2b"}:
        return pd.DataFrame()

    keys = ["topology", "nodeCount", "k", "fanOut"]
    if "failureProbability" in spec.config_keys:
        keys.append("failureProbability")

    prepared = prepare_metrics(df)
    per_graph = (
        prepared.groupby(keys + ["graphSeed"], dropna=False)[DERIVED_METRICS]
        .mean()
        .reset_index()
    )

    records: list[dict[str, object]] = []
    for config_values, group in per_graph.groupby(keys, dropna=False):
        if not isinstance(config_values, tuple):
            config_values = (config_values,)
        config_record = dict(zip(keys, config_values))

        for metric in DERIVED_METRICS:
            stats = ci_for_values(group[metric])
            records.append({
                "suite": spec.name,
                "derived_quantity": "fanOut mean averaged over viewFraction",
                "metric": metric,
                "metric_label": METRIC_LABELS[metric],
                **config_record,
                **stats,
            })

    out = pd.DataFrame.from_records(records)
    if "failureProbability" not in out.columns:
        out["failureProbability"] = np.nan
    return out[
        [
            "suite", "derived_quantity", "metric", "metric_label",
            "topology", "nodeCount", "k", "fanOut", "failureProbability",
            "mean", "ci_low", "ci_high", "ci_half_width", "sd_graph",
            "se_graph", "n_graphs_used",
        ]
    ]


def fanout_contrast_ci(df: pd.DataFrame, spec: SuiteSpec) -> pd.DataFrame:
    """Paired graph-level CI for fanOut=5 minus fanOut=1."""
    if spec.name not in {"rq2", "rq2b"}:
        return pd.DataFrame()

    keys = ["topology", "nodeCount", "k"]
    if "failureProbability" in spec.config_keys:
        keys.append("failureProbability")

    prepared = prepare_metrics(df)
    per_graph = (
        prepared.groupby(keys + ["graphSeed", "fanOut"], dropna=False)[DERIVED_METRICS]
        .mean()
        .reset_index()
    )

    records: list[dict[str, object]] = []
    for config_values, group in per_graph.groupby(keys, dropna=False):
        if not isinstance(config_values, tuple):
            config_values = (config_values,)
        config_record = dict(zip(keys, config_values))

        for metric in DERIVED_METRICS:
            pivot = group.pivot_table(
                index="graphSeed", columns="fanOut", values=metric, aggfunc="mean"
            )
            if 1 not in pivot.columns or 5 not in pivot.columns:
                continue
            diff = pivot[5] - pivot[1]
            stats = ci_for_values(diff)
            records.append({
                "suite": spec.name,
                "contrast": "fanOut=5 minus fanOut=1, averaged over viewFraction",
                "metric": metric,
                "metric_label": METRIC_LABELS[metric],
                **config_record,
                **stats,
            })

    out = pd.DataFrame.from_records(records)
    if "failureProbability" not in out.columns:
        out["failureProbability"] = np.nan
    return out[
        [
            "suite", "contrast", "metric", "metric_label",
            "topology", "nodeCount", "k", "failureProbability",
            "mean", "ci_low", "ci_high", "ci_half_width", "sd_graph",
            "se_graph", "n_graphs_used",
        ]
    ]


def ci_summary(ci: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for (suite, metric), group in ci.groupby(["suite", "metric"], dropna=False):
        values = group["ci_half_width"].dropna()
        rel = group["relative_ci_half_width"].replace([np.inf, -np.inf], np.nan).dropna()
        rows.append({
            "suite": suite,
            "metric": metric,
            "metric_label": METRIC_LABELS[metric],
            "configurations": len(group),
            "configs_with_ci": int(values.size),
            "configs_without_ci": int(len(group) - values.size),
            "median_half_width": values.quantile(0.50) if len(values) else math.nan,
            "p75_half_width": values.quantile(0.75) if len(values) else math.nan,
            "p90_half_width": values.quantile(0.90) if len(values) else math.nan,
            "p95_half_width": values.quantile(0.95) if len(values) else math.nan,
            "p99_half_width": values.quantile(0.99) if len(values) else math.nan,
            "max_half_width": values.max() if len(values) else math.nan,
            "median_relative_half_width": rel.quantile(0.50) if len(rel) else math.nan,
            "p95_relative_half_width": rel.quantile(0.95) if len(rel) else math.nan,
        })
    out = pd.DataFrame(rows)
    suite_order = {spec.name: i for i, spec in enumerate(SUITES)}
    metric_order = {metric: i for i, metric in enumerate(METRICS)}
    out["suite_order"] = out["suite"].map(suite_order)
    out["metric_order"] = out["metric"].map(metric_order)
    return out.sort_values(["suite_order", "metric_order"]).drop(
        columns=["suite_order", "metric_order"]
    )


def key_configuration_rows(ci: pd.DataFrame) -> pd.DataFrame:
    metrics = ["T_end", "M", "alpha", "R_run"]
    failure_free = ci[
        (ci["suite"] == "rq3")
        & (ci["nodeCount"] == 5000)
        & (ci["k"] == 6)
        & (ci["viewFraction"] == 1.0)
        & (ci["fanOut"] == 3)
        & (ci["metric"].isin(metrics))
    ].copy()
    failure_free["case"] = "Failure-free, N=5000, k=6, vf=1.0, fo=3"

    byzantine = ci[
        (ci["suite"] == "rq3b")
        & (ci["nodeCount"] == 5000)
        & (ci["k"] == 6)
        & (ci["viewFraction"] == 1.0)
        & (ci["fanOut"] == 3)
        & (ci["failureProbability"].isin([0.15, 0.30]))
        & (ci["metric"].isin(metrics))
    ].copy()
    byzantine["case"] = byzantine["failureProbability"].map(
        lambda fp: f"Non-forwarding fp={fp:.2f}, N=5000, k=6, vf=1.0, fo=3"
    )

    key = pd.concat([failure_free, byzantine], ignore_index=True)
    sort_order = {topology: i for i, topology in enumerate(TOPOLOGIES)}
    key["topology_order"] = key["topology"].map(sort_order)
    key["metric_order"] = key["metric"].map({m: i for i, m in enumerate(metrics)})
    return key.sort_values(["case", "topology_order", "metric_order"]).drop(
        columns=["topology_order", "metric_order"]
    )


def headline_contrasts(ci: pd.DataFrame) -> pd.DataFrame:
    rows = []

    def get_value(suite: str, topology: str, metric: str,
                  failure_probability: float | None = None) -> pd.Series:
        mask = (
            (ci["suite"] == suite)
            & (ci["topology"] == topology)
            & (ci["nodeCount"] == 5000)
            & (ci["k"] == 6)
            & (ci["viewFraction"] == 1.0)
            & (ci["fanOut"] == 3)
            & (ci["metric"] == metric)
        )
        if failure_probability is None:
            mask &= ci["failureProbability"].isna()
        else:
            mask &= ci["failureProbability"].eq(failure_probability)
        found = ci[mask]
        if len(found) != 1:
            raise ValueError(
                f"Expected one row for {suite}/{topology}/{metric}/"
                f"{failure_probability}, found {len(found)}"
            )
        return found.iloc[0]

    contrasts = [
        ("Failure-free latency gap", "rq3", "Ring", "Random", "T_end", None),
        ("Failure-free latency gap", "rq3", "SmallWorld", "Random", "T_end", None),
        ("Failure-free cost gap", "rq3", "Ring", "SmallWorld", "M", None),
        ("High-failure reliability gap", "rq3b", "SmallWorld", "Random", "R_run", 0.30),
        ("High-failure reliability gap", "rq3b", "SmallWorld", "ScaleFree", "R_run", 0.30),
        ("High-failure coverage gap", "rq3b", "SmallWorld", "Ring", "alpha", 0.30),
    ]

    for name, suite, left, right, metric, fp in contrasts:
        a = get_value(suite, left, metric, fp)
        b = get_value(suite, right, metric, fp)
        diff = float(a["mean"] - b["mean"])
        combined_half = math.sqrt(
            float(a["ci_half_width"]) ** 2 + float(b["ci_half_width"]) ** 2
        )
        ratio = abs(diff) / combined_half if combined_half > 0 else math.inf
        rows.append({
            "contrast": name,
            "suite": suite,
            "failureProbability": fp,
            "metric": metric,
            "metric_label": METRIC_LABELS[metric],
            "left_topology": left,
            "right_topology": right,
            "left_mean": a["mean"],
            "right_mean": b["mean"],
            "difference": diff,
            "combined_ci_half_width": combined_half,
            "effect_to_ci_ratio": ratio,
        })

    return pd.DataFrame(rows)


def write_latex_key_table(key: pd.DataFrame) -> None:
    rows = []
    cases = [
        "Failure-free, N=5000, k=6, vf=1.0, fo=3",
        "Non-forwarding fp=0.30, N=5000, k=6, vf=1.0, fo=3",
    ]
    metrics = ["T_end", "M", "R_run"]

    case_labels = {
        "Failure-free, N=5000, k=6, vf=1.0, fo=3": "Failure-free",
        "Non-forwarding fp=0.30, N=5000, k=6, vf=1.0, fo=3": "$q=0.30$",
    }

    for case in cases:
        sub_case = key[key["case"] == case]
        for topology in TOPOLOGIES:
            row = [
                case_labels[case] if topology == TOPOLOGIES[0] else "",
                topology_label(topology),
            ]
            for metric in metrics:
                r = sub_case[
                    (sub_case["topology"] == topology)
                    & (sub_case["metric"] == metric)
                ]
                if len(r) == 0:
                    row.append("N/A")
                else:
                    r = r.iloc[0]
                    row.append(_fmt_interval(r["mean"], r["ci_half_width"], metric))
            rows.append(row)

    lines = [
        "% Auto-generated by analysis/statistical_validity.py",
        "\\begin{tabular}{llccc}",
        "\\hline",
        "Scenario & Topology & $T_{\\mathrm{end}}$ & $M$ & $R$ \\\\",
        "\\hline",
    ]
    for row in rows:
        escaped = [str(cell).replace("_", "\\_") for cell in row]
        lines.append(" & ".join(escaped) + " \\\\")
    lines.extend(["\\hline", "\\end{tabular}", ""])
    (OUT / "ci_key_configurations_latex.tex").write_text("\n".join(lines), encoding="utf-8")


def report_data_totals(audit: pd.DataFrame) -> dict[str, int]:
    """Return headline counts for the deduplicated result spaces."""
    lookup = audit.set_index("suite")
    return {
        "failure_free_rows": int(lookup.loc["rq3", "deduplicated_rows"]),
        "failure_free_configs": int(lookup.loc["rq3", "configurations"]),
        "byzantine_rows": int(lookup.loc["rq3b", "deduplicated_rows"]),
        "byzantine_configs": int(lookup.loc["rq3b", "configurations"]),
        "all_rows": int(lookup.loc["rq3", "deduplicated_rows"]
                        + lookup.loc["rq3b", "deduplicated_rows"]),
        "all_configs": int(lookup.loc["rq3", "configurations"]
                           + lookup.loc["rq3b", "configurations"]),
    }


def all_audit_checks_passed(audit: pd.DataFrame) -> bool:
    checks = [
        "configs_not_1500_trials",
        "configs_not_30_graphs",
        "graph_blocks_not_50_trials",
    ]
    return bool((audit[checks].sum(axis=1) == 0).all())


def append_output_files(rpt: list[str]) -> None:
    rpt.append("## 6. Output Files\n")
    rpt.append("The report is a readable summary. The CSV files contain the full auditable data.\n")
    outputs = [
        ("ci_suite_audit.csv", "one-row-per-suite design audit"),
        ("ci_configuration_audit.csv", "one-row-per-configuration trial and graph count audit"),
        ("ci_all_configurations.csv", "complete graph-level CI table for every suite, configuration, and metric"),
        ("ci_width_summary.csv", "CI half-width percentiles by suite and metric"),
        ("ci_key_configurations.csv", "key thesis configurations used in the paper narrative"),
        ("ci_headline_contrasts.csv", "effect-size versus uncertainty checks for headline claims"),
        ("ci_fanout_averaged_over_view.csv", "fanOut summaries averaged over viewFraction"),
        ("ci_fanout_effect_contrasts.csv", "paired graph-level fanOut=5 minus fanOut=1 contrasts"),
        ("ci_key_configurations_latex.tex", "compact LaTeX table snippet for the paper or appendix"),
    ]
    rows = [[f"`{name}`", description] for name, description in outputs]
    rpt.append(_md_table(["File", "Contents"], rows, aligns=["l", "l"]))
    rpt.append("")


def write_report(audit: pd.DataFrame, summary: pd.DataFrame,
                 key: pd.DataFrame, contrasts: pd.DataFrame,
                 fanout_effects: pd.DataFrame,
                 fanout_contrasts: pd.DataFrame) -> None:
    rpt: list[str] = []
    rpt.append("# Statistical Validity Report\n")
    rpt.append("*Auto-generated by `analysis/statistical_validity.py`.*\n")

    rpt.append("## 1. Data Integrity Audit\n")
    headers = [
        "Suite", "Meaning", "Rows", "Configs", "Trials/config", "Graphs/config",
        "Trials/graph", "Dedup removed", "Issues",
    ]
    rows = []
    for _, r in audit.iterrows():
        issues = []
        if r["configs_not_1500_trials"]:
            issues.append(f"{int(r['configs_not_1500_trials'])} configs != 1500 trials")
        if r["configs_not_30_graphs"]:
            issues.append(f"{int(r['configs_not_30_graphs'])} configs != 30 graphs")
        if r["graph_blocks_not_50_trials"]:
            issues.append(f"{int(r['graph_blocks_not_50_trials'])} graph blocks != 50 trials")
        rows.append([
            r["suite"],
            r["label"],
            f"{int(r['deduplicated_rows']):,}",
            f"{int(r['configurations']):,}",
            f"{int(r['min_trials_per_config']):,}-{int(r['max_trials_per_config']):,}",
            f"{int(r['min_graphs_per_config'])}-{int(r['max_graphs_per_config'])}",
            f"{int(r['min_trials_per_graph'])}-{int(r['max_trials_per_graph'])}",
            f"{int(r['duplicates_removed']):,}",
            "; ".join(issues) if issues else "None",
        ])
    rpt.append(_md_table(headers, rows, aligns=["l", "l", "r", "r", "r", "r", "r", "r", "l"]))
    rpt.append("")

    rpt.append("## 2. Confidence-Interval Width Summary\n")
    rpt.append(
        "This table covers **all metrics** used by the result scripts. It reports "
        "the median, 95th percentile, and maximum 95% CI half-width across "
        "configurations. Smaller values mean more stable estimates. Some latency "
        "thresholds have fewer CIs because no graph-level value exists when the "
        "threshold is never reached in the relevant trials.\n"
    )
    headers = [
        "Suite", "Metric", "Configs", "Median half-width", "95th percentile",
        "Max", "Median relative", "95th relative", "Configs without CI",
    ]
    rows = []
    for _, r in summary.iterrows():
        metric = r["metric"]
        rows.append([
            r["suite"],
            METRIC_LABELS[metric],
            int(r["configurations"]),
            _fmt(r["median_half_width"], METRIC_FORMATS[metric]),
            _fmt(r["p95_half_width"], METRIC_FORMATS[metric]),
            _fmt(r["max_half_width"], METRIC_FORMATS[metric]),
            _fmt(r["median_relative_half_width"] * 100, ".2f") + "%",
            _fmt(r["p95_relative_half_width"] * 100, ".2f") + "%",
            int(r["configs_without_ci"]),
        ])
    rpt.append(_md_table(headers, rows, aligns=["l", "l", "r", "r", "r", "r", "r", "r", "r"]))
    rpt.append("")

    rpt.append("## 3. Key Thesis Configurations\n")
    rpt.append(
        "These are the main large-network configurations used repeatedly in the "
        "paper. Values are graph-level means with 95% CI half-widths.\n"
    )
    display_cases = [
        "Failure-free, N=5000, k=6, vf=1.0, fo=3",
        "Non-forwarding fp=0.30, N=5000, k=6, vf=1.0, fo=3",
    ]
    display_metrics = ["T_end", "M", "alpha", "R_run"]
    headers = ["Case", "Topology"] + [METRIC_LABELS[m] for m in display_metrics]
    rows = []
    for case in display_cases:
        case_rows = key[key["case"] == case]
        for topology in TOPOLOGIES:
            row = [case if topology == TOPOLOGIES[0] else "", topology_label(topology)]
            for metric in display_metrics:
                r = case_rows[
                    (case_rows["topology"] == topology)
                    & (case_rows["metric"] == metric)
                ]
                if len(r) == 0:
                    row.append("N/A")
                else:
                    r = r.iloc[0]
                    row.append(_fmt_interval(r["mean"], r["ci_half_width"], metric))
            rows.append(row)
    rpt.append(_md_table(headers, rows, aligns=["l", "l", "r", "r", "r", "r"]))
    rpt.append("")

    rpt.append("## 4. Headline Contrasts\n")
    rpt.append(
        "The ratio compares the absolute effect size with the combined CI "
        "half-width of the two estimates. Large ratios indicate that uncertainty "
        "is small relative to the reported effect.\n"
    )
    headers = ["Contrast", "Metric", "Left", "Right", "Difference", "Combined CI half-width", "Ratio"]
    rows = []
    for _, r in contrasts.iterrows():
        metric = r["metric"]
        fp = "" if pd.isna(r["failureProbability"]) else f", fp={r['failureProbability']:.2f}"
        rows.append([
            f"{r['contrast']} ({r['suite']}{fp})",
            METRIC_LABELS[metric],
            topology_label(r["left_topology"]),
            topology_label(r["right_topology"]),
            _fmt(r["difference"], METRIC_FORMATS[metric]),
            _fmt(r["combined_ci_half_width"], METRIC_FORMATS[metric]),
            _fmt(r["effect_to_ci_ratio"], ".1f"),
        ])
    rpt.append(_md_table(headers, rows, aligns=["l", "l", "l", "l", "r", "r", "r"]))
    rpt.append("")

    rpt.append("## 5. Peer-Selection Effect Checks\n")
    rpt.append(
        "Several Results statements average fanOut effects over viewFraction. "
        "The following table checks the large N=5000, k=6 case using graph-level "
        "CIs. The delta is paired by graphSeed and equals fanOut=5 minus fanOut=1.\n"
    )
    headers = ["Suite", "fp", "Topology", "Metric", "fo=1", "fo=5", "Delta"]
    rows = []
    for suite, fp in [("rq2", math.nan), ("rq2b", 0.30)]:
        effects = fanout_effects[
            (fanout_effects["suite"] == suite)
            & (fanout_effects["nodeCount"] == 5000)
            & (fanout_effects["k"] == 6)
        ]
        deltas = fanout_contrasts[
            (fanout_contrasts["suite"] == suite)
            & (fanout_contrasts["nodeCount"] == 5000)
            & (fanout_contrasts["k"] == 6)
        ]
        if pd.isna(fp):
            effects = effects[effects["failureProbability"].isna()]
            deltas = deltas[deltas["failureProbability"].isna()]
            fp_label = "-"
        else:
            effects = effects[effects["failureProbability"].eq(fp)]
            deltas = deltas[deltas["failureProbability"].eq(fp)]
            fp_label = f"{fp:.2f}"

        for topology in TOPOLOGIES:
            for metric in DERIVED_METRICS:
                r1 = effects[
                    (effects["topology"] == topology)
                    & (effects["metric"] == metric)
                    & (effects["fanOut"] == 1)
                ]
                r5 = effects[
                    (effects["topology"] == topology)
                    & (effects["metric"] == metric)
                    & (effects["fanOut"] == 5)
                ]
                rd = deltas[
                    (deltas["topology"] == topology)
                    & (deltas["metric"] == metric)
                ]
                if len(r1) == 0 or len(r5) == 0 or len(rd) == 0:
                    continue
                r1 = r1.iloc[0]
                r5 = r5.iloc[0]
                rd = rd.iloc[0]
                rows.append([
                    suite,
                    fp_label,
                    topology_label(topology),
                    METRIC_LABELS[metric],
                    _fmt_interval(r1["mean"], r1["ci_half_width"], metric),
                    _fmt_interval(r5["mean"], r5["ci_half_width"], metric),
                    _fmt_interval(rd["mean"], rd["ci_half_width"], metric),
                ])
    rpt.append(_md_table(headers, rows, aligns=["l", "l", "l", "l", "r", "r", "r"]))
    rpt.append("")

    append_output_files(rpt)

    (OUT / "statistical_validity_report.md").write_text("\n".join(rpt), encoding="utf-8")


def main() -> None:
    audit_rows = []
    config_audits = []
    ci_tables = []
    fanout_effect_tables = []
    fanout_contrast_tables = []

    for spec in SUITES:
        print(f"Loading {spec.name} ...")
        df, row_counts = load_suite(spec)
        audit_row, config_audit = design_audit(df, spec, row_counts)
        ci = graph_level_ci(df, spec)
        fanout_effect = fanout_averaged_ci(df, spec)
        fanout_contrast = fanout_contrast_ci(df, spec)
        audit_rows.append(audit_row)
        config_audit.insert(0, "suite", spec.name)
        config_audits.append(config_audit)
        ci_tables.append(ci)
        if not fanout_effect.empty:
            fanout_effect_tables.append(fanout_effect)
        if not fanout_contrast.empty:
            fanout_contrast_tables.append(fanout_contrast)
        print(
            f"  {len(df):,} rows, {audit_row['configurations']:,} configs, "
            f"{audit_row['min_graphs_per_config']}-{audit_row['max_graphs_per_config']} graphs/config"
        )

    audit = pd.DataFrame(audit_rows)
    per_config_audit = pd.concat(config_audits, ignore_index=True)
    all_ci = pd.concat(ci_tables, ignore_index=True)
    summary = ci_summary(all_ci)
    key = key_configuration_rows(all_ci)
    contrasts = headline_contrasts(all_ci)
    fanout_effects = pd.concat(fanout_effect_tables, ignore_index=True)
    fanout_contrasts = pd.concat(fanout_contrast_tables, ignore_index=True)

    audit.to_csv(OUT / "ci_suite_audit.csv", index=False)
    per_config_audit.to_csv(OUT / "ci_configuration_audit.csv", index=False)
    all_ci.to_csv(OUT / "ci_all_configurations.csv", index=False)
    summary.to_csv(OUT / "ci_width_summary.csv", index=False)
    key.to_csv(OUT / "ci_key_configurations.csv", index=False)
    contrasts.to_csv(OUT / "ci_headline_contrasts.csv", index=False)
    fanout_effects.to_csv(OUT / "ci_fanout_averaged_over_view.csv", index=False)
    fanout_contrasts.to_csv(OUT / "ci_fanout_effect_contrasts.csv", index=False)

    write_latex_key_table(key)
    write_report(audit, summary, key, contrasts, fanout_effects, fanout_contrasts)

    print(f"\nWrote statistical validity outputs to {OUT}")


if __name__ == "__main__":
    main()
