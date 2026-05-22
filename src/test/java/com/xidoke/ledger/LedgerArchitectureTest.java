package com.xidoke.ledger;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    private static final String[] FEATURE_PACKAGES = {
        "..account..", "..transfer..", "..topup..", "..ledger..", "..idempotency..", "..outbox.."
    };

    // Feature packages must not depend on each other; dependencies onto shared common are allowed.
    @ArchTest
    static final ArchRule featurePackagesAreIndependent = slices().matching("com.xidoke.ledger.(*)..")
            .namingSlices("$1")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("..common.."))
            .as("feature packages must not depend on each other (common is the only shared package)");

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
                    "..account..",
                    "..transfer..",
                    "..topup..",
                    "..ledger..",
                    "..idempotency..",
                    "..outbox..",
                    "..common.web..")
            .as("@RestController must reside in a feature package or common.web")
            .allowEmptyShould(true);
}
