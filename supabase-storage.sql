-- =====================================================================
--  «چی داری؟» — راه‌اندازی فضای ذخیره تصاویر
--
--  این فایل را در SQL Editor پروژه Supabase اجرا کنید (یک‌بار کافی است).
--  اجرای دوباره مشکلی ایجاد نمی‌کند.
--
--  پس از اجرا، تصویر محصولات روی سرور ذخیره می‌شود و همه کاربران
--  آن را می‌بینند — نه فقط گوشی خودِ فروشنده.
-- =====================================================================


-- ---------------------------------------------------------------------
-- ۱) ساخت سطل (bucket) تصاویر محصولات
--
--    public = true  →  خواندن تصویر بدون نیاز به ورود (مثل هر فروشگاه اینترنتی)
--    سقف حجم ۱ مگابایت با موتور فشرده‌سازی برنامه هماهنگ است؛ اگر فایلی
--    بزرگ‌تر بیاید، خود سرور ردش می‌کند.
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'product-images',
  'product-images',
  true,
  1048576,                                        -- ۱ مگابایت
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
  public             = true,
  file_size_limit    = 1048576,
  allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];


-- ---------------------------------------------------------------------
-- ۲) سیاست‌های دسترسی
--
--    قاعده: دیدن تصویر برای همه آزاد، ولی هر کاربر فقط داخل پوشه‌ی
--    خودش می‌تواند فایل بگذارد یا پاک کند.
--
--    ساختار مسیر فایل‌ها:  {user_id}/{نام فایل}.jpg
--    تابع storage.foldername(name) مسیر را تکه‌تکه می‌کند و تکه اول
--    باید با شناسه کاربر واردشده یکی باشد.
-- ---------------------------------------------------------------------

-- پاک کردن سیاست‌های قبلی (برای اجرای دوباره بدون خطا)
drop policy if exists "product images public read"   on storage.objects;
drop policy if exists "product images user insert"   on storage.objects;
drop policy if exists "product images user update"   on storage.objects;
drop policy if exists "product images user delete"   on storage.objects;

-- خواندن: همه، حتی کاربران وارد نشده
create policy "product images public read"
  on storage.objects for select
  using (bucket_id = 'product-images');

-- بارگذاری: فقط کاربر واردشده و فقط در پوشه خودش
create policy "product images user insert"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'product-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- جایگزینی فایل (وقتی تصویر محصول عوض می‌شود)
create policy "product images user update"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'product-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- حذف: فقط فایل‌های خودِ کاربر
create policy "product images user delete"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'product-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );


-- ---------------------------------------------------------------------
-- ۳) ستون تصویر فروشگاه (اگر از قبل نباشد)
--    جدول products از قبل image_url دارد؛ این برای لوگوی فروشگاه است.
-- ---------------------------------------------------------------------
alter table public.stores
  add column if not exists image_url text not null default '';

alter table public.products
  add column if not exists image_url text not null default '';

-- بارکد هم اگر در نسخه قبلی اضافه نشده بود
alter table public.products
  add column if not exists barcode text not null default '';

create index if not exists products_barcode_idx
  on public.products (barcode) where barcode <> '';


-- =====================================================================
--  تمام شد ✅
--
--  بررسی:
--    select id, public, file_size_limit from storage.buckets
--      where id = 'product-images';
--
--    select policyname from pg_policies
--      where tablename = 'objects' and policyname like 'product images%';
--
--  باید یک سطل و چهار سیاست ببینید.
-- =====================================================================
