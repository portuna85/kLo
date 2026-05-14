create table if not exists winning_number_frequency_summary (
    ball int not null,
    hit_count bigint not null default 0,
    last_calculated_round int not null default 0,
    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint pk_winning_number_frequency_summary primary key (ball),
    constraint ck_winning_number_frequency_summary_ball check (ball between 1 and 45),
    constraint ck_winning_number_frequency_summary_count check (hit_count >= 0),
    constraint ck_winning_number_frequency_summary_round check (last_calculated_round >= 0)
);
