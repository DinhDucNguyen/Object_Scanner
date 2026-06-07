    // ============================================================
    // Training Data
    // ============================================================
    function trainingStatusLabel(status) {
      return {
        cho_duyet: 'Chờ duyệt',
        da_duyet: 'Đã duyệt',
        tu_choi: 'Từ chối',
      }[status] || status || 'Chưa rõ';
    }

    function trainingStatusColor(status) {
      return {
        cho_duyet: '#f59e0b',
        da_duyet: '#22c55e',
        tu_choi: '#ef4444',
      }[status] || '#94a3b8';
    }

    function trainingSourceLabel(source) {
      return { yolo: 'YOLO', gemini: 'Gemini', admin: 'Admin' }[source] || source || '—';
    }

    function trainingCoverageLabel(coverage) {
      return {
        custom_yolo: 'Đã có YOLO custom',
        coco_known: 'Đã có COCO',
        db_only: 'Có DB, chưa có model',
        new_gemini: 'Object mới từ Gemini',
      }[coverage] || coverage || 'Chưa rõ model';
    }

    function trainingCoverageClass(coverage) {
      return {
        custom_yolo: 'coverage-custom',
        coco_known: 'coverage-coco',
        db_only: 'coverage-db',
        new_gemini: 'coverage-new',
      }[coverage] || 'coverage-unknown';
    }

    function trainingRecommendationLabel(value) {
      return {
        high_priority: 'Ưu tiên train',
        recommended: 'Nên train',
        optional: 'Train thêm nếu cần',
        not_needed: 'Không cần train',
      }[value] || value || 'Chưa rõ';
    }

    function trainingRecommendationClass(value) {
      return {
        high_priority: 'recommend-high',
        recommended: 'recommend-yes',
        optional: 'recommend-optional',
        not_needed: 'recommend-no',
      }[value] || 'recommend-unknown';
    }

    function trainingNextActionMeta(action, group) {
      const pending = group.pending_count ?? (group.status_counts || {}).cho_duyet ?? 0;
      const ready = group.ready_for_dataset;
      const recommendation = group.training_recommendation;
      const datasetVersions = group.dataset_versions || [];
      if (pending > 0 || action === 'review_pending') {
        return { label: 'Cần duyệt ảnh', cls: 'action-review', icon: 'bi-hourglass-split' };
      }
      if (action === 'collect_more') {
        return { label: 'Thu thêm ảnh', cls: 'action-collect', icon: 'bi-camera' };
      }
      if (ready || action === 'ready_to_train') {
        return { label: 'Sẵn sàng train', cls: 'action-ready', icon: 'bi-lightning-charge' };
      }
      if (action === 'already_exported' || datasetVersions.length > 0) {
        return { label: 'Đã có dataset', cls: 'action-done', icon: 'bi-check2-circle' };
      }
      if (recommendation === 'not_needed') {
        return { label: 'Đã có model', cls: 'action-done', icon: 'bi-shield-check' };
      }
      if (action === 'mostly_rejected') {
        return { label: 'Cần kiểm tra lại', cls: 'action-recheck', icon: 'bi-exclamation-triangle' };
      }
      return { label: 'Theo dõi thêm', cls: 'action-monitor', icon: 'bi-eye' };
    }

    function trainingFilterValue(id) {
      const el = document.getElementById(id);
      return el ? el.value.trim() : '';
    }

    let trainingFilterTimer = null;
    function debouncedLoadTrainingData() {
      clearTimeout(trainingFilterTimer);
      trainingFilterTimer = setTimeout(loadTrainingData, 250);
    }

    function buildTrainingSummaryPath() {
      const params = new URLSearchParams();
      const filters = {
        search: trainingFilterValue('td-search'),
        recommendation: trainingFilterValue('td-recommendation-filter'),
        model_coverage: trainingFilterValue('td-coverage-filter'),
        status: trainingFilterValue('td-status-filter'),
        source: trainingFilterValue('td-source-filter'),
      };
      Object.entries(filters).forEach(([key, value]) => {
        if (value) params.set(key, value);
      });
      const query = params.toString();
      return `/training-summary${query ? `?${query}` : ''}`;
    }

    let _trainingPreset = 'all';

    function setTrainingPreset(preset, btn) {
      _trainingPreset = preset || 'all';
      document.querySelectorAll('[data-td-preset]').forEach(b => b.classList.remove('active'));
      const active = btn || document.querySelector(`[data-td-preset="${preset}"]`);
      if (active) active.classList.add('active');
      const rec = document.getElementById('td-recommendation-filter');
      const cov = document.getElementById('td-coverage-filter');
      const sta = document.getElementById('td-status-filter');
      if (rec) rec.value = '';
      if (cov) cov.value = '';
      if (sta) sta.value = '';
      // 'ready' không set filter backend — lọc client-side sau khi nhận data
      if (preset === 'review' && sta) sta.value = 'cho_duyet';
      if (preset === 'train' && rec) rec.value = 'train';
      if (preset === 'known' && cov) cov.value = 'known';
      loadTrainingData();
    }

    function _pendingReason(img) {
      if (img.note && img.note.startsWith('quality_fail:')) return ''; // đã có qualityFlag hiển thị
      if (img.source === 'gemini') return 'Gemini — cần admin duyệt';
      const conf = Number(img.confidence);
      if (!isNaN(conf) && conf < 0.85) return `Tin cậy thấp (${Math.round(conf * 100)}%)`;
      if (!img.object_id) return 'Chưa có đối tượng trong DB';
      return '';
    }

    function _qualityFlag(note, score) {
      if (!note || !note.startsWith('quality_fail:')) return '';
      const detail = note.slice('quality_fail:'.length);
      const n = detail.toLowerCase();
      let label = '?';
      if (n.startsWith('anh_bi_mo')) label = 'mờ';
      else if (n.startsWith('anh_qua_toi')) label = 'tối';
      else if (n.startsWith('anh_qua_sang')) label = 'sáng';
      else if (n.startsWith('anh_qua_nho')) label = 'nhỏ';
      const scoreText = score != null ? ` (${score})` : '';
      return `<span class="td-quality-flag" title="${escHtml(detail)}">${label}${scoreText}</span>`;
    }

    function renderTrainingCard(r) {
      const counts = r.status_counts || {};
      const approved = r.approved_count ?? counts.da_duyet ?? 0;
      const pending = r.pending_count ?? counts.cho_duyet ?? 0;
      const min = r.min_images_for_training ?? 5;
      const ready = r.ready_for_dataset ?? (approved >= min);
      const nextAction = r.next_action || '';
      const cardAccent = pending > 0 ? '#f59e0b' : ready ? '#22c55e' : nextAction === 'collect_more' ? '#6366f1' : '#e2e8f0';
      const nextMeta = trainingNextActionMeta(nextAction, r);
      const coverageBadge = `<span class="td-meta-badge ${trainingCoverageClass(r.model_coverage)}">${escHtml(trainingCoverageLabel(r.model_coverage))}</span>`;
      const recommendationBadge = `<span class="td-meta-badge ${trainingRecommendationClass(r.training_recommendation)}">${escHtml(trainingRecommendationLabel(r.training_recommendation))}</span>`;
      const translations = (r.translations || []).slice(0, 3).map(t =>
        `<span class="badge bg-light text-dark border" title="${escHtml(t.word || '')}" style="font-size:.72rem;">${escHtml(t.language_code || '')}: ${escHtml(t.word || '')}</span>`
      ).join('');
      const THUMB_INITIAL = 8;
      const allImages = r.images || [];
      const hasMore = allImages.length > THUMB_INITIAL;
      const _renderThumb = (img, objectCode) => {
        const borderColor = trainingStatusColor(img.status);
        const acts = img.id ? [
          `<button class="btn-act${img.status === 'da_duyet' ? ' disabled' : ''}" ${img.status === 'da_duyet' ? 'disabled' : `onclick="approveTrainingImage(${img.id},'${objectCode}',this)"`} title="Duyệt"><i class="bi bi-check-lg"></i></button>`,
          `<button class="btn-act del${img.status === 'tu_choi' ? ' disabled' : ''}" ${img.status === 'tu_choi' ? 'disabled' : `onclick="unlinkScan(${img.id},'${objectCode}',this)"`} title="Từ chối"><i class="bi bi-x-lg"></i></button>`,
          `<button class="btn-act" onclick="openReassign(${img.id},'${objectCode}')" title="Chuyển object"><i class="bi bi-arrow-left-right"></i></button>`,
          `<button class="btn-act del" onclick="deleteTrainingImage(${img.id},'${objectCode}',this)" title="Xóa"><i class="bi bi-trash3"></i></button>`,
        ].join('') : '';
        const qualityFlag = _qualityFlag(img.note, img.quality_score);
        const pendingReason = img.status === 'cho_duyet' ? _pendingReason(img) : '';
        const thumbTitle = [trainingSourceLabel(img.source), trainingStatusLabel(img.status), pendingReason].filter(Boolean).join(' — ');
        return `<div class="td-thumb-wrap" data-status="${escHtml(img.status || '')}">
          <img src="${img.url}" alt="" title="${escHtml(thumbTitle)}"
            onclick="zoomImage('${img.url}')"
            style="border:3px solid ${borderColor};"
            onerror="this.parentElement.style.display='none'">
          ${qualityFlag}
          ${pendingReason ? `<div class="td-thumb-pending-reason">${escHtml(pendingReason)}</div>` : ''}
          <div class="td-thumb-source">${trainingSourceLabel(img.source)}</div>
          ${acts ? `<div class="td-thumb-actions">${acts}</div>` : ''}
        </div>`;
      };
      const safeImagesJson = escHtml(JSON.stringify(allImages));
      const thumbs = allImages.slice(0, THUMB_INITIAL).map(img => _renderThumb(img, r.object_code)).join('')
        + (hasMore
          ? `<button class="td-more-count" onclick="expandTrainingCard(this,this.dataset.objectCode)" data-images="${safeImagesJson}" data-initial="${THUMB_INITIAL}" data-object-code="${escHtml(r.object_code || '')}">+${allImages.length - THUMB_INITIAL}<br><span style="font-size:.65rem;font-weight:400;">ảnh</span></button>`
          : '');
      const datasetVersions = r.dataset_versions || [];
      const confidence = r.avg_confidence == null ? '' : ` · ${Math.round(Number(r.avg_confidence) * 100)}% tin cậy`;
      const progressPct = Math.min(100, Math.round(approved / min * 100));
      const missing = Math.max((r.missing_approved_images ?? (min - approved)), 0);
      return `
      <div class="panel td-card ${nextMeta.cls}" style="padding:0;overflow:hidden;border-top:3px solid ${cardAccent};">
        <div class="td-card-head">
          <div class="td-title-stack">
            <div class="td-title-line">
              <span class="td-object-code cell-ellipsis" title="${escHtml(r.object_code || '')}">${escHtml(r.object_code || '')}</span>
            </div>
            <div class="td-card-sub">${escHtml(r.category || 'Chưa phân loại')}${confidence}</div>
          </div>
          <span class="td-next-action ${nextMeta.cls}"><i class="bi ${nextMeta.icon} me-1"></i>${nextMeta.label}</span>
        </div>
        <div class="td-meta-row">
          ${recommendationBadge}
          ${coverageBadge}
          ${pending > 0 ? `<span class="td-meta-badge" style="background:#fff3cd;color:#92400e;">${pending} chờ duyệt</span>` : ''}
          ${ready ? `<span class="td-meta-badge" style="background:#dcfce7;color:#166534;">Sẵn sàng export</span>` : ''}
          ${missing > 0 && ['high_priority', 'recommended'].includes(r.training_recommendation) ? `<span class="td-meta-badge" style="background:#f8fafc;color:#475569;border-color:#e2e8f0;">Cần thêm ${missing} ảnh</span>` : ''}
        </div>
        <div style="padding:.1rem 1rem .4rem;">
          <div style="height:4px;background:#e2e8f0;border-radius:2px;overflow:hidden;">
            <div style="height:100%;width:${progressPct}%;background:${cardAccent};border-radius:2px;transition:width .3s;"></div>
          </div>
          <div style="font-size:.68rem;color:#94a3b8;margin-top:2px;">${approved}/${min} ảnh tối thiểu để train</div>
        </div>
        ${translations ? `<div class="td-card-translations">${translations}</div>` : ''}
        <div class="td-thumb-grid">
          ${thumbs || '<span class="text-muted" style="font-size:.8rem;padding:.5rem 0;">Chưa có ảnh</span>'}
        </div>
        <div class="td-card-footer">
          ${pending ? `<button class="btn btn-sm btn-warning" onclick="focusPendingTrainingImages(this)"><i class="bi bi-hourglass-split me-1"></i>Xem ${pending} ảnh chờ</button>` : ''}
          ${ready ? `<button class="btn btn-sm btn-outline-primary" onclick="createDatasetVersion()"><i class="bi bi-bookmark-check me-1"></i>Tạo dataset</button>` : ''}
          ${datasetVersions.length ? `<span class="badge text-bg-light border align-self-center" style="font-size:.7rem;">Dataset: ${datasetVersions.map(escHtml).join(', ')}</span>` : ''}
          <button class="btn btn-sm btn-outline-danger" style="font-size:.76rem;padding:2px 8px;" onclick="unlinkAllScans('${escHtml(r.object_code || '')}')"><i class="bi bi-x-lg me-1"></i>Từ chối nhóm</button>
        </div>
      </div>`;
    }

    function focusPendingTrainingImages(btn) {
      const card = btn.closest('.td-card');
      if (!card) return;
      if (!btn.dataset.defaultHtml) btn.dataset.defaultHtml = btn.innerHTML;
      const isFiltered = btn.dataset.filtered === '1';
      if (isFiltered) {
        // Bỏ filter — hiện lại tất cả
        card.querySelectorAll('.td-thumb-wrap').forEach(showTrainingThumb);
        // Khôi phục nút Thu gọn nếu đang bị ẩn
        const collapseBtn = card.querySelector('.td-collapse-btn');
        if (collapseBtn) collapseBtn.style.display = '';
        btn.dataset.filtered = '0';
        btn.innerHTML = btn.dataset.defaultHtml || `<i class="bi bi-hourglass-split me-1"></i>Xem ảnh chờ`;
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-warning');
      } else {
        // Nếu còn nút +N chưa expand → expand trước
        const moreBtn = card.querySelector('.td-more-count:not(.td-collapse-btn)');
        if (moreBtn) {
          moreBtn.click(); // sync — insert HTML ngay lập tức
          requestAnimationFrame(() => applyPendingFilter(card, btn));
        } else {
          applyPendingFilter(card, btn);
        }
      }
    }

    function showTrainingThumb(thumb) {
      thumb.style.display = '';
      requestAnimationFrame(() => thumb.classList.remove('td-thumb-filtered-out'));
    }

    function hideTrainingThumb(thumb) {
      thumb.classList.add('td-thumb-filtered-out');
      setTimeout(() => {
        if (thumb.classList.contains('td-thumb-filtered-out')) thumb.style.display = 'none';
      }, 150);
    }

    function applyPendingFilter(card, btn) {
      card.querySelectorAll('.td-thumb-wrap').forEach(t => {
        if (t.dataset.status === 'cho_duyet') showTrainingThumb(t);
        else hideTrainingThumb(t);
      });
      // Ẩn nút Thu gọn khi đang filter (sẽ khôi phục khi bỏ filter)
      const collapseBtn = card.querySelector('.td-collapse-btn');
      if (collapseBtn) collapseBtn.style.display = 'none';
      btn.dataset.filtered = '1';
      btn.innerHTML = `<i class="bi bi-grid me-1"></i>Xem tất cả`;
      btn.classList.remove('btn-warning');
      btn.classList.add('btn-secondary');
      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function loadTrainingData() {
      const grid = document.getElementById('td-grid');
      const empty = document.getElementById('td-empty');
      grid.innerHTML = '<div class="text-muted p-3"><span class="spinner-border spinner-border-sm me-2"></span>Đang tải...</div>';
      empty.style.display = 'none';
      try {
        let records = await apiJSON(buildTrainingSummaryPath());
        if (!records || records.length === 0) {
          grid.innerHTML = '';
          empty.style.display = '';
          _renderTrainingKpis({ objects: 0, images: 0, pending: 0, approved: 0, rejected: 0, priority: 0 });
          return;
        }
        // KPI luôn tính trên toàn bộ data trước khi filter client-side
        _renderTrainingKpis({
          objects: records.length,
          images: records.reduce((s, r) => s + (r.total_images || 0), 0),
          pending: records.reduce((s, r) => s + ((r.status_counts || {}).cho_duyet || 0), 0),
          approved: records.reduce((s, r) => s + ((r.status_counts || {}).da_duyet || 0), 0),
          rejected: records.reduce((s, r) => s + ((r.status_counts || {}).tu_choi || 0), 0),
          priority: records.filter(r => ['high_priority', 'recommended'].includes(r.training_recommendation)).length,
        });
        // Preset 'ready': filter client-side vì backend không có param này
        if (_trainingPreset === 'ready') {
          records = records.filter(r => r.ready_for_dataset);
        }
        if (!records.length) { grid.innerHTML = ''; empty.style.display = ''; return; }
        grid.innerHTML = records.map(renderTrainingCard).join('');
      } catch (e) {
        grid.innerHTML = `<div class="text-danger p-3">Lỗi: ${e.message}</div>`;
      }
    }

    function _renderTrainingKpis(k) {
      const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
      set('td-total-objects', k.objects);
      set('td-total-images', k.images);
      set('td-pending-images', k.pending);
      set('td-approved-images', k.approved);
      set('td-rejected-images', k.rejected);
      set('td-train-priority', k.priority);
    }

    // Training image management
    function expandTrainingCard(btn, objectCode) {
      try {
        const allImages = JSON.parse(btn.dataset.images || '[]');
        const imagesData = btn.dataset.images || '[]';
        const initialData = btn.dataset.initial || '8';
        const initial = parseInt(initialData, 10);
        const remaining = allImages.slice(initial);
        const safeObjectCode = escHtml(objectCode || btn.dataset.objectCode || '');
        const html = remaining.map(img => {
          const borderColor = trainingStatusColor(img.status);
          const acts = img.id ? [
            `<button class="btn-act${img.status === 'da_duyet' ? ' disabled' : ''}" ${img.status === 'da_duyet' ? 'disabled' : `onclick="approveTrainingImage(${img.id},'${objectCode}',this)"`} title="Duyệt"><i class="bi bi-check-lg"></i></button>`,
            `<button class="btn-act del${img.status === 'tu_choi' ? ' disabled' : ''}" ${img.status === 'tu_choi' ? 'disabled' : `onclick="unlinkScan(${img.id},'${objectCode}',this)"`} title="Từ chối"><i class="bi bi-x-lg"></i></button>`,
            `<button class="btn-act" onclick="openReassign(${img.id},'${objectCode}')" title="Chuyển object"><i class="bi bi-arrow-left-right"></i></button>`,
            `<button class="btn-act del" onclick="deleteTrainingImage(${img.id},'${objectCode}',this)" title="Xóa"><i class="bi bi-trash3"></i></button>`,
          ].join('') : '';
          const qualityFlag = _qualityFlag(img.note, img.quality_score);
          const pendingReason = img.status === 'cho_duyet' ? _pendingReason(img) : '';
          const thumbTitle = [trainingSourceLabel(img.source), trainingStatusLabel(img.status), pendingReason].filter(Boolean).join(' — ');
          return `<div class="td-thumb-wrap td-thumb-expanded" data-status="${escHtml(img.status || '')}">
            <img src="${img.url}" alt="" title="${escHtml(thumbTitle)}"
              onclick="zoomImage('${img.url}')"
              style="border:3px solid ${borderColor};"
              onerror="this.parentElement.style.display='none'">
            ${qualityFlag}
            ${pendingReason ? `<div class="td-thumb-pending-reason">${escHtml(pendingReason)}</div>` : ''}
            <div class="td-thumb-source">${trainingSourceLabel(img.source)}</div>
            ${acts ? `<div class="td-thumb-actions">${acts}</div>` : ''}
          </div>`;
        }).join('');
        // thay nút +N bằng ảnh còn lại + nút thu gọn
        btn.insertAdjacentHTML('beforebegin', html);
        btn.outerHTML = `<button class="td-more-count td-collapse-btn" onclick="collapseTrainingCard(this)" data-images="${escHtml(imagesData)}" data-initial="${escHtml(initialData)}" data-object-code="${safeObjectCode}" data-remaining="${remaining.length}" style="background:#f1f5f9;color:#475569;border-color:#cbd5e1;font-size:.72rem;"><i class="bi bi-chevron-up"></i><br><span style="font-size:.65rem;">Thu gọn</span></button>`;
      } catch (_) {}
    }

    function collapseTrainingCard(btn) {
      const grid = btn.closest('.td-thumb-grid');
      const card = btn.closest('.td-card');
      if (!grid) return;
      const imagesData = btn.dataset.images || '[]';
      const initialData = btn.dataset.initial || '8';
      const objectCode = btn.dataset.objectCode || '';
      const remaining = parseInt(btn.dataset.remaining || '0', 10);
      const expandedThumbs = Array.from(grid.querySelectorAll('.td-thumb-expanded'));
      expandedThumbs.forEach(el => el.classList.add('td-thumb-collapsing'));
      setTimeout(() => expandedThumbs.forEach(el => el.remove()), 150);
      // Khôi phục tất cả thumb còn lại (initial thumbnails) về hiển thị bình thường
      grid.querySelectorAll('.td-thumb-wrap').forEach(showTrainingThumb);
      // Reset nút "Xem ảnh chờ" nếu đang ở trạng thái filter
      if (card) {
        const filterBtn = card.querySelector('.btn-warning[data-filtered], .btn-secondary[data-filtered]');
        if (filterBtn && filterBtn.dataset.filtered === '1') {
          filterBtn.dataset.filtered = '0';
          filterBtn.innerHTML = filterBtn.dataset.defaultHtml || `<i class="bi bi-hourglass-split me-1"></i>Xem ảnh chờ`;
          filterBtn.classList.remove('btn-secondary');
          filterBtn.classList.add('btn-warning');
        }
      }
      if (remaining > 0) {
        btn.outerHTML = `<button class="td-more-count" onclick="expandTrainingCard(this,this.dataset.objectCode)" data-images="${escHtml(imagesData)}" data-initial="${escHtml(initialData)}" data-object-code="${escHtml(objectCode)}">+${remaining}<br><span style="font-size:.65rem;font-weight:400;">ảnh</span></button>`;
      } else {
        btn.remove();
      }
      if (card) {
        requestAnimationFrame(() => {
          const target = card.querySelector('.td-card-head') || card;
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
      }
    }

    function _thumbUpdateInPlace(btnEl, newStatus) {
      const wrap = btnEl?.closest('.td-thumb-wrap');
      if (!wrap) return false;
      const color = trainingStatusColor(newStatus);
      const img = wrap.querySelector('img');
      if (img) img.style.border = `3px solid ${color}`;
      wrap.dataset.status = newStatus;
      // disable buttons theo trạng thái mới
      wrap.querySelectorAll('.btn-act').forEach(b => {
        const isApprove = b.title === 'Duyệt';
        const isUnlink  = b.title === 'Từ chối';
        if (isApprove && newStatus === 'da_duyet') { b.classList.add('disabled'); b.disabled = true; }
        if (isUnlink  && newStatus === 'tu_choi')  { b.classList.add('disabled'); b.disabled = true; }
        if (isApprove && newStatus === 'tu_choi')  { b.classList.remove('disabled'); b.disabled = false; }
        if (isUnlink  && newStatus === 'da_duyet') { b.classList.remove('disabled'); b.disabled = false; }
      });
      wrap.querySelector('.td-thumb-pending-reason')?.remove();
      return true;
    }

    async function approveTrainingImage(trainingImageId, objectCode, btnEl) {
      try {
        await apiJSON(`/training-images/${trainingImageId}/approve`, { method: 'PATCH' });
        toast(`Đã duyệt ảnh cho "${objectCode}"`, 'success');
        if (!_thumbUpdateInPlace(btnEl, 'da_duyet')) loadTrainingData();
      } catch (e) {
        toast('Lỗi: ' + e.message, 'danger');
      }
    }

    async function unlinkScan(trainingImageId, objectCode, btnEl) {
      confirmAction(
        `Từ chối ảnh này khỏi tập training của "${objectCode}"? Ảnh vẫn được giữ trong lịch sử quét, chỉ không dùng để train.`,
        async () => {
          try {
            await apiJSON(`/training-images/${trainingImageId}/unlink`, { method: 'PATCH' });
            toast('Đã từ chối ảnh training', 'success');
            if (!_thumbUpdateInPlace(btnEl, 'tu_choi')) loadTrainingData();
          } catch (e) {
            toast('Lỗi: ' + e.message, 'danger');
          }
        },
        'Từ chối ảnh'
      );
    }

    async function deleteTrainingImage(trainingImageId, objectCode, btnEl) {
      confirmAction(
        `Xóa ảnh này khỏi Training Data của "${objectCode}"? Lịch sử quét và prediction liên quan vẫn được giữ.`,
        async () => {
          try {
            await apiJSON(`/training-images/${trainingImageId}`, { method: 'DELETE' });
            toast('Đã xóa ảnh khỏi Training Data', 'success');
            const wrap = btnEl?.closest('.td-thumb-wrap');
            const card = wrap?.closest('.td-card');
            if (wrap) {
              wrap.style.transition = 'opacity .2s';
              wrap.style.opacity = '0';
              setTimeout(() => {
                wrap.remove();
                // Nếu card không còn ảnh nào da_duyet/cho_duyet → ẩn card
                const remaining = card?.querySelectorAll('.td-thumb-wrap[data-status="cho_duyet"], .td-thumb-wrap[data-status="da_duyet"]');
                if (card && remaining && remaining.length === 0) {
                  card.style.transition = 'opacity .3s';
                  card.style.opacity = '0';
                  setTimeout(() => card.remove(), 300);
                }
              }, 200);
            } else loadTrainingData();
          } catch (e) {
            toast('Lỗi: ' + e.message, 'danger');
          }
        },
        'Xóa ảnh'
      );
    }

    async function unlinkAllScans(objectCode) {
      confirmAction(
        `Từ chối toàn bộ ảnh training của "${objectCode}"?\n(Ảnh vẫn giữ trong lịch sử quét, chỉ không dùng để train)`,
        async () => {
          try {
            const res = await apiJSON(`/training-images/unlink-all?object_code=${encodeURIComponent(objectCode)}`, { method: 'DELETE' });
            toast(`Đã từ chối ${res.count} ảnh của "${objectCode}"`, 'warning');
            loadTrainingData();
          } catch (e) {
            toast('Lỗi: ' + e.message, 'danger');
          }
        },
        'Từ chối tất cả'
      );
    }

    function createDatasetVersion() {
      document.getElementById('fm-title').textContent = 'Tạo phiên bản Dataset';
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-body').innerHTML = `
        <div class="mb-3">
          <label class="form-label fw-semibold">Mã phiên bản <span class="text-danger">*</span></label>
          <input id="dv-version" class="form-control" placeholder="v1" value="v1" />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold">Ghi chú</label>
          <input id="dv-note" class="form-control" placeholder="Mô tả ngắn cho phiên bản này (không bắt buộc)" />
        </div>`;
      const modal = new bootstrap.Modal(document.getElementById('form-modal'));
      document.getElementById('fm-submit').onclick = async () => {
        const version = document.getElementById('dv-version')?.value.trim();
        const note = document.getElementById('dv-note')?.value.trim() || '';
        if (!version) { toast('Vui lòng nhập mã phiên bản', 'warning'); return; }
        try {
          document.getElementById('fm-submit').disabled = true;
          const res = await apiJSON(`/training-datasets?ma_phien_ban=${encodeURIComponent(version)}&ghi_chu=${encodeURIComponent(note)}`, { method: 'POST' });
          toast(`Đã tạo dataset ${res.ma_phien_ban}: ${res.tong_anh} ảnh, ${res.tong_nhan} nhãn`, 'success');
          modal.hide();
          loadTrainingData();
        } catch (e) {
          toast('Lỗi: ' + e.message, 'danger');
        } finally {
          document.getElementById('fm-submit').disabled = false;
        }
      };
      modal.show();
    }

    let _reassignTrainingImageId = null;
    let _reassignCurrentCode = null;
    let _reassignMode = 'training';
    let _reassignPredictionId = null;
    let _reassignRootPredictionId = null;
    let _cachedObjects = [];

    async function openReassign(trainingImageId, currentCode) {
      _reassignMode = 'training';
      _reassignTrainingImageId = trainingImageId;
      _reassignPredictionId = null;
      _reassignRootPredictionId = null;
      _reassignCurrentCode = currentCode;
      document.getElementById('rm-current-code').textContent = currentCode;
      document.getElementById('rm-search').value = '';
      if (_cachedObjects.length === 0) {
        try { _cachedObjects = await apiJSON('/objects?limit=1000'); } catch (_) { _cachedObjects = []; }
      }
      _fillReassignSelect('');
      bootstrap.Modal.getOrCreateInstance(document.getElementById('reassign-modal')).show();
    }

    async function openReviewImageReassign(predictionId, currentCode, rootPredictionId) {
      _reassignMode = 'review';
      _reassignTrainingImageId = null;
      _reassignPredictionId = predictionId;
      _reassignRootPredictionId = rootPredictionId;
      _reassignCurrentCode = currentCode;
      document.getElementById('rm-current-code').textContent = currentCode;
      document.getElementById('rm-search').value = '';
      if (_cachedObjects.length === 0) {
        try { _cachedObjects = await apiJSON('/objects?limit=1000'); } catch (_) { _cachedObjects = []; }
      }
      _fillReassignSelect('');
      bootstrap.Modal.getOrCreateInstance(document.getElementById('reassign-modal')).show();
    }

    function _fillReassignSelect(search) {
      const sel = document.getElementById('rm-select');
      const q = (search || '').toLowerCase();
      const filtered = _cachedObjects
        .filter(o => o.ma_doi_tuong !== _reassignCurrentCode)
        .filter(o => !q || o.ma_doi_tuong.includes(q) || (o.category_name || '').toLowerCase().includes(q))
        .slice(0, 60);
      sel.innerHTML = filtered.map(o =>
        `<option value="${o.ma_doi_tuong}">${o.ma_doi_tuong}${o.category_name ? ' — ' + o.category_name : ''}</option>`
      ).join('') || '<option disabled>Không tìm thấy</option>';
    }

    document.getElementById('rm-search').addEventListener('input', e => _fillReassignSelect(e.target.value));

    document.getElementById('rm-confirm').addEventListener('click', async () => {
      const sel = document.getElementById('rm-select');
      const targetCode = sel.value;
      if (!targetCode || sel.options[sel.selectedIndex]?.disabled) {
        toast('Vui lòng chọn đối tượng đích', 'warning'); return;
      }
      try {
        if (_reassignMode === 'review') {
          await apiJSON(`/predictions/${_reassignPredictionId}/reassign-image?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        } else {
          await apiJSON(`/training-images/${_reassignTrainingImageId}/reassign?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        }
        bootstrap.Modal.getInstance(document.getElementById('reassign-modal')).hide();
        toast(`Đã chuyển ảnh sang "${targetCode}"`, 'success');
        _cachedObjects = [];
        if (_reassignMode === 'review') {
          openPredModal(_reassignRootPredictionId);
          loadPredictions();
          updatePendingBadge();
        } else {
          loadTrainingData();
        }
      } catch (e) {
        toast('Lỗi: ' + e.message, 'danger');
      }
    });

