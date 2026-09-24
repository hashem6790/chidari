#!/usr/bin/env bash
# ارسال پروژه به گیت‌هاب — اجرا: bash push.sh
set -e
REPO="https://github.com/hashem6790/chidari.git"

echo "🔍 بررسی امنیتی پیش از ارسال…"
if git ls-files | grep -qE "^secrets\.properties$|\.jks$|\.keystore$"; then
  echo "❌ فایل حساس در گیت پیدا شد! ارسال متوقف شد."; exit 1
fi
if git grep -qI "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" -- '*.kt' '*.html' '*.md' 2>/dev/null; then
  echo "❌ کلید Supabase در سورس پیدا شد! ارسال متوقف شد."; exit 1
fi
echo "✅ هیچ کلید یا فایل حساسی در گیت نیست"

git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REPO"
git branch -M main
echo ""
echo "📤 در حال ارسال… (نام کاربری: hashem6790 ، رمز: توکن گیت‌هاب)"
git push -u origin main
echo ""
echo "🎉 انجام شد: https://github.com/hashem6790/chidari"
