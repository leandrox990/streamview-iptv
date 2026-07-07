-- ============================================================================
-- Parche: listas de canales DINÁMICAS (editables desde el panel admin)
-- Ejecuta TODO esto en Supabase → SQL Editor → Run
-- ============================================================================

-- sv_config ahora también devuelve las listas personalizadas
create or replace function sv_config()
returns json language sql security definer set search_path = public, extensions as $$
  select json_build_object(
    'latest_version', (select value from app_config where key = 'latest_version'),
    'min_version',    (select value from app_config where key = 'min_version'),
    'message',        (select value from app_config where key = 'message'),
    'lists',          (select value from app_config where key = 'custom_lists')
  );
$$;

-- Guardar las listas (protegido por el token de administrador)
create or replace function sv_set_lists(p_token text, p_lists text)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare tok text;
begin
  select value into tok from app_config where key = 'admin_token';
  if tok is null or tok <> crypt(p_token, tok) then
    return json_build_object('ok', false, 'error', 'Token de administrador inválido');
  end if;
  insert into app_config(key, value) values ('custom_lists', p_lists)
    on conflict (key) do update set value = excluded.value;
  return json_build_object('ok', true);
end $$;

grant execute on function sv_config()                to anon, authenticated;
grant execute on function sv_set_lists(text,text)    to anon, authenticated;

-- valor inicial (lista vacía)
insert into app_config(key,value) values ('custom_lists', '[]') on conflict (key) do nothing;
