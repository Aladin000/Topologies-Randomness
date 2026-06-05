#!/usr/bin/env python3

import pathlib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

from _style import (
    TOPOLOGIES, TOPO_COLORS, TOPO_LABELS,
    FO_COLORS, VF_COLORS,
    FP_VALUES, FP_LINESTYLES, FP_LABELS, FP_SHORT_LABELS,
    FIG_SINGLE_H, FIG_DOUBLE_H,
    SUPTITLE_SIZE, SUBTITLE_SIZE, ANNOT_SIZE, LEGEND_SIZE, TICK_SIZE,
    HEATMAP_ANNOT,
    apply_theme, topo_label, thousands_formatter, compact_formatter,
    fix_heatmap_text_contrast,
)

# --- paths ---

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "rq2b.csv"
OUT  = ROOT / "analysis" / "figures" / "rq2b"
OUT.mkdir(parents=True, exist_ok=True)

# --- theme ---

apply_theme()

# --- constants ---

VF_VALUES = [0.2, 0.4, 0.6, 0.8, 1.0]
FO_VALUES = [1, 2, 3, 4, 5]
N_VALUES  = [100, 500, 1000, 5000]

# --- load ---

print(f"Loading {DATA} ...")
df = pd.read_csv(DATA)
print(f"  {len(df):,} rows, {df['topology'].nunique()} topologies, "
      f"{df['nodeCount'].nunique()} N-values, k={df['k'].unique()[0]} (fixed)")
print(f"  failureProbability levels: {sorted(df['failureProbability'].unique())}")

# --- per-config aggregates ---

GROUP_KEYS = ["topology", "nodeCount", "k", "viewFraction", "fanOut", "failureProbability"]

agg = df.groupby(GROUP_KEYS).agg(
    T_end_mean = ("T_end", "mean"),
    Omega_mean = ("Omega", "mean"),
    M_mean     = ("M",     "mean"),
    R          = ("R_run", "mean"),
    alpha_mean = ("alpha", "mean"),
    L50_mean   = ("L_0.5", lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
    L90_mean   = ("L_0.9", lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
    L100_mean  = ("L_1.0", lambda x: x[x >= 0].mean() if (x >= 0).any() else np.nan),
    F_eff_mean = ("F_eff", "mean"),
    n_trials   = ("T_end", "count"),
).reset_index()

# --- heatmap helper ---

def _heatmap_annot_values(pivot, fmt_func):
    """Build an annotation matrix using a custom formatter."""
    annot = pivot.copy().astype(str)
    for r in range(pivot.shape[0]):
        for c in range(pivot.shape[1]):
            annot.iloc[r, c] = fmt_func(pivot.iloc[r, c])
    return annot


def fig_heatmaps(metric, metric_label, filename, fmt=".1f", cmap="YlOrRd_r",
                 vmin=None, vmax=None, use_compact=False):
    """Side-by-side heatmaps for each fp level, per topology and N."""
    n_fp = len(FP_VALUES)
    n_cols = len(TOPOLOGIES) * n_fp

    fig, axes = plt.subplots(len(N_VALUES), n_cols,
                             figsize=(5 * n_cols, FIG_SINGLE_H * len(N_VALUES)))

    for topo_idx, t in enumerate(TOPOLOGIES):
        for fp_idx, fp in enumerate(FP_VALUES):
            col = topo_idx * n_fp + fp_idx
            for row, n in enumerate(N_VALUES):
                ax = axes[row, col]
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                pivot = sub.pivot_table(index="viewFraction", columns="fanOut",
                                        values=metric)
                pivot = pivot.reindex(index=VF_VALUES, columns=FO_VALUES)

                if use_compact:
                    annot = _heatmap_annot_values(
                        pivot, lambda v: f"{v / 1000:.0f}K" if v >= 1000 else f"{v:.0f}")
                    sns.heatmap(pivot, ax=ax, annot=annot, fmt="", cmap=cmap,
                                vmin=vmin, vmax=vmax, linewidths=0.5,
                                linecolor="white", cbar=(col == n_cols - 1),
                                annot_kws={"size": HEATMAP_ANNOT})
                else:
                    sns.heatmap(pivot, ax=ax, annot=True, fmt=fmt, cmap=cmap,
                                vmin=vmin, vmax=vmax, linewidths=0.5,
                                linecolor="white", cbar=(col == n_cols - 1),
                                annot_kws={"size": HEATMAP_ANNOT})

                fix_heatmap_text_contrast(ax, pivot.values, cmap, vmin, vmax)
                ax.set_title(f"{topo_label(t)}, N={n}\n{FP_SHORT_LABELS[fp]}", fontsize=9)
                ax.set_ylabel("viewFraction" if col == 0 else "")
                ax.set_xlabel("fanOut" if row == len(N_VALUES) - 1 else "")
                ax.invert_yaxis()

    fig.suptitle(f"{metric_label} — Byzantine Robustness (fp=0.15 vs fp=0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / filename)
    plt.close(fig)
    print(f"  ✓ {filename}")


# --- figure 4: line plots - T_end vs viewFraction ---

def fig_line_vf():
    """T_end vs viewFraction: solid for fp=0.15, dashed for fp=0.30."""
    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for fo in FO_VALUES:
                for fp in FP_VALUES:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["failureProbability"] == fp)]
                    line = sub[sub["fanOut"] == fo].sort_values("viewFraction")
                    ax.plot(line["viewFraction"], line["T_end_mean"],
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=FO_COLORS[fo],
                            label=f"fo={fo}, {FP_SHORT_LABELS[fp]}" if row == 0 and col == 0 else "",
                            linewidth=2, markersize=4)
            ax.set_xlabel("viewFraction" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $T_{end}$" if col == 0 else "")
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(VF_VALUES)
    axes[0, 0].legend(frameon=True, ncol=2, fontsize=7)
    fig.suptitle(r"$T_{end}$ vs viewFraction — Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_04_line_vf_T_end.png")
    plt.close(fig)
    print("  ✓ rq2b_04_line_vf_T_end.png")


# --- figure 5: line plots - T_end vs fanOut ---

def fig_line_fo():
    """T_end vs fanOut: solid for fp=0.15, dashed for fp=0.30."""
    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for vf in VF_VALUES:
                for fp in FP_VALUES:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["failureProbability"] == fp)]
                    line = sub[sub["viewFraction"] == vf].sort_values("fanOut")
                    ax.plot(line["fanOut"], line["T_end_mean"],
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=VF_COLORS[vf],
                            label=f"vf={vf}, {FP_SHORT_LABELS[fp]}" if row == 0 and col == 0 else "",
                            linewidth=2, markersize=4)
            ax.set_xlabel("fanOut" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $T_{end}$" if col == 0 else "")
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(FO_VALUES)
    axes[0, 0].legend(frameon=True, ncol=2, fontsize=7)
    fig.suptitle(r"$T_{end}$ vs fanOut — Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_05_line_fo_T_end.png")
    plt.close(fig)
    print("  ✓ rq2b_05_line_fo_T_end.png")


# --- figure 5b: line plots - Omega vs viewFraction ---

def fig_line_vf_Omega():
    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for fo in FO_VALUES:
                for fp in FP_VALUES:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["failureProbability"] == fp)]
                    line = sub[sub["fanOut"] == fo].sort_values("viewFraction")
                    ax.plot(line["viewFraction"], line["Omega_mean"],
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=FO_COLORS[fo],
                            label=f"fo={fo}, {FP_SHORT_LABELS[fp]}" if row == 0 and col == 0 else "",
                            linewidth=2, markersize=4)
            ax.set_xlabel("viewFraction" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $\Omega$" if col == 0 else "")
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(VF_VALUES)
            ax.yaxis.set_major_formatter(compact_formatter())
    axes[0, 0].legend(frameon=True, ncol=2, fontsize=7)
    fig.suptitle(r"$\Omega$ vs viewFraction — Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_05b_line_vf_Omega.png")
    plt.close(fig)
    print("  ✓ rq2b_05b_line_vf_Omega.png")


# --- figure 5c: line plots - Omega vs fanOut ---

def fig_line_fo_Omega():
    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for vf in VF_VALUES:
                for fp in FP_VALUES:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["failureProbability"] == fp)]
                    line = sub[sub["viewFraction"] == vf].sort_values("fanOut")
                    ax.plot(line["fanOut"], line["Omega_mean"],
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=VF_COLORS[vf],
                            label=f"vf={vf}, {FP_SHORT_LABELS[fp]}" if row == 0 and col == 0 else "",
                            linewidth=2, markersize=4)
            ax.set_xlabel("fanOut" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $\Omega$" if col == 0 else "")
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(FO_VALUES)
            ax.yaxis.set_major_formatter(compact_formatter())
    axes[0, 0].legend(frameon=True, ncol=2, fontsize=7)
    fig.suptitle(r"$\Omega$ vs fanOut — Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_05c_line_fo_Omega.png")
    plt.close(fig)
    print("  ✓ rq2b_05c_line_fo_Omega.png")


# --- figure 6: reliability heatmap ---

def fig_reliability_boundary():
    n_fp = len(FP_VALUES)
    n_cols = len(TOPOLOGIES) * n_fp

    fig, axes = plt.subplots(len(N_VALUES), n_cols,
                             figsize=(5 * n_cols, FIG_SINGLE_H * len(N_VALUES)))
    for topo_idx, t in enumerate(TOPOLOGIES):
        for fp_idx, fp in enumerate(FP_VALUES):
            col = topo_idx * n_fp + fp_idx
            for row, n in enumerate(N_VALUES):
                ax = axes[row, col]
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                pivot = sub.pivot_table(index="viewFraction", columns="fanOut",
                                        values="R")
                pivot = pivot.reindex(index=VF_VALUES, columns=FO_VALUES)
                sns.heatmap(pivot, ax=ax, annot=True, fmt=".2f", cmap="RdYlGn",
                            vmin=0.0, vmax=1.0, linewidths=0.5, linecolor="white",
                            cbar=(col == n_cols - 1),
                            annot_kws={"size": HEATMAP_ANNOT})
                fix_heatmap_text_contrast(ax, pivot.values, "RdYlGn", 0.0, 1.0)
                ax.set_title(f"{topo_label(t)}, N={n}\n{FP_SHORT_LABELS[fp]}", fontsize=9)
                ax.set_ylabel("viewFraction" if col == 0 else "")
                ax.set_xlabel("fanOut" if row == len(N_VALUES) - 1 else "")
                ax.invert_yaxis()

    fig.suptitle("Reliability $R$ — Byzantine Robustness (fp=0.15 vs fp=0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_06_reliability_heatmap.png")
    plt.close(fig)
    print("  ✓ rq2b_06_reliability_heatmap.png")


# --- figure 7: interaction - % reduction in T_end ---

def fig_interaction():
    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for row, n in enumerate(N_VALUES):
        for col, t in enumerate(TOPOLOGIES):
            ax = axes[row, col]
            for fo in [1, 3, 5]:
                for fp in FP_VALUES:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["failureProbability"] == fp)]
                    line = sub[sub["fanOut"] == fo].sort_values("viewFraction")
                    if len(line) == 0:
                        continue
                    base = line["T_end_mean"].iloc[0]
                    improvement = (base - line["T_end_mean"]) / base * 100
                    ax.plot(line["viewFraction"], improvement,
                            marker="o", linestyle=FP_LINESTYLES[fp],
                            color=FO_COLORS[fo],
                            label=(f"fo={fo}, {FP_SHORT_LABELS[fp]}"
                                   if row == 0 and col == 0 else ""),
                            linewidth=2, markersize=4)
            ax.set_xlabel("viewFraction" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"% reduction in $T_{end}$" if col == 0 else "")
            if row == 0:
                ax.set_title(topo_label(t))
            ax.set_xticks(VF_VALUES)
            ax.axhline(0, color="grey", linestyle="--", linewidth=0.8)
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.5, 0.5),
                            xycoords="axes fraction", textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold", rotation=90,
                            ha="center", va="center")
    axes[0, 0].legend(frameon=True, ncol=2, fontsize=7)
    fig.suptitle(r"Interaction: % Reduction in $T_{end}$ — Byzantine Robustness "
                 r"(solid=fp0.15, dashed=fp0.30), k=6", fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_07_interaction.png")
    plt.close(fig)
    print("  ✓ rq2b_07_interaction.png")


# --- figure 8: diminishing returns - T_end and Omega vs fanOut ---

def fig_diminishing_returns():
    T_COLOR = "#4477AA"
    O_COLOR = "#EE6677"

    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for fp in FP_VALUES:
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["viewFraction"] == 1.0) & (agg["failureProbability"] == fp)]
                line = sub.sort_values("fanOut")
                l1, = ax.plot(line["fanOut"], line["T_end_mean"],
                              marker="o", linestyle=FP_LINESTYLES[fp],
                              color=T_COLOR, linewidth=2, markersize=5)
                ax2 = ax.twinx()
                l2, = ax2.plot(line["fanOut"], line["Omega_mean"],
                               marker="s", linestyle=FP_LINESTYLES[fp],
                               color=O_COLOR, linewidth=2, markersize=5)
                ax2.yaxis.set_major_formatter(compact_formatter())
                if col != 3:
                    ax2.set_ylabel("")
                else:
                    ax2.set_ylabel(r"Mean $\Omega$", color=O_COLOR)
                ax2.tick_params(axis="y", colors=O_COLOR)

            ax.set_xlabel("fanOut" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $T_{end}$" if col == 0 else "", color=T_COLOR)
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(FO_VALUES)
            ax.tick_params(axis="y", colors=T_COLOR)

            if row == 0 and col == 0:
                from matplotlib.lines import Line2D
                legend_elements = [
                    Line2D([0], [0], color=T_COLOR, linestyle="solid", label=r"$T_{end}$ fp=0.15"),
                    Line2D([0], [0], color=T_COLOR, linestyle="dashed", label=r"$T_{end}$ fp=0.30"),
                    Line2D([0], [0], color=O_COLOR, linestyle="solid", label=r"$\Omega$ fp=0.15"),
                    Line2D([0], [0], color=O_COLOR, linestyle="dashed", label=r"$\Omega$ fp=0.30"),
                ]
                ax.legend(handles=legend_elements, loc="upper right", frameon=True, fontsize=7)

    fig.suptitle(r"Diminishing Returns: $T_{end}$ and $\Omega$ vs fanOut — "
                 r"Byzantine Robustness (fp=0.15 vs fp=0.30), vf=1.0, k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_08_diminishing_returns.png")
    plt.close(fig)
    print("  ✓ rq2b_08_diminishing_returns.png")


# --- figure 9: CDF of L_1.0 across settings ---

def fig_cdf_L100():
    settings = [
        ("vf=1.0, fo=3", 1.0, 3),
        ("vf=0.6, fo=2", 0.6, 2),
        ("vf=0.2, fo=1", 0.2, 1),
    ]
    colors = ["#4CAF50", "#FF9800", "#E91E63"]

    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for row, n in enumerate(N_VALUES):
        for col, t in enumerate(TOPOLOGIES):
            ax = axes[row, col]
            for (label, vf, fo), color in zip(settings, colors):
                for fp in FP_VALUES:
                    sub = df[(df["topology"] == t) & (df["nodeCount"] == n) &
                             (df["viewFraction"] == vf) & (df["fanOut"] == fo) &
                             (df["failureProbability"] == fp)]
                    vals = sub["L_1.0"]
                    reached = vals[vals >= 0].sort_values()
                    if len(reached) == 0:
                        continue
                    n_total = len(vals)
                    cdf_y = np.arange(1, len(reached) + 1) / n_total
                    ax.step(reached, cdf_y, where="post",
                            color=color, linestyle=FP_LINESTYLES[fp],
                            label=(f"{label}, {FP_SHORT_LABELS[fp]}"
                                   if row == 0 and col == 0 else ""),
                            linewidth=2)
            ax.set_xlabel("Round $t$" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"$P(L_{1.0} \leq t)$" if col == 0 else "")
            if row == 0:
                ax.set_title(topo_label(t))
            ax.set_ylim(0, 1.05)
            ax.axhline(0.9, color="grey", linestyle="--", linewidth=0.8, alpha=0.6)
            if col == 0:
                ax.annotate(f"N = {n}", xy=(0, 0.5), xytext=(-0.5, 0.5),
                            xycoords="axes fraction", textcoords="axes fraction",
                            fontsize=SUBTITLE_SIZE, fontweight="bold", rotation=90,
                            ha="center", va="center")
    axes[0, 0].legend(frameon=True, fontsize=7, ncol=2)
    fig.suptitle(r"CDF of $L_{1.0}$ — Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_09_cdf_L100.png")
    plt.close(fig)
    print("  ✓ rq2b_09_cdf_L100.png")


# --- figure 10: topology sensitivity to fanOut ---

def fig_topology_sensitivity():
    fig, axes = plt.subplots(1, len(N_VALUES), figsize=(6 * len(N_VALUES), FIG_SINGLE_H))
    for ax, n in zip(axes, N_VALUES):
        sub = agg[agg["nodeCount"] == n]
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                tsub = sub[(sub["topology"] == t) & (sub["failureProbability"] == fp)]
                mean_T = tsub.groupby("fanOut")["T_end_mean"].mean()
                ax.plot(mean_T.index, mean_T.values,
                        marker="o", linestyle=FP_LINESTYLES[fp],
                        color=TOPO_COLORS[t],
                        label=(f"{topo_label(t)}, {FP_SHORT_LABELS[fp]}"
                               if n == N_VALUES[0] else ""),
                        linewidth=2, markersize=5)
        ax.set_xlabel("fanOut")
        ax.set_ylabel(r"Mean $T_{end}$ (averaged over vf)")
        ax.set_title(f"N = {n}")
        ax.set_xticks(FO_VALUES)
    axes[0].legend(frameon=True, fontsize=7, ncol=2)
    fig.suptitle("Topology Sensitivity to fanOut — Byzantine Robustness "
                 "(solid=fp0.15, dashed=fp0.30), k=6", fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_10_topology_sensitivity.png")
    plt.close(fig)
    print("  ✓ rq2b_10_topology_sensitivity.png")


# --- figure 11: F_eff heatmap ---

def fig_heatmap_Feff():
    fig_heatmaps("F_eff_mean", r"Mean $F_{eff}$ (messages per informed node)",
                 "rq2b_11_heatmap_F_eff.png", fmt=".1f", cmap="YlOrRd")


# --- figure 12: diminishing returns for viewFraction ---

def fig_diminishing_returns_vf():
    T_COLOR = "#4477AA"
    O_COLOR = "#EE6677"

    fig, axes = plt.subplots(len(N_VALUES), 4, figsize=(20, FIG_SINGLE_H * len(N_VALUES)))
    for col, t in enumerate(TOPOLOGIES):
        for row, n in enumerate(N_VALUES):
            ax = axes[row, col]
            for fp in FP_VALUES:
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["fanOut"] == 3) & (agg["failureProbability"] == fp)]
                line = sub.sort_values("viewFraction")
                l1, = ax.plot(line["viewFraction"], line["T_end_mean"],
                              marker="o", linestyle=FP_LINESTYLES[fp],
                              color=T_COLOR, linewidth=2, markersize=5)
                ax2 = ax.twinx()
                l2, = ax2.plot(line["viewFraction"], line["Omega_mean"],
                               marker="s", linestyle=FP_LINESTYLES[fp],
                               color=O_COLOR, linewidth=2, markersize=5)
                ax2.yaxis.set_major_formatter(compact_formatter())
                if col != 3:
                    ax2.set_ylabel("")
                else:
                    ax2.set_ylabel(r"Mean $\Omega$", color=O_COLOR)
                ax2.tick_params(axis="y", colors=O_COLOR)

            ax.set_xlabel("viewFraction" if row == len(N_VALUES) - 1 else "")
            ax.set_ylabel(r"Mean $T_{end}$" if col == 0 else "", color=T_COLOR)
            ax.set_title(f"{topo_label(t)}, N={n}")
            ax.set_xticks(VF_VALUES)
            ax.tick_params(axis="y", colors=T_COLOR)

            if row == 0 and col == 0:
                from matplotlib.lines import Line2D
                legend_elements = [
                    Line2D([0], [0], color=T_COLOR, linestyle="solid", label=r"$T_{end}$ fp=0.15"),
                    Line2D([0], [0], color=T_COLOR, linestyle="dashed", label=r"$T_{end}$ fp=0.30"),
                    Line2D([0], [0], color=O_COLOR, linestyle="solid", label=r"$\Omega$ fp=0.15"),
                    Line2D([0], [0], color=O_COLOR, linestyle="dashed", label=r"$\Omega$ fp=0.30"),
                ]
                ax.legend(handles=legend_elements, loc="upper right", frameon=True, fontsize=7)

    fig.suptitle(r"Diminishing Returns: $T_{end}$ and $\Omega$ vs viewFraction — "
                 r"Byzantine Robustness (fp=0.15 vs fp=0.30), fo=3, k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_12_diminishing_returns_vf.png")
    plt.close(fig)
    print("  ✓ rq2b_12_diminishing_returns_vf.png")


# --- figure 13: CDF of alpha for incomplete trials ---

def fig_cdf_alpha_incomplete():
    incomplete = df[df["R_run"] == 0]
    if len(incomplete) == 0:
        print("  ✓ rq2b_13_cdf_alpha_incomplete.png (no incomplete — skipped)")
        return

    fig, axes = plt.subplots(1, len(N_VALUES), figsize=(6 * len(N_VALUES), FIG_SINGLE_H))
    for ax, n in zip(axes, N_VALUES):
        sub = incomplete[incomplete["nodeCount"] == n]
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                vals = sub[(sub["topology"] == t) &
                           (sub["failureProbability"] == fp)]["alpha"].sort_values()
                if len(vals) == 0:
                    continue
                cdf_y = np.arange(1, len(vals) + 1) / len(vals)
                ax.step(vals, cdf_y, where="post",
                        color=TOPO_COLORS[t], linestyle=FP_LINESTYLES[fp],
                        label=(f"{topo_label(t)}, {FP_SHORT_LABELS[fp]} (n={len(vals):,})"
                               if n == N_VALUES[0] else ""),
                        linewidth=2)
        ax.set_xlabel(r"Final coverage $\alpha$")
        ax.set_ylabel("CDF" if n == N_VALUES[0] else "")
        ax.set_title(f"N = {n}")
        ax.set_xlim(0, 1.01)
    axes[0].legend(frameon=True, fontsize=7, ncol=2)
    fig.suptitle(r"CDF of $\alpha$ for Incomplete Trials ($R_{run}=0$) — "
                 r"Byzantine Robustness (solid=fp0.15, dashed=fp0.30), k=6",
                 fontsize=SUPTITLE_SIZE)
    fig.tight_layout()
    fig.savefig(OUT / "rq2b_13_cdf_alpha_incomplete.png")
    plt.close(fig)
    print("  ✓ rq2b_13_cdf_alpha_incomplete.png")


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
    rpt.append("# RQ2b Results Report — Byzantine Robustness × Peer-Selection Randomness\n")
    rpt.append(f"*Auto-generated by `rq2b_analysis.py`*\n")

    # --- section 1: overview ---
    rpt.append("## 1. Data Overview\n")
    rpt.append(f"- **Source file:** `data/rq2b.csv`")
    rpt.append(f"- **Total trial rows:** {len(df):,}")
    rpt.append(f"- **Topologies:** {', '.join(topo_label(t) for t in TOPOLOGIES)}")
    rpt.append(f"- **N values:** {N_VALUES}")
    rpt.append(f"- **k:** {df['k'].unique()[0]} (fixed)")
    rpt.append(f"- **viewFraction values:** {VF_VALUES}")
    rpt.append(f"- **fanOut values:** {FO_VALUES}")
    rpt.append(f"- **Failure probability values:** {FP_VALUES}")
    rpt.append(f"- **Trials per configuration:** {agg['n_trials'].iloc[0]:,}")
    rpt.append(f"- **Unique configurations:** {len(agg)}")
    rpt.append("")

    # --- section 2: full metric table (per fp level) ---
    rpt.append("## 2. Complete Metric Summary\n")
    for fp in FP_VALUES:
        rpt.append(f"### Failure Probability = {fp}\n")
        for t in TOPOLOGIES:
            for n in N_VALUES:
                rpt.append(f"#### {topo_label(t)}, N={n}\n")
                headers = ["vf", "fo", "T_end", "Omega", "M", "R", "alpha",
                           "L_0.5", "L_0.9", "L_1.0", "F_eff"]
                rows = []
                for vf in VF_VALUES:
                    for fo in FO_VALUES:
                        r = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                                (agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                                (agg["failureProbability"] == fp)]
                        if len(r) == 0:
                            continue
                        r = r.iloc[0]
                        rows.append([
                            _fmt(vf, ".1f"), fo,
                            _fmt(r["T_end_mean"]), _fmt(r["Omega_mean"], ".0f"),
                            _fmt(r["M_mean"]), _fmt(r["R"], ".3f"),
                            _fmt(r["alpha_mean"], ".3f"),
                            _fmt(r["L50_mean"], ".1f"), _fmt(r["L90_mean"], ".1f"),
                            _fmt(r["L100_mean"], ".1f"), _fmt(r["F_eff_mean"], ".1f"),
                        ])
                rpt.append(_md_table(headers, rows))
                rpt.append("")

    # --- section 3: effect of viewFraction (fanOut fixed) with comparison ---
    rpt.append("## 3. Effect of viewFraction (fanOut Fixed) — Comparison Across fp Levels\n")
    rpt.append("Mean T_end. Shows how increasing view size affects latency under Byzantine failures.\n")
    for t in TOPOLOGIES:
        for n in N_VALUES:
            rpt.append(f"### {topo_label(t)}, N={n}\n")
            headers = (["fanOut", "fp"] +
                       [f"vf={vf}" for vf in VF_VALUES] +
                       ["% change vf0.2→1.0"])
            rows = []
            for fo in FO_VALUES:
                for fp in FP_VALUES:
                    vals = []
                    for vf in VF_VALUES:
                        r = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                                (agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                                (agg["failureProbability"] == fp)]
                        vals.append(r.iloc[0]["T_end_mean"] if len(r) > 0 else float("nan"))
                    change = ((vals[0] - vals[-1]) / vals[0] * 100
                              if vals[0] > 0 else float("nan"))
                    rows.append(
                        [fo, FP_SHORT_LABELS[fp]] +
                        [_fmt(v) for v in vals] +
                        [_fmt(change, ".1f") + "%"]
                    )
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 4: effect of fanOut (viewFraction fixed) with comparison ---
    rpt.append("## 4. Effect of fanOut (viewFraction Fixed) — Comparison Across fp Levels\n")
    rpt.append("Mean T_end. Shows how increasing contact rate affects latency under Byzantine failures.\n")
    for t in TOPOLOGIES:
        for n in N_VALUES:
            rpt.append(f"### {topo_label(t)}, N={n}\n")
            headers = (["vf", "fp"] +
                       [f"fo={fo}" for fo in FO_VALUES] +
                       ["% change fo1→5"])
            rows = []
            for vf in VF_VALUES:
                for fp in FP_VALUES:
                    vals = []
                    for fo in FO_VALUES:
                        r = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                                (agg["viewFraction"] == vf) & (agg["fanOut"] == fo) &
                                (agg["failureProbability"] == fp)]
                        vals.append(r.iloc[0]["T_end_mean"] if len(r) > 0 else float("nan"))
                    change = ((vals[0] - vals[-1]) / vals[0] * 100
                              if vals[0] > 0 else float("nan"))
                    rows.append(
                        [_fmt(vf, ".1f"), FP_SHORT_LABELS[fp]] +
                        [_fmt(v) for v in vals] +
                        [_fmt(change, ".1f") + "%"]
                    )
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    # --- section 5: reliability boundary ---
    rpt.append("## 5. Reliability Boundary\n")
    rpt.append("Configurations where R < 1.0 (not all trials reach full coverage).\n")
    headers = ["Topology", "N", "fp", "vf", "fo", "R", "T_end"]
    rows = []
    for fp in FP_VALUES:
        for t in TOPOLOGIES:
            for n in N_VALUES:
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp) & (agg["R"] < 1.0)]
                sub = sub.sort_values("R")
                for _, r in sub.iterrows():
                    rows.append([
                        topo_label(t), n, FP_SHORT_LABELS[fp],
                        _fmt(r["viewFraction"], ".1f"), int(r["fanOut"]),
                        _fmt(r["R"], ".3f"), _fmt(r["T_end_mean"]),
                    ])
    if rows:
        rpt.append(_md_table(headers, rows))
    else:
        rpt.append("All configurations achieve R = 1.0.")
    rpt.append("")

    # --- section 6: diminishing returns ---
    rpt.append("## 6. Diminishing Returns Analysis\n")
    rpt.append("At vf=1.0: relative improvement per unit increase in fanOut.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for t in TOPOLOGIES:
            for n in N_VALUES:
                rpt.append(f"#### {topo_label(t)}, N={n}\n")
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["viewFraction"] == 1.0) &
                          (agg["failureProbability"] == fp)].sort_values("fanOut")
                headers = ["fanOut", "T_end", "Omega", "ΔT_end", "ΔOmega"]
                rows = []
                prev_t, prev_o = None, None
                for _, r in sub.iterrows():
                    dt = _fmt(r["T_end_mean"] - prev_t, "+.2f") if prev_t is not None else "—"
                    do = _fmt(r["Omega_mean"] - prev_o, "+.0f") if prev_o is not None else "—"
                    rows.append([
                        int(r["fanOut"]), _fmt(r["T_end_mean"]),
                        _fmt(r["Omega_mean"], ".0f"), dt, do,
                    ])
                    prev_t, prev_o = r["T_end_mean"], r["Omega_mean"]
                rpt.append(_md_table(headers, rows))
                rpt.append("")

    # --- section 7: interaction effects ---
    rpt.append("## 7. Interaction Between viewFraction and fanOut\n")
    rpt.append("% reduction in T_end from vf=0.2 to vf=1.0, at different fanOut levels.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for n in N_VALUES:
            rpt.append(f"#### N = {n}\n")
            headers = ["Topology"] + [f"fo={fo}" for fo in [1, 3, 5]]
            rows = []
            for t in TOPOLOGIES:
                vals = []
                for fo in [1, 3, 5]:
                    sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                              (agg["fanOut"] == fo) & (agg["failureProbability"] == fp)]
                    lo = sub[sub["viewFraction"] == 0.2]["T_end_mean"].values
                    hi = sub[sub["viewFraction"] == 1.0]["T_end_mean"].values
                    if len(lo) > 0 and len(hi) > 0 and lo[0] > 0:
                        vals.append(_fmt((lo[0] - hi[0]) / lo[0] * 100, ".1f") + "%")
                    else:
                        vals.append("N/A")
                rows.append([topo_label(t)] + vals)
            rpt.append(_md_table(headers, rows))
            rpt.append("")

    rpt.append("Under Byzantine failures, viewFraction still interacts with fanOut — "
               "higher fp amplifies the benefit of larger views at high fanOut, since "
               "redundant peers compensate for failed contacts.\n")

    # --- section 8: diminishing returns for viewFraction ---
    rpt.append("## 8. Diminishing Returns — viewFraction (fo=3 fixed)\n")
    rpt.append("Relative improvement per step increase in viewFraction.\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for t in TOPOLOGIES:
            for n in N_VALUES:
                rpt.append(f"#### {topo_label(t)}, N={n}\n")
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["fanOut"] == 3) &
                          (agg["failureProbability"] == fp)].sort_values("viewFraction")
                headers = ["vf", "T_end", "Omega", "ΔT_end", "ΔOmega"]
                rows = []
                prev_t, prev_o = None, None
                for _, r in sub.iterrows():
                    dt = _fmt(r["T_end_mean"] - prev_t, "+.2f") if prev_t is not None else "—"
                    do = _fmt(r["Omega_mean"] - prev_o, "+.0f") if prev_o is not None else "—"
                    rows.append([
                        _fmt(r["viewFraction"], ".1f"),
                        _fmt(r["T_end_mean"]), _fmt(r["Omega_mean"], ".0f"), dt, do,
                    ])
                    prev_t, prev_o = r["T_end_mean"], r["Omega_mean"]
                rpt.append(_md_table(headers, rows))
                rpt.append("")

    # --- section 9: incomplete trials (alpha) ---
    rpt.append("## 9. Incomplete Trials Analysis\n")
    incomplete = df[df["R_run"] == 0]
    rpt.append(f"- **Total incomplete trials:** {len(incomplete):,} / {len(df):,} "
               f"({len(incomplete) / len(df) * 100:.2f}%)\n")
    if len(incomplete) > 0:
        headers = ["Topology", "N", "fp", "Count", "Mean alpha", "Min alpha", "Max alpha"]
        rows = []
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                for n in N_VALUES:
                    sub = incomplete[(incomplete["topology"] == t) &
                                     (incomplete["nodeCount"] == n) &
                                     (incomplete["failureProbability"] == fp)]
                    if len(sub) == 0:
                        continue
                    rows.append([
                        topo_label(t), n, FP_SHORT_LABELS[fp], f"{len(sub):,}",
                        _fmt(sub["alpha"].mean(), ".3f"),
                        _fmt(sub["alpha"].min(), ".3f"),
                        _fmt(sub["alpha"].max(), ".3f"),
                    ])
        if rows:
            rpt.append(_md_table(headers, rows))
    rpt.append("")

    # --- section 10: best/worst configs ---
    rpt.append("## 10. Best and Worst Configurations per Topology\n")
    for fp in FP_VALUES:
        rpt.append(f"### {FP_LABELS[fp]}\n")
        for t in TOPOLOGIES:
            for n in N_VALUES:
                rpt.append(f"#### {topo_label(t)}, N={n}\n")
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                if len(sub) == 0:
                    rpt.append("*(No data — configuration skipped.)*\n")
                    continue
                best = sub.loc[sub["T_end_mean"].idxmin()]
                worst = sub.loc[sub["T_end_mean"].idxmax()]
                rpt.append(
                    f"- **Fastest:** vf={best['viewFraction']:.1f}, fo={int(best['fanOut'])} "
                    f"→ T_end={best['T_end_mean']:.2f}, Ω={best['Omega_mean']:.0f}, "
                    f"R={best['R']:.3f}")
                rpt.append(
                    f"- **Slowest:** vf={worst['viewFraction']:.1f}, fo={int(worst['fanOut'])} "
                    f"→ T_end={worst['T_end_mean']:.2f}, Ω={worst['Omega_mean']:.0f}, "
                    f"R={worst['R']:.3f}")
                ratio = (worst['T_end_mean'] / best['T_end_mean']
                         if best['T_end_mean'] > 0 else float('nan'))
                rpt.append(f"- **Speedup ratio (worst/best):** {ratio:.1f}x")
                rpt.append("")

    # --- section 11: fp comparison ---
    rpt.append("## 11. Failure Probability Impact Summary\n")
    rpt.append("Comparison of mean T_end between fp=0.15 and fp=0.30 (averaged over configs).\n")
    headers = ["Topology", "N", "T_end (fp=0.15)", "T_end (fp=0.30)", "Δ%", "R (fp=0.15)", "R (fp=0.30)"]
    rows = []
    for t in TOPOLOGIES:
        for n in N_VALUES:
            vals = {}
            for fp in FP_VALUES:
                sub = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                          (agg["failureProbability"] == fp)]
                vals[fp] = {
                    "T_end": sub["T_end_mean"].mean(),
                    "R": sub["R"].mean(),
                }
            t_lo = vals[0.15]["T_end"]
            t_hi = vals[0.30]["T_end"]
            delta = (t_hi - t_lo) / t_lo * 100 if t_lo > 0 else float("nan")
            rows.append([
                topo_label(t), n,
                _fmt(t_lo), _fmt(t_hi), _fmt(delta, "+.1f") + "%",
                _fmt(vals[0.15]["R"], ".3f"), _fmt(vals[0.30]["R"], ".3f"),
            ])
    rpt.append(_md_table(headers, rows))
    rpt.append("")

    # --- section 12: findings ---
    rpt.append("## 12. Findings Summary\n")
    all_configs = agg.copy()
    rpt.append(f"1. **Total configurations with R < 1.0:** "
               f"{len(all_configs[all_configs['R'] < 1.0])} / {len(all_configs)}")

    item = 2
    for fp in FP_VALUES:
        fp_agg = all_configs[all_configs["failureProbability"] == fp]
        unreliable = fp_agg[fp_agg["R"] < 0.95]
        rpt.append(f"{item}. **{FP_LABELS[fp]}:** {len(unreliable)} / {len(fp_agg)} "
                   f"configs have R < 0.95")
        item += 1

    for n in N_VALUES:
        for t in TOPOLOGIES:
            for fp in FP_VALUES:
                sub_n = agg[(agg["topology"] == t) & (agg["nodeCount"] == n) &
                            (agg["failureProbability"] == fp)]
                if len(sub_n) == 0:
                    continue
                best = sub_n.loc[sub_n["T_end_mean"].idxmin()]
                worst = sub_n.loc[sub_n["T_end_mean"].idxmax()]
                ratio = (worst["T_end_mean"] / best["T_end_mean"]
                         if best["T_end_mean"] > 0 else float("nan"))
                rpt.append(
                    f"{item}. **{topo_label(t)} (N={n}, {FP_SHORT_LABELS[fp]}):** "
                    f"range = {best['T_end_mean']:.1f} to {worst['T_end_mean']:.1f} rounds "
                    f"({ratio:.1f}x)")
                item += 1
    rpt.append("")

    # average overhead of fp=0.30 vs fp=0.15
    fp15_mean = all_configs[all_configs["failureProbability"] == 0.15]["T_end_mean"].mean()
    fp30_mean = all_configs[all_configs["failureProbability"] == 0.30]["T_end_mean"].mean()
    overhead = (fp30_mean - fp15_mean) / fp15_mean * 100 if fp15_mean > 0 else float("nan")
    rpt.append(f"{item}. **Average T_end overhead (fp=0.30 vs fp=0.15):** "
               f"{overhead:+.1f}% across all configurations")
    rpt.append("")

    report_path = OUT / "rq2b_results_report.md"
    report_path.write_text("\n".join(rpt), encoding="utf-8")
    print(f"\n  Report saved to {report_path}")


# --- main ---

if __name__ == "__main__":
    print(f"\nGenerating RQ2b figures → {OUT}/\n")
    fig_heatmaps("T_end_mean", r"Mean $T_{end}$ (rounds)",
                 "rq2b_01_heatmap_T_end.png", fmt=".1f", cmap="YlOrRd_r")
    fig_heatmaps("Omega_mean", r"Mean $\Omega$ (messages)",
                 "rq2b_02_heatmap_Omega.png", cmap="YlOrRd", use_compact=True)
    fig_heatmaps("M_mean", r"Mean $M$ (messages per node)",
                 "rq2b_03_heatmap_M.png", fmt=".1f", cmap="YlOrRd")
    fig_line_vf()
    fig_line_fo()
    fig_line_vf_Omega()
    fig_line_fo_Omega()
    fig_reliability_boundary()
    fig_interaction()
    fig_diminishing_returns()
    fig_cdf_L100()
    fig_topology_sensitivity()
    fig_heatmap_Feff()
    fig_diminishing_returns_vf()
    fig_cdf_alpha_incomplete()
    generate_report()
    print(f"\nDone. {len(list(OUT.glob('*.png')))} figures + report saved to {OUT}/")
