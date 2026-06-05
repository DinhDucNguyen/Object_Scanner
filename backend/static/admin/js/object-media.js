    // ============================================================
    // Object Media Management
    // ============================================================
    async function openObjMediaModal(objectCode) {
      document.getElementById('fm-title').textContent = `áº¢nh Ä‘á»‘i tÆ°á»£ng â€” ${objectCode}`;
      document.getElementById('fm-submit').style.display = 'none';
      document.getElementById('fm-body').innerHTML = `<div class="text-center py-3"><div class="spinner-border text-primary" style="width:1.4rem;height:1.4rem;"></div></div>`;
      const modal = new bootstrap.Modal(document.getElementById('form-modal'));
      modal.show();

      async function renderMediaModal() {
        try {
          const media = await apiJSON(`/objects/${objectCode}/media`);
          let html = `
        <div class="mb-3">
          <label class="form-label fw-semibold">áº¢nh hiá»‡n táº¡i</label>
          ${media.length === 0
              ? '<p class="text-muted" style="font-size:.83rem;">ChÆ°a cÃ³ áº£nh nÃ o.</p>'
              : `<div class="d-flex flex-wrap gap-2">${media.map(m => `
                <div style="width:80px;">
                  <img src="${m.url}" onclick="zoomImage('${m.url}')" style="width:80px;height:80px;object-fit:cover;border-radius:6px;border:2px solid ${m.is_primary ? 'var(--primary)' : 'var(--border)'};cursor:zoom-in;" />
                  <div class="d-flex gap-1 mt-1 justify-content-center">
                    ${m.is_primary
                      ? `<button class="btn-act" style="width:36px;color:#f59e0b;" onclick="unsetMediaPrimary(${m.id}, '${objectCode}')" title="Bá» áº£nh chÃ­nh"><i class="bi bi-star-fill"></i></button>`
                      : `<button class="btn-act" style="width:36px;" onclick="setMediaPrimary(${m.id}, '${objectCode}')" title="Äáº·t lÃ m áº£nh chÃ­nh"><i class="bi bi-star"></i></button>`
                    }
                    <button class="btn-act del" style="width:36px;" onclick="deleteMedia(${m.id}, '${objectCode}')" title="XoÃ¡"><i class="bi bi-trash3"></i></button>
                  </div>
                </div>`).join('')}</div>`}
        </div>
        <hr/>
        <label class="form-label fw-semibold">ThÃªm áº£nh má»›i</label>
        <div class="mb-2">
          <label class="form-label">URL áº£nh</label>
          <input id="media-url" class="form-control form-control-sm" placeholder="https://..." />
        </div>
        <div class="mb-2">
          <label class="form-label">Hoáº·c upload file</label>
          <input id="media-file" type="file" accept="image/*" class="form-control form-control-sm" />
        </div>
        <div class="form-check mb-2">
          <input class="form-check-input" type="checkbox" id="media-primary" checked />
          <label class="form-check-label" style="font-size:.83rem;">Äáº·t lÃ m áº£nh chÃ­nh</label>
        </div>
        <button class="btn btn-sm btn-primary w-100" onclick="uploadObjectMedia('${objectCode}')">
          <i class="bi bi-upload me-1"></i>LÆ°u áº£nh
        </button>`;
          document.getElementById('fm-body').innerHTML = html;
        } catch (e) {
          document.getElementById('fm-body').innerHTML = `<div class="alert alert-danger">Lá»—i: ${e.message}</div>`;
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
        toast('Nháº­p URL hoáº·c chá»n file áº£nh', 'warning'); return;
      }

      const btn = document.querySelector('#form-modal .btn-primary');
      if (btn) { btn.disabled = true; btn.textContent = 'Äang táº£i lÃªn...'; }
      try {
        await fetch(`${API}/objects/${objectCode}/media`, {
          method: 'POST',
          headers: { 'Authorization': 'Bearer ' + TOKEN },
          body: formData,
        }).then(async r => { if (!r.ok) throw new Error(await r.text()); return r.json(); });
        toast('ÄÃ£ thÃªm áº£nh');
        if (window._renderMediaModal) await window._renderMediaModal();
        loadObjects();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      finally { if (btn) { btn.disabled = false; btn.textContent = 'LÆ°u thay Ä‘á»•i'; } }
    }

    async function setMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/primary`, { method: 'POST' });
        toast('ÄÃ£ Ä‘áº·t áº£nh chÃ­nh');
        if (window._renderMediaModal) await window._renderMediaModal();
        loadObjects();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
    }

    async function deleteMedia(mediaId, objectCode) {
      confirmAction(
        'XoÃ¡ áº£nh nÃ y khá»i Ä‘á»‘i tÆ°á»£ng?',
        async () => {
          try {
            await apiJSON(`/objects/media/${mediaId}`, { method: 'DELETE' });
            toast('ÄÃ£ xoÃ¡ áº£nh', 'warning');
            if (window._renderMediaModal) await window._renderMediaModal();
            loadObjects();
          } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
        },
        'XoÃ¡ áº£nh'
      );
    }

    async function unsetMediaPrimary(mediaId, objectCode) {
      try {
        await apiJSON(`/objects/media/${mediaId}/unset-primary`, { method: 'POST' });
        toast('ÄÃ£ bá» áº£nh chÃ­nh');
        if (window._renderMediaModal) await window._renderMediaModal();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
    }

