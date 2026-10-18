# Subtitle language selection — 2026-10-18

Missing caption metadata no longer defaults to English. Public and legacy playback routes share language normalization, including ISO-639 aliases and conflicting label/code/filename evidence. Unknown tracks remain unknown; conflicting tracks are labeled unconfirmed.

English selection uses supported Media3 tracks with matching language evidence and cannot fall back to an unrelated default or forced text track. The menu preserves an exact user-selected supported track, including an explicitly selected unknown-language track. Offline caption and resolver regression checks cover the existing download path.

No failing title was available to inspect the actual subtitle text. A provider file that is consistently mislabeled still needs a concrete example to verify its contents.
