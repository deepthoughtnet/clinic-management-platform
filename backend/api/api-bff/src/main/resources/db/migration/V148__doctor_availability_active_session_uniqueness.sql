alter table if exists doctor_availability
    drop constraint if exists uq_doctor_availability_slot;

drop index if exists uq_doctor_availability_slot_active;

create unique index if not exists uq_doctor_availability_slot_active
    on doctor_availability (tenant_id, doctor_user_id, day_of_week, start_time, end_time)
    where active;
