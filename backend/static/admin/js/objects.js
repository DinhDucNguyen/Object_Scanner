    // ============================================================
    // Objects
    // ============================================================
    async function loadObjects() {
      const search = document.getElementById('obj-search')?.value || '';
      const catId = document.getElementById('obj-cat-filter')?.value || '';
      const tbody = document.getElementById('obj-body');
      if (tbody) tbody.innerHTML = loadingRow(7);
      try {
        let url = `/objects?limit=9999&offset=0`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (catId) url += `&category_id=${catId}`;
        const rawData = await apiJSON(url);
        const noImageObjs = rawData.filter(o => !o.has_image);
        const data = objectOnlyNoImage ? noImageObjs : rawData;
        const pageData = getPagedRows('objects', data);
        renderTablePagination('objects', pageData);
        const countEl = document.querySelector('#section-objects .result-count');
        if (countEl) {
          if (objectOnlyNoImage) {
            countEl.innerHTML = `Lá»c: ${data.length} chÆ°a cÃ³ áº£nh â€” <a href="#" onclick="clearNoImageFilter();return false;" style="font-weight:600;">Xem táº¥t cáº£</a>`;
          } else {
            countEl.textContent = data.length ? `${data.length} Ä‘á»‘i tÆ°á»£ng` : '';
          }
        }
        const warnEl = document.getElementById('obj-image-warning');
        const warnText = document.getElementById('obj-image-warning-text');
        if (noImageObjs.length > 0) {
          warnText.textContent = `${noImageObjs.length} Ä‘á»‘i tÆ°á»£ng Ä‘ang thiáº¿u áº£nh Ä‘áº¡i diá»‡n`;
          warnEl.style.display = '';
        } else {
          warnEl.style.display = 'none';
        }
        document.getElementById('obj-body').innerHTML = data.length
          ? pageData.rows.map((o, idx) => {
            const objectCode = escHtml(o.ma_doi_tuong || 'â€”');
            const objectCodeRaw = o.ma_doi_tuong || '';
            const objectCodeArg = escHtml(JSON.stringify(objectCodeRaw));
            const aliases = (o.aliases || []).slice(0, 4);
            const aliasHtml = aliases.length ? `
              <div class="alias-chips">
                <span class="alias-label">Alias</span>
                ${aliases.map(a => `<span class="alias-chip cell-ellipsis" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</span>`).join('')}
                ${(o.aliases || []).length > aliases.length ? `<span class="alias-chip">+${(o.aliases || []).length - aliases.length}</span>` : ''}
              </div>` : '';
            return `
          <tr data-has-image="${o.has_image ? '1' : '0'}">
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted fw-semibold">${o.id}</td>
            <td>
              <div class="object-main">
                <div class="object-code-line">
                  <code class="cell-ellipsis code-cell" title="${objectCode}">${objectCode}</code>
                </div>
                ${aliasHtml}
              </div>
            </td>
            <td>${o.category_name ? `<span class="badge badge-neutral">${escHtml(o.category_name)}</span>` : '<span class="text-muted">â€”</span>'}</td>
            <td><div class="object-trans-stack">${translationBadge(o.translation_count, o.pending_translation_count)}</div></td>
            <td class="text-end">
              <div class="btn-actions object-actions">
                <button class="btn-act" style="position:relative;" onclick="openObjMediaModal(${objectCodeArg})" title="${!o.has_image ? 'ThÃªm áº£nh Ä‘áº¡i diá»‡n' : 'Quáº£n lÃ½ áº£nh'}"><i class="bi bi-image"></i>${!o.has_image ? '<span style="position:absolute;top:2px;right:2px;width:6px;height:6px;border-radius:50%;background:#f59e0b;display:block;"></span>' : ''}</button>
                <button class="btn-act" onclick='openEditObjModal(${JSON.stringify(o)})' title="Sá»­a"><i class="bi bi-pencil"></i></button>
                <div class="dropdown action-menu">
                  <button class="btn-act dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Thao tÃ¡c khÃ¡c">
                    <i class="bi bi-three-dots"></i>
                  </button>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li><button type="button" class="dropdown-item" onclick='openObjectAliasModal(${JSON.stringify(o)})'><i class="bi bi-link-45deg"></i>TÃªn gá»i khÃ¡c</button></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><button type="button" class="dropdown-item text-danger" onclick="deleteObj(${o.id})"><i class="bi bi-trash3"></i>XoÃ¡ Ä‘á»‘i tÆ°á»£ng</button></li>
                  </ul>
                </div>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(5, 'bi-box-seam', 'KhÃ´ng cÃ³ Ä‘á»‘i tÆ°á»£ng nÃ o');
      } catch (e) { toast('Lá»—i táº£i Ä‘á»‘i tÆ°á»£ng: ' + e.message, 'danger'); }
    }

    function openCreateObjectModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'ThÃªm Ä‘á»‘i tÆ°á»£ng má»›i';
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-card">
      <p class="approve-card-title">ThÃ´ng tin Ä‘á»‘i tÆ°á»£ng</p>
      <div class="mb-3">
        <label class="form-label mb-1">MÃ£ Ä‘á»‘i tÆ°á»£ng <span class="text-danger">*</span></label>
        <input id="obj-code" class="form-control form-control-sm" placeholder="vd: apple, dog, car..." />
      </div>
      <div>
        <label class="form-label mb-1">Danh má»¥c</label>
        <select id="obj-cat" class="form-select form-select-sm">${catOptions(null)}</select>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ma_doi_tuong: document.getElementById('obj-code').value.trim(), danh_muc_id: document.getElementById('obj-cat').value || null };
        if (!body.ma_doi_tuong) { toast('Nháº­p mÃ£ Ä‘á»‘i tÆ°á»£ng', 'warning'); return; }
        try { await apiJSON('/objects', { method: 'POST', body: JSON.stringify(body) }); toast('ÄÃ£ thÃªm Ä‘á»‘i tÆ°á»£ng'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditObjModal(o) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sá»­a Ä‘á»‘i tÆ°á»£ng: ' + o.ma_doi_tuong;
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chá»‰nh sá»­a Ä‘á»‘i tÆ°á»£ng <code>${escHtml(o.ma_doi_tuong || '')}</code></span>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">ThÃ´ng tin Ä‘á»‘i tÆ°á»£ng</p>
      <div>
        <label class="form-label mb-1">Danh má»¥c</label>
        <select id="obj-cat" class="form-select form-select-sm">${catOptions(o.danh_muc_id)}</select>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { danh_muc_id: document.getElementById('obj-cat').value || null };
        try { await apiJSON(`/objects/${o.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('ÄÃ£ cáº­p nháº­t'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function getAliasSuggestion(objectCode) {
      const code = String(objectCode || '').trim().toLowerCase();
      if (!code) {
        return {
          alias: 'tomatoes',
          display: 'Tomatoes',
          examples: ['tomatoes', 'cherry_tomato', 'ca_chua'],
        };
      }
      if (code === 'tomato' || code === 'tomatoes') {
        return {
          alias: 'tomatoes',
          display: 'Tomatoes',
          examples: ['tomatoes', 'cherry_tomato', 'ca_chua'],
        };
      }
      if (code === 'key' || code === 'keys') {
        return {
          alias: code === 'keys' ? 'key' : 'keys',
          display: code === 'keys' ? 'Key' : 'Keys',
          examples: ['key', 'keys', 'house_keys'],
        };
      }
      const plural = code.endsWith('s')
        ? code
        : code.endsWith('y')
          ? code.slice(0, -1) + 'ies'
          : code + 's';
      return {
        alias: plural,
        display: plural.replaceAll('_', ' '),
        examples: [plural, code.replaceAll('_', ' ')],
      };
    }

    function openObjectAliasModal(o) {
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-title').textContent = 'TÃªn gá»i khÃ¡c: ' + o.ma_doi_tuong;
      const aliases = o.aliases || [];
      const objectCode = escHtml(o.ma_doi_tuong || '');
      const suggestion = getAliasSuggestion(o.ma_doi_tuong);
      const aliasExample = escHtml(suggestion.alias);
      const displayExample = escHtml(suggestion.display);
      const examplesText = suggestion.examples.map(x => `<code>${escHtml(x)}</code>`).join(', ');
      document.getElementById('fm-body').innerHTML = `
    <div class="alias-help">
      <i class="bi bi-info-circle"></i>
      <div>
        <strong>Alias lÃ  nhÃ£n phá»¥ Ä‘Æ°á»£c gom vá» object chÃ­nh.</strong>
        Náº¿u há»‡ thá»‘ng nháº­n ra má»™t tÃªn khÃ¡c nhÆ°ng thá»±c ra váº«n lÃ  <code>${objectCode}</code>,
        nháº­p tÃªn Ä‘Ã³ á»Ÿ Ä‘Ã¢y thay vÃ¬ táº¡o object má»›i.
        <div class="mt-1">VÃ­ dá»¥ cÃ³ thá»ƒ gáº¯n: ${examplesText}</div>
        <div class="alias-flow">
          <div class="alias-flow-cell">
            <strong>NhÃ£n há»‡ thá»‘ng tráº£ vá»</strong>
            <code>${aliasExample}</code>
          </div>
          <div class="alias-flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="alias-flow-cell">
            <strong>Object chÃ­nh trong DB</strong>
            <code>${objectCode}</code>
          </div>
        </div>
      </div>
    </div>

    <div>
      <div class="d-flex align-items-center justify-content-between mb-2">
        <label class="form-label fw-semibold mb-0">TÃªn gá»i khÃ¡c hiá»‡n cÃ³</label>
        <span class="badge badge-neutral">${aliases.length} alias</span>
      </div>
      <div id="obj-alias-list" class="alias-list">
        ${aliases.length ? aliases.map(a => `
          <div class="alias-item" id="alias-row-${a.id}">
            <div style="min-width:0;flex:1;">
              <code class="cell-ellipsis" style="max-width:220px;" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</code>
              ${a.ten_hien_thi ? `<div class="text-muted mt-1" style="font-size:.78rem;">Hiá»ƒn thá»‹: ${escHtml(a.ten_hien_thi)}</div>` : ''}
            </div>
            <div class="d-flex gap-1">
              <button class="btn-act" onclick="toggleEditAlias(${a.id},'${escHtml(a.ma_bi_danh || '')}','${escHtml(a.ten_hien_thi || '')}','${escHtml(a.ngon_ngu || 'en')}')" title="Sá»­a tÃªn gá»i nÃ y">
                <i class="bi bi-pencil"></i>
              </button>
              <button class="btn-act del" onclick="deleteObjectAlias(${a.id})" title="XoÃ¡ tÃªn gá»i nÃ y">
                <i class="bi bi-trash3"></i>
              </button>
            </div>
          </div>
          <div id="alias-edit-${a.id}" style="display:none;" class="alias-edit-form">
            <div class="alias-form-grid">
              <div>
                <label class="form-label" style="font-size:.78rem;">NhÃ£n phá»¥ / alias <span class="text-danger">*</span></label>
                <input id="alias-edit-code-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ma_bi_danh || '')}">
              </div>
              <div>
                <label class="form-label" style="font-size:.78rem;">TÃªn hiá»ƒn thá»‹</label>
                <input id="alias-edit-display-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ten_hien_thi || '')}">
              </div>
              <div>
                <label class="form-label" style="font-size:.78rem;">NgÃ´n ngá»¯</label>
                <input id="alias-edit-lang-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ngon_ngu || 'en')}">
              </div>
            </div>
            <div class="d-flex gap-2 justify-content-end mt-2">
              <button class="btn btn-sm btn-outline-secondary" onclick="toggleEditAlias(${a.id})">Huá»·</button>
              <button class="btn btn-sm btn-primary" onclick="saveEditAlias(${a.id})"><i class="bi bi-check-lg me-1"></i>LÆ°u</button>
            </div>
          </div>`).join('') : '<div class="alias-empty"><i class="bi bi-link-45deg me-1"></i>ChÆ°a cÃ³ tÃªn gá»i khÃ¡c cho object nÃ y.</div>'}
      </div>
    </div>

    <div class="border rounded p-3" style="background:#fafbfc;">
      <div class="fw-semibold mb-2" style="font-size:.84rem;">Gáº¯n nhÃ£n phá»¥ vÃ o object nÃ y</div>
      <div class="alias-form-grid">
        <div>
          <label class="form-label">NhÃ£n phá»¥ / alias <span class="text-danger">*</span></label>
          <input id="obj-alias-code" class="form-control form-control-sm" placeholder="vd: ${aliasExample}">
        </div>
        <div>
          <label class="form-label">TÃªn hiá»ƒn thá»‹ cho ngÆ°á»i há»c</label>
          <input id="obj-alias-display" class="form-control form-control-sm" placeholder="vd: ${displayExample}">
        </div>
        <div>
          <label class="form-label">NgÃ´n ngá»¯</label>
          <input id="obj-alias-lang" class="form-control form-control-sm" value="en">
        </div>
      </div>
      <div class="form-text mt-2" style="font-size:.76rem;">
        Sau khi lÆ°u, alias nÃ y sáº½ trá» vá» <code>${objectCode}</code>. KhÃ´ng táº¡o object má»›i.
      </div>
      <div class="d-flex justify-content-end mt-3">
        <button class="btn btn-sm btn-primary" onclick="saveObjectAlias(${o.id})">
          <i class="bi bi-link-45deg me-1"></i>Gáº¯n vÃ o ${objectCode}
        </button>
      </div>
    </div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function saveObjectAlias(objectId) {
      const code = document.getElementById('obj-alias-code')?.value.trim();
      const display = document.getElementById('obj-alias-display')?.value.trim();
      const lang = document.getElementById('obj-alias-lang')?.value.trim() || 'en';
      if (!code) { toast('Nháº­p nhÃ£n phá»¥ / alias', 'warning'); return; }
      const btn = document.getElementById('fm-submit');
      if (btn) btn.disabled = true;
      try {
        await apiJSON('/object-aliases', {
          method: 'POST',
          body: JSON.stringify({
            doi_tuong_id: objectId,
            ma_bi_danh: code,
            ten_hien_thi: display || null,
            ngon_ngu: lang,
          }),
        });
        toast('ÄÃ£ lÆ°u tÃªn gá»i khÃ¡c');
        bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        loadObjects();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      finally { if (btn) btn.disabled = false; }
    }

    async function deleteObjectAlias(aliasId) {
      confirmAction('XÃ³a bÃ­ danh nÃ y?', async () => {
        try {
          await apiJSON(`/object-aliases/${aliasId}`, { method: 'DELETE' });
          toast('ÄÃ£ xÃ³a bÃ­ danh', 'warning');
          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
          loadObjects();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'XÃ³a bÃ­ danh');
    }

    function toggleEditAlias(aliasId, code, display, lang) {
      const editDiv = document.getElementById(`alias-edit-${aliasId}`);
      const rowDiv = document.getElementById(`alias-row-${aliasId}`);
      if (!editDiv) return;
      const isOpen = editDiv.style.display !== 'none';
      if (isOpen) {
        editDiv.style.display = 'none';
        rowDiv.style.opacity = '1';
      } else {
        if (code !== undefined) {
          document.getElementById(`alias-edit-code-${aliasId}`).value = code;
          document.getElementById(`alias-edit-display-${aliasId}`).value = display || '';
          document.getElementById(`alias-edit-lang-${aliasId}`).value = lang || 'en';
        }
        editDiv.style.display = 'block';
        rowDiv.style.opacity = '0.5';
        document.getElementById(`alias-edit-code-${aliasId}`)?.focus();
      }
    }

    async function saveEditAlias(aliasId) {
      const code = document.getElementById(`alias-edit-code-${aliasId}`)?.value.trim();
      const display = document.getElementById(`alias-edit-display-${aliasId}`)?.value.trim();
      const lang = document.getElementById(`alias-edit-lang-${aliasId}`)?.value.trim() || 'en';
      if (!code) { toast('Nháº­p nhÃ£n phá»¥ / alias', 'warning'); return; }
      const triggerEl = document.querySelector(`[onclick*="saveEditAlias(${aliasId})"]`);
      if (triggerEl) triggerEl.disabled = true;
      try {
        await apiJSON(`/object-aliases/${aliasId}`, {
          method: 'PUT',
          body: JSON.stringify({ ma_bi_danh: code, ten_hien_thi: display || null, ngon_ngu: lang }),
        });
        toast('ÄÃ£ cáº­p nháº­t bÃ­ danh');
        bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        loadObjects();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      finally { if (triggerEl) triggerEl.disabled = false; }
    }

    async function deleteObj(id) {
      confirmAction('XoÃ¡ Ä‘á»‘i tÆ°á»£ng nÃ y? Táº¥t cáº£ báº£n dá»‹ch liÃªn quan sáº½ bá»‹ xoÃ¡.', async () => {
        try { await apiJSON(`/objects/${id}`, { method: 'DELETE' }); toast('ÄÃ£ xoÃ¡ Ä‘á»‘i tÆ°á»£ng', 'warning'); loadObjects(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'XoÃ¡ Ä‘á»‘i tÆ°á»£ng');
    }

    function zoomImage(url) {
      const overlay = document.createElement('div');
      overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.85);z-index:9999;display:flex;align-items:center;justify-content:center;cursor:zoom-out;';
      overlay.onclick = () => overlay.remove();
      const img = document.createElement('img');
      img.src = url;
      img.style.cssText = 'max-width:90vw;max-height:90vh;border-radius:8px;box-shadow:0 8px 40px rgba(0,0,0,.6);object-fit:contain;';
      overlay.appendChild(img);
      document.body.appendChild(overlay);
    }

    function toggleAllTransChecks(masterCb) {
      document.querySelectorAll('.trans-check').forEach(cb => { cb.checked = masterCb.checked; });
      updateTransBatchToolbar();
    }

    function updateTransBatchToolbar() {
      const checked = document.querySelectorAll('.trans-check:checked');
      const toolbar = document.getElementById('trans-batch-toolbar');
      if (!toolbar) return;
      if (checked.length > 0) {
        toolbar.classList.remove('is-hidden');
        document.getElementById('trans-batch-count').textContent = checked.length;
      } else {
        toolbar.classList.add('is-hidden');
      }
    }

    async function batchApproveTrans() {
      const ids = Array.from(document.querySelectorAll('.trans-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) return;
      confirmAction(`XÃ¡c nháº­n ${ids.length} báº£n dá»‹ch Ä‘Ã£ chá»n?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/translations/${id}`, { method: 'PUT', body: JSON.stringify({ da_xac_nhan: true }) }); ok++; }
          catch { fail++; }
        }
        if (fail > 0) toast(`XÃ¡c nháº­n ${ok}/${ids.length} â€” ${fail} lá»—i`, 'warning');
        else toast(`ÄÃ£ xÃ¡c nháº­n ${ok}/${ids.length} báº£n dá»‹ch`);
        loadTranslations();
      }, 'XÃ¡c nháº­n báº£n dá»‹ch');
    }

    function filterNoImage() {
      objectOnlyNoImage = true;
      reloadPagedTable('objects');
    }

    function clearNoImageFilter() {
      objectOnlyNoImage = false;
      reloadPagedTable('objects');
    }

