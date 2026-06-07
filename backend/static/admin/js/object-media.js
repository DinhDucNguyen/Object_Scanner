    // ============================================================
    // Object Media Management
    // ============================================================
    async function openObjMediaModal(objectCode) {
      document.getElementById('fm-title').textContent = `Ảnh đối tượng — ${objectCode}`;
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-body').innerHTML = `<div class="text-center py-3"><div class="spinner-border text-primary" style="width:1.4rem;height:1.4rem;"></div></div>`;
      const modal = new bootstrap.Modal(document.getElementById('form-modal'));
      modal.show();

      async function renderMediaModal() {
        try {
          const media = await apiJSON(`/objects/${objectCode}/media`);
          let html = `
        <div class="mb-3">
          <label class="form-label fw-semibold">Ảnh hiện tại</label>
          ${media.length === 0
              ? '<p class="text-muted" style="font-size:.83rem;">Chưa có ảnh nào.</p>'
              : `<div class="d-flex flex-wrap gap-2">${media.map(m => `
                <div style="width:80px;">
                  <img src="${m.url}" onclick="zoomImage('${m.url}')" style="width:80px;height:80px;object-fit:cover;border-radius:6px;border:2px solid ${m.is_primary ? 'var(--primary)' : 'var(--border)'};cursor:zoom-in;" />
                  <div class="d-flex gap-1 mt-1 justify-content-center">
                    ${m.is_primary
                      ? `<button class="btn-act" style="width:36px;color:#f59e0b;" onclick="unsetMediaPrimary(${m.id}, '${objectCode}')" title="Bỏ ảnh chính"><i class="bi bi-star-fill"></i></button>`
                      : `<button class="btn-act" style="width:36px;" onclick="setMediaPrimary(${m.id}, '${objectCode}')" title="Đặt làm ảnh chính"><i class="bi bi-star"></i></button>`
                    }
                    <button class="btn-act del" style="width:36px;" onclick="deleteMedia(${m.id}, '${objectCode}')" title="Xoá"><i class="bi bi-trash3"></i></button>
                  </div>
                </div>`).join('')}</div>`}
        </div>
        <hr/>
        <label class="form-label fw-semibold">Thêm ảnh mới</label>
        <div class="mb-2">
          <label class="form-label">URL ảnh</label>
          <input id="media-url" class="form-control form-control-sm" placeholder="https://..." />
        </div>
        <div class="mb-2">
          <label class="form-label">Hoặc upload file</label>
          <input id="media-file" type="file" accept="image/*" class="form-control form-control-sm" />
        </div>
        <div class="form-check mb-2">
          <input class="form-check-input" type="checkbox" id="media-primary" checked />
          <label class="form-check-label" style="font-size:.83rem;">Đặt làm ảnh chính</label>
        </div>
        <button class="btn btn-sm btn-primary w-100" onclick="uploadObjectMedia('${objectCode}')">
          <i class="bi bi-upload me-1"></i>Lưu ảnh
        </button>`;
          document.getElementById('fm-body').innerHTML = html;
        } catch (e) {
          document.getElementById('fm-body').innerHTML = `<div class="alert alert-danger">Lỗi: ${e.message}</div>`;
        }
      }
      window._currentMediaObjectCode = objectCode;
      window._renderMediaModal = renderMediaModal;
      await renderMediaModal();
    }

    async function uploadObjectMedia(objectCode) {
      const urlInput = document.getElementById('media-url')?.value.trim();
      const fileInput = document.getElementById('media-file');
      const isPrimary = document.getElementById('media-primary')?.checked ?? true;

      const formData = new FormData();
      formData.append('is_primary', isPrimary);
      if (fileInput?.files?.[0]) {
        formData.append('image', fileInput.files[0]);
      } else if (urlInput) {
        formData.append('image_url', urlInput);
      } else {
        toast('Nhập URL hoặc chọn file ảnh', 'warning'); return;
      }

      const btn = document.querySelector('#form-modal .btn-primary');
      if (btn) { btn.disabled = true; btn.textContent = 'Đang tải lên...'; }
      try {
        await fetch(`${API}/objects/${objectCode}/media`, {
          method: 'POST',
          headers: { 'Authorization': 'Bearer ' + TOKEN },
          body: formData,
        }).then(async r => { if (!r.ok) throw new Error(await r.text()); return r.json(); });
        toast('Đã thêm ảnh');
        if (window._renderMediaModal) await window._renderMediaModal();
        loadObjects();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
      finally { if (btn) { btn.disabled = false; btn.textContent = 'Lưu thay đổi'; } }
    }

    async function setMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/primary`, { method: 'POST' });
        toast('Đã đặt ảnh chính');
        if (window._renderMediaModal) await window._renderMediaModal();
        loadObjects();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

    async function deleteMedia(mediaId, objectCode) {
      confirmAction(
        'Xoá ảnh này khỏi đối tượng?',
        async () => {
          try {
            await apiJSON(`/objects/media/${mediaId}`, { method: 'DELETE' });
            toast('Đã xoá ảnh', 'warning');
            if (window._renderMediaModal) await window._renderMediaModal();
            loadObjects();
          } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
        },
        'Xoá ảnh'
      );
    }

    async function unsetMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/unset-primary`, { method: 'POST' });
        toast('Đã bỏ ảnh chính');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }
    }

