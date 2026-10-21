#!/usr/bin/env python3
"""Draws the figures of docs/recommendations from the CSVs written by tools/DocFigureData.java.

    python3 tools/plot_doc_figures.py build/doc-data docs/img
"""
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.colors import LinearSegmentedColormap

D = Path(sys.argv[1])
O = Path(sys.argv[2])
O.mkdir(parents=True, exist_ok=True)

SURF, INK, MUTED, GRID = "#fcfcfb", "#0b0b0b", "#52514e", "#e6e5e1"
CAT = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"]
SEQ = ["#cde2fb", "#b7d3f6", "#9ec5f4", "#86b6ef", "#6da7ec", "#5598e7", "#3987e5", "#2a78d6", "#256abf",
       "#1c5cab", "#184f95", "#104281", "#0d366b"]
CMAP = LinearSegmentedColormap.from_list("seq", SEQ)
MARK = ["o", "s", "^", "D"]
CITIES = ["Paris", "Kourou", "Singapore", "Dubai"]

plt.rcParams.update({
    "figure.facecolor": SURF, "axes.facecolor": SURF, "savefig.facecolor": SURF,
    "text.color": INK, "axes.labelcolor": MUTED, "xtick.color": MUTED, "ytick.color": MUTED,
    "axes.edgecolor": GRID, "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.6,
    "axes.spines.top": False, "axes.spines.right": False, "font.size": 10, "axes.titlesize": 11,
    "axes.titleweight": "bold", "axes.titlelocation": "left", "legend.frameon": False,
    "lines.linewidth": 2, "svg.fonttype": "none", "font.family": "DejaVu Sans",
})


def save(fig, name, svg=True):
    fig.tight_layout()
    fig.savefig(O / (name + (".svg" if svg else ".png")), dpi=130)
    plt.close(fig)


def csv(n):
    return pd.read_csv(D / (n + ".csv"))


def line_fig(name, title, xlabel, ylabel, series, logx=False, logy=False, size=(6.6, 3.8), xlim=None, ylim=None):
    """series: list of (label, x, y[, linestyle])."""
    fig, ax = plt.subplots(figsize=size)
    for i, s in enumerate(series):
        ax.plot(s[1], s[2], color=CAT[i], label=s[0], linestyle=s[3] if len(s) > 3 else "-")
    if logx:
        ax.set_xscale("log")
    if logy:
        ax.set_yscale("log")
    if xlim:
        ax.set_xlim(*xlim)
    if ylim:
        ax.set_ylim(*ylim)
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    if len(series) > 1:
        ax.legend()
    save(fig, name)


def land_mask():
    t = pd.read_csv(D / "map_topo.csv", index_col=0)
    return t


def map_fig(name, title, label, f, vmin=None, vmax=None, log=False, cmap=CMAP, ticks=None):
    m = pd.read_csv(D / f"map_{f}.csv", index_col=0)
    lons = m.columns.astype(float).values
    lats = m.index.values.astype(float)
    z = m.values.astype(float)
    fig, ax = plt.subplots(figsize=(7.2, 3.9))
    norm = matplotlib.colors.LogNorm(vmin, vmax) if log else matplotlib.colors.Normalize(vmin, vmax)
    im = ax.pcolormesh(lons, lats, np.ma.masked_invalid(z), cmap=cmap, norm=norm, shading="nearest", rasterized=True)
    t = land_mask()
    ax.contour(t.columns.astype(float).values, t.index.values.astype(float), np.nan_to_num(t.values), levels=[0.0005],
               colors=[INK], linewidths=0.35, alpha=0.6)
    ax.set_xlim(-180, 180)
    ax.set_ylim(-85, 85)
    ax.set_xticks(range(-180, 181, 60))
    ax.set_yticks(range(-60, 61, 30))
    ax.grid(color=GRID, linewidth=0.4)
    ax.set_xlabel("Longitude (°)")
    ax.set_ylabel("Latitude (°)")
    ax.set_title(title)
    cb = fig.colorbar(im, ax=ax, pad=0.015, fraction=0.03)
    cb.set_label(label, color=MUTED)
    cb.outline.set_visible(False)
    for c, (lat, lon) in zip(CITIES, [(48.85, 2.35), (5.16, -52.65), (1.35, 103.82), (25.2, 55.27)]):
        ax.plot(lon, lat, marker="o", ms=4, mfc=SURF, mec=INK, mew=1)
    save(fig, name, svg=False)


# ---- P.838 -------------------------------------------------------------------------------------
c = csv("p838_coeffs")
line_fig("p838_k", "P.838-3: coefficient k", "Frequency (GHz)", "k", [("H", c.f, c.kH), ("V", c.f, c.kV, "--")],
         logx=True, logy=True, size=(4.8, 3.4))
line_fig("p838_alpha", "P.838-3: exponent α", "Frequency (GHz)", "α", [("H", c.f, c.aH), ("V", c.f, c.aV, "--")],
         logx=True, size=(4.8, 3.4))
g = csv("p838_gamma")
line_fig("p838_gamma", "γR = k·Rα (45° polarisation, 0° elevation)", "Frequency (GHz)", "Specific attenuation (dB/km)",
         [(f"R = {r} mm/h", g.f, g[f"r{r}"]) for r in (1, 10, 50, 100)], logx=True, logy=True)

# ---- P.676 -------------------------------------------------------------------------------------
g = csv("p676_gamma")
fig, ax = plt.subplots(figsize=(6.6, 3.8))
ax.plot(g.f, g.gamma0 + g.gammaw, color=INK, lw=1, label="total γ")
ax.plot(g.f, g.gamma0, color=CAT[0], label="dry air γ0 (O₂)")
ax.plot(g.f, g.gammaw, color=CAT[1], label="water vapour γw")
ax.set_yscale("log")
ax.set_ylim(1e-3, 1e2)
ax.set_xlabel("Frequency (GHz)")
ax.set_ylabel("Specific attenuation (dB/km)")
ax.set_title("P.676-13: sea-level specific attenuation (7.5 g/m³, 15 °C)")
for x, t in [(22.2, "H₂O"), (60, "O₂"), (118.75, "O₂"), (183.3, "H₂O"), (325, "H₂O")]:
    ax.annotate(t, (x, 1.4e-3), ha="center", color=MUTED, fontsize=8)
ax.legend(loc="upper left")
save(fig, "p676_spectrum")
s = csv("p676_slant")
line_fig("p676_slant", "Slant-path gaseous attenuation at 20 GHz", "Elevation angle (°)", "Attenuation (dB)",
         [("Annex 2 (equivalent heights)", s.el, s.annex2), ("Annex 1 (ray trace)", s.el, s.annex1, "--")],
         logy=True, size=(5.4, 3.6))
h = csv("p676_heights")
line_fig("p676_heights", "Equivalent heights (Annex 2)", "Frequency (GHz)", "Height (km)",
         [("h0 (dry air)", h.f, h.h0), ("hw (water vapour)", h.f, h.hw)], size=(5.4, 3.6))

# ---- P.837 -------------------------------------------------------------------------------------
r = csv("p837_rate")
line_fig("p837_rate", "Rainfall rate exceeded for p % of an average year", "Percentage of time p (%)",
         "Rainfall rate (mm/h)", [(c, r[r.city == c].p, r[r.city == c].rate) for c in CITIES], logx=True, logy=True)
map_fig("p837_map", "P.837-7: rainfall rate exceeded for 0.01 % of the time", "R0.01 (mm/h)", "r001", 0, 150)

# ---- P.839 / 840 / 453 / 836 / 1510 / 1511 maps ------------------------------------------------
map_fig("p839_map", "P.839-4: mean annual rain height hR = h0 + 0.36 km", "Rain height (km)", "hr", 0, 5.5)
map_fig("p840_map", "P.840-7: reduced cloud liquid water exceeded for 1 % of the time", "Lred (kg/m²)", "lred1", 0, 3)
map_fig("p453_map", "P.453-13: wet refractivity Nwet exceeded for 50 % of the time", "Nwet (N-units)", "nwet50", 0, 140)
map_fig("p836_rho", "P.836-6: surface water vapour density exceeded for 1 %", "ρ (g/m³)", "rho1", 0, 30)
map_fig("p836_v", "P.836-6: total water vapour content exceeded for 1 %", "V (kg/m²)", "v1", 0, 70)
map_fig("p1510_map", "P.1510-1: annual mean surface temperature", "T (K)", "tmean", 235, 305)
map_fig("p1511_map", "P.1511-1: topographic altitude above mean sea level", "Altitude (km)", "topo", 0, 4)

k = csv("p840_kl")
line_fig("p840_kl", "Cloud liquid water specific attenuation coefficient Kl", "Frequency (GHz)",
         "Kl ((dB/km)/(g/m³))",
         [("−8 °C", k.f, k.tm8), ("0 °C", k.f, k.t0), ("10 °C", k.f, k.t10), ("20 °C", k.f, k.t20)], logy=True)

e = csv("p453_es")
line_fig("p453_es", "Saturation vapour pressure", "Temperature (°C)", "es (hPa)",
         [("over water", e.t, e.water), ("over ice", e.t, e.ice, "--")], logy=True, size=(5.4, 3.6))

v = csv("p836_p")
fig, axs = plt.subplots(1, 2, figsize=(8.4, 3.6))
for i, cty in enumerate(CITIES):
    q = v[v.city == cty]
    axs[0].plot(q.p, q.rho, color=CAT[i], marker=MARK[i], ms=3, label=cty)
    axs[1].plot(q.p, q.v, color=CAT[i], marker=MARK[i], ms=3, label=cty)
for a, t, yl in zip(axs, ["Surface density ρ", "Total content V"], ["g/m³", "kg/m²"]):
    a.set_xscale("log")
    a.set_xlabel("Percentage of time p (%)")
    a.set_ylabel(yl)
    a.set_title(t)
axs[0].legend()
save(fig, "p836_p")

m = csv("p1510_months")
fig, ax = plt.subplots(figsize=(5.8, 3.6))
for i, cty in enumerate(CITIES):
    q = m[m.city == cty]
    ax.plot(q.month, q.t, color=CAT[i], marker=MARK[i], ms=4, label=cty)
ax.set_xticks(range(1, 13))
ax.set_xlabel("Month")
ax.set_ylabel("Mean surface temperature (K)")
ax.set_title("P.1510-1: monthly mean temperature")
ax.legend()
save(fig, "p1510_months")

# ---- P.835 -------------------------------------------------------------------------------------
p = csv("p835_profile")
fig, axs = plt.subplots(1, 3, figsize=(9.6, 3.8), sharey=True)
for a, col, t, xl, lg, c0 in zip(axs, ["t", "p", "rho"], ["Temperature", "Pressure", "Water vapour density"],
                                 ["K", "hPa (log)", "g/m³ (log)"], [False, True, True], CAT[:3]):
    a.plot(p[col].clip(lower=1e-6), p.h, color=c0)
    a.set_title(t)
    a.set_xlabel(xl)
    if lg:
        a.set_xscale("log")
axs[0].set_ylabel("Height (km)")
axs[2].set_xlim(1e-4, 20)
axs[1].set_xlim(1e-3, 2000)
save(fig, "p835_profile")

# ---- P.618 -------------------------------------------------------------------------------------
a = csv("p618_p")
line_fig("p618_p", "Rain attenuation exceeded for p % (20 GHz, 35°)", "Percentage of time p (%)", "A_rain (dB)",
         [(c, a[a.city == c].p, a[a.city == c].a) for c in CITIES], logx=True, logy=True)
a = csv("p618_f")
line_fig("p618_f", "Rain attenuation exceeded for 0.01 % (35°)", "Frequency (GHz)", "A_rain (dB)",
         [(c, a[a.city == c].f, a[a.city == c].a) for c in CITIES], logy=True)
x = csv("p618_xpd")
line_fig("p618_xpd", "Rain XPD vs co-polar attenuation (35°, 0.01 %)", "Co-polar attenuation Ap (dB)", "XPD (dB)",
         [(f"{f} GHz", x[x.f == f].ap, x[x.f == f].xpd) for f in (12, 20, 30)], size=(5.4, 3.6))
sc = csv("p618_scint")
line_fig("p618_scint", "Scintillation fade, Paris, p = 1 %, 1.2 m antenna", "Elevation angle (°)", "A_s (dB)",
         [(f"{f} GHz", sc[sc.f == f].el, sc[sc.f == f].a) for f in (12, 20, 30)], logy=True, size=(5.4, 3.6))

# ---- combined ----------------------------------------------------------------------------------
t = csv("itur_f")
fig, ax = plt.subplots(figsize=(6.6, 3.8))
for i, (col, lab) in enumerate([("gas", "gases"), ("cloud", "clouds"), ("rain", "rain"), ("scint", "scintillation")]):
    ax.plot(t.f, t[col], color=CAT[i], label=lab)
ax.plot(t.f, t.total, color=INK, lw=2.4, linestyle="--", label="total")
ax.set_yscale("log")
ax.set_xlabel("Frequency (GHz)")
ax.set_ylabel("Attenuation (dB)")
ax.set_title("Paris, 35°, p = 0.1 %, 1.2 m antenna")
ax.legend(ncol=2)
save(fig, "itur_contributions")
print("ok")
