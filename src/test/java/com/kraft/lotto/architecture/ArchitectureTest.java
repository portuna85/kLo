package com.kraft.lotto.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

/**
 * ?熬곥굤????고뱱 ?잙?裕?????裕??
 *
 * <p>???덉쓢 16.8 / 3 ?熬곥굤????고뱱 ???????띠룆踰???類ｋ펲:
 * <ul>
 *     <li>domain ??ｌ뫒筌?? Spring/JPA/Web ??琉돠???띠럾?嶺뚯솘?嶺뚯솘? ???낅츎??</li>
 *     <li>domain ??ｌ뫒筌?? BusinessException(support ??ｌ뫒筌????깅뇶)??嶺뚯쉳???嶺뚣볦굣???? ???낅츎??</li>
 *     <li>Controller(web ??ｌ뫒筌???JPA Entity/Repository(infrastructure ??ｌ뫒筌???嶺뚯쉳???嶺뚣볦굣???? ???낅츎??</li>
 *     <li>{@code @Entity} ???????노츎 feature.*.infrastructure ????뺟춯?뼿 ???????異??브퀡????類ｋ펲.</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.kraft.lotto",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
    @DisplayName("tests for ArchitectureTest")
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
                    .as("domain ??ｌ뫒筌?? Spring/JPA/Web/Hibernate????琉돠??怨댄맋??????類ｋ펲");

    @ArchTest
    static final ArchRule domain_should_not_use_business_exception =
            noClasses()
                    .that().resideInAPackage("..feature..domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.kraft.lotto.support.BusinessException")
                    .as("domain ??ｌ뫒筌?? support.BusinessException??嶺뚯쉳???嶺뚣볦굣???怨댄맋??????類ｋ펲");

    @ArchTest
    static final ArchRule controllers_should_not_use_infrastructure =
            noClasses()
                    .that().resideInAPackage("..feature..web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..feature..infrastructure..")
                    .as("Controller(web ??ｌ뫒筌???JPA Entity/Repository(infrastructure ??ｌ뫒筌???嶺뚯쉳???嶺뚣볦굣???怨댄맋??????類ｋ펲");

    @ArchTest
    static final ArchRule entities_should_reside_in_infrastructure_packages =
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..feature..infrastructure..")
                    .as("@Entity ???????노츎 feature.*.infrastructure ????뺟춯?뼿 ???????異??熬곣뫚???怨룻뒍 ??類ｋ펲");
}
