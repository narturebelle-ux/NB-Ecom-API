-- Provider documents move to S3 (owner, 12 Nov 2026). Null storage_provider = the old way, bytes in
-- application_document_contents, which keeps working. V1 is unchanged (its checksum is already recorded).
alter table application_documents add column storage_provider varchar(32);
alter table application_documents add column storage_bucket varchar(255);
alter table application_documents add column storage_object_path varchar(1000);
alter table application_documents add column scan_status varchar(32);
alter table application_documents add column kind varchar(32) not null default 'DOCUMENT';
