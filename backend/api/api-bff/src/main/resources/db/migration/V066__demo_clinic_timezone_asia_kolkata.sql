-- Ensure the local demo clinic uses India clinic time instead of UTC.
update tenant_notification_settings
set timezone = 'Asia/Kolkata',
    updated_at = now()
where tenant_id = '11111111-1111-1111-1111-111111111111'
  and (timezone is null or btrim(timezone) = '' or timezone = 'UTC');
