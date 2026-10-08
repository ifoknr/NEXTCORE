#!/usr/bin/env bash
# Posts a NextCore release to the Telegram channel: the banner with a short
# Arabic summary and download link, then the flashable zip.
#
# Needs BOT_TOKEN and CHAT_ID (repository secrets), and TAG, REPO and
# RUN_URL in the environment. Usage: telebot.sh <zip>
set -euo pipefail

file="$1"
if [ ! -f "$file" ]; then
	echo "error: $file not found" >&2
	exit 1
fi

api="https://api.telegram.org/bot$BOT_TOKEN"
# A tag push links its release; a manual test run links the build instead.
if [[ "$TAG" == v* ]]; then
	release_url="https://github.com/$REPO/releases/tag/$TAG"
	label="$TAG"
else
	release_url="${RUN_URL:-https://github.com/$REPO/actions}"
	label="$(grep -m1 '^version=' "$GITHUB_WORKSPACE/mainfiles/module.prop" | cut -d= -f2-) (test)"
fi
banner="$GITHUB_WORKSPACE/.github/assets/banner.png"
notes="$GITHUB_WORKSPACE/.github/release-notes.md"

# Caption: the top-level bullets of the release notes, HTML-escaped and
# trimmed by characters (not bytes, so Arabic letters stay whole). A photo
# caption holds 1024 characters.
caption=$(TAG="$label" URL="$release_url" NOTES="$notes" python3 - <<'PY'
import html, os, re
tag, url, path = os.environ["TAG"], os.environ["URL"], os.environ["NOTES"]
bullets = []
if os.path.exists(path):
    for line in open(path, encoding="utf-8"):
        if line.startswith("- "):
            text = re.sub(r"\*\*|`", "", line[2:].strip())
            short = text if len(text) <= 140 else text[:139].rstrip() + "…"
            bullets.append("• " + html.escape(short, quote=False))
head = f"<b>🚀 NextCore {html.escape(tag, quote=False)}</b>\n\n"
tail = f'\n\n📥 <a href="{url}">صفحة الإصدار والتفاصيل كاملة</a>\n⚠️ احذف AZenith وأعد التشغيل قبل التثبيت.'
body = ""
for b in bullets:
    if len(head) + len(body) + len(b) + 1 + len(tail) > 1000:
        break
    body += b + "\n"
print(head + body.rstrip("\n") + tail)
PY
)

# Telegram HTML only needs &, < and > escaped.
esc() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }

send() {
	local out
	out=$(curl -sS "$@")
	if ! printf '%s' "$out" | grep -q '"ok":true'; then
		echo "Telegram error: $out" >&2
		return 1
	fi
}

if [ -f "$banner" ]; then
	send "$api/sendPhoto" \
		--form-string chat_id="$CHAT_ID" \
		-F photo=@"$banner" \
		--form-string parse_mode=HTML \
		--form-string caption="$caption"
else
	send "$api/sendMessage" \
		--form-string chat_id="$CHAT_ID" \
		--form-string parse_mode=HTML \
		--form-string disable_web_page_preview=true \
		--form-string text="$caption"
fi

send "$api/sendDocument" \
	--form-string chat_id="$CHAT_ID" \
	-F document=@"$file" \
	--form-string parse_mode=HTML \
	--form-string caption="📦 <b>$(basename "$file" | esc)</b>
فلّشه من KernelSU أو APatch أو Magisk ثم أعد التشغيل."

echo "Posted $label to Telegram."
