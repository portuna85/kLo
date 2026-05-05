package com.kraft.lotto.infra.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.admin")
public record KraftAdminProperties(String username, String password, List<String> allowedIpRanges) {
}
