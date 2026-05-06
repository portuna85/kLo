package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;

/** 추천 후보 로또 번호 조합을 생성한다. */
public interface LottoNumberGenerator {

    LottoCombination generate();
}
