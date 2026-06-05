    // Users
    // ============================================================
    async function loadUsers() {
      const search = document.getElementById('user-search')?.value || '';
      const roleFilter = document.getElementById('user-role-filter')?.value || '';
      const statusFilter = document.getElementById('user-status-filter')?.value || '';
      const tbody = document.getElementById('user-body');
      if (tbody) tbody.innerHTML = loadingRow(6);
      try {
        let data = await apiJSON(`/users?limit=9999${search ? '&search=' + encodeURIComponent(search) : ''}`);
        if (roleFilter) data = data.filter(u => (u.vai_tro || '').toLowerCase().includes(roleFilter));
        if (statusFilter === 'active') data = data.filter(u => (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active');
        if (statusFilter === 'locked') data = data.filter(u => !((u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active'));
        const pageData = getPagedRows('users', data);
        renderTablePagination('users', pageData);
        const panel = document.querySelector('#section-users .panel-header .result-count');
        if (panel) panel.textContent = `${data.length} ngÆ°á»i dÃ¹ng`;
        document.getElementById('user-body').innerHTML = data.length
          ? pageData.rows.map((u, idx) => {
            const isAdmin = (u.vai_tro || '').toLowerCase().includes('admin') || (u.vai_tro || '').includes('quan_tri');
            const isActive = (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active';
            const usernameSafe = escHtml(u.ten_dang_nhap || '');
            const emailSafe = escHtml(u.email || '');
            const fullNameSafe = escHtml(u.ho_ten || '');
            const roleSafe = escHtml(u.vai_tro || '');
            const usernameArg = escHtml(JSON.stringify(u.ten_dang_nhap || ''));
            const roleArg = escHtml(JSON.stringify(u.vai_tro || ''));
            return `<tr>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td class="text-muted">${u.id}</td>
            <td>
              <div class="d-flex align-items-center gap-2">
                <div style="width:28px;height:28px;border-radius:50%;background:var(--primary);display:flex;align-items:center;justify-content:center;color:#fff;font-size:.7rem;font-weight:700;flex-shrink:0;">${(u.ten_dang_nhap || '?').charAt(0).toUpperCase()}</div>
                <span class="fw-semibold cell-ellipsis name-cell" title="${usernameSafe}">${usernameSafe}</span>
              </div>
            </td>
            <td class="text-muted" style="font-size:.82rem;"><span class="cell-ellipsis email-cell" title="${emailSafe}">${emailSafe}</span></td>
            <td><span class="cell-ellipsis name-cell" title="${fullNameSafe}">${u.ho_ten ? fullNameSafe : 'â€”'}</span></td>
            <td><span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'}">${u.vai_tro ? roleSafe : 'â€”'}</span></td>
            <td><span class="badge ${isActive ? 'badge-approved' : 'badge-rejected'}">${isActive ? 'Hoáº¡t Ä‘á»™ng' : 'Bá»‹ khoÃ¡'}</span></td>
            <td class="text-muted" style="font-size:.82rem;">${fmtDateShort(u.ngay_tao)}</td>
            <td class="text-end">
              <div class="btn-actions">
                <button class="btn-act" onclick="openUserStatsModal(${u.id}, ${usernameArg})" title="Xem thá»‘ng kÃª"><i class="bi bi-bar-chart-line"></i></button>
                <button class="btn-act" onclick="openUserRoleModal(${u.id}, ${roleArg})" title="Äá»•i vai trÃ²"><i class="bi bi-person-gear"></i></button>
                <div class="dropdown action-menu">
                  <button class="btn-act dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Thao tÃ¡c khÃ¡c">
                    <i class="bi bi-three-dots"></i>
                  </button>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li><button type="button" class="dropdown-item" onclick="openResetPasswordModal(${u.id}, ${usernameArg})"><i class="bi bi-key"></i>Äáº·t láº¡i máº­t kháº©u</button></li>
                    <li><button type="button" class="dropdown-item" onclick="toggleUserStatus(${u.id}, ${isActive})"><i class="bi ${isActive ? 'bi-lock' : 'bi-unlock'}"></i>${isActive ? 'KhoÃ¡ tÃ i khoáº£n' : 'Má»Ÿ khoÃ¡'}</button></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><button type="button" class="dropdown-item text-danger" onclick="deleteUser(${u.id}, ${usernameArg})"><i class="bi bi-trash3"></i>XoÃ¡ tÃ i khoáº£n</button></li>
                  </ul>
                </div>
              </div>
            </td>
          </tr>`;
          }).join('')
          : emptyRow(9, 'bi-people', 'KhÃ´ng cÃ³ ngÆ°á»i dÃ¹ng nÃ o');
      } catch (e) { toast('Lá»—i táº£i users: ' + e.message, 'danger'); }
    }

    async function toggleUserStatus(userId, isCurrentlyActive) {
      const newStatusId = isCurrentlyActive ? 2 : 1;
      const action = isCurrentlyActive ? 'KhoÃ¡ tÃ i khoáº£n nÃ y?' : 'Má»Ÿ khoÃ¡ tÃ i khoáº£n nÃ y?';
      confirmAction(action, async () => {
        try {
          await apiJSON(`/users/${userId}/status`, { method: 'PUT', body: JSON.stringify({ trang_thai_id: newStatusId }) });
          toast(isCurrentlyActive ? 'ÄÃ£ khoÃ¡ tÃ i khoáº£n' : 'ÄÃ£ má»Ÿ khoÃ¡ tÃ i khoáº£n', isCurrentlyActive ? 'warning' : 'success');
          loadUsers();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      });
    }

    async function deleteUser(userId, username) {
      confirmAction(`XoÃ¡ tÃ i khoáº£n "${username}"? HÃ nh Ä‘á»™ng nÃ y khÃ´ng thá»ƒ hoÃ n tÃ¡c.`, async () => {
        try {
          await apiJSON(`/users/${userId}`, { method: 'DELETE' });
          toast('ÄÃ£ xoÃ¡ tÃ i khoáº£n', 'warning');
          loadUsers();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'XoÃ¡ tÃ i khoáº£n');
    }

    function openResetPasswordModal(userId, username) {
      document.getElementById('fm-title').textContent = `Äáº·t láº¡i máº­t kháº©u â€” ${username}`;
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3 p-2 rounded bg-light" style="font-size:.84rem;">
      <i class="bi bi-info-circle text-muted me-1"></i>
      Máº­t kháº©u má»›i sáº½ Ä‘Æ°á»£c Ã¡p dá»¥ng ngay, ngÆ°á»i dÃ¹ng cáº§n Ä‘Äƒng nháº­p láº¡i.
    </div>
    <div class="mb-3">
      <label class="form-label">Máº­t kháº©u má»›i <span class="text-danger">*</span></label>
      <input id="new-password" type="password" class="form-control form-control-sm" placeholder="Tá»‘i thiá»ƒu 8 kÃ½ tá»±, cÃ³ chá»¯ hoa, thÆ°á»ng vÃ  sá»‘" />
    </div>
    <div class="mb-1">
      <label class="form-label">XÃ¡c nháº­n máº­t kháº©u</label>
      <input id="confirm-password" type="password" class="form-control form-control-sm" placeholder="Nháº­p láº¡i máº­t kháº©u" />
    </div>`;
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-submit').textContent = 'Äáº·t láº¡i máº­t kháº©u';
      document.getElementById('fm-submit').onclick = async () => {
        const pw = document.getElementById('new-password').value.trim();
        const cpw = document.getElementById('confirm-password').value.trim();
        if (!pw || pw.length < 8) { toast('Máº­t kháº©u tá»‘i thiá»ƒu 8 kÃ½ tá»±', 'warning'); return; }
        if (!/[a-z]/.test(pw)) { toast('Máº­t kháº©u pháº£i cÃ³ Ã­t nháº¥t 1 chá»¯ thÆ°á»ng', 'warning'); return; }
        if (!/[A-Z]/.test(pw)) { toast('Máº­t kháº©u pháº£i cÃ³ Ã­t nháº¥t 1 chá»¯ viáº¿t hoa', 'warning'); return; }
        if (!/\d/.test(pw)) { toast('Máº­t kháº©u pháº£i cÃ³ Ã­t nháº¥t 1 chá»¯ sá»‘', 'warning'); return; }
        if (pw !== cpw) { toast('Máº­t kháº©u xÃ¡c nháº­n khÃ´ng khá»›p', 'warning'); return; }
        try {
          await apiJSON(`/users/${userId}/reset-password`, { method: 'POST', body: JSON.stringify({ new_password: pw }) });
          toast('ÄÃ£ Ä‘áº·t láº¡i máº­t kháº©u');
          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

    function openUserRoleModal(userId, currentRole) {
      document.getElementById('fm-title').textContent = 'Cáº­p nháº­t vai trÃ² â€” #' + userId;
      const isAdmin = (currentRole || '').toLowerCase().includes('admin') || currentRole === 'quan_tri';
      document.getElementById('fm-body').innerHTML = `
    <div class="mb-3 p-2 rounded bg-light d-flex align-items-center gap-2" style="font-size:.84rem;">
      <i class="bi bi-person-circle text-muted"></i>
      Vai trÃ² hiá»‡n táº¡i: <span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'} ms-1">${currentRole || 'â€”'}</span>
    </div>
    <div class="mb-3"><label class="form-label">Vai trÃ² má»›i</label>
      <select id="role-id" class="form-select form-select-sm">
        <option value="1" ${!isAdmin ? 'selected' : ''}>NgÆ°á»i dÃ¹ng (nguoi_dung)</option>
        <option value="2" ${isAdmin ? 'selected' : ''}>Quáº£n trá»‹ (admin)</option>
      </select></div>`;
      document.getElementById('fm-submit').onclick = async () => {
        const vai_tro_id = parseInt(document.getElementById('role-id').value);
        try { await apiJSON(`/users/${userId}/role`, { method: 'PUT', body: JSON.stringify({ vai_tro_id }) }); toast('ÄÃ£ cáº­p nháº­t vai trÃ²'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadUsers(); }
        catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      };
      new bootstrap.Modal(document.getElementById('form-modal')).show();
    }

