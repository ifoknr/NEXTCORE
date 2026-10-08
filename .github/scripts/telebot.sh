#!/usr/bin/env bash
# Posts a NextCore build to the Telegram channel as one message: the flashable
# zip with a caption (what changed, requirements, how to update) and link
# buttons under it.
#
#   KIND=release  a pushed v* tag; changes since the previous tag
#   KIND=beta     a push to main; changes in that push
#
# Needs BOT_TOKEN and CHAT_ID (repository secrets) and, from the workflow,
# KIND, TAG, REPO, RUN_URL and BEFORE (the commit before a push).
# Usage: telebot.sh <zip>
set -euo pipefail

file="$1"
if [ ! -f "$file" ]; then
	echo "error: $file not found" >&2
	exit 1
fi

export ZIP_NAME VERSION_LABEL SINCE COMPARE_URL CHANGES COMMIT_COUNT DIFFSTAT
ZIP_NAME="$(basename "$file")"
VERSION_LABEL="$(grep -m1 '^version=' "$GITHUB_WORKSPACE/mainfiles/module.prop" | cut -d= -f2-)"
SINCE=""

# Which commits this post covers.
from=""
to="HEAD"
if [ "$KIND" = release ]; then
	to="$TAG"
	from="$(git describe --tags --abbrev=0 "$TAG^" 2>/dev/null || true)"
	SINCE="$from"
	COMPARE_URL="https://github.com/$REPO/compare/${from:-main}...$TAG"
elif [ -n "${BEFORE:-}" ] && ! [[ "$BEFORE" =~ ^0+$ ]] && git cat-file -e "$BEFORE^{commit}" 2>/dev/null; then
	from="$BEFORE"
	COMPARE_URL="https://github.com/$REPO/compare/${BEFORE:0:12}...$(git rev-parse --short=12 HEAD)"
else
	COMPARE_URL="https://github.com/$REPO/commits/main"
fi

if [ -n "$from" ]; then
	log_range=("$from..$to")
	COMMIT_COUNT="$(git rev-list --count --no-merges "$from..$to")"
	DIFFSTAT="$(git diff --shortstat "$from" "$to" || true)"
else
	log_range=(-5 "$to")
	COMMIT_COUNT=""
	DIFFSTAT=""
fi
# One record per first-parent commit: subject, then body (merge commits carry
# the pull request title in the body).
CHANGES="$(git log --first-parent --format='%s%x1f%b%x1e' "${log_range[@]}" | head -c 20000)"

# Caption (HTML) and button rows. A document caption holds 1024 characters,
# so the change list is trimmed by whole lines.
payload="$(python3 - <<'PY'
import html, json, os, re

e = lambda s: html.escape(s, quote=False)
kind, tag, repo = os.environ["KIND"], os.environ["TAG"], os.environ["REPO"]
version = os.environ.get("VERSION_LABEL") or tag
since = os.environ.get("SINCE", "")

lines = []
for rec in os.environ.get("CHANGES", "").split("\x1e"):
    if "\x1f" not in rec:
        continue
    subject, body = rec.strip().split("\x1f", 1)
    m = re.match(r"Merge pull request #(\d+)", subject)
    if m:
        title = next((l.strip() for l in body.splitlines() if l.strip()), "")
        if title:
            lines.append(f"{title} (#{m.group(1)})")
    elif not subject.startswith("Merge "):
        lines.append(subject)

need = "⚙️ <b>يحتاج:</b> KernelSU / APatch / Magisk · Android 10+"
if kind == "release":
    head = f"🚀 <b>NextCore {e(tag)}</b>"
    sub = "<i>Safe performance engine · محرك أداء آمن</i>"
    title_new = "📝 <b>الجديد</b>" + (f" <i>since {e(since)}</i>" if since else "")
    footer = (f"{need}\n🔄 يوصلك التحديث من مدير الروت تلقائياً\n\n"
              "⚠️ <i>احذف AZenith وأعد التشغيل قبل التثبيت.</i>\n#NextCore #تحديث")
else:
    head = f"🧪 <b>NextCore Beta</b> <code>{e(version)}</code>"
    sub = "<i>Test build from main · نسخة تجريبية</i>"
    title_new = "📝 <b>الجديد</b>"
    footer = (f"{need}\n🧪 للتجربة: ممكن يكون فيها أخطاء، ارجع للإصدار الرسمي لو صار شي.\n\n"
              "⚠️ <i>Test build, use at your own risk.</i>\n#NextCore #تجريبي")

stats = []
if os.environ.get("COMMIT_COUNT"):
    stats.append(f"{os.environ['COMMIT_COUNT']} commits")
diff = os.environ.get("DIFFSTAT", "").strip()
if diff:
    # " 12 files changed, 340 insertions(+), 80 deletions(-)" -> "12 files, +340, -80"
    d = re.sub(r" changed", "", diff)
    d = re.sub(r"(\d+) insertions?\(\+\)", r"+\1", d)
    d = re.sub(r"(\d+) deletions?\(-\)", r"-\1", d)
    stats.append(d)
stats_line = f"\n<i>{e(' · '.join(stats))}</i>" if stats else ""

slot = "<blockquote expandable></blockquote>"
fixed = f"{head}\n{sub}\n\n{title_new}\n{slot}{stats_line}\n\n{footer}"
# Telegram counts the caption after removing tags.
room = 1000 - len(re.sub(r"<[^>]+>", "", fixed))
items = []
for i, l in enumerate(lines or ["Maintenance build"]):
    item = "• " + e(l if len(l) <= 160 else l[:159].rstrip() + "…")
    if sum(len(x) + 1 for x in items) + len(item) + 14 > room:
        items.append(f"• … +{len(lines) - i} more")
        break
    items.append(item)
caption = fixed.replace(slot, "<blockquote expandable>" + "\n".join(items) + "</blockquote>")

if kind == "release":
    download = f"https://github.com/{repo}/releases/download/{tag}/{os.environ['ZIP_NAME']}"
    details = f"https://github.com/{repo}/releases/tag/{tag}"
else:
    download = os.environ["RUN_URL"]
    details = os.environ["COMPARE_URL"]
keyboard = {"inline_keyboard": [
    [{"text": "⬇️ Download", "url": download}, {"text": "📋 Details", "url": details}],
    [{"text": "⭐ Repository", "url": f"https://github.com/{repo}"},
     {"text": "👤 GitHub", "url": "https://github.com/" + repo.split("/")[0]}],
]}
print(json.dumps({"caption": caption, "markup": json.dumps(keyboard)}))
PY
)"

caption="$(printf '%s' "$payload" | python3 -c 'import json,sys; sys.stdout.write(json.load(sys.stdin)["caption"])')"
markup="$(printf '%s' "$payload" | python3 -c 'import json,sys; sys.stdout.write(json.load(sys.stdin)["markup"])')"

thumb=()
if [ -f "$GITHUB_WORKSPACE/logo.jpg" ]; then
	thumb=(-F thumbnail=@"$GITHUB_WORKSPACE/logo.jpg")
fi

out="$(curl -sS "https://api.telegram.org/bot$BOT_TOKEN/sendDocument" \
	--form-string chat_id="$CHAT_ID" \
	-F document=@"$file" \
	"${thumb[@]}" \
	--form-string parse_mode=HTML \
	--form-string caption="$caption" \
	--form-string reply_markup="$markup")"
if ! printf '%s' "$out" | grep -q '"ok":true'; then
	echo "Telegram error: $out" >&2
	exit 1
fi
echo "Posted $KIND $ZIP_NAME to Telegram."
