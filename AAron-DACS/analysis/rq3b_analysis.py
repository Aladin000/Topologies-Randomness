#!/usr/bin/env python3

import pathlib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.lines as mlines
import seaborn as sns

from _style import (
    TOPOLOGIES, TOPO_COLORS, TOPO_LABELS, TOPO_MARKERS,
    FIG_SINGLE_H, FIG_SMALL_W,
    SUPTITLE_SIZE, SUBTITLE_SIZE, ANNOT_SIZE, LEGEND_SIZE, TICK_SIZE,
    apply_theme, topo_label, thousands_formatter, compact_formatter,
    FP_LINESTYLES, FP_LABELS, FP_SHORT_LABELS, FP_VALUES,
)

# --- paths ---

ROOT     = pathlib.Path(__file__).resolve().parent.parent
RQ1_DATA = ROOT / "data" / "rq1b.csv"
RQ2_DATA = ROOT / "data" / "rq2b.csv"
OUT      = ROOT / "analysis" / "figures" / "rq3b"
OUT.mkdir(parents=True, exist_ok=True)

# --- theme ---

apply_theme()

# --- load and merge ---

print(f"Loading {RQ1_DATA} and {RQ2_DATA} ...")
df1 = pd.read_csv(RQ1_DATA)
df2 = pd.read_csv(RQ2_DATA)

df = pd.concat([df1, df2], ignore_index=True)
df = df.drop_duplicates(subset=[
    "topology", "nodeCount", "k", "viewFraction", "fanOut",
    "failureProbability", "graphSeed", "simulationSeed", "trial",
])
print(f"  Merged: {len(df):,} unique trial rows")

N_VALUES = sorted(df["nodeCount"].unique())
N_RQ2 = [100, 500, 1000, 5000]

# --- marker alpha per fp level (scatter differentiation) ---

_FP_ALPHA = {0.15: 0.85, 0.30: 0.45}

# --- per-config aggregates (includes failureProbability) ---

GROUP_KEYS = ["topology", "nodeCount", "k", "viewFraction", "fanOut",
              "failureProbability"]

agg = df.groupby(GROUP_KEYS).agg(
    T_end_mean   = ("T_end",  "mean"),
    T_end_median = ("T_end",  "median"),
    Omega_mean   = ("Omega",  "mean"),
    M_mean       = ("M",      "mean"),
    R            = ("R_run",  "mean"),
    F_eff_mean   = ("F_eff",  "mean"),
    n_trials     = ("T_end",  "count"),
).reset_index()

# --- legend helpers ---

def _topo_fp_label(topo, fp):
    return f"{topo_label(topo)}, {FP_SHORT_LABELS[fp]}"


def _build_combined_legend(ax, loc="best"):
    """Build a two-section legend: topologies + fp levels."""
    topo_handles = [
        mlines.Line2D([], [], color=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                       linestyle="None", markersize=7, label=topo_label(t))
        for t in TOPOLOGIES
    ]
    fp_handles = [
        mlines.Line2D([], [], color="grey", linestyle=FP_LINESTYLES[fp],
                       linewidth=2, label=FP_LABELS[fp])
        for fp in FP_VALUES
    ]
    ax.legend(handles=topo_handles + fp_handles, frameon=True, loc=loc,
              fontsize=LEGEND_SIZE)


def _build_scatter_legend(ax, loc="best"):
    """Legend for scatter plots using alpha to distinguish fp."""
    topo_handles = [
        mlines.Line2D([], [], color=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                       linestyle="None", markersize=7, label=topo_label(t))
        for t in TOPOLOGIES
    ]
    fp_handles = [
        mlines.Line2D([], [], color="grey", marker="o", linestyle="None",
                       markersize=7, alpha=_FP_ALPHA[fp],
                       label=FP_LABELS[fp])
        for fp in FP_VALUES
    ]
    ax.legend(handles=topo_handles + fp_handles, frameon=True, loc=loc,
              fontsize=LEGEND_SIZE)


# --- figure 1: t_end vs omega scatter ---

def fig_speed_vs_cost():
    fig, axes = plt.subplots(1, len(N_VALUES),
                             figsize=(7 * len(N_VALUES), FIG_SINGLE_H + 0.5))
    if len(N_VALUES) == 1:
        axes = [axes]
    for ax, n in zip(axes, N_VALUES):
        sub = agg[agg["nodeCount"] == n]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                ax.scatter(tsub["T_end_mean"], tsub["Omega_mean"],
                           c=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                           s=60, alpha=_FP_ALPHA[fp],
                           edgecolors="white", linewidth=0.5)
        ax.set_xlabel(r"Mean $T_{end}$ (rounds)")
        ax.set_ylabel(r"Mean $\Omega$ (messages)")
        ax.set_title(f"N = {n}")
        ax.yaxis.set_major_formatter(thousands_formatter())
    _build_scatter_legend(axes[0])
    fig.suptitle(r"Byzantine Robustness — Speed vs Cost: $T_{end}$ vs $\Omega$"
                 "\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_01_speed_vs_cost.png")
    plt.close(fig)
    print("  [01] rq3b_01_speed_vs_cost.png")


# --- figure 2: reliability vs omega scatter ---

def fig_reliability_vs_cost():
    fig, axes = plt.subplots(1, len(N_VALUES),
                             figsize=(7 * len(N_VALUES), FIG_SINGLE_H + 0.5))
    if len(N_VALUES) == 1:
        axes = [axes]
    for ax, n in zip(axes, N_VALUES):
        sub = agg[agg["nodeCount"] == n]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                ax.scatter(tsub["Omega_mean"], tsub["R"],
                           c=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                           s=60, alpha=_FP_ALPHA[fp],
                           edgecolors="white", linewidth=0.5)
        ax.set_xlabel(r"Mean $\Omega$ (messages)")
        ax.set_ylabel("Reliability $R$")
        ax.set_title(f"N = {n}")
        ax.set_ylim(-0.05, 1.05)
        ax.axhline(0.95, color="grey", linestyle="--", linewidth=0.8,
                   alpha=0.6)
        ax.xaxis.set_major_formatter(thousands_formatter())
    _build_scatter_legend(axes[0])
    fig.suptitle(r"Byzantine Robustness — Reliability vs Cost: $R$ vs $\Omega$"
                 "\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_02_reliability_vs_cost.png")
    plt.close(fig)
    print("  [02] rq3b_02_reliability_vs_cost.png")


# --- figure 3: pareto frontiers ---

def _pareto_front(points):
    """Return indices of Pareto-optimal points (minimise both dimensions)."""
    sorted_idx = np.argsort(points[:, 0])
    pareto = []
    best_y = np.inf
    for idx in sorted_idx:
        if points[idx, 1] < best_y:
            pareto.append(idx)
            best_y = points[idx, 1]
    return np.array(pareto)


def fig_pareto():
    fig, axes = plt.subplots(1, len(N_VALUES),
                             figsize=(7 * len(N_VALUES), FIG_SINGLE_H + 0.5))
    if len(N_VALUES) == 1:
        axes = [axes]
    for ax, n in zip(axes, N_VALUES):
        sub = agg[agg["nodeCount"] == n]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                pts = tsub[["T_end_mean", "Omega_mean"]].values
                if len(pts) == 0:
                    continue
                ax.scatter(pts[:, 0], pts[:, 1], c=TOPO_COLORS[t],
                           marker=TOPO_MARKERS[t], s=40, alpha=0.25,
                           edgecolors="none")
                if len(pts) < 2:
                    ax.plot(pts[:, 0], pts[:, 1],
                            linestyle=FP_LINESTYLES[fp],
                            color=TOPO_COLORS[t], linewidth=2.5,
                            marker=TOPO_MARKERS[t], markersize=5)
                    continue
                front_idx = _pareto_front(pts)
                front = pts[front_idx]
                front = front[front[:, 0].argsort()]
                ax.plot(front[:, 0], front[:, 1],
                        linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2.5)
        ax.set_xlabel(r"Mean $T_{end}$ (rounds)")
        ax.set_ylabel(r"Mean $\Omega$ (messages)")
        ax.set_title(f"N = {n}")
        ax.yaxis.set_major_formatter(thousands_formatter())
    _build_combined_legend(axes[0])
    fig.suptitle("Byzantine Robustness — Pareto Frontiers: Speed vs Cost"
                 "\n(solid = fp 0.15, dashed = fp 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_03_pareto.png")
    plt.close(fig)
    print("  [03] rq3b_03_pareto.png")


# --- figure 4: topology modulates trade-offs ---

def fig_topology_tradeoff():
    fig, axes = plt.subplots(1, len(N_RQ2),
                             figsize=(7 * len(N_RQ2), FIG_SINGLE_H + 0.5))
    for ax, n in zip(axes, N_RQ2):
        sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6)]
        sub_reliable = sub[sub["R"] >= 0.95]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub_reliable[(sub_reliable["topology"] == t) &
                                    (sub_reliable["failureProbability"] == fp)]
                if len(tsub) == 0:
                    continue
                ax.scatter(tsub["M_mean"], tsub["T_end_mean"],
                           c=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                           s=80, alpha=_FP_ALPHA[fp],
                           edgecolors="white", linewidth=0.5)
        ax.set_xlabel(r"Mean $M$ (messages per node)")
        ax.set_ylabel(r"Mean $T_{end}$ (rounds)")
        ax.set_title(f"N = {n} ($R \\geq 0.95$ only)")
    _build_scatter_legend(axes[0])
    fig.suptitle("Byzantine Robustness — Topology Modulates Trade-offs: "
                 r"Normalised Cost vs Speed — k=6"
                 "\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_04_topology_tradeoff.png")
    plt.close(fig)
    print("  [04] rq3b_04_topology_tradeoff.png")


# --- figure 5: optimal settings per topology ---

def fig_optimal_settings():
    fig, axes = plt.subplots(1, len(N_RQ2),
                             figsize=(7 * len(N_RQ2), FIG_SINGLE_H + 0.5))
    for ax, n in zip(axes, N_RQ2):
        sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6) &
                  (agg["R"] >= 0.95)]
        x_labels = []
        bars_fastest = []
        bars_cheapest = []
        annot_fastest = []
        annot_cheapest = []
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                tag = f"{topo_label(t)}\n{FP_SHORT_LABELS[fp]}"
                x_labels.append(tag)
                if len(tsub) == 0:
                    bars_fastest.append(0)
                    bars_cheapest.append(0)
                    annot_fastest.append(None)
                    annot_cheapest.append(None)
                    continue
                best = tsub.loc[tsub["T_end_mean"].idxmin()]
                cheapest = tsub.loc[tsub["Omega_mean"].idxmin()]
                bars_fastest.append(best["T_end_mean"])
                bars_cheapest.append(cheapest["T_end_mean"])
                annot_fastest.append(best)
                annot_cheapest.append(cheapest)

        x = np.arange(len(x_labels))
        width = 0.35
        ax.bar(x - width / 2, bars_fastest, width,
               label=r"Fastest (min $T_{end}$)", color="#1565C0",
               edgecolor="white")
        ax.bar(x + width / 2, bars_cheapest, width,
               label=r"Cheapest (min $\Omega$)", color="#81D4FA",
               edgecolor="white")
        ax.set_xticks(x)
        ax.set_xticklabels(x_labels, rotation=25, ha="right",
                           fontsize=TICK_SIZE - 1)
        ax.set_ylabel(r"Mean $T_{end}$")
        ax.set_title(f"N = {n} ($R \\geq 0.95$)")

        for i in range(len(x_labels)):
            if annot_fastest[i] is not None:
                r = annot_fastest[i]
                ax.text(i - width / 2, bars_fastest[i] + 0.3,
                        f"vf={r['viewFraction']:.1f}\nfo={int(r['fanOut'])}",
                        ha="center", va="bottom", fontsize=ANNOT_SIZE - 1)
            if annot_cheapest[i] is not None:
                r = annot_cheapest[i]
                ax.text(i + width / 2, bars_cheapest[i] + 0.3,
                        f"vf={r['viewFraction']:.1f}\nfo={int(r['fanOut'])}",
                        ha="center", va="bottom", fontsize=ANNOT_SIZE - 1)

    axes[0].legend(frameon=True)
    fig.suptitle("Byzantine Robustness — Optimal Settings per Topology: "
                 r"Fastest vs Cheapest ($R \geq 0.95$)"
                 "\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_05_optimal_settings.png")
    plt.close(fig)
    print("  [05] rq3b_05_optimal_settings.png")


# --- figure 6: scaling effects on trade-offs ---

def fig_scaling_tradeoffs():
    _scale_colors = {100: "#42A5F5", 500: "#E91E63",
                     1000: "#7B1FA2", 5000: "#FF6F00"}
    _scale_markers = {100: "o", 500: "^", 1000: "s", 5000: "P"}
    n_cols = len(TOPOLOGIES) * len(FP_VALUES)
    fig, axes = plt.subplots(1, n_cols,
                             figsize=(5 * n_cols, FIG_SINGLE_H))
    axes = np.atleast_1d(axes)
    idx = 0
    for t in TOPOLOGIES:
        for fp in FP_VALUES:
            ax = axes[idx]
            for n_val in N_RQ2:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n_val) &
                          (agg["k"] == 6) &
                          (agg["failureProbability"] == fp)]
                if len(sub) == 0:
                    continue
                ax.scatter(sub["T_end_mean"], sub["Omega_mean"],
                           c=_scale_colors.get(n_val, "#666666"),
                           marker=_scale_markers.get(n_val, "D"),
                           s=50, alpha=0.6, label=f"N={n_val}",
                           edgecolors="white", linewidth=0.5)
            ax.set_xlabel(r"Mean $T_{end}$")
            ax.set_ylabel(r"Mean $\Omega$" if idx == 0 else "")
            ax.set_title(f"{topo_label(t)}, {FP_SHORT_LABELS[fp]}")
            ax.yaxis.set_major_formatter(compact_formatter())
            if idx == 0:
                ax.legend(frameon=True)
            idx += 1
    fig.suptitle("Byzantine Robustness — Scaling Effects: Trade-off Curves"
                 " — k=6\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_06_scaling_tradeoffs.png")
    plt.close(fig)
    print("  [06] rq3b_06_scaling_tradeoffs.png")


# --- figure 7a: reliability threshold (min fanOut) ---

def fig_reliability_threshold():
    fig, axes = plt.subplots(1, len(N_RQ2),
                             figsize=(6 * len(N_RQ2), FIG_SINGLE_H))
    for ax, n in zip(axes, N_RQ2):
        sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6)]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                if len(tsub) == 0:
                    continue
                thresholds = []
                for vf in sorted(tsub["viewFraction"].unique()):
                    vfsub = tsub[(tsub["viewFraction"] == vf) &
                                 (tsub["R"] >= 0.95)]
                    if len(vfsub) > 0:
                        thresholds.append((vf, vfsub["fanOut"].min()))
                    else:
                        thresholds.append((vf, np.nan))
                if not thresholds:
                    continue
                vfs, fos = zip(*thresholds)
                ax.plot(vfs, fos, marker="o", color=TOPO_COLORS[t],
                        linestyle=FP_LINESTYLES[fp],
                        linewidth=2, markersize=7)
        ax.set_xlabel("viewFraction")
        ax.set_ylabel(r"Minimum fanOut for $R \geq 0.95$")
        ax.set_title(f"N = {n}")
        ax.set_xticks([0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_yticks([1, 2, 3, 4, 5])
    _build_combined_legend(axes[0])
    fig.suptitle("Byzantine Robustness — Reliability Threshold: "
                 r"Minimum fanOut for $R \geq 0.95$ — k=6"
                 "\n(solid = fp 0.15, dashed = fp 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_07a_reliability_threshold_fo.png")
    plt.close(fig)
    print("  [07a] rq3b_07a_reliability_threshold_fo.png")


# --- figure 7b: minimum viewfraction threshold ---

def fig_reliability_threshold_vf():
    fig, axes = plt.subplots(1, len(N_RQ2),
                             figsize=(6 * len(N_RQ2), FIG_SINGLE_H))
    for ax, n in zip(axes, N_RQ2):
        sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6)]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                if len(tsub) == 0:
                    continue
                thresholds = []
                for fo in sorted(tsub["fanOut"].unique()):
                    fosub = tsub[(tsub["fanOut"] == fo) &
                                 (tsub["R"] >= 0.95)]
                    if len(fosub) > 0:
                        thresholds.append((fo, fosub["viewFraction"].min()))
                    else:
                        thresholds.append((fo, np.nan))
                if not thresholds:
                    continue
                fos, vfs = zip(*thresholds)
                ax.plot(fos, vfs, marker="o", color=TOPO_COLORS[t],
                        linestyle=FP_LINESTYLES[fp],
                        linewidth=2, markersize=7)
        ax.set_xlabel("fanOut")
        ax.set_ylabel(r"Minimum viewFraction for $R \geq 0.95$")
        ax.set_title(f"N = {n}")
        ax.set_xticks([1, 2, 3, 4, 5])
        ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
    _build_combined_legend(axes[0])
    fig.suptitle("Byzantine Robustness — Reliability Threshold: "
                 r"Minimum viewFraction for $R \geq 0.95$ — k=6"
                 "\n(solid = fp 0.15, dashed = fp 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_07b_reliability_threshold_vf.png")
    plt.close(fig)
    print("  [07b] rq3b_07b_reliability_threshold_vf.png")


# --- figure 8: efficiency - f_eff vs t_end ---

def fig_efficiency():
    fig, axes = plt.subplots(1, len(N_VALUES),
                             figsize=(7 * len(N_VALUES), FIG_SINGLE_H + 0.5))
    if len(N_VALUES) == 1:
        axes = [axes]
    for ax, n in zip(axes, N_VALUES):
        sub = agg[(agg["nodeCount"] == n) & (agg["R"] >= 0.95)]
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                tsub = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]
                ax.scatter(tsub["T_end_mean"], tsub["F_eff_mean"],
                           c=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                           s=60, alpha=_FP_ALPHA[fp],
                           edgecolors="white", linewidth=0.5)
        ax.set_xlabel(r"Mean $T_{end}$ (rounds)")
        ax.set_ylabel(r"Mean $F_{eff}$ (messages per informed node)")
        ax.set_title(f"N = {n} ($R \\geq 0.95$)")
    _build_scatter_legend(axes[0])
    fig.suptitle("Byzantine Robustness — Efficiency: "
                 r"$F_{eff}$ vs Speed — Reliable Configurations Only"
                 "\n(fp = 0.15 vs 0.30)")
    fig.tight_layout()
    fig.savefig(OUT / "rq3b_08_efficiency.png")
    plt.close(fig)
    print("  [08] rq3b_08_efficiency.png")


# --- comprehensive results report ---

def _md_table(headers, rows, aligns=None):
    if aligns is None:
        aligns = ["l"] + ["r"] * (len(headers) - 1)
    sep = "|"
    for h, a in zip(headers, aligns):
        sep += " ---: |" if a == "r" else " :--- |"
    hdr = "| " + " | ".join(headers) + " |"
    lines = [hdr, sep]
    for r in rows:
        lines.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(lines)


def _fmt(v, f=".2f"):
    if v is None or (isinstance(v, float) and np.isnan(v)):
        return "N/A"
    return f"{v:{f}}"


def generate_report():
    rpt = []
    rpt.append("# RQ3B Results Report  —  Byzantine Robustness Trade-off "
               "Analysis\n")
    rpt.append("*Auto-generated by `rq3b_analysis.py`*\n")

    # --- section 1: overview ---
    rpt.append("## 1. Data Overview\n")
    rpt.append("- **Sources:** `data/rq1b.csv` + `data/rq2b.csv` "
               "(merged, deduplicated)")
    rpt.append(f"- **Unique trial rows:** {len(df):,}")
    rpt.append(f"- **Unique configurations:** {len(agg)}")
    rpt.append(f"- **Failure-probability levels:** "
               f"{', '.join(str(fp) for fp in FP_VALUES)}")
    for fp in FP_VALUES:
        cnt = len(agg[agg["failureProbability"] == fp])
        rpt.append(f"  - fp={fp}: {cnt} configurations")
    rpt.append("")

    # --- section 2: speed vs cost per fp ---
    rpt.append("## 2. Speed vs Cost  —  T_end and Omega Ranges per Topology "
               "and Failure Level\n")
    for fp in FP_VALUES:
        rpt.append(f"### Failure Probability = {fp}\n")
        for n in N_VALUES:
            rpt.append(f"#### N = {n}\n")
            headers = ["Topology", "T_end min", "T_end max",
                       "Ω min", "Ω max", "Cost range (Ω)"]
            rows = []
            for t in TOPOLOGIES:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                if len(sub) == 0:
                    rows.append([topo_label(t)] + ["N/A"] * 5)
                    continue
                rows.append([
                    topo_label(t),
                    _fmt(sub["T_end_mean"].min()),
                    _fmt(sub["T_end_mean"].max()),
                    _fmt(sub["Omega_mean"].min(), ".0f"),
                    _fmt(sub["Omega_mean"].max(), ".0f"),
                    _fmt(sub["Omega_mean"].max() - sub["Omega_mean"].min(),
                         ".0f"),
                ])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 3: pareto-optimal configurations ---
    rpt.append("## 3. Pareto-Optimal Configurations (Speed vs Cost)\n")
    rpt.append("Configurations on the Pareto frontier — no other config for "
               "the same topology, N, and fp is both faster AND cheaper.\n")
    for fp in FP_VALUES:
        rpt.append(f"### Failure Probability = {fp}\n")
        for n in N_VALUES:
            for t in TOPOLOGIES:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                if len(sub) == 0:
                    continue
                pts = sub[["T_end_mean", "Omega_mean"]].values
                front_idx = _pareto_front(pts)
                front_rows = sub.iloc[front_idx].sort_values("T_end_mean")
                rpt.append(f"#### {topo_label(t)}, N={n} "
                           f"({len(front_idx)} Pareto-optimal)\n")
                headers = ["k", "vf", "fo", "T_end", "Ω", "M", "R", "F_eff"]
                rows = []
                for _, r in front_rows.iterrows():
                    rows.append([
                        int(r["k"]),
                        _fmt(r["viewFraction"], ".1f"),
                        int(r["fanOut"]),
                        _fmt(r["T_end_mean"]),
                        _fmt(r["Omega_mean"], ".0f"),
                        _fmt(r["M_mean"]),
                        _fmt(r["R"], ".3f"),
                        _fmt(r["F_eff_mean"]),
                    ])
                rpt.append(_md_table(headers, rows))
                rpt.append("")

    # --- section 4: optimal settings (reliable only) ---
    rpt.append("## 4. Optimal Settings per Topology (R >= 0.95)\n")
    for fp in FP_VALUES:
        rpt.append(f"### Failure Probability = {fp}\n")
        for n in N_VALUES:
            rpt.append(f"#### N = {n}\n")
            headers = ["Topology", "Criterion", "k", "vf", "fo",
                       "T_end", "Ω", "M", "R", "F_eff"]
            rows = []
            for t in TOPOLOGIES:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp) &
                          (agg["R"] >= 0.95)]
                if len(sub) == 0:
                    rows.append([topo_label(t), "—", "—", "—", "—",
                                 "(no reliable config)", "", "", "", ""])
                    continue
                fastest = sub.loc[sub["T_end_mean"].idxmin()]
                cheapest = sub.loc[sub["Omega_mean"].idxmin()]
                for label, r in [("Fastest", fastest), ("Cheapest", cheapest)]:
                    rows.append([
                        topo_label(t), label, int(r["k"]),
                        _fmt(r["viewFraction"], ".1f"), int(r["fanOut"]),
                        _fmt(r["T_end_mean"]),
                        _fmt(r["Omega_mean"], ".0f"),
                        _fmt(r["M_mean"]),
                        _fmt(r["R"], ".3f"),
                        _fmt(r["F_eff_mean"]),
                    ])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 5a: reliability threshold - min fanOut ---
    rpt.append("## 5a. Reliability Threshold  —  Minimum fanOut for "
               "R >= 0.95\n")
    rpt.append("Per topology, viewFraction, and failure probability "
               "(k=6).\n")
    for fp in FP_VALUES:
        rpt.append(f"### fp = {fp}\n")
        for n in N_RQ2:
            rpt.append(f"#### N = {n}\n")
            sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6) &
                      (agg["failureProbability"] == fp)]
            vf_vals = sorted(sub["viewFraction"].unique())
            if not vf_vals:
                rpt.append("_No data._\n")
                continue
            headers = ["Topology"] + [f"vf={vf:.1f}" for vf in vf_vals]
            rows = []
            for t in TOPOLOGIES:
                vals = []
                for vf in vf_vals:
                    vfsub = sub[(sub["topology"] == t) &
                                (sub["viewFraction"] == vf) &
                                (sub["R"] >= 0.95)]
                    if len(vfsub) > 0:
                        vals.append(str(int(vfsub["fanOut"].min())))
                    else:
                        vals.append("none")
                rows.append([topo_label(t)] + vals)
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 5b: reliability threshold - min viewFraction ---
    rpt.append("## 5b. Reliability Threshold  —  Minimum viewFraction for "
               "R >= 0.95\n")
    rpt.append("Per topology, fanOut, and failure probability (k=6).\n")
    for fp in FP_VALUES:
        rpt.append(f"### fp = {fp}\n")
        for n in N_RQ2:
            rpt.append(f"#### N = {n}\n")
            sub = agg[(agg["nodeCount"] == n) & (agg["k"] == 6) &
                      (agg["failureProbability"] == fp)]
            fo_vals = sorted(sub["fanOut"].unique())
            if not fo_vals:
                rpt.append("_No data._\n")
                continue
            headers = ["Topology"] + [f"fo={int(fo)}" for fo in fo_vals]
            rows = []
            for t in TOPOLOGIES:
                vals = []
                for fo in fo_vals:
                    fosub = sub[(sub["topology"] == t) &
                                (sub["fanOut"] == fo) &
                                (sub["R"] >= 0.95)]
                    if len(fosub) > 0:
                        vals.append(f"{fosub['viewFraction'].min():.1f}")
                    else:
                        vals.append("none")
                rows.append([topo_label(t)] + vals)
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 6: cross-fp comparison ---
    rpt.append("## 6. Cross-Failure-Level Comparison\n")
    rpt.append("How does increasing failure probability from 0.15 to 0.30 "
               "affect the best achievable trade-offs?\n")
    for n in N_VALUES:
        rpt.append(f"### N = {n}, R >= 0.95\n")
        headers = ["Topology", "fp",
                   "Fastest T_end", "at (vf, fo)",
                   "Cheapest Ω", "at (vf, fo)"]
        rows = []
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp) &
                          (agg["R"] >= 0.95)]
                if len(sub) == 0:
                    rows.append([topo_label(t), str(fp),
                                 "N/A", "N/A", "N/A", "N/A"])
                    continue
                f_row = sub.loc[sub["T_end_mean"].idxmin()]
                c_row = sub.loc[sub["Omega_mean"].idxmin()]
                rows.append([
                    topo_label(t), str(fp),
                    _fmt(f_row["T_end_mean"]),
                    f"vf={f_row['viewFraction']:.1f}, "
                    f"fo={int(f_row['fanOut'])}",
                    _fmt(c_row["Omega_mean"], ".0f"),
                    f"vf={c_row['viewFraction']:.1f}, "
                    f"fo={int(c_row['fanOut'])}",
                ])
        rpt.append(_md_table(headers, rows))
        rpt.append("")

    # --- section 7: universality vs topology-dependence ---
    rpt.append("## 7. Universality vs Topology-Dependence\n")
    rpt.append("Are the optimal settings the same across topologies and "
               "failure levels?\n")
    for n in N_RQ2:
        rpt.append(f"### N = {n}, k=6, R >= 0.95\n")
        headers = ["Topology", "fp",
                   "Fastest (vf, fo)", "Cheapest (vf, fo)"]
        rows = []
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = agg[(agg["topology"] == t) &
                          (agg["nodeCount"] == n) &
                          (agg["k"] == 6) &
                          (agg["failureProbability"] == fp) &
                          (agg["R"] >= 0.95)]
                if len(sub) == 0:
                    rows.append([topo_label(t), str(fp), "N/A", "N/A"])
                    continue
                f_row = sub.loc[sub["T_end_mean"].idxmin()]
                c_row = sub.loc[sub["Omega_mean"].idxmin()]
                rows.append([
                    topo_label(t), str(fp),
                    f"vf={f_row['viewFraction']:.1f}, "
                    f"fo={int(f_row['fanOut'])}",
                    f"vf={c_row['viewFraction']:.1f}, "
                    f"fo={int(c_row['fanOut'])}",
                ])
        rpt.append(_md_table(headers, rows))
        rpt.append("")

    # --- section 8: scaling effects ---
    rpt.append("## 8. Scaling Effects on Trade-offs\n")
    rpt.append("How trade-off ranges change with network size (k=6).\n")
    for fp in FP_VALUES:
        rpt.append(f"### fp = {fp}\n")
        headers = (["Topology"]
                   + [f"T_end @{n}" for n in N_VALUES]
                   + [f"Ω @{n}" for n in N_VALUES])
        rows = []
        for t in TOPOLOGIES:
            row = [topo_label(t)]
            for n in N_VALUES:
                s = agg[(agg["topology"] == t) &
                        (agg["nodeCount"] == n) &
                        (agg["k"] == 6) &
                        (agg["failureProbability"] == fp)]
                if len(s) > 0:
                    row.append(f"{s['T_end_mean'].min():.1f}–"
                               f"{s['T_end_mean'].max():.1f}")
                else:
                    row.append("N/A")
            for n in N_VALUES:
                s = agg[(agg["topology"] == t) &
                        (agg["nodeCount"] == n) &
                        (agg["k"] == 6) &
                        (agg["failureProbability"] == fp)]
                if len(s) > 0:
                    row.append(f"{s['Omega_mean'].min():.0f}–"
                               f"{s['Omega_mean'].max():.0f}")
                else:
                    row.append("N/A")
            rows.append(row)
        rpt.append(_md_table(headers, rows))
        rpt.append("")

    # --- section 9: efficiency ---
    rpt.append("## 9. Efficiency  —  F_eff for Reliable Configurations\n")
    rpt.append("F_eff = messages per informed node. Lower is more "
               "efficient. R >= 0.95.\n")
    for fp in FP_VALUES:
        rpt.append(f"### fp = {fp}\n")
        for n in N_VALUES:
            rpt.append(f"#### N = {n}\n")
            sub = agg[(agg["nodeCount"] == n) & (agg["R"] >= 0.95) &
                      (agg["failureProbability"] == fp)]
            headers = ["Topology", "Best F_eff", "T_end at best",
                       "Worst F_eff", "T_end at worst"]
            rows = []
            for t in TOPOLOGIES:
                tsub = sub[sub["topology"] == t]
                if len(tsub) == 0:
                    rows.append([topo_label(t)] + ["N/A"] * 4)
                    continue
                best = tsub.loc[tsub["F_eff_mean"].idxmin()]
                worst = tsub.loc[tsub["F_eff_mean"].idxmax()]
                rows.append([
                    topo_label(t),
                    _fmt(best["F_eff_mean"]),
                    _fmt(best["T_end_mean"]),
                    _fmt(worst["F_eff_mean"]),
                    _fmt(worst["T_end_mean"]),
                ])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 10: findings ---
    rpt.append("## 10. Findings Summary\n")

    reliable = agg[agg["R"] >= 0.95]
    total = len(agg)
    rpt.append(f"1. **Configurations with R >= 0.95:** {len(reliable)} / "
               f"{total} ({len(reliable) / total * 100:.1f}%)")

    for fp in FP_VALUES:
        fp_rel = reliable[reliable["failureProbability"] == fp]
        fp_total = len(agg[agg["failureProbability"] == fp])
        pct = len(fp_rel) / fp_total * 100 if fp_total > 0 else 0
        rpt.append(f"   - fp={fp}: {len(fp_rel)} / {fp_total} ({pct:.1f}%)")

    item = 2
    for n in N_VALUES:
        for fp in FP_VALUES:
            sub = agg[(agg["nodeCount"] == n) &
                      (agg["failureProbability"] == fp) &
                      (agg["R"] >= 0.95)]
            if len(sub) == 0:
                continue
            overall_fastest = sub.loc[sub["T_end_mean"].idxmin()]
            overall_cheapest = sub.loc[sub["Omega_mean"].idxmin()]
            rpt.append(
                f"{item}. **Overall fastest reliable config "
                f"(N={n}, fp={fp}):** "
                f"{topo_label(overall_fastest['topology'])} "
                f"k={int(overall_fastest['k'])}, "
                f"vf={overall_fastest['viewFraction']:.1f}, "
                f"fo={int(overall_fastest['fanOut'])} → "
                f"T_end={overall_fastest['T_end_mean']:.2f}")
            item += 1
            rpt.append(
                f"{item}. **Overall cheapest reliable config "
                f"(N={n}, fp={fp}):** "
                f"{topo_label(overall_cheapest['topology'])} "
                f"k={int(overall_cheapest['k'])}, "
                f"vf={overall_cheapest['viewFraction']:.1f}, "
                f"fo={int(overall_cheapest['fanOut'])} → "
                f"Ω={overall_cheapest['Omega_mean']:.0f}")
            item += 1
    rpt.append("")

    report_path = OUT / "rq3b_results_report.md"
    report_path.write_text("\n".join(rpt), encoding="utf-8")
    print(f"\n  Report saved to {report_path}")


# --- main ---

if __name__ == "__main__":
    print(f"\nGenerating RQ3B figures → {OUT}/\n")
    fig_speed_vs_cost()
    fig_reliability_vs_cost()
    fig_pareto()
    fig_topology_tradeoff()
    fig_optimal_settings()
    fig_scaling_tradeoffs()
    fig_reliability_threshold()
    fig_reliability_threshold_vf()
    fig_efficiency()
    generate_report()
    print(f"\nDone. {len(list(OUT.glob('*.png')))} figures + report "
          f"saved to {OUT}/")
