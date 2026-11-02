-- Lets a journey earn a new reminder when it reaches a new stage, without a dismissed one coming back (BUG-16).
-- The table belongs to NB-Ecom-Notifications-API now, but this API stays its only migrator. Apply before deploying that API.
alter table reminders add column dedupe_key varchar(64);
