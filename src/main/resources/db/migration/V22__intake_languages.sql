-- Every language the user is comfortable in, in her order of preference (used to match providers and
-- onboarding coordinators). onboarding_intakes.language stays as the first entry for older clients.
create table onboarding_intake_languages (
    intake_id bigint not null references onboarding_intakes (id),
    position integer not null,
    language varchar(255) not null,
    primary key (intake_id, position)
);

insert into onboarding_intake_languages (intake_id, position, language)
select id, 0, trim(language) from onboarding_intakes where language is not null and trim(language) <> '';
