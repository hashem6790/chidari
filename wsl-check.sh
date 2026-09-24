#!/usr/bin/env bash
# ------------------------------------------------------------
#  بررسی وضعیت WSL برای ارسال پروژه به گیت‌هاب
#  خروجی این اسکریپت امن است: توکن‌ها خودکار پنهان می‌شوند
# ------------------------------------------------------------
echo "════════ گزارش وضعیت ════════"

echo "── ۱) سیستم"
echo "کاربر : $(whoami)"
echo "مسیر  : $(pwd)"
grep -qi microsoft /proc/version 2>/dev/null && echo "محیط  : WSL ✓" || echo "محیط  : لینوکس معمولی"

echo
echo "── ۲) گیت"
if command -v git >/dev/null 2>&1; then
  echo "نصب   : ✓ $(git --version)"
  echo "نام   : $(git config --global user.name  2>/dev/null || echo '(تنظیم نشده)')"
  echo "ایمیل : $(git config --global user.email 2>/dev/null || echo '(تنظیم نشده)')"
  echo "helper: $(git config --global credential.helper 2>/dev/null || echo '(ندارد)')"
else
  echo "نصب   : ✗ گیت نصب نیست"
fi

echo
echo "── ۳) پروژه"
FOUND=""
for d in ./ChiDari ./chidari ~/ChiDari ~/chidari /mnt/c/Users/*/Downloads/ChiDari; do
  [ -d "$d" ] && FOUND="$FOUND $d"
done
if [ -n "$FOUND" ]; then
  echo "پیدا شد:$FOUND"
else
  echo "پیدا نشد — سورس هنوز روی این سیستم نیست"
fi

if [ -d .git ]; then
  echo "اینجا مخزن گیت هست ✓"
  echo "commit: $(git log --oneline 2>/dev/null | wc -l) عدد"
  echo "شاخه  : $(git branch --show-current 2>/dev/null)"
  # آدرس remote با پنهان کردن توکن
  R=$(git remote get-url origin 2>/dev/null)
  if [ -n "$R" ]; then
    echo "remote: $(echo "$R" | sed -E 's#//[^@]*@#//***TOKEN-HIDDEN***@#')"
  else
    echo "remote: تنظیم نشده"
  fi
else
  echo "اینجا مخزن گیت نیست"
fi

echo
echo "── ۴) اتصال به گیت‌هاب"
if curl -s -m 15 -o /dev/null -w "%{http_code}" https://github.com | grep -q 200; then
  echo "دسترسی: ✓ گیت‌هاب در دسترس است"
else
  echo "دسترسی: ✗ گیت‌هاب باز نشد (فیلترینگ یا اینترنت)"
fi
echo "وضعیت مخزن شما: $(curl -s -m 15 -o /dev/null -w '%{http_code}' https://github.com/hashem6790/chidari)  (۲۰۰ = موجود)"

echo
echo "── ۵) فایل زیپ"
for z in ./chidari-source.zip ~/chidari-source.zip /mnt/c/Users/*/Downloads/chidari-source.zip; do
  [ -f "$z" ] && echo "پیدا شد: $z"
done
echo "════════ پایان گزارش ════════"
