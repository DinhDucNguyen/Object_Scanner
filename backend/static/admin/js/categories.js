    // ============================================================
    // Categories
    // ============================================================
    async function loadCategories() {
      const tbody = document.getElementById('cat-body');
      if (tbody) tbody.innerHTML = loadingRow(4);
      try {
        categories = await apiJSON('/categories');
        const filterEl = document.getElementById('obj-cat-filter');
        if (filterEl) {
          const cur = filterEl.value;
          filterEl.innerHTML = '<option value="">Táº¥t cáº£ danh má»¥c</option>' +
            categories.map(c => `<option value="${c.id}" ${String(c.id) === cur ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
        }
        const tbody = document.getElementById('cat-body');
        if (!tbody) return;
        const pageData = getPagedRows('categories', categories);
        renderTablePagination('categories', pageData);
        const countEl = document.querySelector('#section-categories .result-count');
        if (countEl) countEl.textContent = categories.length ? `${categories.length} danh má»¥c` : '';
        tbody.innerHTML = categories.length
          ? pageData.rows.map((c, idx) => {
            const parentName = categories.find(x => x.id === c.danh_muc_cha)?.ten_danh_muc;
            return `<tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${c.id}</td>
            <td class="fw-semibold">${c.ten_danh_muc || 'â€”'}</td>
            <td>${parentName ? `<span class="badge badge-neutral">${parentName}</span>` : '<span class="text-muted">â€”</span>'}</td>
            <td class="text-muted" style="font-size:.82rem;">${c.mo_ta || 'â€”'}</td>
            <td><span class="badge badge-neutral">${c.object_count ?? 0}</span></td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick='openEditCatModal(${JSON.stringify(c)})' title="Sá»­a"><i class="bi bi-pencil"></i></button>
                <button class="btn-act del" onclick="deleteCat(${c.id})" title="XoÃ¡"><i class="bi bi-trash3"></i></button>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(7, 'bi-folder-x', 'ChÆ°a cÃ³ danh má»¥c nÃ o');
      } catch (e) { toast('Lá»—i táº£i danh má»¥c: ' + e.message, 'danger'); }
    }

    function catOptions(selectedId) {
      return `<option value="">â€” KhÃ´ng cÃ³ â€”</option>` +
        categories.map(c => `<option value="${c.id}" ${c.id === selectedId ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
    }

    function openCreateCatModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'ThÃªm danh má»¥c má»›i';
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-card">
      <p class="approve-card-title">ThÃ´ng tin danh má»¥c</p>
      <div class="mb-3">
        <label class="form-label mb-1">TÃªn danh má»¥c <span class="text-danger">*</span></label>
        <input id="cat-name" class="form-control form-control-sm" placeholder="VÃ­ dá»¥: TrÃ¡i cÃ¢y" />
      </div>
      <div class="mb-3">
        <label class="form-label mb-1">Danh má»¥c cha</label>
        <select id="cat-parent" class="form-select form-select-sm">${catOptions(null)}</select>
      </div>
      <div>
        <label class="form-label mb-1">MÃ´ táº£</label>
        <textarea id="cat-desc" class="form-control form-control-sm" rows="2" style="resize:none;line-height:1.55;"></textarea>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        if (!body.ten_danh_muc) { toast('Nháº­p tÃªn danh má»¥c', 'warning'); return; }
        try { await apiJSON('/categories', { method: 'POST', body: JSON.stringify(body) }); toast('ÄÃ£ thÃªm danh má»¥c'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditCatModal(c) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sá»­a danh má»¥c #' + c.id;
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chá»‰nh sá»­a danh má»¥c <strong>#${c.id}</strong></span>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">ThÃ´ng tin danh má»¥c</p>
      <div class="mb-3">
        <label class="form-label mb-1">TÃªn danh má»¥c <span class="text-danger">*</span></label>
        <input id="cat-name" class="form-control form-control-sm" value="${c.ten_danh_muc || ''}" />
      </div>
      <div class="mb-3">
        <label class="form-label mb-1">Danh má»¥c cha</label>
        <select id="cat-parent" class="form-select form-select-sm">${catOptions(c.danh_muc_cha)}</select>
      </div>
      <div>
        <label class="form-label mb-1">MÃ´ táº£</label>
        <textarea id="cat-desc" class="form-control form-control-sm" rows="2" style="resize:none;line-height:1.55;">${c.mo_ta || ''}</textarea>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        try { await apiJSON(`/categories/${c.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('ÄÃ£ cáº­p nháº­t danh má»¥c'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function deleteCat(id) {
      confirmAction('XoÃ¡ danh má»¥c nÃ y? HÃ nh Ä‘á»™ng khÃ´ng thá»ƒ hoÃ n tÃ¡c.', async () => {
        try { await apiJSON(`/categories/${id}`, { method: 'DELETE' }); toast('ÄÃ£ xoÃ¡ danh má»¥c', 'warning'); loadCategories(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'XoÃ¡ danh má»¥c');
    }

