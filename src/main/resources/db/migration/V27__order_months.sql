-- Duration-based pricing: how many of the plan's 9 months an order pays for (see PlanPricing).
-- Null on orders placed before this -- they were full 9-month plans.
alter table orders add column months integer;
-- The due date the months were counted from; while an order holds one, the journey's EDD can't move far from it.
alter table orders add column priced_due_date date;
