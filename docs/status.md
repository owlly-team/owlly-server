---
title: Owlly API 진행 현황
---

<style>
.bar-bg{background:#e5e7eb;border-radius:8px;height:16px;max-width:480px;overflow:hidden;margin:4px 0 14px}
.bar-fill{height:16px}
.api-head{display:grid;grid-template-columns:62px 54px 1.1fr 1.2fr 60px 64px;gap:10px;
  padding:6px 12px;font-size:11px;font-weight:700;color:#6b7280;text-transform:uppercase;letter-spacing:.02em}
.api-row{border:1px solid #e5e7eb;border-radius:6px;margin:5px 0;background:#fff}
.api-row[open]{border-color:#9aa4b2;box-shadow:0 1px 4px rgba(0,0,0,.06)}
.api-row summary{list-style:none;cursor:pointer;padding:9px 12px;
  display:grid;grid-template-columns:62px 54px 1.1fr 1.2fr 60px 64px;gap:10px;align-items:center}
.api-row summary::-webkit-details-marker{display:none}
.api-row summary:hover{background:#f9fafb}
.api-id{font-weight:700;color:#374151;font-size:13px}
.m{font-weight:700;font-size:11px;padding:3px 0;border-radius:4px;text-align:center}
.m-GET{background:#dcfce7;color:#166534}.m-POST{background:#fef3c7;color:#92400e}
.m-PUT{background:#dbeafe;color:#1e40af}.m-PATCH{background:#f3e8ff;color:#6b21a8}.m-DELETE{background:#fee2e2;color:#991b1b}
.api-path{font-family:ui-monospace,Menlo,monospace;font-size:12px;color:#1f2937;overflow-wrap:anywhere}
.api-desc{font-size:12.5px;color:#6b7280;overflow-wrap:anywhere}
.api-owner{font-size:12px;color:#4b5563}
.st-done{color:#16a34a;font-weight:700;font-size:12px}
.st-todo{color:#9ca3af;font-weight:700;font-size:12px}
.api-body{padding:2px 14px 14px}
.api-body h4{margin:12px 0 4px;font-size:12px;color:#374151;text-transform:uppercase;letter-spacing:.03em}
.api-body .meta{font-size:12.5px;color:#4b5563;margin:2px 0}
.api-body pre{background:#0f172a;color:#e2e8f0;padding:11px 13px;border-radius:6px;
  overflow:auto;font-size:12px;line-height:1.5;margin:4px 0}
.api-body ul{margin:4px 0;padding-left:18px}
.api-body li{font-size:12.5px;margin:2px 0}
.api-body code{background:#f1f5f9;padding:1px 6px;border-radius:4px;font-size:12px;color:#be123c}
.dto-row{margin:6px 0;border:1px dashed #cbd5e1;border-radius:6px;background:#f8fafc}
.dto-row summary{cursor:pointer;padding:7px 11px;font-size:12.5px;color:#334155;list-style:none}
.dto-row summary::-webkit-details-marker{display:none}
.dto-row summary:hover{background:#eef2f7}
.dto-row pre{margin:0 11px 10px}
@media(max-width:640px){
  .api-head{display:none}
  .api-row summary{grid-template-columns:54px 50px 1fr 58px;gap:8px}
  .api-row summary .api-desc,.api-row summary .api-owner{display:none}
}
</style>

전체 **{% assign total = 0 %}{% assign done = 0 %}{% for s in site.data.apis.sections %}{% for it in s.items %}{% assign total = total | plus: 1 %}{% if it.done %}{% assign done = done | plus: 1 %}{% endif %}{% endfor %}{% endfor %}{{ total }}개** 엔드포인트 · 행을 클릭하면 요청·응답·오류가 열립니다 · 마지막 갱신 **2026-11-09**

## 📊 전체 진행률

{% assign pct = done | times: 100 | divided_by: total %}**{{ done }} / {{ total }} 완료 — {{ pct }}%**
<div class="bar-bg"><div class="bar-fill" style="background:#16a34a;width:{{ pct }}%"></div></div>

### 개인별 진행률

{% for o in site.data.apis.owners %}{% assign ot = 0 %}{% assign od = 0 %}{% for s in site.data.apis.sections %}{% if s.owner == o %}{% for it in s.items %}{% assign ot = ot | plus: 1 %}{% if it.done %}{% assign od = od | plus: 1 %}{% endif %}{% endfor %}{% endif %}{% endfor %}{% assign op = od | times: 100 | divided_by: ot %}**{{ o }}** — {{ od }} / {{ ot }} ({{ op }}%)
<div class="bar-bg" style="max-width:360px;height:12px"><div class="bar-fill" style="background:#2563eb;height:12px;width:{{ op }}%"></div></div>
{% endfor %}

---
{% for s in site.data.apis.sections %}
## {{ s.name }} — {{ s.owner }}

<div class="api-head"><span>ID</span><span>METHOD</span><span>PATH</span><span>설명</span><span>담당</span><span>상태</span></div>
{% for it in s.items %}<details class="api-row">
<summary><span class="api-id">{{ it.id }}</span><span class="m m-{{ it.method }}">{{ it.method }}</span><code class="api-path">{{ it.path }}</code><span class="api-desc">{{ it.desc }}</span><span class="api-owner">{{ s.owner }}</span><span class="{% if it.done %}st-done">✅ 완료{% else %}st-todo">⬜ 미완료{% endif %}</span></summary>
<div class="api-body">
<p class="meta"><b>권한</b> · {{ it.auth }}</p>
<h4>요청 (Request)</h4>
<pre>{{ it.req }}</pre>
<h4>성공 응답 (Response)</h4>
<p class="meta">{{ it.success }}</p>
{% if it.res %}<pre>{{ it.res }}</pre>
{% else %}{% assign sp = it.success | split: "data = " %}{% if sp.size > 1 %}{% assign dname = sp[1] | split: " (" | first %}{% assign dkey = dname | remove: "[]" | strip %}{% assign d = site.data.dtos[dkey] %}{% if d %}<details class="dto-row"><summary>▸ <code>{{ dname }}</code> 필드 구조 보기</summary><pre>{{ d.skeleton }}</pre></details>{% endif %}{% endif %}{% endif %}
<h4>오류 (Error)</h4>
{% if it.errors.size > 0 %}<ul>{% for e in it.errors %}<li><code>{{ e }}</code></li>{% endfor %}</ul>{% else %}<p class="meta">업무 오류 없음 (공통 오류 계약만 적용)</p>{% endif %}
</div>
</details>
{% endfor %}
{% endfor %}
---

## 📦 DTO 사전

응답에 나오는 DTO의 필드 구조입니다. 값은 실제 샘플이 아니라 타입·enum 표기이고, 중첩 DTO(예: `SettlementItem[]`)는 이 목록에서 찾아보세요.

{% for pair in site.data.dtos %}<details class="dto-row" id="dto-{{ pair[0] }}"><summary><b>{{ pair[0] }}</b></summary><pre>{{ pair[1].skeleton }}</pre></details>
{% endfor %}
---

### 참고

- 행을 클릭하면 해당 API의 **요청·성공 응답·오류 코드**가 펼쳐집니다 (일부 API는 응답 예시 JSON 포함).
- 상태를 바꾸려면 **`docs/_data/apis.yml`** 에서 해당 항목의 `done: false` 를 `done: true` 로 고치세요. 표·진행률이 자동 갱신됩니다.
- 내용 기준은 `docs/api.md` 명세입니다. 완료 초기값은 `develop` 머지 기준(API-04·16·20·21)입니다.
