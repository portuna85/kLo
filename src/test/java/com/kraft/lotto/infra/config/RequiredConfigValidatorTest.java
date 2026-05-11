package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredConfigValidatorTest {

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
}
