#!/usr/bin/env python3

import pathlib

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.lines as mlines

from _style import (
    TOPOLOGIES, TOPO_COLORS, TOPO_MARKERS,
    FP_LINESTYLES, FP_VALUES,
    topo_label,
)

# --- paths ---

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
OUT = ROOT / "analysis" / "figures" / "thesis"
OUT.mkdir(parents=True, exist_ok=True)

# --- representative slices ---

K_REP = 6              # matched average degree level discussed in the text
N_REP = 5000           # headline network size
SETTING_A = (1.0, 3)   # (viewFraction, fanOut)

# --- readability-first style ---

plt.rcParams.update({
    "figure.dpi":        150,
    "savefig.dpi":       300,
    "savefig.bbox":      "tight",
    "font.size":         16,
    "axes.titlesize":    18,
    "axes.labelsize":    18,
    "xtick.labelsize":   15,
    "ytick.labelsize":   15,
    "legend.fontsize":   15,
    "lines.linewidth":   2.6,
    "lines.markersize":  9,
    "axes.grid":         True,
    "grid.alpha":        0.35,
})

LW = 2.6
MS = 9

# --- data ---

print("Loading data ...")
rq1 = pd.read_csv(DATA / "rq1.csv")
rq2 = pd.read_csv(DATA / "rq2.csv")
rq2b = pd.read_csv(DATA / "rq2b.csv")


def agg_failure_free(df):
    return df.groupby(["topology", "nodeCount", "k", "viewFraction", "fanOut"]).agg(
        T_end_mean=("T_end", "mean"),
        Omega_mean=("Omega", "mean"),
        M_mean=("M", "mean"),
        R=("R_run", "mean"),
    ).reset_index()


def agg_byzantine(df):
    return df.groupby(["topology", "nodeCount", "k", "viewFraction",
                       "fanOut", "failureProbability"]).agg(
        T_end_mean=("T_end", "mean"),
        R=("R_run", "mean"),
    ).reset_index()


agg1 = agg_failure_free(rq1)
agg2 = agg_failure_free(rq2)
agg2b = agg_byzantine(rq2b)

# --- legend helpers ---


def topo_handles():
    return [mlines.Line2D([], [], color=TOPO_COLORS[t], marker=TOPO_MARKERS[t],
                          linestyle="-", linewidth=LW, markersize=MS,
                          label=topo_label(t))
            for t in TOPOLOGIES]


def fp_handles():
    return [mlines.Line2D([], [], color="0.25",
                          linestyle=FP_LINESTYLES[fp], linewidth=LW,
                          label=f"q = {fp:.2f}")
            for fp in FP_VALUES]


def save(fig, name):
    fig.savefig(OUT / name)
    plt.close(fig)
    print(f"  saved {name}")


# --- 1. thesis_latency_scaling : T_end vs N, Setting A, k=6 (fig:app_latency_scaling) ---

def fig_latency_scaling():
    vf, fo = SETTING_A
    ref = agg1[(agg1["k"] == K_REP) & (agg1["viewFraction"] == vf) &
               (agg1["fanOut"] == fo)]
    fig, ax = plt.subplots(figsize=(7.2, 5.4))
    for t in TOPOLOGIES:
        sub = ref[ref["topology"] == t].sort_values("nodeCount")
        ax.plot(sub["nodeCount"], sub["T_end_mean"], marker=TOPO_MARKERS[t],
                color=TOPO_COLORS[t], label=topo_label(t), linewidth=LW,
                markersize=MS)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel(r"Network size $N$")
    ax.set_ylabel(r"Mean dissemination time $T_{\mathrm{end}}$")
    ax.set_xticks([50, 100, 200, 500, 1000, 5000])
    ax.set_xticklabels([50, 100, 200, 500, 1000, 5000])
    ax.legend(frameon=True)
    save(fig, "thesis_latency_scaling.png")


# --- 2. thesis_message_cost : M vs N, Setting A, k=6 (fig:app_message_cost) ---

def fig_message_cost():
    vf, fo = SETTING_A
    ref = agg1[(agg1["k"] == K_REP) & (agg1["viewFraction"] == vf) &
               (agg1["fanOut"] == fo)]
    fig, ax = plt.subplots(figsize=(7.2, 5.4))
    for t in TOPOLOGIES:
        sub = ref[ref["topology"] == t].sort_values("nodeCount")
        ax.plot(sub["nodeCount"], sub["M_mean"], marker=TOPO_MARKERS[t],
                color=TOPO_COLORS[t], label=topo_label(t), linewidth=LW,
                markersize=MS)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel(r"Network size $N$")
    ax.set_ylabel(r"Mean normalised cost $M$")
    ax.set_xticks([50, 100, 200, 500, 1000, 5000])
    ax.set_xticklabels([50, 100, 200, 500, 1000, 5000])
    ax.legend(frameon=True)
    save(fig, "thesis_message_cost.png")


# --- 3. thesis_fanout_sensitivity : T_end vs fanOut, k=6, N=5000 (fig:app_fanout) ---

def fig_fanout_sensitivity():
    sub = agg2[agg2["nodeCount"] == N_REP]
    fig, ax = plt.subplots(figsize=(8.0, 5.4))
    for t in TOPOLOGIES:
        tsub = sub[sub["topology"] == t]
        mean_T = tsub.groupby("fanOut")["T_end_mean"].mean()
        ax.plot(mean_T.index, mean_T.values, marker=TOPO_MARKERS[t],
                color=TOPO_COLORS[t], label=topo_label(t), linewidth=LW,
                markersize=MS)
    ax.set_yscale("log")
    ax.set_xlabel(r"fanOut")
    ax.set_ylabel(r"Mean $T_{\mathrm{end}}$ (avg. over vf)")
    ax.set_xticks([1, 2, 3, 4, 5])
    ax.legend(frameon=True)
    save(fig, "thesis_fanout_sensitivity.png")


# --- 4. thesis_bft_reliability_threshold : min fanOut for R>=0.95 (fig:app_bft_threshold) ---
# Annotated grid (no overlap possible): rows = topology, columns = N, one block
# per failure level q. Each cell is the smallest fanOut that reaches R>=0.95 at
# viewFraction=1.0; an empty (grey) cell means no tested fanOut (<=5) did.

VF_THRESHOLD = 1.0


def _threshold_matrix(sub, fp, n_values):
    M = np.full((len(TOPOLOGIES), len(n_values)), np.nan)
    for i, t in enumerate(TOPOLOGIES):
        for j, n in enumerate(n_values):
            ok = sub[(sub["topology"] == t) & (sub["nodeCount"] == n) &
                     (np.isclose(sub["failureProbability"], fp)) &
                     (sub["R"] >= 0.95)]
            if len(ok):
                M[i, j] = ok["fanOut"].min()
    return M


def fig_bft_reliability_threshold():
    sub = agg2b[np.isclose(agg2b["viewFraction"], VF_THRESHOLD)]
    n_values = sorted(sub["nodeCount"].unique())
    cmap = plt.get_cmap("YlGnBu").copy()
    cmap.set_bad("#e6e6e6")
    fig, axes = plt.subplots(1, len(FP_VALUES), figsize=(11.0, 4.8))
    im = None
    for col, (ax, fp) in enumerate(zip(axes, FP_VALUES)):
        M = _threshold_matrix(sub, fp, n_values)
        im = ax.imshow(np.ma.masked_invalid(M), cmap=cmap, vmin=1, vmax=5,
                       aspect="auto")
        ax.set_xticks(range(len(n_values)))
        ax.set_xticklabels([str(n) for n in n_values])
        ax.set_yticks(range(len(TOPOLOGIES)))
        if col == 0:
            ax.set_yticklabels([topo_label(t) for t in TOPOLOGIES])
        else:
            ax.set_yticklabels([])
        ax.set_xlabel(r"Network size $N$")
        ax.set_title(f"$q = {fp:.2f}$")
        ax.grid(False)
        ax.set_xticks(np.arange(-0.5, len(n_values), 1), minor=True)
        ax.set_yticks(np.arange(-0.5, len(TOPOLOGIES), 1), minor=True)
        ax.grid(which="minor", color="white", linewidth=2)
        ax.tick_params(which="minor", length=0)
        for i in range(len(TOPOLOGIES)):
            for j in range(len(n_values)):
                v = M[i, j]
                if np.isnan(v):
                    ax.text(j, i, "\u2014", ha="center", va="center",
                            color="#999999", fontsize=16)
                else:
                    txt_color = "white" if (v - 1) / 4 > 0.55 else "black"
                    ax.text(j, i, f"{int(v)}", ha="center", va="center",
                            color=txt_color, fontsize=17, fontweight="bold")
    cbar = fig.colorbar(im, ax=axes, fraction=0.046, pad=0.04,
                        ticks=[1, 2, 3, 4, 5])
    cbar.set_label(r"Minimum fanOut for $R \geq 0.95$", fontsize=15)
    save(fig, "thesis_bft_reliability_threshold.png")


# --- 5. thesis_coverage_cdf_failure_free : CDF of alpha (incomplete), N=5000 (fig:app_alpha_failure_free) ---

def fig_coverage_cdf_failure_free():
    # Pool incomplete trials across all sizes: the cited mean-alpha values
    # (Random 0.998, Scale-Free 0.997) are all-N, and pooling gives the
    # non-ring curves visible spread instead of a single spike at N=5000.
    inc = rq2[rq2["R_run"] == 0]
    fig, ax = plt.subplots(figsize=(8.0, 5.4))
    for t in TOPOLOGIES:
        vals = inc[inc["topology"] == t]["alpha"].sort_values().values
        if len(vals) == 0:
            continue
        cdf = np.arange(1, len(vals) + 1) / len(vals)
        ax.step(vals, cdf, where="post", color=TOPO_COLORS[t],
                label=f"{topo_label(t)} (n={len(vals):,})", linewidth=LW)
    ax.set_xlabel(r"Final coverage $\alpha$")
    ax.set_ylabel("Cumulative fraction of trials")
    ax.set_xlim(0.9, 1.005)
    ax.legend(frameon=True, loc="upper left")
    save(fig, "thesis_coverage_cdf_failure_free.png")


# --- 6. thesis_coverage_cdf_bft : CDF of alpha (incomplete), N=5000, both q (fig:app_alpha_bft) ---

def fig_coverage_cdf_bft():
    # Pool incomplete trials across all sizes (matching the failure-free twin).
    # Two incompatible scales coexist: Ring's coverage collapses across the full
    # [0,1] range (median ~0.79 at q=0.15, ~0.44 at q=0.30) while the non-local
    # classes stay inside [0.99,1]. So we keep the full-range main axes (Ring's
    # collapse) AND a zoomed inset over [0.98,1] where the near-complete
    # distributions the text refers to actually separate.
    inc = rq2b[rq2b["R_run"] == 0]

    def draw(target):
        for fp in FP_VALUES:
            for t in TOPOLOGIES:
                vals = inc[(inc["topology"] == t) &
                           (np.isclose(inc["failureProbability"], fp))]["alpha"]
                vals = vals.sort_values().values
                if len(vals) == 0:
                    continue
                cdf = np.arange(1, len(vals) + 1) / len(vals)
                target.step(vals, cdf, where="post", color=TOPO_COLORS[t],
                            linestyle=FP_LINESTYLES[fp], linewidth=LW)

    fig, ax = plt.subplots(figsize=(8.4, 5.6))
    draw(ax)
    ax.set_xlabel(r"Final coverage $\alpha$")
    ax.set_ylabel("Cumulative fraction of trials")
    ax.set_xlim(0, 1.005)
    ax.set_ylim(-0.02, 1.02)

    axin = ax.inset_axes([0.40, 0.16, 0.40, 0.52])
    draw(axin)
    axin.set_xlim(0.98, 1.001)
    axin.set_ylim(0, 1.0)
    axin.set_title(r"detail: $\alpha \geq 0.98$", fontsize=12, loc="left")
    axin.set_xticks([0.98, 0.99, 1.00])
    axin.tick_params(labelsize=11)
    axin.grid(True, alpha=0.3)

    ax.legend(handles=topo_handles() + fp_handles(), frameon=True, ncol=2,
              loc="upper left", fontsize=12)
    save(fig, "thesis_coverage_cdf_bft.png")


# --- run all ---

def main():
    print(f"Writing thesis figures to {OUT} (k={K_REP}, N={N_REP}) ...")
    fig_latency_scaling()
    fig_message_cost()
    fig_fanout_sensitivity()
    fig_bft_reliability_threshold()
    fig_coverage_cdf_failure_free()
    fig_coverage_cdf_bft()
    print("Done.")


if __name__ == "__main__":
    main()
