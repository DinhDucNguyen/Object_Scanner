    // ============================================================
    // Predictions
    // ============================================================
    function setPredFilter(status) {
      const sel = document.getElementById('pred-filter');
      if (sel) { sel.value = status; reloadPagedTable('predictions'); }
    }

    async function cleanupKnownClassPredictions() {
      confirmAction(
        'Dá»n prediction Ä‘ang chá» duyá»‡t cá»§a cÃ¡c class YOLO custom (10 class) vÃ  COCO (80 class)?\nPrediction cÃ³ object chÃ­nh thá»©c sáº½ Ä‘Æ°á»£c liÃªn káº¿t scan rá»“i xÃ³a khá»i hÃ ng chá»; prediction thiáº¿u object sáº½ Ä‘Æ°á»£c giá»¯ láº¡i.',
        async () => {
          try {
            const res = await apiJSON('/predictions/cleanup-known-classes', { method: 'DELETE' });
            const matched = res.matched ?? 0;
            const resolved = res.resolved ?? res.count ?? 0;
            const skipped = Array.isArray(res.skipped_missing_object) ? res.skipped_missing_object.length : 0;
            const skippedMsg = skipped ? `, giá»¯ láº¡i ${skipped} prediction thiáº¿u object` : '';
            toast(`ÄÃ£ xá»­ lÃ½ ${resolved}/${matched} prediction YOLO/COCO${skippedMsg}`, 'warning');
            loadPredictions();
          } catch (e) {
            toast('Lá»—i: ' + e.message, 'danger');
          }
        },
        'Dá»n dáº¹p YOLO/COCO'
      );
    }

    async function exportTrainingData() {
      const btn = document.getElementById('btn-export-training');
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Äang xuáº¥t...'; }
      try {
        const res = await fetch('/api/admin/predictions/export-training', {
          headers: { 'Authorization': 'Bearer ' + TOKEN }
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'yolo_training_data.jsonl';
        a.click();
        URL.revokeObjectURL(url);
        toast('Xuáº¥t training data thÃ nh cÃ´ng', 'success');
      } catch (e) {
        toast('Xuáº¥t tháº¥t báº¡i: ' + e.message, 'danger');
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-download me-1"></i>Export training data'; }
      }
    }

    async function exportTrainingGrouped() {
      const btn = document.getElementById('btn-export-grouped');
      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Äang xuáº¥t...'; }
      try {
        const res = await fetch('/api/admin/predictions/export-training-grouped', {
          headers: { 'Authorization': 'Bearer ' + TOKEN }
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'yolo_training_grouped.jsonl';
        a.click();
        URL.revokeObjectURL(url);
        toast('Xuáº¥t grouped data thÃ nh cÃ´ng', 'success');
      } catch (e) {
        toast('Xuáº¥t tháº¥t báº¡i: ' + e.message, 'danger');
      } finally {
        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-collection me-1"></i>Export grouped'; }
      }
    }

    async function loadTrainingStats() {
      try {
        const stats = await apiJSON('/stats');
        const approved = stats.da_duyet ?? 0;
        const pending  = stats.cho_duyet ?? 0;
        const rejected = stats.tu_choi ?? 0;
        document.getElementById('training-approved-count').textContent = approved;
        document.getElementById('training-pending-count').textContent  = pending;
        document.getElementById('training-rejected-count').textContent = rejected;
        const exportBtn = document.getElementById('btn-export-training');
        if (exportBtn) exportBtn.classList.toggle('disabled', approved === 0);
        const groupedBtn = document.getElementById('btn-export-grouped');
        if (groupedBtn) groupedBtn.classList.toggle('disabled', approved === 0);
      } catch (e) { console.warn('loadTrainingStats:', e.message); }
    }

    async function loadPredictions() {
      loadTrainingStats();
      const status = document.getElementById('pred-filter')?.value || 'cho_duyet';
      try {
        const search = document.getElementById('pred-search')?.value.trim() || '';
        let predUrl = `/predictions?trang_thai=${status}&limit=1000`;
        if (search) predUrl += `&search=${encodeURIComponent(search)}`;
        const data = await apiJSON(predUrl);
        const pageData = getPagedRows('predictions', data);
        renderTablePagination('predictions', pageData);
        const countEl = document.querySelector('#section-predictions .result-count');
        if (countEl) countEl.textContent = data.length ? `${data.length} káº¿t quáº£` : '';
        const isPending = (document.getElementById('pred-filter')?.value || '') === 'cho_duyet';
        document.getElementById('pred-body').innerHTML = data.length
          ? pageData.rows.map((p, idx) => `
          <tr>
            <td>${isPending ? `<input type="checkbox" class="form-check-input pred-check" data-id="${p.id}" onchange="updateBatchToolbar()">` : ''}</td>
            <td class="stt-cell">${pageData.start + idx + 1}</td>
            <td><span class="text-muted fw-semibold">${p.id}</span></td>
            <td>${p.scan_image_url ? `<img src="${p.scan_image_url}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer;" onclick="openPredModal(${p.id})" title="Xem chi tiáº¿t">` : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>
            <td><code class="cell-ellipsis code-cell" title="${escHtml(p.nhan_du_doan || '')}">${escHtml(p.nhan_du_doan || 'â€”')}</code></td>
            <td>${confBar(p.do_tin_cay)}</td>
            <td>${statusBadge(p.trang_thai)}</td>
            <td class="text-muted" style="font-size:.82rem;">${fmtDate(p.thoi_gian)}</td>
            <td>
              <div class="btn-actions">
                <button class="btn-act" onclick="openPredModal(${p.id})" title="Chi tiáº¿t"><i class="bi bi-eye"></i></button>
              </div>
            </td>
          </tr>`).join('')
          : emptyRow(9, 'bi-inbox', 'KhÃ´ng cÃ³ prediction nÃ o');
        document.getElementById('pred-check-all').checked = false;
        updateBatchToolbar();
      } catch (e) { toast('Lá»—i táº£i predictions: ' + e.message, 'danger'); }
    }

    function statusBadge(s) {
      const map = { cho_duyet: 'badge-pending', da_duyet: 'badge-approved', tu_choi: 'badge-rejected' };
      const labels = { cho_duyet: 'Chá» duyá»‡t', da_duyet: 'ÄÃ£ duyá»‡t', tu_choi: 'Tá»« chá»‘i' };
      return `<span class="badge ${map[s] || 'badge-neutral'}">${labels[s] || s}</span>`;
    }

    async function openPredModal(id) {
      const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('pred-modal'));
      document.getElementById('pm-id').textContent = id;
      document.getElementById('pm-body').innerHTML = `<div class="text-center py-4"><div class="spinner-border text-primary" style="width:1.5rem;height:1.5rem;"></div></div>`;
      document.getElementById('pm-footer').innerHTML = '';
      modal.show();
      try {
        const p = await apiJSON(`/predictions/${id}`);
        const vp = p.vocab_payload;
        const relatedImages = p.related_images || [];
        const rootPredictionId = p.du_doan_goc_id || p.id;
        const canEditRelatedImages = p.trang_thai === 'cho_duyet';
        const relatedImagesHtml = relatedImages.length ? `
      <div class="mb-3">
        <div class="d-flex align-items-center justify-content-between mb-2">
          <p class="fw-bold mb-0" style="font-size:.85rem;">
            <i class="bi bi-images text-primary me-1"></i>áº¢nh liÃªn quan
          </p>
          <span class="badge badge-neutral">${relatedImages.length} áº£nh</span>
        </div>
        <div class="d-flex flex-wrap gap-2">
          ${relatedImages.map(img => `
            <div style="width:92px;">
              <img src="${img.image_url}"
                   style="width:92px;height:92px;object-fit:cover;border-radius:8px;border:1px solid #dee2e6;cursor:zoom-in;"
                   alt="áº¢nh liÃªn quan"
                   onclick="zoomImage('${img.image_url}')"
                   title="Scan #${img.lich_su_quet_id || 'â€”'}">
              <div class="mt-1" style="font-size:.72rem;line-height:1.2;">
                <span class="badge ${img.vai_tro === 'chinh' ? 'badge-approved' : 'badge-neutral'}">
                  ${img.vai_tro === 'chinh' ? 'ChÃ­nh' : 'Bá»• sung'}
                </span>
                <div class="text-muted mt-1">Scan #${img.lich_su_quet_id || 'â€”'}</div>
                ${canEditRelatedImages ? `
                  <div class="d-flex gap-1 mt-1 flex-wrap">
                    ${img.vai_tro !== 'chinh' ? `<button class="btn-act del" style="width:28px;height:26px;" onclick="detachReviewImage(${img.prediction_id}, ${rootPredictionId})" title="Bá» khá»i nhÃ³m duyá»‡t"><i class="bi bi-trash3"></i></button>
                    <button class="btn-act" style="width:28px;height:26px;" onclick="openReviewImageReassign(${img.prediction_id}, '${p.nhan_du_doan || ''}', ${rootPredictionId})" title="Chuyá»ƒn sang object Ä‘Ã£ cÃ³"><i class="bi bi-arrow-left-right"></i></button>` : ''}
                    <button class="btn-act" style="width:28px;height:26px;color:#7c3aed;" onclick="openSplitToNewObject(${img.prediction_id}, ${rootPredictionId}, '${img.vai_tro}')" title="TÃ¡ch thÃ nh object má»›i"><i class="bi bi-scissors"></i></button>
                  </div>` : ''}
              </div>
            </div>
          `).join('')}
        </div>
      </div>` : '';
        const langFlags = { en: 'ðŸ‡¬ðŸ‡§', vi: 'ðŸ‡»ðŸ‡³', ja: 'ðŸ‡¯ðŸ‡µ', ko: 'ðŸ‡°ðŸ‡·', zh: 'ðŸ‡¨ðŸ‡³', fr: 'ðŸ‡«ðŸ‡·', de: 'ðŸ‡©ðŸ‡ª' };
        let html = `
      ${p.scan_image_url ? `
      <div class="text-center mb-3">
        <img src="${p.scan_image_url}" style="max-height:220px;max-width:100%;border-radius:8px;object-fit:contain;border:1px solid #dee2e6;cursor:zoom-in;" alt="áº¢nh quÃ©t" onclick="zoomImage('${p.scan_image_url}')" title="Nháº¥n Ä‘á»ƒ xem to">
      </div>` : ''}
      ${relatedImagesHtml}
      <div class="row g-2 mb-3">
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">NhÃ£n</div>
          <code style="font-size:.88rem;">${p.nhan_du_doan || 'â€”'}</code>
        </div></div>
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Äá»™ tin cáº­y</div>
          <div class="mt-1">${confBar(p.do_tin_cay)}</div>
        </div></div>
        <div class="col-4"><div class="p-2 rounded bg-light">
          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Tráº¡ng thÃ¡i</div>
          <div class="mt-1">${statusBadge(p.trang_thai)}</div>
        </div></div>
      </div>`;
        if (vp) {
          html += `<p class="fw-bold mb-2" style="font-size:.85rem;">
        <i class="bi bi-book text-primary me-1"></i>Vocab Payload â€” <code style="font-size:.82rem;">${vp.object_code}</code></p>`;
          (vp.translations || []).forEach(t => {
            const examplesHtml = (t.example_sentences || []).map((s, idx) => {
              const en = typeof s === 'object' ? (s.en || '') : s;
              const vi = typeof s === 'object' ? (s.vi || '') : '';
              return `<div class="p-2 mb-2 rounded border" style="background:#fff;font-size:.84rem;line-height:1.45;">
                <div class="d-flex gap-2">
                  <span class="badge badge-neutral" style="height:fit-content;">${idx + 1}</span>
                  <div style="min-width:0;flex:1;">
                    <div>${en || ''}</div>
                    ${vi ? `<div class="text-muted fst-italic mt-1">${vi}</div>` : ''}
                  </div>
                </div>
              </div>`;
            }).join('');
            html += `<div class="vocab-lang-block">
          <div class="d-flex align-items-center gap-2 mb-2">
            <span>${langFlags[t.lang_code] || 'ðŸŒ'}</span>
            <span class="badge badge-lang">${t.lang_code.toUpperCase()}</span>
            <strong>${t.word_name || ''}</strong>
            ${t.phonetic ? `<span class="text-muted fst-italic" style="font-size:.82rem;">${t.phonetic}</span>` : ''}
            ${t.part_of_speech ? `<span class="badge" style="background:#e9d5ff;color:#6b21a8;">${t.part_of_speech}</span>` : ''}
          </div>
          ${t.definition ? `<div class="mb-3" style="font-size:.85rem;line-height:1.5;"><div class="text-muted fw-semibold mb-1" style="font-size:.72rem;text-transform:uppercase;">Äá»‹nh nghÄ©a</div>${t.definition}</div>` : ''}
          ${examplesHtml ? `<div><div class="text-muted fw-semibold mb-2" style="font-size:.72rem;text-transform:uppercase;">VÃ­ dá»¥</div>${examplesHtml}</div>` : ''}
        </div>`;
          });
        }
        setModalBody('pm-body', html);
        if (p.trang_thai === 'cho_duyet') {
          document.getElementById('pm-footer').innerHTML = `
        <button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">ÄÃ³ng</button>
        <button class="btn btn-sm btn-danger ms-auto me-1" onclick="rejectPred(${id})">
          <i class="bi bi-x-lg me-1"></i>Tá»« chá»‘i
        </button>
        <button class="btn btn-sm btn-outline-primary me-1" onclick="showAliasForm(${id})">
          <i class="bi bi-link-45deg me-1"></i>GÃ¡n bÃ­ danh
        </button>
        <button class="btn btn-sm btn-warning me-1" onclick="showApproveForm(${id})">
          <i class="bi bi-pencil me-1"></i>Chá»‰nh & Duyá»‡t
        </button>
        <button class="btn btn-sm btn-success" onclick="approvePredQuick(${id})">
          <i class="bi bi-check-lg me-1"></i>Duyá»‡t ngay
        </button>`;
        } else {
          document.getElementById('pm-footer').innerHTML = `<button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">ÄÃ³ng</button>`;
        }
      } catch (e) {
        document.getElementById('pm-body').innerHTML = `<div class="alert alert-danger">Lá»—i táº£i dá»¯ liá»‡u: ${e.message}</div>`;
      }
    }

    async function approvePred(id, overrides) {
      try {
        await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify(overrides || {}) });
        toast('ÄÃ£ duyá»‡t prediction #' + id);
        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
        loadPredictions(); updatePendingBadge();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
    }

    function approvePredQuick(id) {
      confirmAction(`Duyá»‡t prediction #${id}?`, () => approvePred(id, {}), 'XÃ¡c nháº­n duyá»‡t');
    }

    async function detachReviewImage(predictionId, rootPredictionId) {
      confirmAction('Bá» áº£nh nÃ y khá»i nhÃ³m duyá»‡t? áº¢nh váº«n Ä‘Æ°á»£c giá»¯ trong lá»‹ch sá»­ quÃ©t.', async () => {
        try {
          await apiJSON(`/predictions/${predictionId}/detach-image`, { method: 'PATCH' });
          toast('ÄÃ£ bá» áº£nh khá»i nhÃ³m duyá»‡t', 'warning');
          openPredModal(rootPredictionId);
          loadPredictions();
          updatePendingBadge();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'Bá» áº£nh khá»i nhÃ³m');
    }


    let _splitPredictionId = null;
    let _splitRootPredictionId = null;
    let _splitVaiTro = null;

    function openSplitToNewObject(predictionId, rootPredictionId, vaiTro) {
      _splitPredictionId = predictionId;
      _splitRootPredictionId = rootPredictionId;
      _splitVaiTro = vaiTro || null;
      document.getElementById('sm-current-code').textContent = 'â€”';
      document.getElementById('sm-new-code').value = '';
      document.getElementById('sm-error').style.display = 'none';
      apiJSON(`/predictions/${rootPredictionId}`).then(p => {
        document.getElementById('sm-current-code').textContent = p.nhan_du_doan || 'â€”';
      }).catch(() => {});
      bootstrap.Modal.getOrCreateInstance(document.getElementById('split-modal')).show();
    }

    document.addEventListener('DOMContentLoaded', function() {
      document.getElementById('sm-confirm').addEventListener('click', async function() {
        const newCode = (document.getElementById('sm-new-code').value || '').trim().toLowerCase().replace(/\s+/g, '_');
        const errEl = document.getElementById('sm-error');
        if (!newCode) {
          errEl.textContent = 'Vui lÃ²ng nháº­p mÃ£ Ä‘á»‘i tÆ°á»£ng má»›i';
          errEl.style.display = '';
          return;
        }
        errEl.style.display = 'none';
        const btn = document.getElementById('sm-confirm');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Äang xá»­ lÃ½...';
        try {
          const res = await apiJSON(
            `/predictions/${_splitPredictionId}/split-to-new-object?new_object_code=${encodeURIComponent(newCode)}`,
            { method: 'PATCH' }
          );
          bootstrap.Modal.getInstance(document.getElementById('split-modal')).hide();
          const vocabMsg = res.vocab_generated
            ? ' Gemini Ä‘Ã£ sinh tá»« vá»±ng tá»± Ä‘á»™ng.'
            : ' (Tá»« vá»±ng chÆ°a sinh Ä‘Æ°á»£c, admin cáº§n nháº­p thá»§ cÃ´ng khi duyá»‡t)';
          toast(`ÄÃ£ tÃ¡ch thÃ nh prediction #${res.new_prediction_id} cho "${res.new_object_code}".${vocabMsg}`, 'success', 6000);
          if (_splitVaiTro === 'chinh') {
            bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
          } else {
            openPredModal(_splitRootPredictionId);
          }
          loadPredictions();
          updatePendingBadge();
        } catch (e) {
          errEl.textContent = 'Lá»—i: ' + e.message;
          errEl.style.display = '';
        } finally {
          btn.disabled = false;
          btn.innerHTML = '<i class="bi bi-scissors me-1"></i>TÃ¡ch';
        }
      });
    });

    async function showAliasForm(id) {
      const p = await apiJSON(`/predictions/${id}`).catch(() => null);
      const aliasCode = (p?.vocab_payload?.object_code || p?.nhan_du_doan || '').trim();
      let objects = [];
      try { objects = await apiJSON('/objects?limit=1000'); }
      catch (e) { toast('Lá»—i táº£i danh sÃ¡ch Ä‘á»‘i tÆ°á»£ng: ' + e.message, 'danger'); return; }

      const options = objects.map(o => {
        const aliases = (o.aliases || []).map(a => a.ma_bi_danh).join(', ');
        const aliasText = aliases ? ` | alias: ${aliases}` : '';
        const categoryText = o.category_name ? ` - ${o.category_name}` : '';
        return `<option value="${o.id}">${o.ma_doi_tuong}${categoryText}${aliasText}</option>`;
      }).join('');

      document.getElementById('pm-body').innerHTML = `
    <p class="text-muted mb-3" style="font-size:.82rem;">
      <i class="bi bi-info-circle me-1"></i>
      DÃ¹ng khi Gemini gá»i cÃ¹ng má»™t váº­t thá»ƒ báº±ng tÃªn khÃ¡c. Prediction sáº½ Ä‘Æ°á»£c duyá»‡t, áº£nh/lá»‹ch sá»­ ná»‘i vá» Ä‘á»‘i tÆ°á»£ng chÃ­nh, cÃ²n tÃªn nÃ y lÆ°u trong <code>BiDanhDoiTuong</code>.
    </p>
    <div class="mb-3">
      <label class="form-label fw-semibold" style="font-size:.8rem;">MÃ£ bÃ­ danh</label>
      <input id="alias-code" class="form-control form-control-sm" value="${aliasCode}" placeholder="vd: glasses">
    </div>
    <div class="mb-3">
      <label class="form-label fw-semibold" style="font-size:.8rem;">Äá»‘i tÆ°á»£ng chÃ­nh</label>
      <select id="alias-target" class="form-select form-select-sm">${options}</select>
    </div>
    <div class="row g-2">
      <div class="col-8">
        <label class="form-label fw-semibold" style="font-size:.8rem;">TÃªn hiá»ƒn thá»‹</label>
        <input id="alias-display" class="form-control form-control-sm" value="${aliasCode.replaceAll('_', ' ')}">
      </div>
      <div class="col-4">
        <label class="form-label fw-semibold" style="font-size:.8rem;">NgÃ´n ngá»¯</label>
        <input id="alias-lang" class="form-control form-control-sm" value="en">
      </div>
    </div>`;

      document.getElementById('pm-footer').innerHTML = `
    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})"><i class="bi bi-arrow-left me-1"></i>Quay láº¡i</button>
    <button class="btn btn-sm btn-primary ms-auto" onclick="confirmAlias(${id})">
      <i class="bi bi-link-45deg me-1"></i>GÃ¡n bÃ­ danh
    </button>`;
    }

    async function confirmAlias(id) {
      const target = Number(document.getElementById('alias-target')?.value || 0);
      const aliasCode = document.getElementById('alias-code')?.value.trim();
      const display = document.getElementById('alias-display')?.value.trim();
      const lang = document.getElementById('alias-lang')?.value.trim() || 'en';
      if (!target) { toast('Chá»n Ä‘á»‘i tÆ°á»£ng chÃ­nh', 'warning'); return; }
      if (!aliasCode) { toast('Nháº­p mÃ£ bÃ­ danh', 'warning'); return; }
      const btn = document.getElementById('pm-alias-submit');
      if (btn) btn.disabled = true;
      try {
        const result = await apiJSON(`/predictions/${id}/alias`, {
          method: 'POST',
          body: JSON.stringify({
            doi_tuong_id: target,
            ma_bi_danh: aliasCode,
            ten_hien_thi: display || null,
            ngon_ngu: lang,
          }),
        });
        toast(result.message || 'ÄÃ£ gÃ¡n bÃ­ danh');
        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
        loadPredictions(); loadObjects(); updatePendingBadge();
      } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      finally { if (btn) btn.disabled = false; }
    }

    function normalizeApproveExamples(rawExamples) {
      const source = Array.isArray(rawExamples) ? rawExamples : [];
      return source.slice(0, 3).map(item => {
        if (item && typeof item === 'object') {
          return {
            en: item.en || item.cau_vi_du || '',
            vi: item.vi || item.dich_nghia || '',
          };
        }
        const raw = String(item || '');
        const parts = raw.split('|');
        return {
          en: (parts.shift() || '').trim(),
          vi: parts.join('|').trim(),
        };
      });
    }

    function renderApproveExampleEditors(rawExamples) {
      const items = normalizeApproveExamples(rawExamples);
      while (items.length < 3) items.push({ en: '', vi: '' });
      return items.map((ex, idx) => `
        <div class="approve-example-row">
          <div class="approve-example-index">${idx + 1}</div>
          <div>
            <label class="form-label mb-1">English</label>
            <textarea id="ov-ex-en-${idx}" class="form-control form-control-sm approve-ex-en" rows="2"
              placeholder="I need an eraser.">${escHtml(ex.en || '')}</textarea>
          </div>
          <div>
            <label class="form-label mb-1">Vietnamese</label>
            <textarea id="ov-ex-vi-${idx}" class="form-control form-control-sm approve-ex-vi" rows="2"
              placeholder="TÃ´i cáº§n má»™t cá»¥c táº©y.">${escHtml(ex.vi || '')}</textarea>
          </div>
        </div>`).join('');
    }

    function collectApproveExamples() {
      return Array.from(document.querySelectorAll('.approve-example-row'))
        .map(row => {
          const en = row.querySelector('.approve-ex-en')?.value.trim() || '';
          const vi = row.querySelector('.approve-ex-vi')?.value.trim() || '';
          if (!en && !vi) return '';
          return en && vi ? `${en} | ${vi}` : (en || vi);
        })
        .filter(Boolean);
    }

    async function showApproveForm(id) {
      const p = await apiJSON(`/predictions/${id}`).catch(() => null);
      const vp = p?.vocab_payload;
      const firstTrans = vp?.translations?.[0] || {};
      const exampleEditors = renderApproveExampleEditors(firstTrans.example_sentences || []);

      document.getElementById('pm-body').innerHTML = `
    <div class="approve-note">
      <i class="bi bi-pencil-square"></i>
      <span>Chá»‰nh sá»­a trÆ°á»›c khi duyá»‡t. <strong>Äá»ƒ trá»‘ng = giá»¯ nguyÃªn giÃ¡ trá»‹ Gemini.</strong></span>
    </div>

    <div class="approve-card">
      <p class="approve-card-title">ThÃ´ng tin cÆ¡ báº£n</p>
      <div class="approve-basic-grid mb-3">
        <div>
          <label class="form-label mb-1">Tá»« vá»±ng</label>
          <input id="ov-word" class="form-control form-control-sm" placeholder="vd: Eraser" value="${escHtml(firstTrans.word_name || '')}">
        </div>
        <div>
          <label class="form-label mb-1">PhiÃªn Ã¢m (IPA)</label>
          <input id="ov-phonetic" class="form-control form-control-sm" placeholder="vd: /ÉªËˆreÉª.zÉ™r/" value="${escHtml(firstTrans.phonetic || '')}">
        </div>
        <div>
          <label class="form-label mb-1">Loáº¡i tá»«</label>
          <input id="ov-pos" class="form-control form-control-sm" placeholder="n / v / adj" value="${escHtml(firstTrans.part_of_speech || '')}">
        </div>
      </div>

      <div>
        <label class="form-label mb-1">Äá»‹nh nghÄ©a</label>
        <textarea id="ov-def" class="form-control form-control-sm" style="resize:none;line-height:1.55;" placeholder="Nháº­p Ä‘á»‹nh nghÄ©a...">${escHtml(firstTrans.definition || '')}</textarea>
      </div>
    </div>

    <div class="approve-card">
      <p class="approve-card-title">VÃ­ dá»¥</p>
      <div class="approve-examples-hint">Má»—i dÃ²ng lÃ  má»™t vÃ­ dá»¥. CÃ³ thá»ƒ chá»‰ nháº­p English náº¿u chÆ°a cÃ³ nghÄ©a tiáº¿ng Viá»‡t.</div>
      <div class="approve-examples">${exampleEditors}</div>
    </div>`;

      // Auto-resize textareas theo ná»™i dung sáºµn cÃ³.
      ['ov-def', ...Array.from(document.querySelectorAll('.approve-example-row textarea')).map(ta => ta.id)].forEach(tid => {
        const ta = document.getElementById(tid);
        if (!ta) return;
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
        ta.addEventListener('input', () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; });
      });

      document.getElementById('pm-footer').innerHTML = `
    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})">
      <i class="bi bi-arrow-left me-1"></i>Quay láº¡i
    </button>
    <button class="btn btn-sm btn-success ms-auto px-3" onclick="confirmApproveWithEdits(${id})">
      <i class="bi bi-check-lg me-1"></i>XÃ¡c nháº­n duyá»‡t
    </button>`;
    }

    async function confirmApproveWithEdits(id) {
      const word = document.getElementById('ov-word')?.value.trim();
      const phonetic = document.getElementById('ov-phonetic')?.value.trim();
      const pos = document.getElementById('ov-pos')?.value.trim();
      const def = document.getElementById('ov-def')?.value.trim();
      const examples = collectApproveExamples();

      const overrides = {};
      if (word) overrides.override_word_name = word;
      if (phonetic) overrides.override_phonetic = phonetic;
      if (pos) overrides.override_part_of_speech = pos;
      if (def) overrides.override_definition = def;
      if (examples.length) overrides.override_example_sentences = examples;

      const btn = document.getElementById('pm-approve-submit');
      if (btn) btn.disabled = true;
      try {
        await approvePred(id, overrides);
      } finally {
        if (btn) btn.disabled = false;
      }
    }

    async function rejectPred(id) {
      confirmAction('Tá»« chá»‘i prediction nÃ y?', async () => {
        try {
          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });
          toast('ÄÃ£ tá»« chá»‘i prediction #' + id, 'warning');
          bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();
          loadPredictions(); updatePendingBadge();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      });
    }

    async function approvePredDash(id) {
      confirmAction(`Duyá»‡t prediction #${id}?`, async () => {
        try {
          await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) });
          toast('ÄÃ£ duyá»‡t prediction #' + id);
          loadDashboard();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      }, 'XÃ¡c nháº­n duyá»‡t');
    }

    async function rejectPredDash(id) {
      confirmAction(`Tá»« chá»‘i prediction #${id}?`, async () => {
        try {
          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });
          toast('ÄÃ£ tá»« chá»‘i prediction #' + id, 'warning');
          loadDashboard();
        } catch (e) { toast('Lá»—i: ' + e.message, 'danger'); }
      });
    }

    async function updatePendingBadge() {
      try {
        const stats = await apiJSON('/dashboard');
        const badge = document.getElementById('badge-predictions');
        if (stats.pending_predictions > 0) { badge.textContent = stats.pending_predictions; badge.style.display = ''; }
        else { badge.style.display = 'none'; }
      } catch (_) { }
    }

