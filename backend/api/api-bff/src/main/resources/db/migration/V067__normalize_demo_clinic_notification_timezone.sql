-- Repair the local CuraPilot demo clinic timezone if it was seeded as UTC or left empty.
update tenant_notification_settings tns
set timezone = 'Asia/Kolkata',
    updated_at = now()
from tenants t
where tns.tenant_id = t.id
  and (
    lower(t.code) = 'demo-clinic'
    or lower(t.name) like '%demo clinic%'
    or lower(t.name) like '%curapilot%'
    or lower(t.code) like '%curapilot%'
  )
  and (tns.timezone is null or btrim(tns.timezone) = '' or upper(btrim(tns.timezone)) = 'UTC');
