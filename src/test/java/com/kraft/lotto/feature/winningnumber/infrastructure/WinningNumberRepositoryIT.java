package com.kraft.lotto.feature.winningnumber.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("당첨 번호 레포지토리 통합 테스트")
class WinningNumberRepositoryIT {

    private static final int ROUND_1200 = 1200;
    private static final int ROUND_1201 = 1201;
    private static final int ROUND_1202 = 1202;
    private static final LocalDate DRAW_DATE_1200 = LocalDate.of(2026, 5, 1);
    private static final LocalDate DRAW_DATE_1201 = LocalDate.of(2026, 5, 8);
    private static final LocalDate DRAW_DATE_1202 = LocalDate.of(2026, 5, 15);

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.8")
            .withDatabaseName("kraft_lotto")
            .withUsername("kraft")
            .withPassword("kraft");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
    }

    @Autowired
    WinningNumberRepository repository;

    @PersistenceContext
    EntityManager em;

    private static WinningNumberEntity entityOf(int round, LocalDate drawDate,
                                                int n1, int n2, int n3, int n4, int n5, int n6,
                                                int bonus) {
        WinningNumber domain = new WinningNumber(
                round, drawDate,
                new LottoCombination(List.of(n1, n2, n3, n4, n5, n6)),
                bonus, 1_000_000_000L, 1, 50_000_000_000L);
        return WinningNumberMapper.toEntity(domain, LocalDateTime.now());
    }

    @Test
    @Transactional
    @DisplayName("회차별 저장 및 조회가 가능하다")
    void savesAndFindsByRound() {
        repository.save(entityOf(1100, LocalDate.of(2026, 5, 1), 1, 7, 13, 22, 34, 45, 8));
        em.flush();
        em.clear();

        WinningNumberEntity found = repository.findById(1100).orElseThrow();
        assertThat(found.getRound()).isEqualTo(1100);
        assertThat(found.getN1()).isEqualTo(1);
        assertThat(found.getN6()).isEqualTo(45);
        assertThat(found.getBonusNumber()).isEqualTo(8);
        assertThat(found.getDrawDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @Transactional
    @DisplayName("매퍼를 통한 도메인 변환 시 데이터가 유지된다")
    void mapperRoundtripPreservesDomain() {
        repository.save(entityOf(1101, LocalDate.of(2026, 5, 8), 3, 9, 15, 21, 27, 33, 40));
        em.flush();
        em.clear();

        WinningNumber domain = WinningNumberMapper.toDomain(repository.findById(1101).orElseThrow());
        assertThat(domain.round()).isEqualTo(1101);
        assertThat(domain.combination().numbers()).containsExactly(3, 9, 15, 21, 27, 33);
        assertThat(domain.bonusNumber()).isEqualTo(40);
    }

    @Test
    @Transactional
    @DisplayName("최신 회차 조회 시 가장 큰 회차를 반환한다")
    void findTopByOrderByRoundDescReturnsLatest() {
        repository.save(savedRound1200());
        repository.save(savedRound1202());
        repository.save(savedRound1201());
        em.flush();
        em.clear();

        WinningNumberEntity latest = repository.findTopByOrderByRoundDesc().orElseThrow();
        assertThat(latest.getRound()).isEqualTo(ROUND_1202);
    }

    @Test
    @Transactional
    @DisplayName("최대 회차 번호 조회가 정상 작동한다")
    void findMaxRoundWorks() {
        repository.save(entityOf(1300, LocalDate.of(2026, 5, 1), 1, 7, 13, 22, 34, 45, 8));
        repository.save(entityOf(1302, LocalDate.of(2026, 5, 15), 2, 8, 14, 23, 35, 44, 9));
        em.flush();

        assertThat(repository.findMaxRound()).contains(1302);
    }

    @Test
    @Transactional
    @DisplayName("회차 존재 여부 확인이 정상 작동한다")
    void existsByRoundReturnsCorrectly() {
        repository.save(entityOf(1400, LocalDate.of(2026, 5, 1), 1, 7, 13, 22, 34, 45, 8));
        em.flush();

        assertThat(repository.existsByRound(1400)).isTrue();
        assertThat(repository.existsByRound(9999)).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("회차 내림차순 페이징 조회가 가능하다")
    void findAllByOrderByRoundDescPaginates() {
        for (int r = 1500; r < 1510; r++) {
            repository.save(entityOf(r, LocalDate.of(2026, 5, 1).plusWeeks(r - 1500),
                    1, 2, 3, 4, 5, 6, 7));
        }
        em.flush();
        em.clear();

        var page = repository.findAllByOrderByRoundDesc(PageRequest.of(0, 5));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(10);
        assertThat(page.getContent().getFirst().getRound()).isEqualTo(1509);
        assertThat(page.getContent().getLast().getRound()).isEqualTo(1505);
    }

    @Test
    @Transactional
    @DisplayName("체크 제약 조건에 의해 보너스 번호가 당첨 번호와 같으면 거부한다")
    void rejectsBonusEqualToMainNumberByCheckConstraint() {
        WinningNumberEntity bad = new WinningNumberEntity(
                1600, LocalDate.of(2026, 6, 1),
                1, 2, 3, 4, 5, 6,
                3,
                1_000_000_000L, 1, 50_000_000_000L,
                LocalDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("체크 제약 조건에 의해 정렬되지 않은 번호는 거부한다")
    void rejectsOutOfOrderNumbersByCheckConstraint() {
        WinningNumberEntity bad = new WinningNumberEntity(
                1601, LocalDate.of(2026, 6, 8),
                5, 4, 3, 2, 1, 6,
                7,
                1_000_000_000L, 1, 50_000_000_000L,
                LocalDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("체크 제약 조건에 의해 음수 상금은 거부한다")
    void rejectsNegativeFirstPrizeByCheckConstraint() {
        WinningNumberEntity bad = new WinningNumberEntity(
                1602, LocalDate.of(2026, 6, 15),
                1, 2, 3, 4, 5, 6,
                7,
                -1L, 0, 0L,
                LocalDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static WinningNumberEntity savedRound1200() {
        return entityOf(ROUND_1200, DRAW_DATE_1200, 1, 7, 13, 22, 34, 45, 8);
    }

    private static WinningNumberEntity savedRound1201() {
        return entityOf(ROUND_1201, DRAW_DATE_1201, 3, 9, 15, 21, 27, 33, 40);
    }

    private static WinningNumberEntity savedRound1202() {
        return entityOf(ROUND_1202, DRAW_DATE_1202, 2, 8, 14, 23, 35, 44, 9);
    }
}

