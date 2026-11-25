package com.saasbasics.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.saasbasics.platform.modules")
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule MODULES_MUST_NOT_FORM_CYCLES = slices()
            .matching("com.saasbasics.platform.modules.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule PORTAL_MUST_USE_PUBLIC_TENANT_CONTRACT = noClasses()
            .that().haveSimpleName("PortalEntryService")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..modules.tenant.mapper..", "..modules.tenant.entity..", "..modules.tenant.service..");

    @ArchTest
    static final ArchRule TENANT_PROVISIONING_AND_INDUSTRY_WORKSPACE_USE_MAPPERS = noClasses()
            .that().haveNameMatching(".*\\.(TenantBootstrapService|IndustryRegistry|IndustryWorkspace|TenantMeteringService|ExtensionInstallations|UserProfileService|TrashService)")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..");

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().resideInAnyPackage("..controller..", "..web..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..entity..", "..mapper..");
}
