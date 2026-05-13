package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredConfigValidatorTest {

    @Test
    void exposesRequiredDeployEnvVars() {
        assertThat(RequiredConfigValidator.requiredDeployEnvVars())
                .containsExactly(
                        "KRAFT_DB_NAME",
                        "KRAFT_DB_USER",
                        "KRAFT_DB_PASSWORD",
                        "KRAFT_DB_ROOT_PASSWORD",
                        "KRAFT_ADMIN_API_TOKEN");
    }

    @Test
    void addsProblemWhenProdProfileAndAdminTokenBlank() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token", " ");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("kraft.admin.api-token");
    }

    @Test
    void doesNotAddProblemOutsideProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).isEmpty();
    }

    @Test
    void doesNotAddProblemWhenProdTokenPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kraft.admin.api-token", "token-value");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdAdminTokenProblem(env, problems);

        assertThat(problems).isEmpty();
    }

    @Test
    void addsProblemsWhenProdOperationalConfigsMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).contains("kraft.api.url");
        assertThat(problems.get(1)).contains("kraft.recommend.max-attempts");
    }

    @Test
    void doesNotAddOperationalConfigProblemsOutsideProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        List<String> problems = new ArrayList<>();

        RequiredConfigValidator.addProdOperationalConfigProblems(env, problems);

        assertThat(problems).isEmpty();
    }
}
