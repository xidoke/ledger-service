/**
 * Shared domain kernel: value objects and immutable facts reused across feature packages ({@code Money},
 * {@code AccountId}, {@code TransactionId}, {@code Direction}, {@code LedgerEntry}).
 *
 * <p>Lives under {@code common} so every feature package may depend on it without creating cross-feature dependencies
 * (ADR-0004 package-by-feature, enforced by ArchUnit). {@code LedgerEntry} is modelled here as a shared immutable fact
 * rather than a child of the {@code Transaction} aggregate, consistent with the log-is-truth ledger model (ADR-0005).
 */
@NullMarked
package com.xidoke.ledger.common.domain;

import org.jspecify.annotations.NullMarked;
