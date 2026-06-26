    // Users

    // ============================================================

    async function loadUsers() {

      const search = document.getElementById('user-search')?.value || '';

      const roleFilter = document.getElementById('user-role-filter')?.value || '';

      const statusFilter = document.getElementById('user-status-filter')?.value || '';

      const tbody = document.getElementById('user-body');

      if (tbody) tbody.innerHTML = loadingRow(6);

      try {

        let data = await apiJSON(`/users?limit=1000${search ? '&search=' + encodeURIComponent(search) : ''}`);
        if (roleFilter) data = data.filter(u => (u.vai_tro || '').toLowerCase().includes(roleFilter));

        if (statusFilter === 'active') data = data.filter(u => (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active');

        if (statusFilter === 'locked') data = data.filter(u => !((u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active'));

        const pageData = getPagedRows('users', data);

        renderTablePagination('users', pageData);

        const panel = document.querySelector('#section-users .panel-header .result-count');

        if (panel) panel.textContent = `${data.length} người dùng`;

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

            window._userReg = window._userReg || {};
            window._userReg[u.id] = u;

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

            <td><span class="cell-ellipsis name-cell" title="${fullNameSafe}">${u.ho_ten ? fullNameSafe : '—'}</span></td>

            <td><span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'}">${u.vai_tro ? roleSafe : '—'}</span></td>

            <td><span class="badge ${isActive ? 'badge-approved' : 'badge-rejected'}">${isActive ? 'Hoạt động' : 'Bị khoá'}</span></td>

            <td class="text-muted" style="font-size:.82rem;">${fmtDateShort(u.ngay_tao)}</td>

            <td class="text-end">

              <div class="btn-actions">

                <button class="btn-act" onclick="openUserStatsModal(${u.id}, ${usernameArg})" title="Xem thống kê"><i class="bi bi-bar-chart-line"></i></button>

                <button class="btn-act" onclick="openUserRoleModal(${u.id}, ${roleArg})" title="Đổi vai trò"><i class="bi bi-person-gear"></i></button>

                <button class="btn-act" type="button" title="Thao tác khác" onclick="showUserMenu(event,${u.id})"><i class="bi bi-three-dots"></i></button>

              </div>

            </td>

          </tr>`;

          }).join('')

          : emptyRow(9, 'bi-people', 'Không có người dùng nào');

      } catch (e) { toast('Lỗi tải users: ' + e.message, 'danger'); }

    }



    async function toggleUserStatus(userId, isCurrentlyActive) {

      const newStatusId = isCurrentlyActive ? 2 : 1;

      const action = isCurrentlyActive ? 'Khoá tài khoản này?' : 'Mở khoá tài khoản này?';

      confirmAction(action, async () => {

        try {

          await apiJSON(`/users/${userId}/status`, { method: 'PUT', body: JSON.stringify({ trang_thai_id: newStatusId }) });

          toast(isCurrentlyActive ? 'Đã khoá tài khoản' : 'Đã mở khoá tài khoản', isCurrentlyActive ? 'warning' : 'success');

          loadUsers();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      });

    }



    async function deleteUser(userId, username) {

      confirmAction(`Xoá tài khoản "${username}"? Hành động này không thể hoàn tác.`, async () => {

        try {

          await apiJSON(`/users/${userId}`, { method: 'DELETE' });

          toast('Đã xoá tài khoản', 'warning');

          loadUsers();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      }, 'Xoá tài khoản');

    }



    function openResetPasswordModal(userId, username) {

      document.getElementById('fm-title').textContent = `Đặt lại mật khẩu — ${username}`;

      document.getElementById('fm-body').innerHTML = `

    <div class="mb-3 p-2 rounded bg-light" style="font-size:.84rem;">

      <i class="bi bi-info-circle text-muted me-1"></i>

      Mật khẩu mới sẽ được áp dụng ngay, người dùng cần đăng nhập lại.

    </div>

    <div class="mb-3">

      <label class="form-label">Mật khẩu mới <span class="text-danger">*</span></label>

      <input id="new-password" type="password" class="form-control form-control-sm" placeholder="Tối thiểu 8 ký tự, có chữ hoa, thường và số" />

    </div>

    <div class="mb-1">

      <label class="form-label">Xác nhận mật khẩu</label>

      <input id="confirm-password" type="password" class="form-control form-control-sm" placeholder="Nhập lại mật khẩu" />

    </div>`;

      document.getElementById('fm-submit').style.display = '';

      document.getElementById('fm-submit').textContent = 'Đặt lại mật khẩu';

      document.getElementById('fm-submit').onclick = async () => {

        const pw = document.getElementById('new-password').value.trim();

        const cpw = document.getElementById('confirm-password').value.trim();

        if (!pw || pw.length < 8) { toast('Mật khẩu tối thiểu 8 ký tự', 'warning'); return; }

        if (!/[a-z]/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ thường', 'warning'); return; }

        if (!/[A-Z]/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ viết hoa', 'warning'); return; }

        if (!/\d/.test(pw)) { toast('Mật khẩu phải có ít nhất 1 chữ số', 'warning'); return; }

        if (pw !== cpw) { toast('Mật khẩu xác nhận không khớp', 'warning'); return; }

        try {

          await apiJSON(`/users/${userId}/reset-password`, { method: 'POST', body: JSON.stringify({ new_password: pw }) });

          toast('Đã đặt lại mật khẩu');

          bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      };

      new bootstrap.Modal(document.getElementById('form-modal')).show();

    }



    function openUserRoleModal(userId, currentRole) {

      document.getElementById('fm-title').textContent = 'Cập nhật vai trò — #' + userId;

      const isAdmin = (currentRole || '').toLowerCase().includes('admin') || currentRole === 'quan_tri';

      document.getElementById('fm-body').innerHTML = `

    <div class="mb-3 p-2 rounded bg-light d-flex align-items-center gap-2" style="font-size:.84rem;">

      <i class="bi bi-person-circle text-muted"></i>

      Vai trò hiện tại: <span class="badge ${isAdmin ? 'badge-role-admin' : 'badge-role-user'} ms-1">${currentRole || '—'}</span>

    </div>

    <div class="mb-3"><label class="form-label">Vai trò mới</label>

      <select id="role-id" class="form-select form-select-sm">

        <option value="1" ${!isAdmin ? 'selected' : ''}>Người dùng (nguoi_dung)</option>

        <option value="2" ${isAdmin ? 'selected' : ''}>Quản trị (admin)</option>

      </select></div>`;

      document.getElementById('fm-submit').onclick = async () => {

        const vai_tro_id = parseInt(document.getElementById('role-id').value);

        try { await apiJSON(`/users/${userId}/role`, { method: 'PUT', body: JSON.stringify({ vai_tro_id }) }); toast('Đã cập nhật vai trò'); bootstrap.Modal.getInstance(document.getElementById('form-modal'))?.hide(); loadUsers(); }

        catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      };

      new bootstrap.Modal(document.getElementById('form-modal')).show();

    }
    // Custom popup menu cho Users - render ra body
    let _userMenuEl = null;
    let _userMenuBtn = null;
    function showUserMenu(event, userId) {
      event.stopPropagation();
      const btn = event.currentTarget;
      if (_userMenuEl) {
        _userMenuEl.remove();
        _userMenuEl = null;
        const wasBtn = _userMenuBtn;
        _userMenuBtn = null;
        if (wasBtn === btn) return;
      }
      const u = (window._userReg || {})[userId];
      if (!u) return;
      _userMenuBtn = btn;
      const isActive = (u.trang_thai || '').toLowerCase().includes('hoat') || u.trang_thai === 'active';
      const username = u.ten_dang_nhap || '';
      const rect = btn.getBoundingClientRect();
      const menu = document.createElement('div');
      menu.id = '_userMenu';
      menu.style.cssText = 'position:fixed;z-index:9999;background:#fff;border:1px solid #dbe3ea;border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.12);min-width:190px;padding:4px 0;font-size:.82rem';
      const close = () => { menu.remove(); _userMenuEl = null; _userMenuBtn = null; };
      // Đặt lại mật khẩu
      const resetBtn = document.createElement('button');
      resetBtn.className = 'obj-popup-item';
      resetBtn.innerHTML = '<i class="bi bi-key"></i> Đặt lại mật khẩu';
      resetBtn.onclick = () => { close(); openResetPasswordModal(u.id, username); };
      // Khoá / Mở khoá
      const lockBtn = document.createElement('button');
      lockBtn.className = 'obj-popup-item';
      lockBtn.innerHTML = `<i class="bi ${isActive ? 'bi-lock' : 'bi-unlock'}"></i> ${isActive ? 'Khoá tài khoản' : 'Mở khoá'}`;
      lockBtn.onclick = () => { close(); toggleUserStatus(u.id, isActive); };
      // Divider
      const sep = document.createElement('div');
      sep.style.cssText = 'height:1px;background:#f1f5f9;margin:4px 0';
      // Xoá
      const delBtn = document.createElement('button');
      delBtn.className = 'obj-popup-item text-danger';
      delBtn.innerHTML = '<i class="bi bi-trash3"></i> Xoá tài khoản';
      delBtn.onclick = () => { close(); deleteUser(u.id, username); };
      menu.appendChild(resetBtn);
      menu.appendChild(lockBtn);
      menu.appendChild(sep);
      menu.appendChild(delBtn);
      let top = rect.bottom + 4;
      let left = rect.right - 190;
      if (left < 8) left = 8;
      menu.style.top = top + 'px';
      menu.style.left = left + 'px';
      document.body.appendChild(menu);
      _userMenuEl = menu;
      setTimeout(() => {
        document.addEventListener('click', function closeOnOut(e) {
          if (!menu.contains(e.target)) {
            menu.remove();
            _userMenuEl = null;
            _userMenuBtn = null;
            document.removeEventListener('click', closeOnOut);
          }
        });
      }, 10);
    }
