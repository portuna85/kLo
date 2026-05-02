package com.kraft.lotto.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 아키텍처 규칙 테스트.
 *
 * <p>스펙 16.8 / 3 아키텍처 원칙을 강제한다:
 * <ul>
 *     <li>domain 계층은 Spring/JPA/Web 의존을 가지지 않는다.</li>
 *     <li>domain 계층은 BusinessException(support 계층 예외)을 직접 참조하지 않는다.</li>
 *     <li>Controller(web 계층)는 JPA Entity/Repository(infrastructure 계층)를 직접 참조하지 않는다.</li>
 *     <li>{@code @Entity} 클래스는 feature.*.infrastructure 패키지 내부에만 존재한다.</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.kraft.lotto",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureTest {

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
                    )
                    .as("domain 계층은 Spring/JPA/Web/Hibernate에 의존해서는 안 된다");

    @ArchTest
    static final ArchRule domain_should_not_use_business_exception =
            noClasses()
                    .that().resideInAPackage("..feature..domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.kraft.lotto.support.BusinessException")
                    .as("domain 계층은 support.BusinessException을 직접 참조해서는 안 된다");

    @ArchTest
    static final ArchRule controllers_should_not_use_infrastructure =
            noClasses()
                    .that().resideInAPackage("..feature..web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..feature..infrastructure..")
                    .as("Controller(web 계층)는 JPA Entity/Repository(infrastructure 계층)를 직접 참조해서는 안 된다");

    @ArchTest
    static final ArchRule entities_should_reside_in_infrastructure_packages =
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..feature..infrastructure..")
                    .as("@Entity 클래스는 feature.*.infrastructure 패키지 내부에만 위치해야 한다");
}
