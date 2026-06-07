    // ============================================================

    // Predictions

    // ============================================================

    function setPredFilter(status) {

      const sel = document.getElementById('pred-filter');

      if (sel) { sel.value = status; reloadPagedTable('predictions'); }

    }



    async function cleanupKnownClassPredictions() {

      confirmAction(

        'Dọn prediction đang chờ duyệt của các class YOLO custom (10 class) và COCO (80 class)?\nPrediction có object chính thức sẽ được liên kết scan rồi xóa khỏi hàng chờ; prediction thiếu object sẽ được giữ lại.',

        async () => {

          try {

            const res = await apiJSON('/predictions/cleanup-known-classes', { method: 'DELETE' });

            const matched = res.matched ?? 0;

            const resolved = res.resolved ?? res.count ?? 0;

            const skipped = Array.isArray(res.skipped_missing_object) ? res.skipped_missing_object.length : 0;

            const skippedMsg = skipped ? `, giữ lại ${skipped} prediction thiếu object` : '';

            toast(`Đã xử lý ${resolved}/${matched} prediction YOLO/COCO${skippedMsg}`, 'warning');

            loadPredictions();

          } catch (e) {

            toast('Lỗi: ' + e.message, 'danger');

          }

        },

        'Dọn dẹp YOLO/COCO'

      );

    }



    async function exportTrainingData() {

      const btn = document.getElementById('btn-export-training');

      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Đang xuất...'; }

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

        toast('Xuất training data thành công', 'success');

      } catch (e) {

        toast('Xuất thất bại: ' + e.message, 'danger');

      } finally {

        if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-download me-1"></i>Export training data'; }

      }

    }



    async function exportTrainingGrouped() {

      const btn = document.getElementById('btn-export-grouped');

      if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Đang xuất...'; }

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

        toast('Xuất grouped data thành công', 'success');

      } catch (e) {

        toast('Xuất thất bại: ' + e.message, 'danger');

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

        if (countEl) countEl.textContent = data.length ? `${data.length} kết quả` : '';

        const isPending = (document.getElementById('pred-filter')?.value || '') === 'cho_duyet';

        document.getElementById('pred-body').innerHTML = data.length

          ? pageData.rows.map((p, idx) => `

          <tr>

            <td>${isPending ? `<input type="checkbox" class="form-check-input pred-check" data-id="${p.id}" onchange="updateBatchToolbar()">` : ''}</td>

            <td class="stt-cell">${pageData.start + idx + 1}</td>

            <td><span class="text-muted fw-semibold">${p.id}</span></td>

            <td>${p.scan_image_url ? `<img src="${p.scan_image_url}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer;" onclick="openPredModal(${p.id})" title="Xem chi tiết">` : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>

            <td><code class="cell-ellipsis code-cell" title="${escHtml(p.nhan_du_doan || '')}">${escHtml(p.nhan_du_doan || '—')}</code></td>

            <td>${confBar(p.do_tin_cay)}</td>

            <td>${statusBadge(p.trang_thai)}</td>

            <td class="text-muted" style="font-size:.82rem;">${fmtDate(p.thoi_gian)}</td>

            <td>

              <div class="btn-actions">

                <button class="btn-act" onclick="openPredModal(${p.id})" title="Chi tiết"><i class="bi bi-eye"></i></button>

              </div>

            </td>

          </tr>`).join('')

          : emptyRow(9, 'bi-inbox', 'Không có prediction nào');

        document.getElementById('pred-check-all').checked = false;

        updateBatchToolbar();

      } catch (e) { toast('Lỗi tải predictions: ' + e.message, 'danger'); }

    }



    function statusBadge(s) {

      const map = { cho_duyet: 'badge-pending', da_duyet: 'badge-approved', tu_choi: 'badge-rejected' };

      const labels = { cho_duyet: 'Chờ duyệt', da_duyet: 'Đã duyệt', tu_choi: 'Từ chối' };

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

            <i class="bi bi-images text-primary me-1"></i>Ảnh liên quan

          </p>

          <span class="badge badge-neutral">${relatedImages.length} ảnh</span>

        </div>

        <div class="d-flex flex-wrap gap-2">

          ${relatedImages.map(img => `

            <div style="width:92px;">

              <img src="${img.image_url}"

                   style="width:92px;height:92px;object-fit:cover;border-radius:8px;border:1px solid #dee2e6;cursor:zoom-in;"

                   alt="Ảnh liên quan"

                   onclick="zoomImage('${img.image_url}')"

                   title="Scan #${img.lich_su_quet_id || '—'}">

              <div class="mt-1" style="font-size:.72rem;line-height:1.2;">

                <span class="badge ${img.vai_tro === 'chinh' ? 'badge-approved' : 'badge-neutral'}">

                  ${img.vai_tro === 'chinh' ? 'Chính' : 'Bổ sung'}

                </span>

                <div class="text-muted mt-1">Scan #${img.lich_su_quet_id || '—'}</div>

                ${canEditRelatedImages ? `

                  <div class="d-flex gap-1 mt-1 flex-wrap">

                    ${img.vai_tro !== 'chinh' ? `<button class="btn-act del" style="width:28px;height:26px;" onclick="detachReviewImage(${img.prediction_id}, ${rootPredictionId})" title="Bỏ khỏi nhóm duyệt"><i class="bi bi-trash3"></i></button>

                    <button class="btn-act" style="width:28px;height:26px;" onclick="openReviewImageReassign(${img.prediction_id}, '${p.nhan_du_doan || ''}', ${rootPredictionId})" title="Chuyển sang object đã có"><i class="bi bi-arrow-left-right"></i></button>` : ''}

                    <button class="btn-act" style="width:28px;height:26px;color:#7c3aed;" onclick="openSplitToNewObject(${img.prediction_id}, ${rootPredictionId}, '${img.vai_tro}')" title="Tách thành object mới"><i class="bi bi-scissors"></i></button>

                  </div>` : ''}

              </div>

            </div>

          `).join('')}

        </div>

      </div>` : '';

        const langFlags = { en: '🇬🇧', vi: '🇻🇳', ja: '🇯🇵', ko: '🇰🇷', zh: '🇨🇳', fr: '🇫🇷', de: '🇩🇪' };

        let html = `

      ${p.scan_image_url ? `

      <div class="text-center mb-3">

        <img src="${p.scan_image_url}" style="max-height:220px;max-width:100%;border-radius:8px;object-fit:contain;border:1px solid #dee2e6;cursor:zoom-in;" alt="Ảnh quét" onclick="zoomImage('${p.scan_image_url}')" title="Nhấn để xem to">

      </div>` : ''}

      ${relatedImagesHtml}

      <div class="row g-2 mb-3">

        <div class="col-4"><div class="p-2 rounded bg-light">

          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Nhãn</div>

          <code style="font-size:.88rem;">${p.nhan_du_doan || '—'}</code>

        </div></div>

        <div class="col-4"><div class="p-2 rounded bg-light">

          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Độ tin cậy</div>

          <div class="mt-1">${confBar(p.do_tin_cay)}</div>

        </div></div>

        <div class="col-4"><div class="p-2 rounded bg-light">

          <div class="text-muted" style="font-size:.7rem;font-weight:600;text-transform:uppercase;">Trạng thái</div>

          <div class="mt-1">${statusBadge(p.trang_thai)}</div>

        </div></div>

      </div>`;

        if (vp) {

          html += `<p class="fw-bold mb-2" style="font-size:.85rem;">

        <i class="bi bi-book text-primary me-1"></i>Vocab Payload — <code style="font-size:.82rem;">${vp.object_code}</code></p>`;

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

            <span>${langFlags[t.lang_code] || '🌐'}</span>

            <span class="badge badge-lang">${t.lang_code.toUpperCase()}</span>

            <strong>${t.word_name || ''}</strong>

            ${t.phonetic ? `<span class="text-muted fst-italic" style="font-size:.82rem;">${t.phonetic}</span>` : ''}

            ${t.part_of_speech ? `<span class="badge" style="background:#e9d5ff;color:#6b21a8;">${t.part_of_speech}</span>` : ''}

          </div>

          ${t.definition ? `<div class="mb-3" style="font-size:.85rem;line-height:1.5;"><div class="text-muted fw-semibold mb-1" style="font-size:.72rem;text-transform:uppercase;">Định nghĩa</div>${t.definition}</div>` : ''}

          ${examplesHtml ? `<div><div class="text-muted fw-semibold mb-2" style="font-size:.72rem;text-transform:uppercase;">Ví dụ</div>${examplesHtml}</div>` : ''}

        </div>`;

          });

        }

        setModalBody('pm-body', html);

        if (p.trang_thai === 'cho_duyet') {

          document.getElementById('pm-footer').innerHTML = `

        <button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">Đóng</button>

        <button class="btn btn-sm btn-danger ms-auto me-1" onclick="rejectPred(${id})">

          <i class="bi bi-x-lg me-1"></i>Từ chối

        </button>

        <button class="btn btn-sm btn-outline-primary me-1" onclick="showAliasForm(${id})">

          <i class="bi bi-link-45deg me-1"></i>Gán bí danh

        </button>

        <button class="btn btn-sm btn-warning me-1" onclick="showApproveForm(${id})">

          <i class="bi bi-pencil me-1"></i>Chỉnh & Duyệt

        </button>

        <button class="btn btn-sm btn-success" onclick="approvePredQuick(${id})">

          <i class="bi bi-check-lg me-1"></i>Duyệt ngay

        </button>`;

        } else {

          document.getElementById('pm-footer').innerHTML = `<button class="btn btn-sm btn-outline-secondary" data-bs-dismiss="modal">Đóng</button>`;

        }

      } catch (e) {

        document.getElementById('pm-body').innerHTML = `<div class="alert alert-danger">Lỗi tải dữ liệu: ${e.message}</div>`;

      }

    }



    async function approvePred(id, overrides) {

      try {

        await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify(overrides || {}) });

        toast('Đã duyệt prediction #' + id);

        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();

        loadPredictions(); updatePendingBadge();

      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

    }



    function approvePredQuick(id) {

      confirmAction(`Duyệt prediction #${id}?`, () => approvePred(id, {}), 'Xác nhận duyệt');

    }



    async function detachReviewImage(predictionId, rootPredictionId) {

      confirmAction('Bỏ ảnh này khỏi nhóm duyệt? Ảnh vẫn được giữ trong lịch sử quét.', async () => {

        try {

          await apiJSON(`/predictions/${predictionId}/detach-image`, { method: 'PATCH' });

          toast('Đã bỏ ảnh khỏi nhóm duyệt', 'warning');

          openPredModal(rootPredictionId);

          loadPredictions();

          updatePendingBadge();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      }, 'Bỏ ảnh khỏi nhóm');

    }





    let _splitPredictionId = null;

    let _splitRootPredictionId = null;

    let _splitVaiTro = null;



    function openSplitToNewObject(predictionId, rootPredictionId, vaiTro) {

      _splitPredictionId = predictionId;

      _splitRootPredictionId = rootPredictionId;

      _splitVaiTro = vaiTro || null;

      document.getElementById('sm-current-code').textContent = '—';

      document.getElementById('sm-new-code').value = '';

      document.getElementById('sm-error').style.display = 'none';

      apiJSON(`/predictions/${rootPredictionId}`).then(p => {

        document.getElementById('sm-current-code').textContent = p.nhan_du_doan || '—';

      }).catch(() => {});

      bootstrap.Modal.getOrCreateInstance(document.getElementById('split-modal')).show();

    }



    document.addEventListener('DOMContentLoaded', function() {

      document.getElementById('sm-confirm').addEventListener('click', async function() {

        const newCode = (document.getElementById('sm-new-code').value || '').trim().toLowerCase().replace(/\s+/g, '_');

        const errEl = document.getElementById('sm-error');

        if (!newCode) {

          errEl.textContent = 'Vui lòng nhập mã đối tượng mới';

          errEl.style.display = '';

          return;

        }

        errEl.style.display = 'none';

        const btn = document.getElementById('sm-confirm');

        btn.disabled = true;

        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang xử lý...';

        try {

          const res = await apiJSON(

            `/predictions/${_splitPredictionId}/split-to-new-object?new_object_code=${encodeURIComponent(newCode)}`,

            { method: 'PATCH' }

          );

          bootstrap.Modal.getInstance(document.getElementById('split-modal')).hide();

          const vocabMsg = res.vocab_generated

            ? ' Gemini đã sinh từ vựng tự động.'

            : ' (Từ vựng chưa sinh được, admin cần nhập thủ công khi duyệt)';

          toast(`Đã tách thành prediction #${res.new_prediction_id} cho "${res.new_object_code}".${vocabMsg}`, 'success', 6000);

          if (_splitVaiTro === 'chinh') {

            bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();

          } else {

            openPredModal(_splitRootPredictionId);

          }

          loadPredictions();

          updatePendingBadge();

        } catch (e) {

          errEl.textContent = 'Lỗi: ' + e.message;

          errEl.style.display = '';

        } finally {

          btn.disabled = false;

          btn.innerHTML = '<i class="bi bi-scissors me-1"></i>Tách';

        }

      });

    });



    async function showAliasForm(id) {

      const p = await apiJSON(`/predictions/${id}`).catch(() => null);

      const aliasCode = (p?.vocab_payload?.object_code || p?.nhan_du_doan || '').trim();

      let objects = [];

      try { objects = await apiJSON('/objects?limit=1000'); }
      catch (e) { toast('Lỗi tải danh sách đối tượng: ' + e.message, 'danger'); return; }



      const options = objects.map(o => {

        const aliases = (o.aliases || []).map(a => a.ma_bi_danh).join(', ');

        const aliasText = aliases ? ` | alias: ${aliases}` : '';

        const categoryText = o.category_name ? ` - ${o.category_name}` : '';

        return `<option value="${o.id}">${o.ma_doi_tuong}${categoryText}${aliasText}</option>`;

      }).join('');



      document.getElementById('pm-body').innerHTML = `

    <p class="text-muted mb-3" style="font-size:.82rem;">

      <i class="bi bi-info-circle me-1"></i>

      Dùng khi Gemini gọi cùng một vật thể bằng tên khác. Prediction sẽ được duyệt, ảnh/lịch sử nối về đối tượng chính, còn tên này lưu trong <code>BiDanhDoiTuong</code>.

    </p>

    <div class="mb-3">

      <label class="form-label fw-semibold" style="font-size:.8rem;">Mã bí danh</label>

      <input id="alias-code" class="form-control form-control-sm" value="${aliasCode}" placeholder="vd: glasses">

    </div>

    <div class="mb-3">

      <label class="form-label fw-semibold" style="font-size:.8rem;">Đối tượng chính</label>

      <select id="alias-target" class="form-select form-select-sm">${options}</select>

    </div>

    <div class="row g-2">

      <div class="col-8">

        <label class="form-label fw-semibold" style="font-size:.8rem;">Tên hiển thị</label>

        <input id="alias-display" class="form-control form-control-sm" value="${aliasCode.replaceAll('_', ' ')}">

      </div>

      <div class="col-4">

        <label class="form-label fw-semibold" style="font-size:.8rem;">Ngôn ngữ</label>

        <input id="alias-lang" class="form-control form-control-sm" value="en">

      </div>

    </div>`;



      document.getElementById('pm-footer').innerHTML = `

    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})"><i class="bi bi-arrow-left me-1"></i>Quay lại</button>

    <button class="btn btn-sm btn-primary ms-auto" onclick="confirmAlias(${id})">

      <i class="bi bi-link-45deg me-1"></i>Gán bí danh

    </button>`;

    }



    async function confirmAlias(id) {

      const target = Number(document.getElementById('alias-target')?.value || 0);

      const aliasCode = document.getElementById('alias-code')?.value.trim();

      const display = document.getElementById('alias-display')?.value.trim();

      const lang = document.getElementById('alias-lang')?.value.trim() || 'en';

      if (!target) { toast('Chọn đối tượng chính', 'warning'); return; }

      if (!aliasCode) { toast('Nhập mã bí danh', 'warning'); return; }

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

        toast(result.message || 'Đã gán bí danh');

        bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();

        loadPredictions(); loadObjects(); updatePendingBadge();

      } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

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

              placeholder="Tôi cần một cục tẩy.">${escHtml(ex.vi || '')}</textarea>

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

      <span>Chỉnh sửa trước khi duyệt. <strong>Để trống = giữ nguyên giá trị Gemini.</strong></span>

    </div>



    <div class="approve-card">

      <p class="approve-card-title">Thông tin cơ bản</p>

      <div class="approve-basic-grid mb-3">

        <div>

          <label class="form-label mb-1">Từ vựng</label>

          <input id="ov-word" class="form-control form-control-sm" placeholder="vd: Eraser" value="${escHtml(firstTrans.word_name || '')}">

        </div>

        <div>

          <label class="form-label mb-1">Phiên âm (IPA)</label>

          <input id="ov-phonetic" class="form-control form-control-sm" placeholder="vd: /ɪˈreɪ.zər/" value="${escHtml(firstTrans.phonetic || '')}">

        </div>

        <div>

          <label class="form-label mb-1">Loại từ</label>

          <input id="ov-pos" class="form-control form-control-sm" placeholder="n / v / adj" value="${escHtml(firstTrans.part_of_speech || '')}">

        </div>

      </div>



      <div>

        <label class="form-label mb-1">Định nghĩa</label>

        <textarea id="ov-def" class="form-control form-control-sm" style="resize:none;line-height:1.55;" placeholder="Nhập định nghĩa...">${escHtml(firstTrans.definition || '')}</textarea>

      </div>

    </div>



    <div class="approve-card">

      <p class="approve-card-title">Ví dụ</p>

      <div class="approve-examples-hint">Mỗi dòng là một ví dụ. Có thể chỉ nhập English nếu chưa có nghĩa tiếng Việt.</div>

      <div class="approve-examples">${exampleEditors}</div>

    </div>`;



      // Auto-resize textareas theo nội dung sẵn có.

      ['ov-def', ...Array.from(document.querySelectorAll('.approve-example-row textarea')).map(ta => ta.id)].forEach(tid => {

        const ta = document.getElementById(tid);

        if (!ta) return;

        ta.style.height = 'auto';

        ta.style.height = ta.scrollHeight + 'px';

        ta.addEventListener('input', () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; });

      });



      document.getElementById('pm-footer').innerHTML = `

    <button class="btn btn-sm btn-outline-secondary" onclick="openPredModal(${id})">

      <i class="bi bi-arrow-left me-1"></i>Quay lại

    </button>

    <button class="btn btn-sm btn-success ms-auto px-3" onclick="confirmApproveWithEdits(${id})">

      <i class="bi bi-check-lg me-1"></i>Xác nhận duyệt

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

      confirmAction('Từ chối prediction này?', async () => {

        try {

          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });

          toast('Đã từ chối prediction #' + id, 'warning');

          bootstrap.Modal.getInstance(document.getElementById('pred-modal'))?.hide();

          loadPredictions(); updatePendingBadge();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      });

    }



    async function approvePredDash(id) {

      confirmAction(`Duyệt prediction #${id}?`, async () => {

        try {

          await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) });

          toast('Đã duyệt prediction #' + id);

          loadDashboard();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      }, 'Xác nhận duyệt');

    }



    async function rejectPredDash(id) {

      confirmAction(`Từ chối prediction #${id}?`, async () => {

        try {

          await apiJSON(`/predictions/${id}/reject`, { method: 'POST' });

          toast('Đã từ chối prediction #' + id, 'warning');

          loadDashboard();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

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
