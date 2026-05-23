package com.xidoke.ledger;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the package-by-feature structure (ADR-0004). Feature packages are independent; common is shared
 * infrastructure. Runs as a JUnit 5 test, so a violation fails the build (and CI).
 */
@AnalyzeClasses(packages = "com.xidoke.ledger", importOptions = ImportOption.DoNotIncludeTests.class)
class LedgerArchitectureTest {

    // Fully qualified on purpose: the root package is com.xidoke.ledger, so a loose "..ledger.." would match
    // every class in the project (the "ledger" segment appears in every package), not just the ledger feature.
    private static final String[] FEATURE_PACKAGES = {
        "com.xidoke.ledger.account..",
        "com.xidoke.ledger.transfer..",
        "com.xidoke.ledger.topup..",
        "com.xidoke.ledger.ledger..",
        "com.xidoke.ledger.idempotency..",
        "com.xidoke.ledger.outbox.."
    };

    // Feature packages are independent, with two allowed exceptions:
    //   1. anyone may depend on shared `common`;
    //   2. use-case features (transfer, topup) may depend on core aggregate features (account, ledger) — a use case
    //      orchestrates aggregates via their public API (ADR-0019) — and may emit events into `outbox` in the same
    //      transaction (ADR-0013). The reverse (core/outbox → use case) stays forbidden, and core features remain
    //      independent of each other (account ↮ ledger).
    @ArchTest
    static final ArchRule featurePackagesAreIndependent = slices().matching("com.xidoke.ledger.(*)..")
            .namingSlices("$1")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("..common.."))
            .ignoreDependency(
                    resideInAnyPackage("com.xidoke.ledger.transfer..", "com.xidoke.ledger.topup.."),
                    resideInAnyPackage(
                            "com.xidoke.ledger.account..", "com.xidoke.ledger.ledger..", "com.xidoke.ledger.outbox.."))
            .as("feature packages independent; use-case features may use core features, emit to outbox, + common "
                    + "(ADR-0019, ADR-0013)");

    // common is shared infrastructure: it must never depend on a feature package.
    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures = noClasses()
            .that()
            .resideInAPackage("..common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FEATURE_PACKAGES)
            .as("common must not depend on feature packages")
            .allowEmptyShould(true);

    // @RestController belongs in a feature package, or common.web for infra/smoke endpoints.
    @ArchTest
    static final ArchRule controllersResideInFeatureOrWebPackages = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAnyPackage(
                    "com.xidoke.ledger.account..",
                    "com.xidoke.ledger.transfer..",
                    "com.xidoke.ledger.topup..",
                    "com.xidoke.ledger.ledger..",
                    "com.xidoke.ledger.idempotency..",
                    "com.xidoke.ledger.outbox..",
                    "com.xidoke.ledger.common.web..")
            .as("@RestController must reside in a feature package or common.web")
            .allowEmptyShould(true);

    // Hexagonal (ADR-0018): domain stays framework-free — no JPA / Spring imports leak into it.
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.springframework..")
            .as("domain packages must not depend on JPA or Spring (hexagonal — ADR-0018)")
            .allowEmptyShould(true);

    // Append-only (ADR-0005), code-layer guard. The DB trigger (V4) is the hard enforcement; these rules stop the
    // code from drifting toward mutation: LedgerEntry must stay an immutable fact, and its query port must stay
    // read-only (corrections are new reversing entries, never an UPDATE/DELETE).
    @ArchTest
    static final ArchRule ledgerEntriesAreImmutable = classes()
            .that()
            .haveSimpleName("LedgerEntry")
            .should()
            .haveOnlyFinalFields()
            .as("LedgerEntry is an append-only immutable fact (ADR-0005)");

    @ArchTest
    static final ArchRule ledgerEntryQueryRepositoryIsReadOnly = methods()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleName("LedgerEntryQueryRepository")
            .should()
            .haveNameNotMatching("(?i)(save|update|delete|insert|remove|merge).*")
            .as("the LedgerEntry query port exposes no mutators — entries are append-only (ADR-0005)")
            .allowEmptyShould(true);
}
