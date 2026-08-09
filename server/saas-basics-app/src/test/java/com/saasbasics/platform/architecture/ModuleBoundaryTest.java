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
    static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_PERSISTENCE = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..entity..", "..mapper..");
}
