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
          filterEl.innerHTML = '<option value="">Tất cả danh mục</option>' +
            categories.map(c => `<option value="${c.id}" ${String(c.id) === cur ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
        }
        const tbody = document.getElementById('cat-body');
        if (!tbody) return;
        const pageData = getPagedRows('categories', categories);
        renderTablePagination('categories', pageData);
        const countEl = document.querySelector('#section-categories .result-count');
        if (countEl) countEl.textContent = categories.length ? `${categories.length} danh mục` : '';
        tbody.innerHTML = categories.length
          ? pageData.rows.map((c, idx) => {
            const parentName = categories.find(x => x.id === c.danh_muc_cha)?.ten_danh_muc;
            return `<tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${c.id}</td>
            <td class="fw-semibold">${c.ten_danh_muc || '—'}</td>
            <td>${parentName ? `<span class="badge badge-neutral">${parentName}</span>` : '<span class="text-muted">—</span>'}</td>
            <td class="text-muted" style="font-size:.82rem;">${c.mo_ta || '—'}</td>
            <td><span class="badge badge-neutral">${c.object_count ?? 0}</span></td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick='openEditCatModal(${JSON.stringify(c)})' title="Sửa"><i class="bi bi-pencil"></i></button>
                <button class="btn-act del" onclick="deleteCat(${c.id})" title="Xoá"><i class="bi bi-trash3"></i></button>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(7, 'bi-folder-x', 'Chưa có danh mục nào');
      } catch (e) { toast('Lỗi tải danh mục: ' + e.message, 'danger'); }
    }

    function catOptions(selectedId) {
      return `<option value="">— Không có —</option>` +
        categories.map(c => `<option value="${c.id}" ${c.id === selectedId ? 'selected' : ''}>${c.ten_danh_muc}</option>`).join('');
    }

    function openCreateCatModal() {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Thêm danh mục mới';
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-card">
      <p class="approve-card-title">Thông tin danh mục</p>
      <div class="mb-3">
        <label class="form-label mb-1">Tên danh mục <span class="text-danger">*</span></label>
        <input id="cat-name" class="form-control form-control-sm" placeholder="Ví dụ: Trái cây" />
      </div>
      <div class="mb-3">
        <label class="form-label mb-1">Danh mục cha</label>
        <select id="cat-parent" class="form-select form-select-sm">${catOptions(null)}</select>
      </div>
      <div>
        <label class="form-label mb-1">Mô tả</label>
        <textarea id="cat-desc" class="form-control form-control-sm" rows="2" style="resize:none;line-height:1.55;"></textarea>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        if (!body.ten_danh_muc) { toast('Nhập tên danh mục', 'warning'); return; }
        try { await apiJSON('/categories', { method: 'POST', body: JSON.stringify(body) }); toast('Đã thêm danh mục'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openEditCatModal(c) {
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-title').textContent = 'Sửa danh mục #' + c.id;
      document.getElementById('fm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chỉnh sửa danh mục <strong>#${c.id}</strong></span>
    </div>
    <div class="approve-card">
      <p class="approve-card-title">Thông tin danh mục</p>
      <div class="mb-3">
        <label class="form-label mb-1">Tên danh mục <span class="text-danger">*</span></label>
        <input id="cat-name" class="form-control form-control-sm" value="${c.ten_danh_muc || ''}" />
      </div>
      <div class="mb-3">
        <label class="form-label mb-1">Danh mục cha</label>
        <select id="cat-parent" class="form-select form-select-sm">${catOptions(c.danh_muc_cha)}</select>
      </div>
      <div>
        <label class="form-label mb-1">Mô tả</label>
        <textarea id="cat-desc" class="form-control form-control-sm" rows="2" style="resize:none;line-height:1.55;">${c.mo_ta || ''}</textarea>
      </div>
    </div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const body = { ten_danh_muc: document.getElementById('cat-name').value.trim(), danh_muc_cha: document.getElementById('cat-parent').value || null, mo_ta: document.getElementById('cat-desc').value.trim() || null };
        try { await apiJSON(`/categories/${c.id}`, { method: 'PUT', body: JSON.stringify(body) }); toast('Đã cập nhật danh mục'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    async function deleteCat(id) {
      confirmAction('Xoá danh mục này? Hành động không thể hoàn tác.', async () => {
        try { await apiJSON(`/categories/${id}`, { method: 'DELETE' }); toast('Đã xoá danh mục', 'warning'); loadCategories(); }
        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      }, 'Xoá danh mục');
    }
