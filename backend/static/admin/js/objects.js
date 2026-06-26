    // ============================================================
    // Objects
    // ============================================================
    async function loadObjects() {
      const search = document.getElementById('obj-search')?.value || '';
      const catId = document.getElementById('obj-cat-filter')?.value || '';
      const tbody = document.getElementById('obj-body');
      if (tbody) tbody.innerHTML = loadingRow(7);
      try {
        let url = `/objects?limit=1000&offset=0`;
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
            countEl.innerHTML = `Lọc: ${data.length} chưa có ảnh — <a href="#" onclick="clearNoImageFilter();return false;" style="font-weight:600;">Xem tất cả</a>`;
          } else {
            countEl.textContent = data.length ? `${data.length} đối tượng` : '';
          }
        }
        const warnEl = document.getElementById('obj-image-warning');
        const warnText = document.getElementById('obj-image-warning-text');
        if (noImageObjs.length > 0) {
          warnText.textContent = `${noImageObjs.length} đối tượng đang thiếu ảnh đại diện`;
          warnEl.style.display = '';
        } else {
          warnEl.style.display = 'none';
        }
        document.getElementById('obj-body').innerHTML = data.length
          ? pageData.rows.map((o, idx) => {
            const objectCode = escHtml(o.ma_doi_tuong || '—');
            const objectCodeRaw = o.ma_doi_tuong || '';
            const objectCodeArg = escHtml(JSON.stringify(objectCodeRaw));
            const aliases = (o.aliases || []).slice(0, 4);
            const aliasHtml = aliases.length ? `
              <div class="alias-chips">
                <span class="alias-label">Alias</span>
                ${aliases.map(a => `<span class="alias-chip cell-ellipsis" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</span>`).join('')}
                ${(o.aliases || []).length > aliases.length ? `<span class="alias-chip">+${(o.aliases || []).length - aliases.length}</span>` : ''}
              </div>` : '';
            window._objReg = window._objReg || {};
            window._objReg[o.id] = o;
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
            <td>${o.category_name ? `<span class="badge badge-neutral">${escHtml(o.category_name)}</span>` : '<span class="text-muted">—</span>'}</td>
            <td><div class="object-trans-stack">${translationBadge(o.translation_count, o.pending_translation_count)}</div></td>
            <td class="text-end">
              <div class="btn-actions object-actions">
                <button class="btn-act" style="position:relative;" onclick="openObjMediaModal(${objectCodeArg})" title="${!o.has_image ? 'Thêm ảnh đại diện' : 'Quản lý ảnh'}"><i class="bi bi-image"></i>${!o.has_image ? '<span style="position:absolute;top:2px;right:2px;width:6px;height:6px;border-radius:50%;background:#f59e0b;display:block;"></span>' : ''}</button>
                <button class="btn-act" onclick='openEditObjModal(${JSON.stringify(o)})' title="Sửa"><i class="bi bi-pencil"></i></button>
                <button class="btn-act" type="button" title="Thao tác khác" onclick="showObjMenu(event,${o.id})"><i class="bi bi-three-dots"></i></button>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(5, 'bi-box-seam', 'Không có đối tượng nào');
      } catch (e) { toast('Lỗi tải đối tượng: ' + e.message, 'danger'); }
    }

    function openCreateObjectModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm đối tượng mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-card">
      <p class="approve-card-title">Thông tin đối tượng</p>
      <div class="mb-3">
        <label class="form-label mb-1">Mã đối tượng <span class="text-danger">*</span></label>
        <input id="obj-code" class="form-control form-control-sm" placeholder="vd: apple, dog, car..." />
      </div>
      <div>
        <label class="form-label mb-1">Danh mục</label>
        <select id="obj-cat" class="form-select form-select-sm">${catOptions(null)}</select>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ma_doi_tuong: document.getElementById('obj-code').value.trim(), danh_muc_id: document.getElementById('obj-cat').value || null };
        if (!body.ma_doi_tuong) { toast('Nhập mã đối tượng', 'warning'); return; }
        try { await apiJSON('/objects', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm đối tượng'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditObjModal(o) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa đối tượng: ' + o.ma_doi_tuong;
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chỉnh sửa đối tượng <code>${escHtml(o.ma_doi_tuong || '')}</code></span>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">Thông tin đối tượng</p>
      <div>
        <label class="form-label mb-1">Danh mục</label>
        <select id="obj-cat" class="form-select form-select-sm">${catOptions(o.danh_muc_id)}</select>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { danh_muc_id: document.getElementById('obj-cat').value || null };
        try { await apiJSON(`/objects/${o.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
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
      document.getElementById('fm-title').textContent = 'Tên gọi khác: ' + o.ma_doi_tuong;
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
        <strong>Alias là nhãn phụ được gom về object chính.</strong>
        Nếu hệ thống nhận ra một tên khác nhưng thực ra vẫn là <code>${objectCode}</code>,
        nhập tên đó ở đây thay vì tạo object mới.
        <div class="mt-1">Ví dụ có thể gắn: ${examplesText}</div>
        <div class="alias-flow">
          <div class="alias-flow-cell">
            <strong>Nhãn hệ thống trả về</strong>
            <code>${aliasExample}</code>
          </div>
          <div class="alias-flow-arrow"><i class="bi bi-arrow-right"></i></div>
          <div class="alias-flow-cell">
            <strong>Object chính trong DB</strong>
            <code>${objectCode}</code>
          </div>
        </div>
      </div>
    </div>

    <div>
      <div class="d-flex align-items-center justify-content-between mb-2">
        <label class="form-label fw-semibold mb-0">Tên gọi khác hiện có</label>
        <span class="badge badge-neutral">${aliases.length} alias</span>
      </div>
      <div id="obj-alias-list" class="alias-list">
        ${aliases.length ? aliases.map(a => `
          <div class="alias-item" id="alias-row-${a.id}">
            <div style="min-width:0;flex:1;">
              <code class="cell-ellipsis" style="max-width:220px;" title="${escHtml(a.ma_bi_danh || '')}">${escHtml(a.ma_bi_danh || '')}</code>
              ${a.ten_hien_thi ? `<div class="text-muted mt-1" style="font-size:.78rem;">Hiển thị: ${escHtml(a.ten_hien_thi)}</div>` : ''}
            </div>
            <div class="d-flex gap-1">
              <button class="btn-act" onclick="toggleEditAlias(${a.id},'${escHtml(a.ma_bi_danh || '')}','${escHtml(a.ten_hien_thi || '')}','${escHtml(a.ngon_ngu || 'en')}')" title="Sửa tên gọi này">
                <i class="bi bi-pencil"></i>
              </button>
              <button class="btn-act del" onclick="deleteObjectAlias(${a.id})" title="Xoá tên gọi này">
                <i class="bi bi-trash3"></i>
              </button>
            </div>
          </div>
          <div id="alias-edit-${a.id}" style="display:none;" class="alias-edit-form">
            <div class="alias-form-grid">
              <div>
                <label class="form-label" style="font-size:.78rem;">Nhãn phụ / alias <span class="text-danger">*</span></label>
                <input id="alias-edit-code-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ma_bi_danh || '')}">
              </div>
              <div>
                <label class="form-label" style="font-size:.78rem;">Tên hiển thị</label>
                <input id="alias-edit-display-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ten_hien_thi || '')}">
              </div>
              <div>
                <label class="form-label" style="font-size:.78rem;">Ngôn ngữ</label>
                <input id="alias-edit-lang-${a.id}" class="form-control form-control-sm" value="${escHtml(a.ngon_ngu || 'en')}">
              </div>
            </div>
            <div class="d-flex gap-2 justify-content-end mt-2">
              <button class="btn btn-sm btn-outline-secondary" onclick="toggleEditAlias(${a.id})">Huỷ</button>
              <button class="btn btn-sm btn-primary" onclick="saveEditAlias(${a.id})"><i class="bi bi-check-lg me-1"></i>Lưu</button>
            </div>
          </div>`).join('') : '<div class="alias-empty"><i class="bi bi-link-45deg me-1"></i>Chưa có tên gọi khác cho object này.</div>'}
      </div>
    </div>

    <div class="border rounded p-3" style="background:#fafbfc;">
      <div class="fw-semibold mb-2" style="font-size:.84rem;">Gắn nhãn phụ vào object này</div>
      <div class="alias-form-grid">
        <div>
          <label class="form-label">Nhãn phụ / alias <span class="text-danger">*</span></label>
          <input id="obj-alias-code" class="form-control form-control-sm" placeholder="vd: ${aliasExample}">
        </div>
        <div>
          <label class="form-label">Tên hiển thị cho người học</label>
          <input id="obj-alias-display" class="form-control form-control-sm" placeholder="vd: ${displayExample}">
        </div>
        <div>
          <label class="form-label">Ngôn ngữ</label>
          <input id="obj-alias-lang" class="form-control form-control-sm" value="en">
        </div>
      </div>
      <div class="form-text mt-2" style="font-size:.76rem;">
        Sau khi lưu, alias này sẽ trỏ về <code>${objectCode}</code>. Không tạo object mới.
      </div>
      <div class="d-flex justify-content-end mt-3">
        <button class="btn btn-sm btn-primary" onclick="saveObjectAlias(${o.id})">
          <i class="bi bi-link-45deg me-1"></i>Gắn vào ${objectCode}
        </button>
      </div>
    </div>`;
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function saveObjectAlias(objectId) {
      const code = document.getElementById('obj-alias-code')?.value.trim();
      const display = document.getElementById('obj-alias-display')?.value.trim();
      const lang = document.getElementById('obj-alias-lang')?.value.trim() || 'en';
      if (!code) { toast('Nhập nhãn phụ / alias', 'warning'); return; }
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
        toast('Đã lưu tên gọi khác');
        bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        loadObjects();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      finally { if (btn) btn.disabled = false; }
    }

    async function deleteObjectAlias(aliasId) {
      confirmAction('Xóa bí danh này?', async () => {
        try {
          await apiJSON(`/object-aliases/${aliasId}`, { method: 'DELETE' });
          toast('Đã xóa bí danh', 'warning');
          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
          loadObjects();
        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xóa bí danh');
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
      if (!code) { toast('Nhập nhãn phụ / alias', 'warning'); return; }
      const triggerEl = document.querySelector(`[onclick*="saveEditAlias(${aliasId})"]`);
      if (triggerEl) triggerEl.disabled = true;
      try {
        await apiJSON(`/object-aliases/${aliasId}`, {
          method: 'PUT',
          body: JSON.stringify({ ma_bi_danh: code, ten_hien_thi: display || null, ngon_ngu: lang }),
        });
        toast('Đã cập nhật bí danh');
        bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        loadObjects();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      finally { if (triggerEl) triggerEl.disabled = false; }
    }

    async function deleteObj(id) {
      confirmAction('Xoá đối tượng này? Tất cả bản dịch liên quan sẽ bị xoá.', async () => {
        try { await apiJSON(`/objects/${id}`, { method: 'DELETE' }); toast('Đã xoá đối tượng', 'warning'); loadObjects(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá đối tượng');
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
      confirmAction(`Xác nhận ${ids.length} bản dịch đã chọn?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/translations/${id}`, { method: 'PUT', body: JSON.stringify({ da_xac_nhan: true }) }); ok++; }
          catch { fail++; }
        }
        if (fail > 0) toast(`Xác nhận ${ok}/${ids.length} — ${fail} lỗi`, 'warning');
        else toast(`Đã xác nhận ${ok}/${ids.length} bản dịch`);
        loadTranslations();
      }, 'Xác nhận bản dịch');
    }

    function filterNoImage() {
      objectOnlyNoImage = true;
      reloadPagedTable('objects');
    }

    function clearNoImageFilter() {
      objectOnlyNoImage = false;
      reloadPagedTable('objects');
    }

    // Custom popup menu - render ra body để tránh bị clip bởi table overflow
    let _objMenuEl = null;
    let _objMenuBtn = null;
    function showObjMenu(event, objId) {
      event.stopPropagation();
      const btn = event.currentTarget;
      // Nếu menu đang mở và nhấn cùng 1 nút → đóng lại (toggle)
      if (_objMenuEl) {
        _objMenuEl.remove();
        _objMenuEl = null;
        const wasBtn = _objMenuBtn;
        _objMenuBtn = null;
        if (wasBtn === btn) return;
      }
      const o = (window._objReg || {})[objId];
      if (!o) return;
      _objMenuBtn = btn;
      const rect = btn.getBoundingClientRect();
      const menu = document.createElement('div');
      menu.id = '_objMenu';
      menu.style.cssText = 'position:fixed;z-index:9999;background:#fff;border:1px solid #dbe3ea;border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.12);min-width:190px;padding:4px 0;font-size:.82rem';
      const aliasBtn = document.createElement('button');
      aliasBtn.className = 'obj-popup-item';
      aliasBtn.innerHTML = '<i class="bi bi-link-45deg"></i> Tên gọi khác';
      aliasBtn.onclick = () => { menu.remove(); _objMenuEl = null; _objMenuBtn = null; openObjectAliasModal(o); };
      const sep = document.createElement('div');
      sep.style.cssText = 'height:1px;background:#f1f5f9;margin:4px 0';
      const delBtn = document.createElement('button');
      delBtn.className = 'obj-popup-item text-danger';
      delBtn.innerHTML = '<i class="bi bi-trash3"></i> Xoá đối tượng';
      delBtn.onclick = () => { menu.remove(); _objMenuEl = null; _objMenuBtn = null; deleteObj(o.id); };
      menu.appendChild(aliasBtn);
      menu.appendChild(sep);
      menu.appendChild(delBtn);
      let top = rect.bottom + 4;
      let left = rect.right - 190;
      if (left < 8) left = 8;
      menu.style.top = top + 'px';
      menu.style.left = left + 'px';
      document.body.appendChild(menu);
      _objMenuEl = menu;
      setTimeout(() => {
        document.addEventListener('click', function close(e) {
          if (!menu.contains(e.target)) {
            menu.remove();
            _objMenuEl = null;
            _objMenuBtn = null;
            document.removeEventListener('click', close);
          }
        });
      }, 10);
    }

