---
layout: status
title: Owlly API 진행 현황
---
{%- assign total = 0 -%}{%- assign done = 0 -%}
{%- for s in site.data.apis.sections -%}{%- for it in s.items -%}{%- assign total = total | plus: 1 -%}{%- if it.status == "완료" -%}{%- assign done = done | plus: 1 -%}{%- endif -%}{%- endfor -%}{%- endfor -%}
{%- assign pct = done | times: 100 | divided_by: total -%}
<h1>🦉 Owlly API 진행 현황</h1>
<p class="lead">전체 {{ total }}개 엔드포인트 · 행을 클릭하면 요청·응답·오류가 열립니다</p>

<h2>📊 진행률</h2>
<div class="rings">
<div class="ring-wrap"><div class="ring" style="width:130px;height:130px;background:conic-gradient(#16a34a {{ pct }}%, #e5e7eb 0)"><div class="ring-hole" style="width:94px;height:94px"><b>{{ pct }}%</b></div></div><div class="ring-cap">전체<small>{{ done }} / {{ total }}</small></div></div>
{%- assign colors = "#6366f1,#0ea5e9,#f59e0b,#2563eb" | split: "," -%}
{%- for o in site.data.apis.owners -%}
{%- assign ot = 0 -%}{%- assign od = 0 -%}{%- assign ow = 0 -%}
{%- for s in site.data.apis.sections -%}{%- if s.owner == o -%}{%- for it in s.items -%}{%- assign ot = ot | plus: 1 -%}{%- if it.status == "완료" -%}{%- assign od = od | plus: 1 -%}{%- elsif it.status == "대기" -%}{%- assign ow = ow | plus: 1 -%}{%- endif -%}{%- endfor -%}{%- endif -%}{%- endfor -%}
{%- assign op = od | times: 100 | divided_by: ot -%}
<div class="ring-wrap"><div class="ring ring-small" style="width:96px;height:96px;background:conic-gradient({{ colors[forloop.index0] }} {{ op }}%, #e5e7eb 0)"><div class="ring-hole" style="width:69px;height:69px"><b>{{ op }}%</b></div></div><div class="ring-cap">{{ o }}<small>{{ od }} / {{ ot }}</small>{%- if ow > 0 -%}<small class="cap-wait">대기 {{ ow }}</small>{%- endif -%}</div></div>
{%- endfor -%}
</div>

<div class="chips"><span class="chip active" data-f="all">전체</span>{%- for o in site.data.apis.owners -%}<span class="chip" data-f="owner:{{ o }}">{{ o }}</span>{%- endfor -%}{%- assign statuses = "완료,진행 중,대기,시작 전" | split: "," -%}{%- for st in statuses -%}<span class="chip" data-f="status:{{ st }}">{{ st }}</span>{%- endfor -%}</div>

{%- for s in site.data.apis.sections -%}
<div class="sec" data-owner="{{ s.owner }}">
<h2>{{ s.name }} — {{ s.owner }}</h2>
<div class="api-head"><span>ID</span><span>METHOD</span><span>PATH</span><span>설명</span><span>담당</span><span>상태</span></div>
{%- for it in s.items -%}
<details class="api-row" data-owner="{{ s.owner }}" data-status="{{ it.status }}">
<summary><span class="api-id">{{ it.id }}</span><span class="m m-{{ it.method }}">{{ it.method }}</span><code class="api-path">{{ it.path }}</code><span class="api-desc">{{ it.desc }}</span><span class="api-owner">{{ s.owner }}</span>{% case it.status %}{% when "완료" %}<span class="st st-done">✅ 완료</span>{% when "진행 중" %}<span class="st st-doing">▶︎ 진행 중</span>{% when "대기" %}<span class="st st-wait" title="{{ it.waitingFor }} 대기">⏸ 대기{% if it.waitingFor %}<small>사유: {{ it.waitingFor }}</small>{% endif %}</span>{% else %}<span class="st st-todo">⬜ 시작 전</span>{% endcase %}</summary>
<div class="api-body">
<p class="meta"><b>권한</b> · {{ it.auth }}</p>
{% if it.status == "대기" %}<p class="meta"><b>대기 사유</b> · {{ it.waitingNote | default: it.waitingFor }}</p>{% endif %}
<h4>요청 (Request)</h4>
<pre>{{ it.req | escape }}</pre>
<h4>성공 응답 (Response)</h4>
<p class="meta">{{ it.success }}</p>
{% if it.res %}<pre>{{ it.res | escape }}</pre>{% else %}{% assign sp = it.success | split: "data = " %}{% if sp.size > 1 %}{% assign dname = sp[1] | split: " (" | first %}{% assign dkey = dname | remove: "[]" | strip %}{% assign d = site.data.dtos[dkey] %}{% if d %}<details class="dto-row"><summary>▸ <code>{{ dname }}</code> 필드 구조 보기</summary><pre>{{ d.skeleton | escape }}</pre></details>{% endif %}{% endif %}{% endif %}
<h4>오류 (Error)</h4>
{% if it.errors.size > 0 %}<ul>{% for e in it.errors %}<li><code>{{ e }}</code></li>{% endfor %}</ul>{% else %}<p class="meta">업무 오류 없음 (공통 오류 계약만 적용)</p>{% endif %}
</div>
</details>
{%- endfor -%}
</div>
{%- endfor -%}

<h2>📦 DTO 사전</h2>
<p class="lead">응답 DTO의 필드 구조(타입·enum 표기). 중첩 DTO(예: <code>SettlementItem[]</code>)는 여기서 찾아보세요.</p>
{%- for pair in site.data.dtos -%}
<details class="dto-row"><summary><b>{{ pair[0] }}</b></summary><pre>{{ pair[1].skeleton | escape }}</pre></details>
{%- endfor -%}

<p class="note">상태 변경: <code>docs/_data/apis.yml</code> 에서 해당 API의 <code>status</code> 를 <code>시작 전</code> / <code>진행 중</code> / <code>대기</code> / <code>완료</code> 중 하나로 수정하면 표·진행률이 자동 갱신됩니다. 대기일 때는 <code>waitingFor</code> 에 기다리는 것(예: 인증, 외부 키, 정책)을, <code>waitingNote</code> 에 자세한 설명을 적어 주세요(행을 펼치면 보임). · 진행률은 완료만 셉니다.</p>
