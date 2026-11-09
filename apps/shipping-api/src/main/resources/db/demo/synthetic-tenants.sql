-- Demo only: run as shipping_owner, after V1-V3, in one transaction.
insert into tenants.tenant values
('11111111-1111-4111-8111-111111111111', 'Tenant Alpha (synthetic)'),
('22222222-2222-4222-8222-222222222222', 'Tenant Beta (synthetic)');
insert into tenants.app_user values
('00000000-0000-4000-8000-0000000000a1', 'clerk-alpha', 'Clerk Alpha (synthetic)'),
('00000000-0000-4000-8000-0000000000b1', 'clerk-beta', 'Clerk Beta (synthetic)'),
('00000000-0000-4000-8000-0000000000c1', 'no-tenant-user', 'User without tenant (synthetic)');
insert into tenants.membership values
('00000000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'CLERK'),
('00000000-0000-4000-8000-0000000000b1', '22222222-2222-4222-8222-222222222222', 'CLERK');
insert into tenants.demo_token select sha256(convert_to('demo-' || handle, 'UTF8')), user_id from tenants.app_user;
