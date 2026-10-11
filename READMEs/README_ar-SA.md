<div dir="rtl">

<p align="center">
  <img src="../.github/assets/banner.png" alt="NextCore" width="100%">
</p>

<p align="center">
  <a href="https://github.com/ifoknr/NEXTCORE/releases/latest"><img src="https://img.shields.io/github/v/release/ifoknr/NEXTCORE?style=flat-square&color=FF6B2C&labelColor=16161C&label=release" alt="Release"></a>
  <img src="https://img.shields.io/badge/KernelSU%20·%20APatch%20·%20Magisk-16161C?style=flat-square" alt="Root managers">
  <img src="https://img.shields.io/badge/Android-10%2B-16161C?style=flat-square&logo=android&logoColor=FF6B2C" alt="Android 10+">
  <a href="https://t.me/IFOKNR1"><img src="https://img.shields.io/badge/Telegram-IFOKNR1-FF6B2C?style=flat-square&logo=telegram&logoColor=white&labelColor=16161C" alt="Telegram"></a>
  <img src="https://img.shields.io/badge/license-Apache%202.0-16161C?style=flat-square" alt="License">
</p>

<p align="center">
  <a href="../README.md">English</a> · <b>العربية</b>
</p>

**NextCore** موديول أداء لـ KernelSU وAPatch وMagisk، ومعه تطبيق لإدارته ومراقبة الألعاب. يتعرّف على معالجك عند كل إقلاع، ويطبّق اللي يناسبه بأمان، ويبدّل ملف الأداء لحاله لما تفتح لعبة.

#### ⚡ الأداء

| | |
| :--- | :--- |
| **أداء مستدام** | يرفع أقل تردد للمعالج (70% للأنوية الكبيرة و50% للصغيرة) ويخلي الحاكم يرفع الباقي حسب الحمل. الجهاز يسخن أبطأ والأداء يثبت أطول. |
| **أولوية اللعبة** | uclamp يودّي اللعبة للأنوية السريعة ويحد مهام الخلفية، وترجع القيم الأصلية في وضع التوازن. |
| **الرسوميات والذاكرة** | وضع الأداء يرفع الحد الأدنى للنص ويخلي الأعلى مفتوح. التثبيت على أعلى تردد بس في **الأداء الأقصى**. |
| **التوفير** | يحد الرسوميات والذاكرة عند النص بدل ما يقفلها على أقل تردد. |
| **لكل لعبة** | دقة أقل، هدف إطارات، رندر، معدل تحديث، عدم الإزعاج، عزل الشحن. |

#### 🛡️ آمن لأي جهاز

- ما يقفل أي ملف في النواة للقراءة فقط، فحماية الحرارة من الشركة تقدر تخفّض التردد دائماً.
- ما يغيّر حاكم الحرارة، وما يوقف خدمات الحرارة، وما يعطّل إعادة التشغيل عند انهيار النواة.
- منظّف الخلفية يقفل تطبيقاتك بس، وما يقرب من الكيبورد والرسائل والاتصال والموسيقى والمنبه.
- أي تعديل مساره مو موجود في نواتك يتجاوزه بدون أخطاء.

#### 📱 التطبيق

| | |
| :--- | :--- |
| **الرئيسية** | حالة الخدمة، عدادات حية، الأوضاع، اللعبة الشغالة، رسم آخر 30 دقيقة، إحصائيات اليوم. |
| **المحركات** | صفحة للمعالج، وللذاكرة، وللإطارات (لحظي، المتوسط، أقل 1%، الثبات). |
| **الجلسات** | كل جلسة لعب بمدتها ومتوسط إطاراتها وأعلى حرارة، ومجموع كل لعبة. |
| **المراقب العائم** | 20 قراءة تختار منها وترتبها، ثلاث أشكال، رسم للإطارات، وتحديث لين 250 ملي ثانية. |

#### 🎮 المراقب العائم

الإطارات تنقرى لحظياً من أوقات إطارات اللعبة نفسها في SurfaceFlinger، ومعها زمن الإطار وأقل 1% والتقطيع.
باقي القراءات: تردد المعالج ومجموعاته وحمله وحرارته، الرسوميات (حمل وتردد وحرارة)، الرام، حرارة البطارية ونسبتها واستهلاكها بالواط، حالة الحرارة، الشبكة، البنق، الساعة، ومدة الجلسة.
اضغط مطوّل على اللوحة داخل اللعبة لتغيير شكلها، واسحبها لتحريكها.

#### 🧩 المعالجات

| المعالج | الدعم |
| :--- | :--- |
| MediaTek · Snapdragon · Exynos · Tensor · Unisoc | **كامل:** التحسينات العامة وتحسينات المعالج |
| غيرها | **جزئي:** التحسينات العامة اللي مساراتها موجودة في نواتك |

يتعرّف على المعالج من `ro.soc.*` ثم `/proc/cpuinfo` ثم تعريفات النواة (`gpufreq` و`fpsgo` لـ MediaTek، و`kgsl` لـ Snapdragon).

#### 📦 التثبيت

1. احذف AZenith لو كان مثبّت وأعد التشغيل.
2. نزّل آخر إصدار من [الإصدارات](https://github.com/ifoknr/NEXTCORE/releases/latest) وفلّشه من مدير الروت.
3. أعد التشغيل، افتح تطبيق NextCore وامنحه الروت.

لو صار شي وقت الإقلاع، أنشئ الملف `/data/local/tmp/nextcore_abort` والخدمة ما تشتغل.

<details>
<summary><b>أوامر الطرفية</b></summary>

```sh
sh /data/adb/modules/nextcore/action.sh get_status     # الحالة
sh /data/adb/modules/nextcore/action.sh set_profile 1  # 1 أداء · 2 توازن · 3 توفير
sh /data/adb/modules/nextcore/action.sh set_auto 1     # الوضع التلقائي
sh /data/adb/modules/nextcore/action.sh doctor         # التشخيص
```

</details>

<br>

<sub>للإبلاغ عن مشكلة: افتح <a href="https://github.com/ifoknr/NEXTCORE/issues">Issue</a> وأرفق ناتج <code>action.sh doctor</code> · رخصة Apache 2.0، انظر <a href="../LICENSE">LICENSE</a> و<a href="../NOTICE.md">NOTICE.md</a></sub>

<sub>مبني على <a href="https://github.com/Liliya2727/AZenith">AZenith</a> من Zexshia، وفق رخصة Apache 2.0.</sub>

</div>
