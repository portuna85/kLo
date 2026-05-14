package com.kraft.lotto.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

@AnalyzeClasses(
        packages = "com.kraft.lotto",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@DisplayName("ArchitectureTest")
class ArchitectureTest {

    private static final DescribedPredicate<JavaClass> DEPRECATED_FOR_REMOVAL =
            new DescribedPredicate<>("are @Deprecated(forRemoval = true)") {
                @Override
                public boolean test(JavaClass input) {
                    Deprecated deprecated = input.reflect().getAnnotation(Deprecated.class);
                    return deprecated != null && deprecated.forRemoval();
                }
            };

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa_or_web =
            noClasses()
                    .that().resideInAPackage("..feature..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            "org.hibernate..",
                            "org.springframework.web.."
                    );

    @ArchTest
    static final ArchRule domain_should_not_use_business_exception =
            noClasses()
                    .that().resideInAPackage("..feature..domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.kraft.lotto.support.BusinessException");

    @ArchTest
    static final ArchRule controllers_should_not_use_infrastructure =
            noClasses()
                    .that().resideInAPackage("..feature..web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..feature..infrastructure..");

    @ArchTest
    static final ArchRule entities_should_reside_in_infrastructure_packages =
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..feature..infrastructure..");

    @ArchTest
    static final ArchRule deprecated_for_removal_should_not_be_service =
            noClasses().that(DEPRECATED_FOR_REMOVAL).should().beAnnotatedWith(Service.class).allowEmptyShould(true);

    @ArchTest
    static final ArchRule deprecated_for_removal_should_not_be_component =
            noClasses().that(DEPRECATED_FOR_REMOVAL).should().beAnnotatedWith(Component.class).allowEmptyShould(true);

    @ArchTest
    static final ArchRule deprecated_for_removal_should_not_be_controller =
            noClasses().that(DEPRECATED_FOR_REMOVAL).should().beAnnotatedWith(Controller.class).allowEmptyShould(true);
}
