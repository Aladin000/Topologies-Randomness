import os
import pathlib

import numpy as np
import matplotlib.colors as mcolors
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import seaborn as sns

# --- MPLCONFIGDIR ---

_MPL_CACHE = pathlib.Path(__file__).resolve().parent / ".mplconfig"
_MPL_CACHE.mkdir(exist_ok=True)
os.environ.setdefault("MPLCONFIGDIR", str(_MPL_CACHE))

# --- topology constants ---

TOPOLOGIES = ["Ring", "SmallWorld", "Random", "ScaleFree"]

# Paul Tol "bright" qualitative palette (colorblind-safe, publication-grade).
TOPO_COLORS = {
    "Ring":       "#4477AA",   # steel blue
    "SmallWorld": "#228833",   # forest green
    "Random":     "#CCBB44",   # gold
    "ScaleFree":  "#EE6677",   # rose
}

TOPO_LABELS = {
    "Ring":       "Ring",
    "SmallWorld": "Small-World",
    "Random":     "Random",
    "ScaleFree":  "Scale-Free",
}

TOPO_MARKERS = {
    "Ring":       "o",
    "SmallWorld": "s",
    "Random":     "D",
    "ScaleFree":  "^",
}

# --- dimension and font-size constants ---

FIG_SINGLE_H = 5.5
FIG_DOUBLE_H = 10
FIG_SMALL_W  = 8

SUPTITLE_SIZE  = 14
SUBTITLE_SIZE  = 11
LABEL_SIZE     = 11
ANNOT_SIZE     = 8
LEGEND_SIZE    = 9
TICK_SIZE      = 9
HEATMAP_ANNOT  = 8

DPI_SCREEN = 150
DPI_SAVE   = 300          # publication quality

# --- peer-selection colour ramps (used by RQ2) ---
# Single-hue sequential ramp (viridis-derived) so ordinal ranking is visually
# obvious: lighter = lower value, darker = higher value.

FO_COLORS = {
    1: "#fde725",   # viridis yellow  (lowest)
    2: "#5ec962",   # viridis green
    3: "#21918c",   # viridis teal
    4: "#3b528b",   # viridis indigo
    5: "#440154",   # viridis purple  (highest)
}
VF_COLORS = {
    0.2: "#fde725",
    0.4: "#5ec962",
    0.6: "#21918c",
    0.8: "#3b528b",
    1.0: "#440154",
}

# --- Byzantine failure probability constants ---

FP_VALUES = [0.15, 0.30]

FP_LINESTYLES = {
    0.15: "solid",
    0.30: "dashed",
}

FP_LABELS = {
    0.15: "fp = 0.15 (moderate)",
    0.30: "fp = 0.30 (high-stress)",
}

FP_SHORT_LABELS = {
    0.15: "fp=0.15",
    0.30: "fp=0.30",
}

# --- theme application ---

def apply_theme():
    """Call once at script start to configure seaborn + matplotlib RC params."""
    sns.set_theme(style="whitegrid", font_scale=1.05)
    plt.rcParams.update({
        "figure.dpi":       DPI_SCREEN,
        "savefig.dpi":      DPI_SAVE,
        "savefig.bbox":     "tight",
        "axes.titlesize":   SUBTITLE_SIZE,
        "axes.labelsize":   LABEL_SIZE,
        "xtick.labelsize":  TICK_SIZE,
        "ytick.labelsize":  TICK_SIZE,
        "legend.fontsize":  LEGEND_SIZE,
        "figure.titlesize": SUPTITLE_SIZE,
    })


# --- helpers ---

def topo_label(t: str) -> str:
    """Human-readable topology name."""
    return TOPO_LABELS.get(t, t)


def thousands_formatter():
    """Axis formatter that turns 12345 -> '12,345'."""
    return ticker.FuncFormatter(lambda x, _: f"{x:,.0f}")


def compact_formatter():
    """Axis formatter that turns 115000 -> '115K', 1200 -> '1.2K'."""
    def _fmt(x, _):
        if abs(x) >= 1_000_000:
            return f"{x / 1_000_000:.1f}M"
        if abs(x) >= 1_000:
            return f"{x / 1_000:.0f}K" if x % 1_000 == 0 else f"{x / 1_000:.1f}K"
        return f"{x:,.0f}"
    return ticker.FuncFormatter(_fmt)


def fix_heatmap_text_contrast(ax, data_2d, cmap_name, vmin=None, vmax=None):
    """Set heatmap annotation text to white on dark cells, black on light.

    Call immediately after ``sns.heatmap()`` on the same *ax*.
    *data_2d* is the underlying numpy array (pivot.values).
    """
    cmap = plt.get_cmap(cmap_name)
    if np.all(np.isnan(data_2d)):
        return
    lo = vmin if vmin is not None else np.nanmin(data_2d)
    hi = vmax if vmax is not None else np.nanmax(data_2d)
    norm = mcolors.Normalize(vmin=lo, vmax=hi)

    for text in ax.texts:
        x, y = text.get_position()
        ci, ri = int(x), int(y)
        if 0 <= ri < data_2d.shape[0] and 0 <= ci < data_2d.shape[1]:
            val = data_2d[ri, ci]
            if np.isnan(val):
                continue
            rgba = cmap(norm(val))
            lum = 0.2126 * rgba[0] + 0.7152 * rgba[1] + 0.0722 * rgba[2]
            text.set_color("white" if lum < 0.5 else "black")
