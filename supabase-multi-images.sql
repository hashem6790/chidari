-- ============================================================
--  «چی داری؟» — پشتیبانی از چند تصویر برای هر محصول (نسخه ۲.۶)
--  این فایل را در Supabase → SQL Editor اجرا کنید.
--  اجرای دوباره‌اش بی‌خطر است.
-- ============================================================

-- ستون تازه: همه‌ی تصاویر محصول، هر کدام در یک خط.
-- تصویر اول همان image_url (عکس اصلی) است.
--
-- چرا ستون متنی و نه جدول جداگانه:
--   حداکثر ۵ تصویر داریم و همیشه با خودِ محصول خوانده می‌شوند.
--   جدول جداگانه یعنی join در هر کوئری + چهار سیاست RLS اضافه،
--   بدون هیچ سودی. اگر روزی تعداد زیاد شد، مهاجرت ساده است.
alter table public.products
    add column if not exists image_urls text not null default '';

-- ردیف‌های موجود: عکس تک‌تایی به فهرست منتقل شود
update public.products
set image_urls = image_url
where image_urls = '' and coalesce(image_url, '') <> '';

-- همگام نگه داشتن عکس اصلی با اولین تصویر فهرست.
-- اگر برنامه‌ی قدیمی‌تری فقط image_url را بفرستد، باز هم درست کار می‌کند.
create or replace function public.sync_product_cover()
returns trigger
language plpgsql
as $$
begin
    if coalesce(new.image_urls, '') <> '' then
        new.image_url := split_part(new.image_urls, E'\n', 1);
    elsif coalesce(new.image_url, '') <> '' then
        new.image_urls := new.image_url;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_sync_product_cover on public.products;
create trigger trg_sync_product_cover
    before insert or update on public.products
    for each row execute function public.sync_product_cover();

-- بررسی
select id, title, image_url, image_urls from public.products limit 5;
