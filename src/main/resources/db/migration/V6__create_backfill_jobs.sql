create table if not exists backfill_jobs (
    job_id varchar(64) not null,
    status varchar(20) not null,
    from_round int not null,
    to_round int not null,
    result_json text null,
    error_message varchar(500) null,
    created_at datetime(6) not null,
    started_at datetime(6) null,
    completed_at datetime(6) null,
    constraint pk_backfill_jobs primary key (job_id),
    constraint ck_backfill_jobs_status check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    constraint ck_backfill_jobs_from_round check (from_round >= 1),
    constraint ck_backfill_jobs_to_round check (to_round >= 1),
    constraint ck_backfill_jobs_round_range check (from_round <= to_round)
);

create index ix_backfill_jobs_status_completed_at on backfill_jobs (status, completed_at);

