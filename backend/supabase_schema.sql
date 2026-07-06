-- ============================================================================
-- StreamView · Backend de login para Supabase
-- Ejecuta TODO este archivo en:  Supabase → SQL Editor → New query → Run
-- ============================================================================

create extension if not exists pgcrypto;

-- ---------- Tablas ----------
create table if not exists accounts (
  id              uuid primary key default gen_random_uuid(),
  username        text unique not null,
  pass            text not null,                 -- hash bcrypt (pgcrypto)
  active          boolean not null default true,
  plan            text default 'basico',
  expires_at      timestamptz,                   -- null = sin vencimiento
  current_session uuid,                          -- sesión activa (single-device)
  current_device  text,
  session_at      timestamptz,
  created_at      timestamptz default now()
);

create table if not exists app_config (
  key   text primary key,
  value text
);

-- Bloquea el acceso directo: solo se entra por las funciones de abajo
alter table accounts   enable row level security;
alter table app_config enable row level security;

-- ---------- Login (crea sesión y expulsa la anterior) ----------
create or replace function sv_login(p_user text, p_pass text, p_device text)
returns json language plpgsql security definer set search_path = public as $$
declare a accounts; sess uuid;
begin
  select * into a from accounts where username = lower(trim(p_user));
  if not found or a.pass <> crypt(p_pass, a.pass) then
    return json_build_object('ok', false, 'error', 'Usuario o contraseña incorrectos');
  end if;
  if not a.active then
    return json_build_object('ok', false, 'error', 'Cuenta desactivada. Contacta al administrador.');
  end if;
  if a.expires_at is not null and a.expires_at < now() then
    return json_build_object('ok', false, 'error', 'Tu acceso ha vencido. Contacta al administrador.');
  end if;
  sess := gen_random_uuid();
  update accounts
     set current_session = sess, current_device = left(coalesce(p_device,''),120), session_at = now()
   where id = a.id;
  return json_build_object('ok', true, 'session', sess, 'username', a.username,
                           'plan', a.plan, 'expires_at', a.expires_at);
end $$;

-- ---------- Validación periódica (single-device) ----------
create or replace function sv_validate(p_user text, p_session uuid)
returns json language plpgsql security definer set search_path = public as $$
declare a accounts;
begin
  select * into a from accounts where username = lower(trim(p_user));
  if not found then return json_build_object('ok', false, 'reason', 'notfound'); end if;
  if not a.active then return json_build_object('ok', false, 'reason', 'disabled'); end if;
  if a.expires_at is not null and a.expires_at < now() then
    return json_build_object('ok', false, 'reason', 'expired'); end if;
  if a.current_session is distinct from p_session then
    return json_build_object('ok', false, 'reason', 'kicked'); end if;   -- sesión abierta en otro dispositivo
  return json_build_object('ok', true);
end $$;

-- ---------- Config pública (versión de la app) ----------
create or replace function sv_config()
returns json language sql security definer set search_path = public as $$
  select json_build_object(
    'latest_version', (select value from app_config where key = 'latest_version'),
    'min_version',    (select value from app_config where key = 'min_version'),
    'message',        (select value from app_config where key = 'message')
  );
$$;

-- ---------- Panel admin (todo protegido por token) ----------
create or replace function sv_admin(p_token text, p_action text, p_payload json default '{}')
returns json language plpgsql security definer set search_path = public as $$
declare tok text;
begin
  select value into tok from app_config where key = 'admin_token';
  if tok is null or tok <> crypt(p_token, tok) then
    return json_build_object('ok', false, 'error', 'Token de administrador inválido');
  end if;

  if p_action = 'list_users' then
    return json_build_object('ok', true, 'users', (
      select coalesce(json_agg(u), '[]'::json) from (
        select id, username, active, plan, expires_at, current_device, session_at, created_at,
               (current_session is not null) as online
        from accounts order by created_at desc
      ) u));

  elsif p_action = 'create_user' then
    insert into accounts(username, pass, plan, expires_at)
    values (lower(trim(p_payload->>'username')),
            crypt(p_payload->>'password', gen_salt('bf')),
            coalesce(nullif(p_payload->>'plan',''), 'basico'),
            nullif(p_payload->>'expires_at','')::timestamptz);
    return json_build_object('ok', true);

  elsif p_action = 'set_password' then
    update accounts set pass = crypt(p_payload->>'password', gen_salt('bf'))
     where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'set_active' then
    update accounts set active = (p_payload->>'active')::boolean,
           current_session = case when (p_payload->>'active')::boolean then current_session else null end
     where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'set_expiry' then
    update accounts set expires_at = nullif(p_payload->>'expires_at','')::timestamptz
     where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'set_plan' then
    update accounts set plan = p_payload->>'plan' where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'kick' then                          -- cerrar sesión activa
    update accounts set current_session = null, current_device = null
     where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'delete_user' then
    delete from accounts where id = (p_payload->>'id')::uuid;
    return json_build_object('ok', true);

  elsif p_action = 'set_version' then
    insert into app_config(key,value) values ('latest_version', p_payload->>'latest_version')
      on conflict (key) do update set value = excluded.value;
    insert into app_config(key,value) values ('min_version', coalesce(p_payload->>'min_version',''))
      on conflict (key) do update set value = excluded.value;
    insert into app_config(key,value) values ('message', coalesce(p_payload->>'message',''))
      on conflict (key) do update set value = excluded.value;
    return json_build_object('ok', true);

  elsif p_action = 'set_admin_token' then               -- cambiar el token admin
    update app_config set value = crypt(p_payload->>'new_token', gen_salt('bf')) where key = 'admin_token';
    return json_build_object('ok', true);

  else
    return json_build_object('ok', false, 'error', 'Acción desconocida');
  end if;

exception
  when unique_violation then return json_build_object('ok', false, 'error', 'Ese usuario ya existe');
  when others          then return json_build_object('ok', false, 'error', SQLERRM);
end $$;

-- ---------- Permisos: solo ejecutar funciones (no tocar tablas) ----------
grant execute on function sv_login(text,text,text)  to anon, authenticated;
grant execute on function sv_validate(text,uuid)    to anon, authenticated;
grant execute on function sv_config()               to anon, authenticated;
grant execute on function sv_admin(text,text,json)  to anon, authenticated;

-- ---------- Datos iniciales ----------
-- Token admin por defecto: "cambiame".  ¡CÁMBIALO! (ver guía o el panel admin)
insert into app_config(key,value) values ('admin_token', crypt('cambiame', gen_salt('bf')))
  on conflict (key) do nothing;
insert into app_config(key,value) values ('latest_version', '1.0.0') on conflict (key) do nothing;
insert into app_config(key,value) values ('min_version', '')        on conflict (key) do nothing;
insert into app_config(key,value) values ('message', '')            on conflict (key) do nothing;
