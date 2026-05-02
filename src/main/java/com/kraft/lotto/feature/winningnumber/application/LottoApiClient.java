package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.Optional;

/**
 * 외부 로또 당첨번호 API에 대한 어댑터.
 * 구현체는 회차(round)에 해당하는 {@link WinningNumber}를 반환하거나,
 * 아직 추첨되지 않은 회차이면 {@link Optional#empty()}를 반환한다.
 *
 * 네트워크/파싱 실패 등 시스템 오류는 {@link LottoApiClientException}로 통일하여 던진다.
 */
public interface LottoApiClient {

    Optional<WinningNumber> fetch(int round);
}
