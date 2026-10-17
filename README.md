# Forge Stuck!!

Compatibility tooling for legacy Forge mods on NeoForge. The repository contains
the Java jar transformation pipeline and a React/Vite compatibility simulator;
simulator output is not proof that a mod works in-game.

## Contributor and agent maintenance

Start with the [agent maintenance playbook](docs/agent-maintenance-playbook.md)
for the architecture map, log reference, crash triage, compatibility patch
guardrails, and pre-PR validation checklist.

Every agent-authored PR touching transform/compat behavior must update the
playbook when behavior, diagnostics, or workflow changes, and add a dated note
linking to its PR. See the playbook's maintenance contract.
