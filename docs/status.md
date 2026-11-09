---
title: Owlly API 진행 현황
---

전체 **{% assign total = 0 %}{% assign done = 0 %}{% for s in site.data.apis.sections %}{% for it in s.items %}{% assign total = total | plus: 1 %}{% if it.done %}{% assign done = done | plus: 1 %}{% endif %}{% endfor %}{% endfor %}{{ total }}개** 엔드포인트 · 마지막 갱신 **2026-11-09**

## 📊 전체 진행률

{% assign pct = done | times: 100 | divided_by: total %}
**{{ done }} / {{ total }} 완료 — {{ pct }}%**

<div style="background:#e5e7eb;border-radius:8px;height:18px;width:100%;max-width:480px;overflow:hidden">
  <div style="background:#1f9d55;height:18px;width:{{ pct }}%"></div>
</div>

### 개인별 진행률

{% for o in site.data.apis.owners %}{% assign ot = 0 %}{% assign od = 0 %}{% for s in site.data.apis.sections %}{% if s.owner == o %}{% for it in s.items %}{% assign ot = ot | plus: 1 %}{% if it.done %}{% assign od = od | plus: 1 %}{% endif %}{% endfor %}{% endif %}{% endfor %}{% assign op = od | times: 100 | divided_by: ot %}
**{{ o }}** — {{ od }} / {{ ot }} ({{ op }}%)
<div style="background:#e5e7eb;border-radius:6px;height:12px;width:100%;max-width:360px;overflow:hidden;margin:4px 0 12px">
  <div style="background:#2563eb;height:12px;width:{{ op }}%"></div>
</div>
{% endfor %}

---
{% for s in site.data.apis.sections %}
## {{ s.name }} — {{ s.owner }}

| ID | Method | 엔드포인트 | 설명 | 담당자 | 상태 |
|---|---|---|---|---|---|
{% for it in s.items -%}
| {{ it.id }} | {{ it.method }} | `{{ it.path }}` | {{ it.desc }} | {{ s.owner }} | {% if it.done %}✅ 완료{% else %}⬜ 미완료{% endif %} |
{% endfor %}
{% endfor %}
---

### 참고

- 상태를 바꾸려면 **`docs/_data/apis.yml`** 에서 해당 항목의 `done: false` 를 `done: true` 로 고치세요. 표와 진행률(전체·개인별)이 자동으로 다시 계산됩니다.
- 완료 초기값은 `develop` 머지 기준(API-04·16·20·21)입니다.
