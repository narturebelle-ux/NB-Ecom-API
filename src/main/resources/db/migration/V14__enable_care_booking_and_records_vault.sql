-- Phase 3 (providers/booking/sessions) and Phase 4 (records vault) have both shipped --
-- flipping the flags V4 seeded off while those phases were in progress.
update feature_flags set enabled = true, updated_at = now(), updated_by = 'migration:V14'
    where flag_key in ('careBooking', 'recordsVault');
