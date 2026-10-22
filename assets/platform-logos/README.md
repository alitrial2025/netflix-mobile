# Platform wordmarks

These bundled SVG wordmarks identify the appearance selected by the user. Brands and marks belong to their respective owners. NetflixPro's own branding remains separate.

`sources.json` records the public source repository and path for each original SVG. Apple TV and Disney+ outlines come from selfhst/icons; Prime Video and Netflix from pheralb/svgl; Peacock's wordmark and colored dots from jonzan0ff/HomeTeam; the outlined JioHotstar lockup from sethihq/baron-tv. White variants retain contrast on dark app surfaces. Prime's blue arrow/lettering and Peacock's colored dots are retained.

`tools/branding/export-platform-logos.py` uses fontTools to normalize the outlined geometry, trim empty margins, preserve source transforms and produce `*-wordmark.svg` plus Android `ic_platform_*.xml` vector drawables. Run it from any directory with `python3 tools/branding/export-platform-logos.py`. The Android UI draws the vectors synchronously; it doesn't download or decode logos during navigation. The source files containing collection-specific backgrounds are retained here for provenance; the exported logo includes only the outlined wordmark.
