# NOUS — Architecture Decision Records (ADRs)

This directory contains all major architectural decisions for the NOUS project.
Each ADR follows the format: Context → Decision → Consequences → Alternatives.

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-min-sdk-29.md) | Minimum SDK 29 (Android 10) | Accepted |
| [0002](0002-hilt-di.md) | Hilt for Dependency Injection | Accepted |
| [0003](0003-multi-module-architecture.md) | Multi-Module Architecture (30+ Gradle Modules) | Accepted |
| [0004](0004-cicd-github-actions.md) | CI/CD on GitHub Actions | Accepted |
| [0005](0005-crashlytics-breakpad.md) | Firebase Crashlytics + Breakpad for Crash Reporting | Accepted |
| [0006](0006-distribution-channels.md) | Distribution: Play Store + GitHub Releases | Accepted |
| [0007](0007-play-integrity-soft-enforcement.md) | Play Integrity Soft Enforcement | Accepted |
| [0008](0008-self-modifying-js-flavor-gated.md) | Self-Modifying JS Engine (Rhino) — Flavor-Gated | Accepted |
| [0009](0009-hacker-module-flavor-gated.md) | Hacker Module — Flavor-Gated | Accepted |
| [0010](0010-llm-model-onboarding-strategy.md) | LLM Model Onboarding Strategy | Accepted |
| [0011](0011-maximum-privacy-default.md) | Maximum Privacy as Default | Accepted |
| [0012](0012-license-proprietary.md) | License: Proprietary (Personal), Future Commercial | Accepted |

## When to Add an ADR

Add a new ADR when making a decision that:
- Affects multiple modules
- Has long-term implications
- Has viable alternatives that were considered
- Would be expensive to reverse

## Format

```markdown
# ADR XXXX — Title

Date: YYYY-MM-DD
Status: Accepted | Proposed | Deprecated | Superseded by ADR YYYY
Decision Owner: <name>

## Context
(Why this decision is being made — what problem are we solving?)

## Decision
(What we decided — be specific and unambiguous.)

## Consequences
(What changes as a result? Positive and negative.)

## Alternatives Considered
(What else did we consider? Why did we reject it?)
```
