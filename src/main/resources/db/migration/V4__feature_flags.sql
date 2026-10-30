-- Column is flag_key, not key - KEY is a reserved SQL word in both H2 and Postgres.
create table feature_flags (
    flag_key varchar(255) not null,
    enabled boolean not null,
    description varchar(500),
    updated_at timestamp(6),
    updated_by varchar(255),
    primary key (flag_key)
);

-- Existing YAML-derived flags, at their current values.
insert into feature_flags (flag_key, enabled, description) values
    ('nurseCompanion', true, 'Maa Sakhi nurse care plan & on-call triage'),
    ('hospitalLeads', true, 'Local maternity hospital directory & tour bookings'),
    ('doctorLiveSessions', true, 'OB-GYN interactive live streams & RSVP'),
    ('assessmentVoucher', true, 'Instant discount voucher on diagnostic completion'),
    ('babyCareProducts', false, 'Future expansion (0-5 years) - hidden by default');

-- New flags for the care-coordination pivot, all off until their phase ships.
insert into feature_flags (flag_key, enabled, description) values
    ('productRecommendations', false, 'Product store pages (Phase 6) - off while care coordination is primary'),
    ('careBooking', false, 'Provider booking & sessions (Phase 3)'),
    ('recordsVault', false, 'Health records vault & sharing (Phase 4)'),
    ('childTracker', false, 'Child profile + vaccination tracker (Phase 6)'),
    ('aiAssistant', false, 'AI record plan (Phase 6) - needs clinical governance first'),
    ('joyCircle', false, 'Non-clinical group events (Phase 6)'),
    ('legacyLeadForms', false, 'Old public lead/assessment forms - retired after Phase 1 onboarding ships');
