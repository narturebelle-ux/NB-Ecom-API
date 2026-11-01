-- Care stops reading money tables: the wallet keeps its own plan code, paid votes get their own record, and
-- nothing here links to orders any more, which NB-Ecom-Payments-API takes over.
-- One deliberate change (F3-S2, D2): free referral-reward orders no longer count as paid votes.
alter table entitlements add column plan_code varchar(255);

update entitlements e
set plan_code = p.code
from orders o
join plan_versions v on v.id = o.plan_version_id
join plans p on p.id = v.plan_id
where o.id = e.order_id;

alter table entitlements drop constraint if exists entitlements_order_id_fkey;
alter table referral_rewards drop constraint if exists referral_rewards_order_id_fkey;

create table paid_orders (
    order_id bigint not null,
    user_id bigint not null,
    refunded boolean not null default false,
    primary key (order_id)
);

create index idx_paid_orders_user on paid_orders (user_id);

insert into paid_orders (order_id, user_id)
select id, user_id from orders where status = 'PAID' and price > 0;
