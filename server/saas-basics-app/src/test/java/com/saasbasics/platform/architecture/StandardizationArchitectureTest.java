package com.saasbasics.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class StandardizationArchitectureTest {

    private static final Pattern MIGRATION_VERSION = Pattern.compile("V(\\d+)__.*\\.sql");

    private static final List<String> DOMAIN_SPECIFIC_CLASS_NAME_PARTS = List.of(
            "LegalEntity",
            "EnterpriseGroup",
            "Company",
            "Corporation",
            "Hospital",
            "School",
            "Government",
            "TaxRegistration",
            "SocialCredit"
    );

    private static final List<String> NON_STANDARD_MIGRATION_DEFAULTS = List.of(
            "Asia/Shanghai",
            "zh-CN",
            ".local",
            "Admin@",
            "138000"
    );

    @Test
    void organizationCoreUsesDomainNeutralNames() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.saasbasics.platform.modules");

        ArchRule rule = classes()
                .that().resideInAPackage("..modules.organization..")
                .and().resideOutsideOfPackage("..modules.organization.extensions..")
                .should(useDomainNeutralClassNames())
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void domainLayersDoNotDependOnVendorSdks() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.saasbasics.platform.modules");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "..modules.organization.domain..",
                        "..modules.ai.domain.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.openai..",
                        "com.anthropic..",
                        "com.azure..",
                        "com.google.cloud..",
                        "software.amazon.awssdk.."
                )
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void organizationPublicContractsDoNotDependOnInternalImplementation() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.saasbasics.platform.modules.organization");

        noClasses()
                .that().resideInAnyPackage(
                        "..modules.organization.api..",
                        "..modules.organization.domain.."
                )
                .should().dependOnClassesThat().resideInAPackage("..modules.organization.internal..")
                .allowEmptyShould(false)
                .check(classes);
    }

    @Test
    void organizationWebLayerDoesNotAccessPersistence() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.saasbasics.platform.modules.organization");

        noClasses()
                .that().resideInAPackage("..modules.organization.internal.web..")
                .should().dependOnClassesThat().resideInAPackage("..modules.organization.internal.persistence..")
                .allowEmptyShould(false)
                .check(classes);
    }

    @Test
    void organizationModuleUsesOnlyTheAuditPublicContract() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.saasbasics.platform.modules");

        noClasses()
                .that().resideInAPackage("..modules.organization..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.audit.service..",
                        "..modules.audit.entity..",
                        "..modules.audit.mapper.."
                )
                .allowEmptyShould(false)
                .check(classes);
    }

    @Test
    void newMigrationsDoNotIntroduceRegionalOrDemoDefaults() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver().getResources(
                "classpath*:db/migration/V*.sql"
        );

        for (Resource migration : migrations) {
            String filename = migration.getFilename();
            if (filename == null) {
                continue;
            }
            Matcher matcher = MIGRATION_VERSION.matcher(filename);
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) <= 24) {
                continue;
            }
            String sql = migration.getContentAsString(StandardCharsets.UTF_8);
            for (String forbiddenDefault : NON_STANDARD_MIGRATION_DEFAULTS) {
                Assertions.assertFalse(
                        sql.contains(forbiddenDefault),
                        filename + " contains non-standard default " + forbiddenDefault
                );
            }
        }
    }

    private ArchCondition<JavaClass> useDomainNeutralClassNames() {
        return new ArchCondition<>("use domain-neutral class names") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (String forbiddenPart : DOMAIN_SPECIFIC_CLASS_NAME_PARTS) {
                    if (item.getSimpleName().contains(forbiddenPart)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                item.getName() + " contains domain-specific term " + forbiddenPart
                        ));
                    }
                }
            }
        };
    }
}
