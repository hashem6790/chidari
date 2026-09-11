-- =====================================================================
--  «چی داری؟» — اسکیمای پایگاه داده Supabase
--  این فایل را در SQL Editor پروژه اجرا کنید (یک‌بار کافی است).
--  اجرای دوباره مشکلی ایجاد نمی‌کند (idempotent).
-- =====================================================================

-- ---------------------------------------------------------------------
-- ۱) پروفایل کاربران
--    برای هر کاربری که ثبت‌نام می‌کند، یک ردیف خودکار ساخته می‌شود.
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  display_name text        not null default '',
  created_at   timestamptz not null default now()
);

-- ساخت خودکار پروفایل هنگام ثبت‌نام
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();


-- ---------------------------------------------------------------------
-- ۲) فروشگاه‌ها
-- ---------------------------------------------------------------------
create table if not exists public.stores (
  id             bigserial primary key,
  owner_id       uuid        not null references auth.users(id) on delete cascade,
  name           text        not null,
  category       text        not null,
  province       text        not null,
  city           text        not null,
  address        text        not null default '',
  phone          text        not null default '',
  bio            text        not null default '',
  lat            double precision not null,
  lng            double precision not null,
  cover_emoji    text        not null default '🏪',
  image_url      text        not null default '',
  open_hours     text        not null default '',
  instagram      text        not null default '',
  rating         double precision not null default 0,
  rating_count   integer     not null default 0,
  follower_count integer     not null default 0,
  is_active      boolean     not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create index if not exists stores_city_idx     on public.stores (province, city);
create index if not exists stores_category_idx on public.stores (category);
create index if not exists stores_owner_idx    on public.stores (owner_id);
create index if not exists stores_updated_idx  on public.stores (updated_at desc);


-- ---------------------------------------------------------------------
-- ۳) محصولات و خدمات
-- ---------------------------------------------------------------------
create table if not exists public.products (
  id               bigserial primary key,
  store_id         bigint      not null references public.stores(id) on delete cascade,
  title            text        not null,
  category         text        not null,
  price            bigint      not null,
  unit             text        not null default 'عدد',
  description      text        not null default '',
  specs            text        not null default '',
  available        boolean     not null default true,
  discount_percent integer     not null default 0,
  emoji            text        not null default '📦',
  image_url        text        not null default '',
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index if not exists products_store_idx    on public.products (store_id);
create index if not exists products_title_idx    on public.products (title);
create index if not exists products_category_idx on public.products (category);
create index if not exists products_updated_idx  on public.products (updated_at desc);


-- ---------------------------------------------------------------------
-- ۴) فالو کردن فروشگاه
-- ---------------------------------------------------------------------
create table if not exists public.follows (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  store_id    bigint      not null references public.stores(id) on delete cascade,
  followed_at timestamptz not null default now(),
  primary key (user_id, store_id)
);

create index if not exists follows_store_idx on public.follows (store_id);


-- ---------------------------------------------------------------------
-- ۵) علاقه‌مندی‌ها
-- ---------------------------------------------------------------------
create table if not exists public.favorites (
  user_id  uuid        not null references auth.users(id) on delete cascade,
  item_key text        not null,          -- 'store:12' یا 'product:34'
  added_at timestamptz not null default now(),
  primary key (user_id, item_key)
);


-- ---------------------------------------------------------------------
-- ۶) امتیازها (هر کاربر برای هر فروشگاه فقط یک امتیاز)
-- ---------------------------------------------------------------------
create table if not exists public.ratings (
  user_id  uuid        not null references auth.users(id) on delete cascade,
  store_id bigint      not null references public.stores(id) on delete cascade,
  score    integer     not null check (score between 1 and 5),
  rated_at timestamptz not null default now(),
  primary key (user_id, store_id)
);


-- =====================================================================
--  به‌روزرسانی خودکار شمارنده‌ها
-- =====================================================================

-- تعداد دنبال‌کننده
create or replace function public.sync_follower_count()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if (tg_op = 'INSERT') then
    update public.stores set follower_count = follower_count + 1 where id = new.store_id;
  elsif (tg_op = 'DELETE') then
    update public.stores set follower_count = greatest(follower_count - 1, 0) where id = old.store_id;
  end if;
  return null;
end; $$;

drop trigger if exists follows_count_trigger on public.follows;
create trigger follows_count_trigger
  after insert or delete on public.follows
  for each row execute function public.sync_follower_count();


-- میانگین امتیاز
create or replace function public.sync_store_rating()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  target bigint := coalesce(new.store_id, old.store_id);
begin
  update public.stores s set
    rating = coalesce((select avg(score) from public.ratings where store_id = target), 0),
    rating_count = (select count(*) from public.ratings where store_id = target)
  where s.id = target;
  return null;
end; $$;

drop trigger if exists ratings_sync_trigger on public.ratings;
create trigger ratings_sync_trigger
  after insert or update or delete on public.ratings
  for each row execute function public.sync_store_rating();


-- به‌روزرسانی updated_at
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end; $$;

drop trigger if exists stores_touch on public.stores;
create trigger stores_touch before update on public.stores
  for each row execute function public.touch_updated_at();

drop trigger if exists products_touch on public.products;
create trigger products_touch before update on public.products
  for each row execute function public.touch_updated_at();


-- =====================================================================
--  امنیت سطح ردیف (RLS)
--  قاعده کلی: خواندن برای همه آزاد، نوشتن فقط برای صاحب داده.
-- =====================================================================

alter table public.profiles  enable row level security;
alter table public.stores    enable row level security;
alter table public.products  enable row level security;
alter table public.follows   enable row level security;
alter table public.favorites enable row level security;
alter table public.ratings   enable row level security;

-- ---- پروفایل ----
drop policy if exists profiles_read   on public.profiles;
drop policy if exists profiles_update on public.profiles;

create policy profiles_read on public.profiles
  for select using (true);
create policy profiles_update on public.profiles
  for update using (auth.uid() = id) with check (auth.uid() = id);

-- ---- فروشگاه‌ها: همه می‌بینند، فقط مالک تغییر می‌دهد ----
drop policy if exists stores_read   on public.stores;
drop policy if exists stores_insert on public.stores;
drop policy if exists stores_update on public.stores;
drop policy if exists stores_delete on public.stores;

create policy stores_read on public.stores
  for select using (true);
create policy stores_insert on public.stores
  for insert with check (auth.uid() = owner_id);
create policy stores_update on public.stores
  for update using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy stores_delete on public.stores
  for delete using (auth.uid() = owner_id);

-- ---- محصولات: همه می‌بینند، فقط مالکِ فروشگاه تغییر می‌دهد ----
drop policy if exists products_read   on public.products;
drop policy if exists products_insert on public.products;
drop policy if exists products_update on public.products;
drop policy if exists products_delete on public.products;

create policy products_read on public.products
  for select using (true);

create policy products_insert on public.products
  for insert with check (
    exists (select 1 from public.stores s
            where s.id = store_id and s.owner_id = auth.uid())
  );
create policy products_update on public.products
  for update using (
    exists (select 1 from public.stores s
            where s.id = store_id and s.owner_id = auth.uid())
  );
create policy products_delete on public.products
  for delete using (
    exists (select 1 from public.stores s
            where s.id = store_id and s.owner_id = auth.uid())
  );

-- ---- فالو: تعداد را همه می‌بینند، ولی هر کس فقط فالوهای خودش را مدیریت می‌کند ----
drop policy if exists follows_read   on public.follows;
drop policy if exists follows_insert on public.follows;
drop policy if exists follows_delete on public.follows;

create policy follows_read on public.follows
  for select using (true);
create policy follows_insert on public.follows
  for insert with check (auth.uid() = user_id);
create policy follows_delete on public.follows
  for delete using (auth.uid() = user_id);

-- ---- علاقه‌مندی: کاملاً خصوصی ----
drop policy if exists favorites_all on public.favorites;

create policy favorites_all on public.favorites
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ---- امتیاز ----
drop policy if exists ratings_read on public.ratings;
drop policy if exists ratings_write on public.ratings;

create policy ratings_read on public.ratings
  for select using (true);
create policy ratings_write on public.ratings
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);


-- =====================================================================
--  تمام شد ✅
--  برای بررسی:  select count(*) from public.stores;
-- =====================================================================
