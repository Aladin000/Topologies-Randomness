# Topologies & Randomness in Gossip Networks

This repository holds two independent products that work together. Each lives in its own folder and can be built, run, and used on its own.

```
  AAron      runs a gossip simulation  ──►  result.json  ──►  AAreplay plays it back
```

- **[`AAron-DACS`](AAron-DACS/)** is the deterministic simulator. You pick a network topology and how nodes choose their peers, then run a single trial or a large sweep. Every run produces the same metrics and writes its result to disk.
- **[`AAreplay-DACS`](AAreplay-DACS/)** is the replay viewer. It takes one result file from AAron and shows, round by round, how the message spread through the network. It does not simulate anything itself; it only plays back what the file records.

## How they fit together

The two tools share one file format and nothing else:

1. AAron simulates a gossip run and writes a `result.json` event log.
2. AAreplay loads that file and reproduces the run visually in the browser.

Because the simulator is deterministic, the same configuration always produces the same result, and the same file always looks the same when replayed.

## Getting started

Each folder has its own README with full setup and usage instructions:

- **AAron** (Java / Maven, with Python analysis scripts): see [`AAron-DACS/README.md`](AAron-DACS/README.md)
- **AAreplay** (TypeScript / Vite browser app): see [`AAreplay-DACS/README.md`](AAreplay-DACS/README.md)

## About

These tools are the software companion to *Topologies & Randomness in Gossip Networks* (Matteo Cannata, Maastricht University, Department of Advanced Computing Sciences, 2026).
