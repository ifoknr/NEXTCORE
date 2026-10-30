## NextCore v2.0.1

تحديث صغير بشكل جديد وإصلاحات، فيه تعديلات من AZenith الأصلي تخص أجهزة MediaTek والحذف.

### 🛠️ الإصلاحات
- **شكل جديد:** أيقونة رمادية هادية مثل باقي أدوات IFOKNR (الشاشة الرئيسية، داخل التطبيق، الـ WebUI)، واسم NextCore بخط جديد في الرئيسية و«حول»، وتوقيع IFOKNR في «حول».
- **MediaTek:** الذاكرة والـ Vcore ترجع لوضعها الطبيعي في التوازن والتوفير بعد الأداء الأقصى. قبل كانت تبقى مثبتة على الأعلى، والجهاز يسخن.
- **MediaTek:** صار يغيّر بس حدود الطاقة والحرارة في PPM. حدود `USER_LIMIT` و `FORCE_LIMIT` تبقى شغالة، وما عاد يفرض `SYS_BOOST`.
- الخدمة ترجع تشتغل بعد الـ soft reboot.
- لو حذفت الموديول من مدير الروت، بعد إعادة التشغيل ينحذف التطبيق وإعداداته وأدواته، وما يبقى شي.

### 📦 التثبيت
ثبّت `NextCore-v2.0.1.zip` فوق النسخة الحالية من KernelSU أو APatch أو Magisk، وأعد التشغيل.

**English**

## NextCore v2.0.1

A small update with a new look and fixes, with changes taken from the original AZenith for MediaTek devices and uninstalling.

### 🛠️ Fixes
- **New look:** a calm grey icon like the other IFOKNR tools (launcher, in the app, the WebUI), the NextCore name in new lettering on Home and About, and IFOKNR's signature on About.
- **MediaTek:** memory and Vcore return to normal in Balanced and Eco after max performance. Before, they stayed pinned at the top and the phone ran warm.
- **MediaTek:** only the PPM power and thermal limits are changed now. `USER_LIMIT` and `FORCE_LIMIT` stay on, and `SYS_BOOST` is no longer forced.
- The service starts again after a soft reboot.
- If you remove the module from the root manager, the app, its settings and helper tools are removed after the next reboot, with nothing left behind.

### 📦 Install
Flash `NextCore-v2.0.1.zip` over the current version from KernelSU, APatch or Magisk, then reboot.

---
شكر لـ Zexshia وفريق AZenith · Thanks to Zexshia and the AZenith team (Apache 2.0).
