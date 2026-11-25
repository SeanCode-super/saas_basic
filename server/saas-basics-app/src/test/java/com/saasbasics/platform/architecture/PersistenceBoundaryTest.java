package com.saasbasics.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = {"com.saasbasics.platform.modules", "com.saasbasics.platform.outbox"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceBoundaryTest {

    @ArchTest
    static final ArchRule BUSINESS_SQL_BELONGS_IN_MAPPER_XML = noClasses()
            .that().doNotHaveFullyQualifiedName("com.saasbasics.platform.modules.integration.mapper.PayOrderMapper")
            .should().dependOnClassesThat().haveNameMatching(
                    "org\\.apache\\.ibatis\\.annotations\\.(Select|Insert|Update|Delete)(Provider)?(\\$List)?");

    @ArchTest
    static final ArchRule JDBC_EXECUTION_REQUIRES_A_REVIEWED_ENGINE = noClasses()
            .that().doNotHaveFullyQualifiedName("com.saasbasics.platform.modules.ai.application.service.Nl2SqlService")
            .and().doNotHaveFullyQualifiedName("com.saasbasics.platform.modules.codegen.service.AiAppBuilderService")
            .and().doNotHaveFullyQualifiedName("com.saasbasics.platform.modules.codegen.service.DynamicDataService")
            .and().doNotHaveFullyQualifiedName("com.saasbasics.platform.modules.tenant.service.TenantBusinessSnapshot")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc.core..");
}
