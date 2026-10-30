-- Not a required consent (ConsentDocumentType.RECORDS_STORAGE.isRequired() == false): it's asked for before
-- the first health-record upload, not at sign-up, so existing users aren't blocked by its arrival.
-- DRAFT placeholder text pending legal review, same convention as V5.
insert into consent_documents (type, version, title, body_markdown, effective_at, active) values
    ('RECORDS_STORAGE', 1, 'Health Records Storage',
     '# Health Records Storage (DRAFT)

I agree to NartureBelle storing the documents I upload. This is placeholder text pending legal review. Do not treat this as a final or binding document.',
     now(), true);
