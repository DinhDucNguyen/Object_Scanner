    // ============================================================
    // Translations
    // ============================================================
    async function loadTranslations() {
      const search = document.getElementById('trans-search')?.value || '';
      const lang = document.getElementById('trans-lang-filter')?.value || '';
      const approved = document.getElementById('trans-approved-filter')?.value || '';
      const tbody = document.getElementById('trans-body');
      if (tbody) tbody.innerHTML = loadingRow(7);
      try {
        let url = `/translations?limit=1000`;
        if (search) url += `&search=${encodeURIComponent(search)}`;
        if (lang) url += `&lang_code=${lang}`;
        if (approved !== '') url += `&approved=${approved}`;
        const data = await apiJSON(url);
        window._transCache = {};
        data.forEach(t => { window._transCache[t.id] = t; });
        const pageData = getPagedRows('translations', data);
        renderTablePagination('translations', pageData);
        const countEl = document.querySelector('#section-translations .result-count');
        if (countEl) countEl.textContent = data.length ? `${data.length} bản dịch` : '';
        document.getElementById('trans-body').innerHTML = data.length
          ? pageData.rows.map((t, idx) => `
          <tr>
            <td><input type="checkbox" class="form-check-input trans-check" data-id="${t.id}" data-approved="${t.da_xac_nhan}" onchange="updateTransBatchToolbar()"></td>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${t.id}</td>
            <td><code class="cell-ellipsis code-cell" title="${escHtml(t.object_code || String(t.doi_tuong_id || ''))}">${escHtml(t.object_code || t.doi_tuong_id || '—')}</code></td>
            <td><span class="badge badge-lang">${(t.lang_code || '').toUpperCase()}</span></td>
            <td class="fw-semibold"><span class="cell-ellipsis name-cell" title="${escHtml(t.tu_vung || '')}">${escHtml(t.tu_vung || '—')}</span></td>
            <td class="text-muted fst-italic" style="font-size:.82rem;">${t.phien_am || '—'}</td>
            <td>${t.da_xac_nhan
              ? '<span class="badge badge-approved"><i class="bi bi-check-lg me-1"></i>Đã xác nhận</span>'
              : '<span class="badge badge-neutral">Chưa xác nhận</span>'}</td>
            <td><span class="badge badge-neutral">${t.example_count}</span></td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick="openViewTransModal(window._transCache[${t.id}])" title="Xem"><i class="bi bi-eye"></i></button>
                <button class="btn-act" onclick="openEditTransModal(window._transCache[${t.id}])" title="Sửa"><i class="bi bi-pencil"></i></button>
                <button class="btn-act del" onclick="deleteTrans(${t.id})" title="Xoá"><i class="bi bi-trash3"></i></button>
              </div>
            </td>
          </tr>`).join('')
          : emptyRow(10, 'bi-translate', 'Không có bản dịch nào');
      } catch (e) { toast('Lỗi tải bản dịch: ' + e.message, 'danger'); }
    }

    function initTransFormTextareas(defId) {
      const ids = [defId, ...Array.from(document.querySelectorAll('.approve-example-row textarea')).map(ta => ta.id)];
      ids.forEach(tid => {
        const ta = document.getElementById(tid);
        if (!ta) return;
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
        ta.addEventListener('input', () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; });
      });
    }

    function openCreateTransModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm bản dịch mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-card">
      <p class="approve-card-title">Thông tin cơ bản</p>
      <div class="approve-basic-grid mb-3">
        <div>
          <label class="form-label mb-1">Từ vựng <span class="text-danger">*</span></label>
          <input id="t-word" class="form-control form-control-sm" />
        </div>
        <div>
          <label class="form-label mb-1">Phiên âm</label>
          <input id="t-phon" class="form-control form-control-sm" placeholder="/ˈæp.əl/" />
        </div>
        <div>
          <label class="form-label mb-1">Loại từ</label>
          <input id="t-pos" class="form-control form-control-sm" placeholder="noun / verb / adjective" />
        </div>
      </div>
      <div class="row g-2 mb-3">
        <div class="col-6">
          <label class="form-label mb-1">Object ID <span class="text-danger">*</span></label>
          <input id="t-oid" type="number" class="form-control form-control-sm" />
        </div>
        <div class="col-6">
          <label class="form-label mb-1">Ngôn ngữ <span class="text-danger">*</span></label>
          <select id="t-lang" class="form-select form-select-sm">
            <option value="en">English</option>
            <option value="vi">Tiếng Việt</option>
          </select>
        </div>
      </div>
      <div>
        <label class="form-label mb-1">Định nghĩa</label>
        <textarea id="t-def" class="form-control form-control-sm" rows="2" style="resize:none;line-height:1.55;"></textarea>
      </div>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">Ví dụ</p>
      <div class="approve-examples-hint">Mỗi dòng là một ví dụ. Có thể chỉ nhập English nếu chưa có nghĩa tiếng Việt.</div>
      <div class="approve-examples">${renderApproveExampleEditors([])}</div>
    </div>`;
      initTransFormTextareas('t-def');
      document.getElementById('fm-submit').onclick = async () => {
        const body = {
          doi_tuong_id: parseInt(document.getElementById('t-oid').value),
          lang_code: document.getElementById('t-lang').value,
          tu_vung: document.getElementById('t-word').value.trim(),
          phien_am: document.getElementById('t-phon').value.trim() || null,
          loai_tu: document.getElementById('t-pos').value.trim() || null,
          dinh_nghia: document.getElementById('t-def').value.trim() || null,
          example_sentences: collectApproveExamples(),
        };
        if (!body.doi_tuong_id || !body.tu_vung) { toast('Thiếu thông tin bắt buộc', 'warning'); return; }
        try { await apiJSON('/translations', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm bản dịch'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openViewTransModal(t) {
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-title').textContent = 'Chi tiết bản dịch #' + t.id;
      const exList = Array.isArray(t.examples) ? t.examples : [];
      const examples = exList.length
        ? `<ol class="mb-0 ps-3">${exList.map(e => `
            <li style="font-size:.83rem;">${e.cau_vi_du || ''}
              ${e.dich_nghia ? `<div class="text-muted" style="font-size:.79rem;font-style:italic;">${e.dich_nghia}</div>` : ''}
            </li>`).join('')}</ol>`
        : '<span class="text-muted" style="font-size:.83rem;">Chưa có ví dụ</span>';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-2 p-2 rounded" style="background:#f8f9fa;font-size:.82rem;">
      Object: <code>${t.object_code || t.doi_tuong_id}</code>
      <span class="badge badge-lang ms-1">${(t.lang_code || '').toUpperCase()}</span>
      ${t.da_xac_nhan ? '<span class="badge badge-approved ms-1"><i class="bi bi-check-lg me-1"></i>Đã xác nhận</span>' : '<span class="badge badge-neutral ms-1">Chưa xác nhận</span>'}
    </div>
    <table class="table table-sm table-borderless mb-2" style="font-size:.85rem;">
      <tr><th style="width:110px;color:var(--text-muted);">Từ vựng</th><td class="fw-semibold">${t.tu_vung || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Phiên âm</th><td class="fst-italic text-primary">${t.phien_am || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Loại từ</th><td>${t.loai_tu || '—'}</td></tr>
      <tr><th style="color:var(--text-muted);">Định nghĩa</th><td>${t.dinh_nghia || '—'}</td></tr>
    </table>
    <div><label class="form-label fw-semibold" style="font-size:.83rem;">Ví dụ (${t.example_count || 0})</label>${examples}</div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditTransModal(t) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa bản dịch #' + t.id;
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Object: <code>${t.object_code || t.doi_tuong_id}</code>
        <span class="badge badge-lang ms-1">${(t.lang_code || '').toUpperCase()}</span>
        ${t.da_xac_nhan ? '<span class="badge badge-approved ms-1"><i class="bi bi-check-lg me-1"></i>Đã xác nhận</span>' : '<span class="badge badge-neutral ms-1">Chưa xác nhận</span>'}
      </span>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">Thông tin cơ bản</p>
      <div class="approve-basic-grid mb-3">
        <div>
          <label class="form-label mb-1">Từ vựng</label>
          <input id="t-word" class="form-control form-control-sm" value="${escHtml(t.tu_vung || '')}" />
        </div>
        <div>
          <label class="form-label mb-1">Phiên âm (IPA)</label>
          <input id="t-phon" class="form-control form-control-sm" value="${escHtml(t.phien_am || '')}" />
        </div>
        <div>
          <label class="form-label mb-1">Loại từ</label>
          <input id="t-pos" class="form-control form-control-sm" value="${escHtml(t.loai_tu || '')}" />
        </div>
      </div>
      <div class="mb-3">
        <label class="form-label mb-1">Định nghĩa</label>
        <textarea id="t-def" class="form-control form-control-sm" style="resize:none;line-height:1.55;" rows="2">${escHtml(t.dinh_nghia || '')}</textarea>
      </div>
      <div>
        <label class="form-label mb-1">Trạng thái xác nhận</label>
        <select id="t-confirmed" class="form-select form-select-sm">
          <option value="true" ${t.da_xac_nhan ? 'selected' : ''}>Đã xác nhận</option>
          <option value="false" ${!t.da_xac_nhan ? 'selected' : ''}>Chưa xác nhận</option>
        </select>
      </div>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">Ví dụ</p>
      <div class="approve-examples-hint">Mỗi dòng là một ví dụ. Có thể chỉ nhập English nếu chưa có nghĩa tiếng Việt.</div>
      <div class="approve-examples">${renderApproveExampleEditors(t.examples || [])}</div>
    </div>`;
      initTransFormTextareas('t-def');
      document.getElementById('fm-submit').onclick = async () => {
        const body = {
          tu_vung: document.getElementById('t-word').value.trim() || null,
          phien_am: document.getElementById('t-phon').value.trim() || null,
          loai_tu: document.getElementById('t-pos').value.trim() || null,
          dinh_nghia: document.getElementById('t-def').value.trim() || null,
          da_xac_nhan: document.getElementById('t-confirmed').value === 'true',
          example_sentences: collectApproveExamples(),
        };
        try { await apiJSON(`/translations/${t.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật bản dịch'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function deleteTrans(id) {
      confirmAction('Xoá bản dịch này?', async () => {
        try { await apiJSON(`/translations/${id}`, { method: 'DELETE' }); toast('Đã xoá bản dịch', 'warning'); loadTranslations(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá bản dịch');
    }
