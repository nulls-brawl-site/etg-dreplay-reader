# DReplay Reader

ExteraGram plugin for opening `.dreplay` files as readable Durak replay reports.

Architecture:

- Python plugin: loader and file-open hooks only.
- Dex bridge: `.dreplay` parsing, action decoding, and viewer UI.

Inferred format:

- `H|timestamp|trump_suit|trump_rank|first_attacker|player0|player1|loser`
- `D|card,card,...`
- `A|time_ms|player|code|arg1|arg2`

Known action codes: `5` attack/throw-in, `6` beat, `7` transfer, `8` take, `9` done.
