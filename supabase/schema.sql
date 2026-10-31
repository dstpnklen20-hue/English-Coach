-- English Coach: синхронизация. Выполнить один раз: Supabase → SQL Editor → New query → Run.
-- Повторный запуск безопасен: каждая команда проверяет, что уже есть.
-- Проект тот же, что у Tik Tak, но таблица своя — данные двух приложений не смешиваются.
--
-- Каждая запись приложения — одна строка: вид (word, known, card, review, activity, settings), uid,
-- время изменения на устройстве и сама запись в JSON. Правила доступа (RLS) дают каждому аккаунту
-- только его записи, поэтому публичный ключ проекта может быть внутри приложения.

create table if not exists public.english_sync (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null,
  id text not null,
  updated_at bigint not null,          -- когда запись изменили на устройстве (мс с 1970 г.)
  deleted boolean not null default false,
  data jsonb not null,
  server_updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, kind, id)
);

create index if not exists english_sync_pull on public.english_sync (user_id, server_updated_at);

-- Более старая версия никогда не затирает новую; каждое изменение получает новое серверное время.
create or replace function public.english_sync_guard() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and new.updated_at <= old.updated_at then
    return null;
  end if;
  new.server_updated_at := clock_timestamp();
  return new;
end;
$$;

drop trigger if exists english_sync_guard on public.english_sync;
create trigger english_sync_guard
  before insert or update on public.english_sync
  for each row execute function public.english_sync_guard();

alter table public.english_sync enable row level security;

drop policy if exists "own records" on public.english_sync;
create policy "own records" on public.english_sync
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.english_sync to authenticated;
