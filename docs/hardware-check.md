# Fitbit Air hardware check — 2026-10-12

User-reported result (nRF Connect):
- Notifications on Heart Rate Measurement (0x2A37): yes, values update immediately
- Overall: "everything is good"

Not separately reported (assumed OK, still to confirm during the on-device test):
- Whether 0x180D appears in the advertisement (BandScanner filters on it; if the scan finds nothing, drop the filter)
- Whether streaming continues after 2+ h with the screen off
- Band battery drain while sharing
