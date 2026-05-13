package com.kraft.lotto.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

/**
 * 아키텍처 경계 규칙을 검증한다.
 *
 * <p>검증 규칙:</p>
 * <ul>
 *     <li>domain 패키지는 Spring/JPA/Web/Hibernate 의존을 갖지 않는다.</li>
 *     <li>domain 패키지는 support.BusinessException 의존을 갖지 않는다.</li>
 *     <li>controller(web)는 infrastructure 패키지를 직접 참조하지 않는다.</li>
 *     <li>{@code @Entity} 클래스는 feature.*.infrastructure 하위에 위치한다.</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.kraft.lotto",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@DisplayName("아키텍처 경계 테스트")
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
                    .as("domain 패키지는 Spring/JPA/Web/Hibernate에 의존하면 안 된다");

    @ArchTest
    static final ArchRule domain_should_not_use_business_exception =
            noClasses()
                    .that().resideInAPackage("..feature..domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.kraft.lotto.support.BusinessException")
                    .as("domain 패키지는 support.BusinessException에 의존하면 안 된다");

    @ArchTest
    static final ArchRule controllers_should_not_use_infrastructure =
            noClasses()
                    .that().resideInAPackage("..feature..web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..feature..infrastructure..")
                    .as("controller는 infrastructure를 직접 참조하면 안 된다");

    @ArchTest
    static final ArchRule entities_should_reside_in_infrastructure_packages =
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..feature..infrastructure..")
                    .as("@Entity 클래스는 feature.*.infrastructure 하위에 위치해야 한다");
}
