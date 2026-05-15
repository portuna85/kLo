package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("RequiredConfigValidator")
class RequiredConfigValidatorTest {

    @Test
    @DisplayName("exposes required deploy env vars")
    void exposesRequiredDeployEnvVars() {
        assertThat(RequiredConfigValidator.requiredDeployEnvVars())
                .containsExactly(
                        "KRAFT_DB_NAME",
                        "KRAFT_DB_USER",
                        "KRAFT_DB_PASSWORD",
                        "KRAFT_DB_ROOT_PASSWORD");
    }

    @Test
    @DisplayName("adds problems when prod operational configs are missing")
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
    @DisplayName("does not add operational config problems outside prod")
    void doesNotAddOperationalConfigProblemsOutsideProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).isEmpty();
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
    @DisplayName("fails when running in container without prod profile")
    void failsWhenInContainerButNotProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("KRAFT_IN_CONTAINER", "true");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProfilePolicyProblems(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("requires prod profile"));
    }

    @Test
    @DisplayName("fails when running outside container without local profile")
    void failsWhenOutsideContainerButNotLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("KRAFT_IN_CONTAINER", "false");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProfilePolicyProblems(env, problems);

        assertThat(problems).anyMatch(p -> p.contains("requires local profile"));
    }
}
