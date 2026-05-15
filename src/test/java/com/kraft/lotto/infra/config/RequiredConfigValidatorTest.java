package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("필수 설정 검증기 테스트")
class RequiredConfigValidatorTest {

    @Test
    @DisplayName("필수 배포 환경 변수 목록을 제공한다")
    void exposesRequiredDeployEnvVars() {
        assertThat(RequiredConfigValidator.requiredDeployEnvVars())
                .containsExactly(
                        "KRAFT_DB_NAME",
                        "KRAFT_DB_USER",
                        "KRAFT_DB_PASSWORD",
                        "KRAFT_DB_ROOT_PASSWORD",
                        "KRAFT_ADMIN_API_TOKENS",
                        "KRAFT_ADMIN_API_TOKEN_HASHES");
    }

    @Test
    @DisplayName("운영 프로파일에서 관리자 토큰이 비어 있으면 문제를 추가한다")
    void addsProblemWhenProdProfileAndAdminTokenBlank() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token", " ");
        env.setProperty("kraft.admin.api-tokens", " ");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("kraft.admin.api-tokens");
    }

    @Test
    @DisplayName("운영 프로파일이 아니면 관리자 토큰 검증을 생략한다")
    void doesNotAddProblemOutsideProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("운영 프로파일에서 평문 관리자 토큰 목록은 허용하지 않는다")
    void addsProblemWhenProdPlainTokensPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-tokens", "token-value-a,token-value-b");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("plain text tokens are not allowed"));
    }

    @Test
    @DisplayName("운영 프로파일에서 레거시 평문 관리자 토큰도 허용하지 않는다")
    void addsProblemWhenOnlyLegacyProdTokenPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token", "legacy-token-value");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("plain text tokens are not allowed"));
    }

    @Test
    @DisplayName("운영 환경에서 필수 운영 설정이 누락되면 문제를 추가한다")
    void addsProblemsWhenProdOperationalConfigsMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).hasSize(3);
        assertThat(problems.get(0)).contains("kraft.api.url");
        assertThat(problems.get(1)).contains("kraft.recommend.max-attempts");
        assertThat(problems.get(2)).contains("kraft.api.client");
    }

    @Test
    @DisplayName("운영 프로파일이 아니면 운영 설정 검증을 생략한다")
    void doesNotAddOperationalConfigProblemsOutsideProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("hash list만 있어도 운영 토큰 검증을 통과한다")
    void doesNotAddProblemWhenProdTokenHashesPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token-hashes", "ops:a3f5b4b8d12f5fce3074b63f3d3c63f2bbf3fa102f9ecfceb4f6f11db5c0b1ab");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("hash 형식이 잘못되면 운영 토큰 검증에 실패한다")
    void addsProblemWhenProdTokenHashesMalformed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token-hashes", "ops:not-a-sha256");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("kraft.admin.api-token-hashes"));
    }

    @Test
    @DisplayName("prod profile requires kraft.api.client=real")
    void addsProblemWhenProdClientIsNotReal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.api.url", "https://example.com");
        env.setProperty("kraft.recommend.max-attempts", "5000");
        env.setProperty("kraft.api.client", "mock");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).anyMatch(it -> it.contains("kraft.api.client"));
    }

    @Test
    @DisplayName("컨테이너 환경에서는 prod 프로파일이 아니면 실패한다")
    void failsWhenInContainerButNotProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("KRAFT_IN_CONTAINER", "true");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProfilePolicyProblems(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("requires prod profile"));
    }

    @Test
    @DisplayName("컨테이너 외 환경에서는 local 프로파일이 아니면 실패한다")
    void failsWhenOutsideContainerButNotLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("KRAFT_IN_CONTAINER", "false");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProfilePolicyProblems(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("requires local profile"));
    }
}
