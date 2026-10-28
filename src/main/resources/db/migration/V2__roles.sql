-- New role model: a user can hold multiple roles. The old single `role` column on `users` stays
-- readable for one release (nothing writes to it any more) and is backfilled here for history.

create table user_roles (
    user_id bigint not null references users (id),
    role varchar(255) not null
);

-- USER -> MOTHER, ADMIN -> SUPER_ADMIN, anything else (including NULL) -> MOTHER.
insert into user_roles (user_id, role)
select id, case when role = 'ADMIN' then 'SUPER_ADMIN' else 'MOTHER' end
from users;
