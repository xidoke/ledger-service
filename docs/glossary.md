# Glossary

Domain term dictionary — the **ubiquitous language** for the ledger-service. When a term in code, docs, or commit messages is ambiguous, this file wins.

**Status**: stub. LDG-30 ports the full glossary from the vault note `Dự án - Ledger Service/90 - Glossary.md` and adds repo-side cross-references (ADRs, code packages).

## Why this file exists

Terms like `Transaction` are overloaded — DB transaction vs domain transaction (a posting of ledger entries) vs HTTP request. Without a single source of truth, every reader (and AI agent) re-derives meaning from context and sometimes gets it wrong.

Source of truth for domain terms. Reference this file in ADR descriptions, code comments where naming is non-obvious, and onboarding material.
