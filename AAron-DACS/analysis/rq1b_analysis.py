#!/usr/bin/env python3

import pathlib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import matplotlib.lines as mlines
import seaborn as sns

from _style import (
    TOPOLOGIES, TOPO_COLORS, TOPO_LABELS, TOPO_MARKERS,
    FP_VALUES, FP_LINESTYLES, FP_LABELS, FP_SHORT_LABELS,
    FIG_SINGLE_H, FIG_SMALL_W,
    SUPTITLE_SIZE, SUBTITLE_SIZE, ANNOT_SIZE, LEGEND_SIZE, TICK_SIZE,
    HEATMAP_ANNOT,
    apply_theme, topo_label, thousands_formatter, fix_heatmap_text_contrast,
)

# --- paths ---

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "rq1b.csv"
OUT  = ROOT / "analysis" / "figures" / "rq1b"
OUT.mkdir(parents=True, exist_ok=True)

# --- theme ---

apply_theme()

# --- constants ---

SETTINGS = {"A": (1.0, 3), "B": (0.6, 2), "C": (0.2, 1)}
K_VALUES = [3, 6, 9]
N_VALUES = [50, 100, 200, 500, 1000, 5000]

FP_HATCHES = {0.15: "", 0.30: "///"}

# --- load ---

print(f"Loading {DATA} ...")
df = pd.read_csv(DATA)
print(f"  {len(df):,} rows, {df['topology'].nunique()} topologies, "
      f"{df['k'].nunique()} k-values, {df['nodeCount'].nunique()} N-values, "
      f"fp ∈ {sorted(df['failureProbability'].unique())}")

# --- helper: per-config aggregates ---


def config_agg(data):
    """Aggregate per-trial rows into per-configuration summary statistics."""
    return data.groupby(
        ["topology", "nodeCount", "k", "viewFraction", "fanOut",
         "failureProbability"]
    ).agg(
        T_end_mean   = ("T_end",   "mean"),
        T_end_median = ("T_end",   "median"),
        T_end_std    = ("T_end",   "std"),
        Omega_mean   = ("Omega",   "mean"),
        Omega_std    = ("Omega",   "std"),
        M_mean       = ("M",       "mean"),
        alpha_mean   = ("alpha",   "mean"),
        R            = ("R_run",   "mean"),
        L50_mean     = ("L_0.5",   lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
        L90_mean     = ("L_0.9",   lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
        L100_mean    = ("L_1.0",   lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
        F_eff_mean   = ("F_eff",   "mean"),
        n_trials     = ("T_end",   "count"),
    ).reset_index()


agg = config_agg(df)


def sel(data, setting="A", k=3):
    """Select rows matching a peer-selection setting and k value (both fp levels)."""
    vf, fo = SETTINGS[setting]
    mask = (data["viewFraction"] == vf) & (data["fanOut"] == fo) & (data["k"] == k)
    return data[mask].copy()


# --- legend helpers ---


def _fp_topo_legend(ax, loc="best", ncol=1):
    """Build a combined topology × fp legend."""
    handles = []
    for t in TOPOLOGIES:
        for fp in FP_VALUES:
            handles.append(mlines.Line2D(
                [], [], color=TOPO_COLORS[t], linestyle=FP_LINESTYLES[fp],
                linewidth=2, marker="o", markersize=5,
                label=f"{topo_label(t)} {FP_SHORT_LABELS[fp]}"))
    ax.legend(handles=handles, frameon=True, loc=loc, ncol=ncol,
              fontsize=LEGEND_SIZE)


def _fp_bar_legend(ax):
    """Build a combined topology × fp legend for bar charts."""
    handles = []
    for t in TOPOLOGIES:
        handles.append(mpatches.Patch(
            facecolor=TOPO_COLORS[t], edgecolor="white",
            label=topo_label(t)))
    handles.append(mpatches.Patch(
        facecolor="grey", edgecolor="black", label=FP_SHORT_LABELS[0.15]))
    handles.append(mpatches.Patch(
        facecolor="grey", edgecolor="black", hatch="///",
        label=FP_SHORT_LABELS[0.30]))
    ax.legend(handles=handles, frameon=True, fontsize=LEGEND_SIZE)



#  FIGURE 1 — Topology comparison bar charts


def fig_topology_comparison():
    n_rows = [100, 1000, 5000]
    metrics = [
        ("T_end_mean", r"Mean $T_{end}$ (rounds)", ".1f"),
        ("Omega_mean", r"Mean $\Omega$ (messages)", ".0f"),
        ("R",          "Reliability $R$",           ".3f"),
    ]
    fig, axes = plt.subplots(len(n_rows), len(metrics),
                             figsize=(17, FIG_SINGLE_H * len(n_rows)))

    n_topo = len(TOPOLOGIES)
    n_fp   = len(FP_VALUES)
    bar_w  = 0.35

    for row, n in enumerate(n_rows):
        ref = sel(agg, "A", k=3)
        ref = ref[ref["nodeCount"] == n]
        for col, (metric, label, fmt) in enumerate(metrics):
            ax = axes[row, col]
            for fi, fp in enumerate(FP_VALUES):
                sub_fp = ref[ref["failureProbability"] == fp]
                bars = []
                for t in TOPOLOGIES:
                    s = sub_fp[sub_fp["topology"] == t]
                    bars.append(s[metric].values[0] if len(s) > 0 else np.nan)
                colors = [TOPO_COLORS[t] for t in TOPOLOGIES]
                x = np.arange(n_topo) + fi * bar_w
                ax.bar(x, bars, bar_w, color=colors, edgecolor="white",
                       linewidth=0.8, hatch=FP_HATCHES[fp])
                bar_max = np.nanmax(bars) if not all(np.isnan(b) for b in bars) else 1
                for i, v in enumerate(bars):
                    if np.isnan(v):
                        continue
                    ax.text(x[i], v + bar_max * 0.02, f"{v:{fmt}}",
                            ha="center", va="bottom", fontsize=ANNOT_SIZE - 1)
            ax.set_xticks(np.arange(n_topo) + bar_w / 2)
            ax.set_xticklabels([topo_label(t) for t in TOPOLOGIES], rotation=15)
            ax.set_ylabel(label if col == 0 or metric == "R" else "")
            if row == 0:
                ax.set_title(label.split("$")[0].strip() if "$" not in label
                             else label)
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.45, 0.5),
                            xycoords="axes fraction", textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold", rotation=90,
                            ha="center", va="center")

    _fp_bar_legend(axes[0, -1])
    fig.suptitle("Byzantine Robustness — Topology Comparison\n"
                 "k=3, Setting A · fp=0.15 (solid) vs fp=0.30 (hatched)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_01_topology_comparison.png")
    plt.close(fig)
    print("  [01] rq1b_01_topology_comparison.png")



#  FIGURE 2 — Scaling: T_end vs N


def fig_scaling_T_end():
    fig, axes = plt.subplots(1, 3, figsize=(16, FIG_SINGLE_H), sharey=False)
    for ax, k in zip(axes, K_VALUES):
        ref = sel(agg, "A", k=k)
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = ref[(ref["topology"] == t) &
                          (ref["failureProbability"] == fp)].sort_values("nodeCount")
                ax.plot(sub["nodeCount"], sub["T_end_mean"],
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2, markersize=6,
                        label=f"{topo_label(t)} {FP_SHORT_LABELS[fp]}")
        ax.set_xlabel("Node count $N$")
        ax.set_ylabel(r"Mean $T_{end}$")
        ax.set_title(f"k = {k}")
        ax.set_xscale("log")
        ax.set_xticks(N_VALUES)
        ax.set_xticklabels([str(n) for n in N_VALUES])
    _fp_topo_legend(axes[0], ncol=2)
    fig.suptitle(r"Byzantine Robustness — Scaling: $T_{end}$ vs $N$"
                 "\nSetting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_02_scaling_T_end.png")
    plt.close(fig)
    print("  [02] rq1b_02_scaling_T_end.png")



#  FIGURE 3 — Scaling: Omega vs N


def fig_scaling_Omega():
    fig, axes = plt.subplots(1, 3, figsize=(16, FIG_SINGLE_H), sharey=False)
    for ax, k in zip(axes, K_VALUES):
        ref = sel(agg, "A", k=k)
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = ref[(ref["topology"] == t) &
                          (ref["failureProbability"] == fp)].sort_values("nodeCount")
                ax.plot(sub["nodeCount"], sub["Omega_mean"],
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2, markersize=6)
        ax.set_xlabel("Node count $N$")
        ax.set_ylabel(r"Mean $\Omega$")
        ax.set_title(f"k = {k}")
        ax.set_xscale("log")
        ax.set_xticks(N_VALUES)
        ax.set_xticklabels([str(n) for n in N_VALUES])
        ax.yaxis.set_major_formatter(thousands_formatter())
    _fp_topo_legend(axes[0], ncol=2)
    fig.suptitle(r"Byzantine Robustness — Scaling: $\Omega$ vs $N$"
                 "\nSetting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_03_scaling_Omega.png")
    plt.close(fig)
    print("  [03] rq1b_03_scaling_Omega.png")



#  FIGURE 4 — Scaling: latency thresholds vs N


def fig_scaling_latency():
    metrics = [("L50_mean", r"$L_{0.5}$"), ("L90_mean", r"$L_{0.9}$"),
               ("L100_mean", r"$L_{1.0}$")]
    fig, axes = plt.subplots(len(metrics), len(K_VALUES),
                             figsize=(16, FIG_SINGLE_H * len(metrics)),
                             sharey="row")
    for col, k in enumerate(K_VALUES):
        ref = sel(agg, "A", k=k)
        for row, (metric, mlabel) in enumerate(metrics):
            ax = axes[row, col]
            for t in TOPOLOGIES:
                for fp in FP_VALUES:
                    sub = ref[(ref["topology"] == t) &
                              (ref["failureProbability"] == fp)].sort_values("nodeCount")
                    ax.plot(sub["nodeCount"], sub[metric],
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=TOPO_COLORS[t], linewidth=2, markersize=6)
            ax.set_xscale("log")
            ax.set_xticks(N_VALUES)
            ax.set_xticklabels([str(n) for n in N_VALUES])
            if col == 0:
                ax.set_ylabel(f"Mean {mlabel}")
            if row == len(metrics) - 1:
                ax.set_xlabel("Node count $N$")
            if row == 0:
                ax.set_title(f"k = {k}")
    _fp_topo_legend(axes[0, 0], ncol=2)
    fig.suptitle("Byzantine Robustness — Scaling: Latency Thresholds vs $N$"
                 "\nSetting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_04_scaling_latency.png")
    plt.close(fig)
    print("  [04] rq1b_04_scaling_latency.png")



#  FIGURE 4b — Scaling: M (messages per node) vs N


def fig_scaling_M():
    fig, axes = plt.subplots(1, len(K_VALUES), figsize=(16, FIG_SINGLE_H),
                             sharey=True)
    for col, k in enumerate(K_VALUES):
        ax = axes[col]
        ref = sel(agg, "A", k=k)
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = ref[(ref["topology"] == t) &
                          (ref["failureProbability"] == fp)].sort_values("nodeCount")
                ax.plot(sub["nodeCount"], sub["M_mean"],
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2, markersize=6)
        ax.set_xscale("log")
        ax.set_xticks(N_VALUES)
        ax.set_xticklabels([str(n) for n in N_VALUES])
        if col == 0:
            ax.set_ylabel(r"Mean $M$ (messages per node)")
        ax.set_xlabel("Node count $N$")
        ax.set_title(f"k = {k}")
    _fp_topo_legend(axes[0], ncol=2)
    fig.suptitle("Byzantine Robustness — Scaling: Messages per Node ($M$) vs $N$"
                 "\nSetting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_04b_scaling_M.png")
    plt.close(fig)
    print("  [04b] rq1b_04b_scaling_M.png")



#  FIGURE 5 — Consistency across peer-selection settings


def fig_consistency_settings():
    n_show = [100, 1000, 5000]
    n_settings = len(SETTINGS)
    n_topo = len(TOPOLOGIES)
    bar_w = 0.35

    fig, axes = plt.subplots(len(n_show), n_settings,
                             figsize=(16, FIG_SINGLE_H * len(n_show)))
    for row, n in enumerate(n_show):
        for col, (label, (vf, fo)) in enumerate(SETTINGS.items()):
            ax = axes[row, col]
            sub = agg[(agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                       (agg["k"] == 3) & (agg["nodeCount"] == n)]
            for fi, fp in enumerate(FP_VALUES):
                sub_fp = sub[sub["failureProbability"] == fp]
                bars = []
                for t in TOPOLOGIES:
                    s = sub_fp[sub_fp["topology"] == t]
                    bars.append(s["T_end_mean"].values[0] if len(s) > 0 else np.nan)
                colors = [TOPO_COLORS[t] for t in TOPOLOGIES]
                x = np.arange(n_topo) + fi * bar_w
                ax.bar(x, bars, bar_w, color=colors, edgecolor="white",
                       linewidth=0.8, hatch=FP_HATCHES[fp])
                bar_max = np.nanmax(bars) if not all(np.isnan(b) for b in bars) else 1
                for i, v in enumerate(bars):
                    if np.isnan(v):
                        continue
                    ax.text(x[i], v + bar_max * 0.02, f"{v:.1f}",
                            ha="center", va="bottom", fontsize=ANNOT_SIZE - 1)
            ax.set_xticks(np.arange(n_topo) + bar_w / 2)
            ax.set_xticklabels([topo_label(t) for t in TOPOLOGIES], rotation=15)
            ax.set_ylabel(r"Mean $T_{end}$" if col == 0 else "")
            if row == 0:
                ax.set_title(f"Setting {label} (vf={vf}, fo={fo})")
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.45, 0.5),
                            xycoords="axes fraction", textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold", rotation=90,
                            ha="center", va="center")

    _fp_bar_legend(axes[0, -1])
    fig.suptitle("Byzantine Robustness — Topology Ranking Consistency\n"
                 "k=3 · fp=0.15 (solid) vs fp=0.30 (hatched)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_05_consistency_settings.png")
    plt.close(fig)
    print("  [05] rq1b_05_consistency_settings.png")



#  FIGURE 6 — Effect of k on T_end


def fig_effect_of_k():
    n_show = [100, 1000, 5000]
    fig, axes = plt.subplots(1, len(n_show), figsize=(6 * len(n_show), FIG_SINGLE_H))
    for ax, n in zip(axes, n_show):
        ref = agg[(agg["viewFraction"] == 1.0) & (agg["fanOut"] == 3) &
                   (agg["nodeCount"] == n)]
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = ref[(ref["topology"] == t) &
                          (ref["failureProbability"] == fp)].sort_values("k")
                ax.plot(sub["k"], sub["T_end_mean"],
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2, markersize=7)
        ax.set_xlabel("Connectivity parameter $k$")
        ax.set_ylabel(r"Mean $T_{end}$")
        ax.set_title(f"N = {n}")
        ax.set_xticks(K_VALUES)
    _fp_topo_legend(axes[0], ncol=2)
    fig.suptitle("Byzantine Robustness — Effect of $k$ on Latency\n"
                 "Setting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_06_effect_of_k.png")
    plt.close(fig)
    print("  [06] rq1b_06_effect_of_k.png")



#  FIGURE 7 — Reliability heatmap (side-by-side panels per fp)


def fig_reliability():
    n_fp = len(FP_VALUES)
    n_settings = len(SETTINGS)
    fig, axes = plt.subplots(n_fp, n_settings,
                             figsize=(18, (FIG_SINGLE_H + 1) * n_fp))

    for fi, fp in enumerate(FP_VALUES):
        for idx, (label, (vf, fo)) in enumerate(SETTINGS.items()):
            ax = axes[fi, idx]
            sub = agg[(agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                       (agg["failureProbability"] == fp)]
            pivot = sub.pivot_table(index="topology",
                                    columns=["k", "nodeCount"],
                                    values="R", aggfunc="first")
            pivot = pivot.reindex(TOPOLOGIES)
            pivot.index = [topo_label(t) for t in TOPOLOGIES]
            col_labels = [f"k={k}\nN={n}" for k, n in pivot.columns]
            pivot.columns = col_labels

            sns.heatmap(pivot, ax=ax, annot=True, fmt=".2f", cmap="RdYlGn",
                        vmin=0.0, vmax=1.0, linewidths=0.5, linecolor="white",
                        cbar=(idx == n_settings - 1),
                        annot_kws={"size": HEATMAP_ANNOT - 1})
            fix_heatmap_text_contrast(ax, pivot.values, "RdYlGn", 0.0, 1.0)
            ax.set_title(f"Setting {label} (vf={vf}, fo={fo})")
            ax.set_ylabel(FP_SHORT_LABELS[fp] if idx == 0 else "")
            ax.tick_params(axis="x", rotation=45)

    fig.suptitle("Byzantine Robustness — Reliability $R$ by Topology, $k$, $N$\n"
                 "Top row: fp=0.15 · Bottom row: fp=0.30")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_07_reliability.png")
    plt.close(fig)
    print("  [07] rq1b_07_reliability.png")



#  FIGURE 7b — Variance scaling: std dev of T_end vs N


def fig_variance_scaling():
    fig, axes = plt.subplots(1, len(K_VALUES), figsize=(16, FIG_SINGLE_H),
                             sharey=False)
    for col, k in enumerate(K_VALUES):
        ax = axes[col]
        ref = sel(agg, "A", k=k)
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub = ref[(ref["topology"] == t) &
                          (ref["failureProbability"] == fp)].sort_values("nodeCount")
                if len(sub) == 0:
                    continue
                ax.plot(sub["nodeCount"], sub["T_end_std"],
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t], linewidth=2, markersize=6)
        ax.set_xlabel("Node count $N$")
        ax.set_ylabel(r"Std Dev of $T_{end}$" if col == 0 else "")
        ax.set_title(f"k = {k}")
        ax.set_xscale("log")
        ax.set_xticks(N_VALUES)
        ax.set_xticklabels([str(n) for n in N_VALUES])
    _fp_topo_legend(axes[0], ncol=2)
    fig.suptitle("Byzantine Robustness — Variance Scaling: Std Dev of $T_{end}$ vs $N$"
                 "\nSetting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_07b_variance_scaling.png")
    plt.close(fig)
    print("  [07b] rq1b_07b_variance_scaling.png")



#  FIGURE 8 — Distribution of T_end: side-by-side violin plots per fp


def fig_variance_violins():
    n_rows = [100, 1000, 5000]
    n_fp = len(FP_VALUES)
    topo_order = [topo_label(t) for t in TOPOLOGIES]
    palette = {topo_label(t): TOPO_COLORS[t] for t in TOPOLOGIES}

    fig, axes = plt.subplots(len(n_rows), len(K_VALUES) * n_fp,
                             figsize=(24, FIG_SINGLE_H * len(n_rows)))

    for row, n in enumerate(n_rows):
        for col, k in enumerate(K_VALUES):
            for fi, fp in enumerate(FP_VALUES):
                ax_idx = col * n_fp + fi
                ax = axes[row, ax_idx]
                sub = df[(df["viewFraction"] == 1.0) & (df["fanOut"] == 3) &
                         (df["k"] == k) & (df["nodeCount"] == n) &
                         (df["failureProbability"] == fp)].copy()
                if len(sub) == 0:
                    ax.set_visible(False)
                    continue
                sub["topo_label"] = sub["topology"].map(TOPO_LABELS)

                sns.violinplot(data=sub, x="topo_label", y="T_end",
                               hue="topo_label", ax=ax,
                               order=topo_order, hue_order=topo_order,
                               palette=palette, inner="box", linewidth=1,
                               saturation=0.85, cut=0, bw_adjust=2.0,
                               legend=False)
                ax.tick_params(axis="x", rotation=15)
                ax.set_xlabel("")
                ax.set_ylabel(r"$T_{end}$" if ax_idx == 0 else "")
                if row == 0:
                    ax.set_title(f"k={k}  {FP_SHORT_LABELS[fp]}")
                if ax_idx == 0:
                    ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.55, 0.5),
                                xycoords="axes fraction",
                                textcoords="axes fraction",
                                fontsize=SUBTITLE_SIZE, fontweight="bold",
                                rotation=90, ha="center", va="center")

    fig.suptitle("Byzantine Robustness — Distribution of $T_{end}$\n"
                 "Setting A · side-by-side fp=0.15 vs fp=0.30")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_08_variance_violins.png")
    plt.close(fig)
    print("  [08] rq1b_08_variance_violins.png")



#  FIGURE 9 — CDF of L_1.0 per topology × fp


def fig_cdf_L100():
    n_rows = [100, 1000, 5000]
    fig, axes = plt.subplots(len(n_rows), 3,
                             figsize=(16, FIG_SINGLE_H * len(n_rows)))

    for row, n in enumerate(n_rows):
        for col, k in enumerate(K_VALUES):
            ax = axes[row, col]
            sub = df[(df["viewFraction"] == 1.0) & (df["fanOut"] == 3) &
                     (df["k"] == k) & (df["nodeCount"] == n)]
            for t in TOPOLOGIES:
                for fp in FP_VALUES:
                    vals = sub[(sub["topology"] == t) &
                               (sub["failureProbability"] == fp)]["L_1.0"]
                    reached = vals[vals >= 0].sort_values()
                    if len(reached) == 0:
                        continue
                    n_total = len(vals)
                    cdf_y = np.arange(1, len(reached) + 1) / n_total
                    ax.step(reached, cdf_y, where="post",
                            color=TOPO_COLORS[t],
                            linestyle=FP_LINESTYLES[fp],
                            linewidth=2)
            ax.set_xlabel("Round $t$" if row == len(n_rows) - 1 else "")
            ax.set_ylabel(r"$P(L_{1.0} \leq t)$" if col == 0 else "")
            if row == 0:
                ax.set_title(f"k = {k}")
            ax.set_ylim(0, 1.05)
            ax.axhline(0.9, color="grey", linestyle="--", linewidth=0.8,
                       alpha=0.6)
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.45, 0.5),
                            xycoords="axes fraction",
                            textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold",
                            rotation=90, ha="center", va="center")

    _fp_topo_legend(axes[0, 0], ncol=2)
    fig.suptitle("Byzantine Robustness — CDF of $L_{1.0}$\n"
                 "Setting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_09_cdf_L100.png")
    plt.close(fig)
    print("  [09] rq1b_09_cdf_L100.png")



#  FIGURE 10 — PDF (KDE) of T_end per topology × fp


def fig_kde_T_end():
    n_rows = [100, 1000, 5000]
    fig, axes = plt.subplots(len(n_rows), 3,
                             figsize=(16, FIG_SINGLE_H * len(n_rows)))

    for row, n in enumerate(n_rows):
        for col, k in enumerate(K_VALUES):
            ax = axes[row, col]
            sub = df[(df["viewFraction"] == 1.0) & (df["fanOut"] == 3) &
                     (df["k"] == k) & (df["nodeCount"] == n)]
            for t in TOPOLOGIES:
                for fp in FP_VALUES:
                    vals = sub[(sub["topology"] == t) &
                               (sub["failureProbability"] == fp)]["T_end"]
                    if len(vals) == 0:
                        continue
                    alpha = 0.15 if fp == FP_VALUES[0] else 0.08
                    sns.kdeplot(vals, ax=ax, color=TOPO_COLORS[t],
                                linestyle=FP_LINESTYLES[fp],
                                linewidth=2, fill=True, alpha=alpha,
                                bw_adjust=3.0)
            ax.set_xlabel(r"$T_{end}$" if row == len(n_rows) - 1 else "")
            ax.set_ylabel("Density" if col == 0 else "")
            if row == 0:
                ax.set_title(f"k = {k}")
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.45, 0.5),
                            xycoords="axes fraction",
                            textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold",
                            rotation=90, ha="center", va="center")

    _fp_topo_legend(axes[0, 0], ncol=2)
    fig.suptitle("Byzantine Robustness — PDF of $T_{end}$ (KDE)\n"
                 "Setting A · fp=0.15 (solid) vs fp=0.30 (dashed)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_10_kde_T_end.png")
    plt.close(fig)
    print("  [10] rq1b_10_kde_T_end.png")



#  FIGURE 11 — Latency profile (L_0.5, L_0.9, L_1.0) grouped bar + fp


def fig_latency_profile():
    n_rows = [100, 1000, 5000]
    latency_colors = ["#81D4FA", "#42A5F5", "#1565C0"]
    latency_labels = [r"$L_{0.5}$", r"$L_{0.9}$", r"$L_{1.0}$"]
    n_topo = len(TOPOLOGIES)
    n_latency = 3
    n_fp = len(FP_VALUES)
    total_bars = n_latency * n_fp
    bar_w = 0.12
    group_w = total_bars * bar_w

    fig, axes = plt.subplots(len(n_rows), 3,
                             figsize=(18, FIG_SINGLE_H * len(n_rows)))

    for row, n in enumerate(n_rows):
        for col, k in enumerate(K_VALUES):
            ax = axes[row, col]
            ref = sel(agg, "A", k=k)
            ref = ref[ref["nodeCount"] == n]

            x = np.arange(n_topo)

            def _val(t, metric, fp):
                s = ref[(ref["topology"] == t) &
                        (ref["failureProbability"] == fp)]
                return s[metric].values[0] if len(s) > 0 else np.nan

            bi = 0
            for li, (metric, lcol, llabel) in enumerate(
                zip(["L50_mean", "L90_mean", "L100_mean"],
                    latency_colors, latency_labels)):
                for fi, fp in enumerate(FP_VALUES):
                    vals = [_val(t, metric, fp) for t in TOPOLOGIES]
                    offset = (bi - (total_bars - 1) / 2) * bar_w
                    show_label = (row == 0 and col == 0)
                    fp_tag = FP_SHORT_LABELS[fp]
                    ax.bar(x + offset, vals, bar_w, color=lcol,
                           edgecolor="white", hatch=FP_HATCHES[fp],
                           label=f"{llabel} {fp_tag}" if show_label else "")
                    bi += 1

            ax.set_xticks(x)
            ax.set_xticklabels([topo_label(t) for t in TOPOLOGIES],
                               rotation=15)
            ax.set_ylabel("Mean round" if col == 0 else "")
            if row == 0:
                ax.set_title(f"k = {k}")
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.45, 0.5),
                            xycoords="axes fraction",
                            textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold",
                            rotation=90, ha="center", va="center")

    axes[0, 0].legend(frameon=True, fontsize=LEGEND_SIZE - 1, ncol=2)
    fig.suptitle("Byzantine Robustness — Latency Profile\n"
                 "Setting A · solid = fp=0.15, hatched = fp=0.30")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_11_latency_profile.png")
    plt.close(fig)
    print("  [11] rq1b_11_latency_profile.png")



#  FIGURE 12 — CDF of alpha for incomplete trials


def fig_cdf_alpha_incomplete():
    incomplete = df[df["R_run"] == 0]
    if len(incomplete) == 0:
        print("  [12] (no incomplete trials — skipping)")
        return

    fig, axes = plt.subplots(1, len(FP_VALUES),
                             figsize=(FIG_SMALL_W * len(FP_VALUES),
                                      FIG_SINGLE_H),
                             sharey=True)

    for fi, fp in enumerate(FP_VALUES):
        ax = axes[fi]
        sub = incomplete[incomplete["failureProbability"] == fp]
        for t in TOPOLOGIES:
            vals = sub[sub["topology"] == t]["alpha"].sort_values()
            if len(vals) == 0:
                continue
            cdf_y = np.arange(1, len(vals) + 1) / len(vals)
            ax.step(vals, cdf_y, where="post",
                    color=TOPO_COLORS[t],
                    label=f"{topo_label(t)} (n={len(vals):,})",
                    linewidth=2)
        ax.set_xlabel(r"Final coverage $\alpha$")
        ax.set_ylabel("CDF" if fi == 0 else "")
        ax.set_title(f"{FP_SHORT_LABELS[fp]}")
        ax.legend(frameon=True, fontsize=LEGEND_SIZE)
        ax.set_xlim(0, 1.01)

    fig.suptitle("Byzantine Robustness — CDF of $\\alpha$ for Incomplete Trials "
                 "($R_{run}=0$)")
    fig.tight_layout()
    fig.savefig(OUT / "rq1b_12_cdf_alpha_incomplete.png")
    plt.close(fig)
    print("  [12] rq1b_12_cdf_alpha_incomplete.png")


#  Comprehensive results report


def _md_table(headers, rows, aligns=None):
    """Build a markdown table string."""
    if aligns is None:
        aligns = ["l"] + ["r"] * (len(headers) - 1)
    sep = "|"
    for a in aligns:
        if a == "r":
            sep += " ---: |"
        elif a == "c":
            sep += " :---: |"
        else:
            sep += " :--- |"
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
    """Write a comprehensive markdown report with all numerical results."""
    rpt = []
    rpt.append("# RQ1b Results Report — Byzantine Robustness\n")
    rpt.append(f"*Auto-generated by `rq1b_analysis.py`*\n")

    # --- section 1: data overview ---
    rpt.append("## 1. Data Overview\n")
    rpt.append(f"- **Source file:** `data/rq1b.csv`")
    rpt.append(f"- **Total trial rows:** {len(df):,}")
    rpt.append(f"- **Topologies:** {', '.join(topo_label(t) for t in TOPOLOGIES)}")
    rpt.append(f"- **k values:** {K_VALUES}")
    rpt.append(f"- **N values:** {N_VALUES}")
    rpt.append(f"- **Failure probabilities:** {FP_VALUES}")
    rpt.append(f"- **Settings:** A (vf=1.0, fo=3), B (vf=0.6, fo=2), "
               f"C (vf=0.2, fo=1)")
    rpt.append(f"- **Trials per configuration:** "
               f"{agg['n_trials'].iloc[0]:,}")
    rpt.append(f"- **Unique configurations:** {len(agg)}")
    rpt.append("")

    # --- section 2: full metric table (setting A) per fp ---
    rpt.append("## 2. Complete Metric Summary — Setting A\n")
    rpt.append("All values are means across trials per configuration. "
               "Latency thresholds exclude incomplete trials (sentinel −1).\n")

    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for k in K_VALUES:
            rpt.append(f"#### k = {k}\n")
            ref = sel(agg, "A", k=k)
            ref = ref[ref["failureProbability"] == fp]
            headers = ["Topology", "N", "T_end", "Ω", "M", "R",
                        "L_0.5", "L_0.9", "L_1.0", "F_eff"]
            rows = []
            for n in N_VALUES:
                for t in TOPOLOGIES:
                    r = ref[(ref["topology"] == t) & (ref["nodeCount"] == n)]
                    if len(r) == 0:
                        continue
                    r = r.iloc[0]
                    rows.append([
                        topo_label(t), n,
                        _fmt(r["T_end_mean"]),
                        _fmt(r["Omega_mean"], ".0f"),
                        _fmt(r["M_mean"]),
                        _fmt(r["R"], ".3f"),
                        _fmt(r["L50_mean"], ".1f"),
                        _fmt(r["L90_mean"], ".1f"),
                        _fmt(r["L100_mean"], ".1f"),
                        _fmt(r["F_eff_mean"]),
                    ])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 3: fp comparison — T_end delta ---
    rpt.append("## 3. Failure-Probability Impact — T_end Increase\n")
    rpt.append("How much worse is fp=0.30 compared to fp=0.15? "
               "(Setting A)\n")

    for k in K_VALUES:
        rpt.append(f"### k = {k}\n")
        ref = sel(agg, "A", k=k)
        headers = ["Topology", "N",
                    "T_end (fp=0.15)", "T_end (fp=0.30)",
                    "Δ absolute", "Δ %"]
        rows = []
        for n in N_VALUES:
            for t in TOPOLOGIES:
                r15 = ref[(ref["topology"] == t) & (ref["nodeCount"] == n) &
                          (ref["failureProbability"] == 0.15)]
                r30 = ref[(ref["topology"] == t) & (ref["nodeCount"] == n) &
                          (ref["failureProbability"] == 0.30)]
                if len(r15) == 0 or len(r30) == 0:
                    continue
                v15 = r15.iloc[0]["T_end_mean"]
                v30 = r30.iloc[0]["T_end_mean"]
                delta = v30 - v15
                pct = (delta / v15 * 100) if v15 > 0 else float("nan")
                rows.append([
                    topo_label(t), n,
                    _fmt(v15), _fmt(v30),
                    _fmt(delta, "+.2f"), _fmt(pct, "+.1f") + "%",
                ])
        rpt.append(_md_table(headers, rows))
        rpt.append("")

    # --- section 4: scaling with N — T_end ---
    rpt.append("## 4. Scaling with N — Mean T_end\n")
    rpt.append("Setting A. How latency grows with network size, per fp.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for k in K_VALUES:
            rpt.append(f"#### k = {k}\n")
            ref = sel(agg, "A", k=k)
            ref = ref[ref["failureProbability"] == fp]
            n_lo, n_hi = N_VALUES[0], N_VALUES[-1]
            headers = (["Topology"] + [f"N={n}" for n in N_VALUES]
                       + [f"Ratio N{n_hi}/N{n_lo}"])
            rows = []
            for t in TOPOLOGIES:
                sub = ref[ref["topology"] == t].sort_values("nodeCount")
                vals = []
                for n in N_VALUES:
                    r = sub[sub["nodeCount"] == n]
                    vals.append(
                        r["T_end_mean"].values[0] if len(r) > 0 else np.nan)
                v_lo, v_hi = vals[0], vals[-1]
                ratio = (v_hi / v_lo
                         if not np.isnan(v_lo) and not np.isnan(v_hi)
                         and v_lo > 0 else float("nan"))
                rows.append([topo_label(t)]
                            + [_fmt(v) for v in vals]
                            + [_fmt(ratio, ".1f") + "x"])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 5: scaling — Omega ---
    rpt.append("## 5. Scaling with N — Mean Omega (Total Messages)\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for k in K_VALUES:
            rpt.append(f"#### k = {k}\n")
            ref = sel(agg, "A", k=k)
            ref = ref[ref["failureProbability"] == fp]
            n_lo, n_hi = N_VALUES[0], N_VALUES[-1]
            headers = (["Topology"] + [f"N={n}" for n in N_VALUES]
                       + [f"Ratio N{n_hi}/N{n_lo}"])
            rows = []
            for t in TOPOLOGIES:
                sub = ref[ref["topology"] == t].sort_values("nodeCount")
                vals = []
                for n in N_VALUES:
                    r = sub[sub["nodeCount"] == n]
                    vals.append(
                        r["Omega_mean"].values[0] if len(r) > 0 else np.nan)
                v_lo, v_hi = vals[0], vals[-1]
                ratio = (v_hi / v_lo
                         if not np.isnan(v_lo) and not np.isnan(v_hi)
                         and v_lo > 0 else float("nan"))
                rows.append([topo_label(t)]
                            + [_fmt(v, ".0f") for v in vals]
                            + [_fmt(ratio, ".1f") + "x"])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 6: effect of k ---
    rpt.append("## 6. Effect of Connectivity (k) — Mean T_end\n")
    rpt.append("Setting A. Shows how increasing network density reduces "
               "latency under Byzantine failures.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for n in [100, 1000, 5000]:
            rpt.append(f"#### N = {n}\n")
            headers = (["Topology"] + [f"k={k}" for k in K_VALUES]
                       + ["% reduction k3→k9"])
            rows = []
            for t in TOPOLOGIES:
                vals = []
                for k in K_VALUES:
                    ref = sel(agg, "A", k=k)
                    r = ref[(ref["topology"] == t) &
                            (ref["nodeCount"] == n) &
                            (ref["failureProbability"] == fp)]
                    vals.append(
                        r["T_end_mean"].values[0] if len(r) > 0 else np.nan)
                has_all = not np.isnan(vals[0]) and not np.isnan(vals[-1])
                reduction = ((vals[0] - vals[-1]) / vals[0] * 100
                             if has_all and vals[0] > 0 else float("nan"))
                rows.append([topo_label(t)]
                            + [_fmt(v) for v in vals]
                            + [_fmt(reduction, ".1f") + "%"])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 7: consistency across settings ---
    rpt.append("## 7. Consistency Across Peer-Selection Settings\n")
    rpt.append("Mean T_end at k=3. Verifies that topology ranking is "
               "robust across settings and scales under Byzantine failures.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for n in [100, 1000, 5000]:
            rpt.append(f"#### N = {n}\n")
            headers = ["Topology", "Setting A", "Setting B", "Setting C",
                        "Ranking stable?"]
            rows = []
            rankings = {}
            for label, (vf, fo) in SETTINGS.items():
                sub = agg[(agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                           (agg["k"] == 3) & (agg["nodeCount"] == n) &
                           (agg["failureProbability"] == fp)]
                avail = sub.sort_values("T_end_mean")["topology"].tolist()
                rankings[label] = avail
            consistent = (rankings["A"] == rankings["B"]
                          == rankings["C"])
            for t in TOPOLOGIES:
                vals = []
                for label, (vf, fo) in SETTINGS.items():
                    sub = agg[
                        (agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                        (agg["k"] == 3) & (agg["nodeCount"] == n) &
                        (agg["failureProbability"] == fp)]
                    s = sub[sub["topology"] == t]
                    vals.append(
                        _fmt(s["T_end_mean"].values[0]) if len(s) > 0
                        else "N/A")
                stable = "Yes" if consistent else "Partial"
                rows.append([topo_label(t)] + vals + [stable])
            rpt.append(_md_table(headers, rows))
            rpt.append(
                f"\n**Ranking A:** "
                f"{' < '.join(topo_label(t) for t in rankings['A'])}")
            rpt.append(
                f"**Ranking B:** "
                f"{' < '.join(topo_label(t) for t in rankings['B'])}")
            rpt.append(
                f"**Ranking C:** "
                f"{' < '.join(topo_label(t) for t in rankings['C'])}")
            rpt.append(
                f"\n**Ranking consistent across all settings:** "
                f"{'Yes' if consistent else 'No'}\n")

    # --- section 8: reliability ---
    rpt.append("## 8. Reliability (R) — Complete Table\n")
    rpt.append("Fraction of trials reaching full coverage (alpha = 1.0).\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for label, (vf, fo) in SETTINGS.items():
            rpt.append(f"#### Setting {label} (vf={vf}, fo={fo})\n")
            headers = ["Topology", "k"] + [f"N={n}" for n in N_VALUES]
            rows = []
            for t in TOPOLOGIES:
                for k in K_VALUES:
                    sub = agg[
                        (agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                        (agg["k"] == k) & (agg["topology"] == t) &
                        (agg["failureProbability"] == fp)]
                    vals = []
                    for n in N_VALUES:
                        r = sub[sub["nodeCount"] == n]
                        if len(r) > 0:
                            vals.append(_fmt(r.iloc[0]["R"], ".3f"))
                        else:
                            vals.append("N/A")
                    rows.append([topo_label(t), k] + vals)
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 9: reliability delta ---
    rpt.append("## 9. Reliability Degradation — fp=0.30 vs fp=0.15\n")
    rpt.append("Setting A, k=3. How much reliability drops with more "
               "Byzantine failures.\n")
    headers = ["Topology", "N", "R (fp=0.15)", "R (fp=0.30)", "Δ R"]
    rows = []
    ref = sel(agg, "A", k=3)
    for n in N_VALUES:
        for t in TOPOLOGIES:
            r15 = ref[(ref["topology"] == t) & (ref["nodeCount"] == n) &
                      (ref["failureProbability"] == 0.15)]
            r30 = ref[(ref["topology"] == t) & (ref["nodeCount"] == n) &
                      (ref["failureProbability"] == 0.30)]
            if len(r15) == 0 or len(r30) == 0:
                continue
            v15 = r15.iloc[0]["R"]
            v30 = r30.iloc[0]["R"]
            delta = v30 - v15
            rows.append([topo_label(t), n,
                         _fmt(v15, ".3f"), _fmt(v30, ".3f"),
                         _fmt(delta, "+.3f")])
    rpt.append(_md_table(headers, rows))
    rpt.append("")

    # --- section 10: variance analysis ---
    rpt.append("## 10. Variance Analysis — Standard Deviation of T_end\n")
    rpt.append("Setting A. Higher std indicates more variable "
               "dissemination times.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for k in K_VALUES:
            rpt.append(f"#### k = {k}\n")
            ref = sel(agg, "A", k=k)
            ref = ref[ref["failureProbability"] == fp]
            headers = ["Topology"] + [f"N={n}" for n in N_VALUES]
            rows = []
            for t in TOPOLOGIES:
                vals = []
                for n in N_VALUES:
                    r = ref[(ref["topology"] == t) & (ref["nodeCount"] == n)]
                    if len(r) > 0:
                        vals.append(_fmt(r.iloc[0]["T_end_std"]))
                    else:
                        vals.append("N/A")
                rows.append([topo_label(t)] + vals)
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 11: latency thresholds ---
    rpt.append("## 11. Latency Thresholds — L_0.5, L_0.9, L_1.0\n")
    rpt.append("Setting A. Mean round at which 50%/90%/100% coverage is "
               "reached (completed trials only for L_1.0).\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for k in K_VALUES:
            rpt.append(f"#### k = {k}\n")
            ref = sel(agg, "A", k=k)
            ref = ref[ref["failureProbability"] == fp]
            headers = ["Topology", "N", "L_0.5", "L_0.9", "L_1.0",
                        "Gap (L_1.0 − L_0.5)"]
            rows = []
            for n in N_VALUES:
                for t in TOPOLOGIES:
                    r = ref[(ref["topology"] == t) & (ref["nodeCount"] == n)]
                    if len(r) == 0:
                        continue
                    r = r.iloc[0]
                    l50 = r["L50_mean"]
                    l100 = r["L100_mean"]
                    gap = (l100 - l50
                           if not np.isnan(l100) and not np.isnan(l50)
                           else float("nan"))
                    rows.append([
                        topo_label(t), n,
                        _fmt(l50, ".1f"), _fmt(r["L90_mean"], ".1f"),
                        _fmt(l100, ".1f"), _fmt(gap, ".1f"),
                    ])
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 12: incomplete trials ---
    rpt.append("## 12. Incomplete Trials Analysis\n")
    incomplete = df[df["R_run"] == 0]
    total = len(df)
    rpt.append(f"- **Total incomplete trials:** {len(incomplete):,} / "
               f"{total:,} ({len(incomplete) / total * 100:.2f}%)\n")

    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        inc_fp = incomplete[incomplete["failureProbability"] == fp]
        rpt.append(f"- Incomplete: {len(inc_fp):,}\n")
        if len(inc_fp) > 0:
            headers = ["Topology", "Setting", "k", "N", "Count",
                        "Mean α", "Min α"]
            rows = []
            for t in TOPOLOGIES:
                for label, (vf, fo) in SETTINGS.items():
                    for k in K_VALUES:
                        for n in N_VALUES:
                            sub = inc_fp[
                                (inc_fp["topology"] == t) &
                                (inc_fp["viewFraction"] == vf) &
                                (inc_fp["fanOut"] == fo) &
                                (inc_fp["k"] == k) &
                                (inc_fp["nodeCount"] == n)
                            ]
                            if len(sub) > 0:
                                rows.append([
                                    topo_label(t), label, k, n,
                                    len(sub),
                                    _fmt(sub["alpha"].mean(), ".3f"),
                                    _fmt(sub["alpha"].min(), ".3f"),
                                ])
            if rows:
                rpt.append(_md_table(headers, rows))
            else:
                rpt.append("No incomplete trials found.")
        rpt.append("")

    # --- section 13: findings ---
    rpt.append("## 13. Findings Summary\n")

    item = 1
    for fp in FP_VALUES:
        ref_a = sel(agg, "A", k=3)
        ref_a = ref_a[ref_a["failureProbability"] == fp]
        for n in [100, 500, 1000, 5000]:
            ref_n = ref_a[ref_a["nodeCount"] == n].sort_values("T_end_mean")
            if len(ref_n) == 0:
                continue
            fastest = ref_n.iloc[0]
            slowest = ref_n.iloc[-1]
            ratio = (slowest["T_end_mean"] / fastest["T_end_mean"]
                     if fastest["T_end_mean"] > 0 else float("nan"))
            rpt.append(
                f"{item}. **At N={n}, {FP_SHORT_LABELS[fp]} (k=3, "
                f"Setting A):** fastest = {topo_label(fastest['topology'])} "
                f"(T_end={fastest['T_end_mean']:.2f}), "
                f"slowest = {topo_label(slowest['topology'])} "
                f"(T_end={slowest['T_end_mean']:.2f}), "
                f"ratio = {ratio:.1f}x")
            item += 1

    rpt.append("")

    r_below_1 = agg[agg["R"] < 1.0]
    rpt.append(f"{item}. **Configurations with R < 1.0:** "
               f"{len(r_below_1)} / {len(agg)}")
    if len(r_below_1) > 0:
        for fp in FP_VALUES:
            affected = r_below_1[
                r_below_1["failureProbability"] == fp
            ]["topology"].unique()
            if len(affected) > 0:
                rpt.append(
                    f"   {FP_SHORT_LABELS[fp]} affected topologies: "
                    f"{', '.join(topo_label(t) for t in affected)}")
    item += 1

    for fp in FP_VALUES:
        unreliable = agg[(agg["R"] < 0.95) & (agg["k"] == 3) &
                         (agg["failureProbability"] == fp)]
        if len(unreliable) > 0:
            worst = unreliable.sort_values("R").iloc[0]
            rpt.append(
                f"{item}. **Least reliable config (k=3, "
                f"{FP_SHORT_LABELS[fp]}):** "
                f"{topo_label(worst['topology'])} "
                f"N={int(worst['nodeCount'])}, "
                f"vf={worst['viewFraction']:.1f}, "
                f"fo={int(worst['fanOut'])} "
                f"→ R={worst['R']:.3f}")
            item += 1

    rpt.append("")

    ref_k3 = sel(agg, "A", k=3)
    rpt.append(f"{item}. **Resilience ranking (fp=0.30 vs fp=0.15, N=1000, "
               f"k=3):**")
    item += 1
    deltas = []
    for t in TOPOLOGIES:
        r15 = ref_k3[(ref_k3["topology"] == t) &
                      (ref_k3["nodeCount"] == 1000) &
                      (ref_k3["failureProbability"] == 0.15)]
        r30 = ref_k3[(ref_k3["topology"] == t) &
                      (ref_k3["nodeCount"] == 1000) &
                      (ref_k3["failureProbability"] == 0.30)]
        if len(r15) > 0 and len(r30) > 0:
            v15 = r15.iloc[0]["T_end_mean"]
            v30 = r30.iloc[0]["T_end_mean"]
            pct = (v30 - v15) / v15 * 100 if v15 > 0 else float("nan")
            deltas.append((t, pct))
    deltas.sort(key=lambda x: x[1])
    for t, pct in deltas:
        rpt.append(f"   - {topo_label(t)}: T_end increases by "
                    f"{pct:+.1f}%")
    rpt.append("")

    # --- write report ---
    report_path = OUT / "rq1b_results_report.md"
    report_path.write_text("\n".join(rpt), encoding="utf-8")
    print(f"\n  Report saved to {report_path}")



#  Main


if __name__ == "__main__":
    print(f"\nGenerating RQ1b (Byzantine) figures → {OUT}/\n")
    fig_topology_comparison()
    fig_scaling_T_end()
    fig_scaling_Omega()
    fig_scaling_latency()
    fig_scaling_M()
    fig_consistency_settings()
    fig_effect_of_k()
    fig_reliability()
    fig_variance_scaling()
    fig_variance_violins()
    fig_cdf_L100()
    fig_kde_T_end()
    fig_latency_profile()
    fig_cdf_alpha_incomplete()
    generate_report()
    print(f"\nDone. {len(list(OUT.glob('*.png')))} figures + report "
          f"saved to {OUT}/")
