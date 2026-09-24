#!/usr/bin/env bash
# ------------------------------------------------------------
#  ساخت کلید امضای برنامه — فقط یک‌بار در عمر پروژه
#
#  ⚠️ این فایل را گم نکنید! بدون آن نمی‌توانید نسخه‌های بعدی را
#     طوری منتشر کنید که روی نسخه فعلی کاربران نصب شود.
# ------------------------------------------------------------
set -e
KS="release.keystore"
ALIAS="chidari"

if [ -f "$KS" ]; then
  echo "⚠️  فایل $KS از قبل وجود دارد."
  echo "    اگر آن را پاک و دوباره بسازید، کاربران دیگر نمی‌توانند"
  echo "    به‌روزرسانی کنند و باید برنامه را حذف و نصب کنند."
  read -p "    ادامه می‌دهید؟ (yes/no) " a
  [ "$a" = "yes" ] || exit 0
fi

echo "🔑 یک رمز برای کلید انتخاب کنید (حداقل ۶ نویسه)"
read -s -p "   رمز: " PASS; echo
read -s -p "   تکرار رمز: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "❌ رمزها یکی نیستند"; exit 1; }
[ ${#PASS} -ge 6 ] || { echo "❌ رمز باید حداقل ۶ نویسه باشد"; exit 1; }

keytool -genkeypair -v \
  -keystore "$KS" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 \
  -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=ChiDari, OU=Mobile, O=ChiDari, L=Tehran, C=IR"

cat > keystore.properties << PROPS
storeFile=../release.keystore
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS

echo
echo "✅ کلید ساخته شد: $KS  (اعتبار ۳۰ سال)"
echo "✅ فایل keystore.properties ساخته شد (در .gitignore است)"
echo
echo "📋 برای GitHub Actions این مقدار را در Secret به نام KEYSTORE_BASE64 بگذارید:"
echo "──────────────────────────────────────────"
base64 -w0 "$KS" 2>/dev/null || base64 "$KS"
echo
echo "──────────────────────────────────────────"
echo "و این سه Secret دیگر:"
echo "  KEYSTORE_PASSWORD = رمزی که وارد کردید"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEY_PASSWORD      = همان رمز"
echo
echo "⚠️  از فایل $KS نسخه پشتیبان بگیرید و جایی امن نگه دارید."
