    // ============================================================
    // Training Data
    // ============================================================
    function trainingStatusLabel(status) {
      return {
        cho_duyet: 'Chá» duyá»‡t',
        da_duyet: 'ÄÃ£ duyá»‡t',
        tu_choi: 'Tá»« chá»‘i',
      }[status] || status || 'ChÆ°a rÃµ';
    }

    function trainingStatusColor(status) {
      return {
        cho_duyet: '#f59e0b',
        da_duyet: '#22c55e',
        tu_choi: '#ef4444',
      }[status] || '#94a3b8';
    }

    function trainingSourceLabel(source) {
      return { yolo: 'YOLO', gemini: 'Gemini', admin: 'Admin' }[source] || source || 'â€”';
    }

    function trainingCoverageLabel(coverage) {
      return {
        custom_yolo: 'ÄÃ£ cÃ³ YOLO custom',
        coco_known: 'ÄÃ£ cÃ³ COCO',
        db_only: 'CÃ³ DB, chÆ°a cÃ³ model',
        new_gemini: 'Object má»›i tá»« Gemini',
      }[coverage] || coverage || 'ChÆ°a rÃµ model';
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
        high_priority: 'Æ¯u tiÃªn train',
        recommended: 'NÃªn train',
        optional: 'Train thÃªm náº¿u cáº§n',
        not_needed: 'KhÃ´ng cáº§n train',
      }[value] || value || 'ChÆ°a rÃµ';
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
        return { label: 'Cáº§n duyá»‡t áº£nh', cls: 'action-review', icon: 'bi-hourglass-split' };
      }
      if (action === 'collect_more') {
        return { label: 'Thu thÃªm áº£nh', cls: 'action-collect', icon: 'bi-camera' };
      }
      if (ready || action === 'ready_to_train') {
        return { label: 'Sáºµn sÃ ng train', cls: 'action-ready', icon: 'bi-lightning-charge' };
      }
      if (action === 'already_exported' || datasetVersions.length > 0) {
        return { label: 'ÄÃ£ cÃ³ dataset', cls: 'action-done', icon: 'bi-check2-circle' };
      }
      if (recommendation === 'not_needed') {
        return { label: 'ÄÃ£ cÃ³ model', cls: 'action-done', icon: 'bi-shield-check' };
      }
      if (action === 'mostly_rejected') {
        return { label: 'Cáº§n kiá»ƒm tra láº¡i', cls: 'action-recheck', icon: 'bi-exclamation-triangle' };
      }
      return { label: 'Theo dÃµi thÃªm', cls: 'action-monitor', icon: 'bi-eye' };
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
      // 'ready' khÃ´ng set filter backend â€” lá»c client-side sau khi nháº­n data
      if (preset === 'review' && sta) sta.value = 'cho_duyet';
      if (preset === 'train' && rec) rec.value = 'train';
      if (preset === 'known' && cov) cov.value = 'known';
      loadTrainingData();
    }

    function _pendingReason(img) {
      if (img.note && img.note.startsWith('quality_fail:')) return ''; // Ä‘Ã£ cÃ³ qualityFlag hiá»ƒn thá»‹
      if (img.source === 'gemini') return 'Gemini â€” cáº§n admin duyá»‡t';
      const conf = Number(img.confidence);
      if (!isNaN(conf) && conf < 0.85) return `Tin cáº­y tháº¥p (${Math.round(conf * 100)}%)`;
      if (!img.object_id) return 'ChÆ°a cÃ³ Ä‘á»‘i tÆ°á»£ng trong DB';
      return '';
    }

    function _qualityFlag(note, score) {
      if (!note || !note.startsWith('quality_fail:')) return '';
      const detail = note.slice('quality_fail:'.length);
      const n = detail.toLowerCase();
      let label = '?';
      if (n.startsWith('anh_bi_mo')) label = 'má»';
      else if (n.startsWith('anh_qua_toi')) label = 'tá»‘i';
      else if (n.startsWith('anh_qua_sang')) label = 'sÃ¡ng';
      else if (n.startsWith('anh_qua_nho')) label = 'nhá»';
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
          `<button class="btn-act${img.status === 'da_duyet' ? ' disabled' : ''}" ${img.status === 'da_duyet' ? 'disabled' : `onclick="approveTrainingImage(${img.id},'${objectCode}',this)"`} title="Duyá»‡t"><i class="bi bi-check-lg"></i></button>`,
          `<button class="btn-act del${img.status === 'tu_choi' ? ' disabled' : ''}" ${img.status === 'tu_choi' ? 'disabled' : `onclick="unlinkScan(${img.id},'${objectCode}',this)"`} title="Tá»« chá»‘i"><i class="bi bi-x-lg"></i></button>`,
          `<button class="btn-act" onclick="openReassign(${img.id},'${objectCode}')" title="Chuyá»ƒn object"><i class="bi bi-arrow-left-right"></i></button>`,
          `<button class="btn-act del" onclick="deleteTrainingImage(${img.id},'${objectCode}',this)" title="XÃ³a"><i class="bi bi-trash3"></i></button>`,
        ].join('') : '';
        const qualityFlag = _qualityFlag(img.note, img.quality_score);
        const pendingReason = img.status === 'cho_duyet' ? _pendingReason(img) : '';
        const thumbTitle = [trainingSourceLabel(img.source), trainingStatusLabel(img.status), pendingReason].filter(Boolean).join(' â€” ');
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
          ? `<button class="td-more-count" onclick="expandTrainingCard(this,this.dataset.objectCode)" data-images="${safeImagesJson}" data-initial="${THUMB_INITIAL}" data-object-code="${escHtml(r.object_code || '')}">+${allImages.length - THUMB_INITIAL}<br><span style="font-size:.65rem;font-weight:400;">áº£nh</span></button>`
          : '');
      const datasetVersions = r.dataset_versions || [];
      const confidence = r.avg_confidence == null ? '' : ` Â· ${Math.round(Number(r.avg_confidence) * 100)}% tin cáº­y`;
      const progressPct = Math.min(100, Math.round(approved / min * 100));
      const missing = Math.max((r.missing_approved_images ?? (min - approved)), 0);
      return `
      <div class="panel td-card ${nextMeta.cls}" style="padding:0;overflow:hidden;border-top:3px solid ${cardAccent};">
        <div class="td-card-head">
          <div class="td-title-stack">
            <div class="td-title-line">
              <span class="td-object-code cell-ellipsis" title="${escHtml(r.object_code || '')}">${escHtml(r.object_code || '')}</span>
            </div>
            <div class="td-card-sub">${escHtml(r.category || 'ChÆ°a phÃ¢n loáº¡i')}${confidence}</div>
          </div>
          <span class="td-next-action ${nextMeta.cls}"><i class="bi ${nextMeta.icon} me-1"></i>${nextMeta.label}</span>
        </div>
        <div class="td-meta-row">
          ${recommendationBadge}
          ${coverageBadge}
          ${pending > 0 ? `<span class="td-meta-badge" style="background:#fff3cd;color:#92400e;">${pending} chá» duyá»‡t</span>` : ''}
          ${ready ? `<span class="td-meta-badge" style="background:#dcfce7;color:#166534;">Sáºµn sÃ ng export</span>` : ''}
          ${missing > 0 && ['high_priority', 'recommended'].includes(r.training_recommendation) ? `<span class="td-meta-badge" style="background:#f8fafc;color:#475569;border-color:#e2e8f0;">Cáº§n thÃªm ${missing} áº£nh</span>` : ''}
        </div>
        <div style="padding:.1rem 1rem .4rem;">
          <div style="height:4px;background:#e2e8f0;border-radius:2px;overflow:hidden;">
            <div style="height:100%;width:${progressPct}%;background:${cardAccent};border-radius:2px;transition:width .3s;"></div>
          </div>
          <div style="font-size:.68rem;color:#94a3b8;margin-top:2px;">${approved}/${min} áº£nh tá»‘i thiá»ƒu Ä‘á»ƒ train</div>
        </div>
        ${translations ? `<div class="td-card-translations">${translations}</div>` : ''}
        <div class="td-thumb-grid">
          ${thumbs || '<span class="text-muted" style="font-size:.8rem;padding:.5rem 0;">ChÆ°a cÃ³ áº£nh</span>'}
        </div>
        <div class="td-card-footer">
          ${pending ? `<button class="btn btn-sm btn-warning" onclick="focusPendingTrainingImages(this)"><i class="bi bi-hourglass-split me-1"></i>Xem ${pending} áº£nh chá»</button>` : ''}
          ${ready ? `<button class="btn btn-sm btn-outline-primary" onclick="createDatasetVersion()"><i class="bi bi-bookmark-check me-1"></i>Táº¡o dataset</button>` : ''}
          ${datasetVersions.length ? `<span class="badge text-bg-light border align-self-center" style="font-size:.7rem;">Dataset: ${datasetVersions.map(escHtml).join(', ')}</span>` : ''}
          <button class="btn btn-sm btn-outline-danger" style="font-size:.76rem;padding:2px 8px;" onclick="unlinkAllScans('${escHtml(r.object_code || '')}')"><i class="bi bi-x-lg me-1"></i>Tá»« chá»‘i nhÃ³m</button>
        </div>
      </div>`;
    }

    function focusPendingTrainingImages(btn) {
      const card = btn.closest('.td-card');
      if (!card) return;
      if (!btn.dataset.defaultHtml) btn.dataset.defaultHtml = btn.innerHTML;
      const isFiltered = btn.dataset.filtered === '1';
      if (isFiltered) {
        // Bá» filter â€” hiá»‡n láº¡i táº¥t cáº£
        card.querySelectorAll('.td-thumb-wrap').forEach(showTrainingThumb);
        // KhÃ´i phá»¥c nÃºt Thu gá»n náº¿u Ä‘ang bá»‹ áº©n
        const collapseBtn = card.querySelector('.td-collapse-btn');
        if (collapseBtn) collapseBtn.style.display = '';
        btn.dataset.filtered = '0';
        btn.innerHTML = btn.dataset.defaultHtml || `<i class="bi bi-hourglass-split me-1"></i>Xem áº£nh chá»`;
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-warning');
      } else {
        // Náº¿u cÃ²n nÃºt +N chÆ°a expand â†’ expand trÆ°á»›c
        const moreBtn = card.querySelector('.td-more-count:not(.td-collapse-btn)');
        if (moreBtn) {
          moreBtn.click(); // sync â€” insert HTML ngay láº­p tá»©c
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
      // áº¨n nÃºt Thu gá»n khi Ä‘ang filter (sáº½ khÃ´i phá»¥c khi bá» filter)
      const collapseBtn = card.querySelector('.td-collapse-btn');
      if (collapseBtn) collapseBtn.style.display = 'none';
      btn.dataset.filtered = '1';
      btn.innerHTML = `<i class="bi bi-grid me-1"></i>Xem táº¥t cáº£`;
      btn.classList.remove('btn-warning');
      btn.classList.add('btn-secondary');
      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function loadTrainingData() {
      const grid = document.getElementById('td-grid');
      const empty = document.getElementById('td-empty');
      grid.innerHTML = '<div class="text-muted p-3"><span class="spinner-border spinner-border-sm me-2"></span>Äang táº£i...</div>';
      empty.style.display = 'none';
      try {
        let records = await apiJSON(buildTrainingSummaryPath());
        if (!records || records.length === 0) {
          grid.innerHTML = '';
          empty.style.display = '';
          _renderTrainingKpis({ objects: 0, images: 0, pending: 0, approved: 0, rejected: 0, priority: 0 });
          return;
        }
        // KPI luÃ´n tÃ­nh trÃªn toÃ n bá»™ data trÆ°á»›c khi filter client-side
        _renderTrainingKpis({
          objects: records.length,
          images: records.reduce((s, r) => s + (r.total_images || 0), 0),
          pending: records.reduce((s, r) => s + ((r.status_counts || {}).cho_duyet || 0), 0),
          approved: records.reduce((s, r) => s + ((r.status_counts || {}).da_duyet || 0), 0),
          rejected: records.reduce((s, r) => s + ((r.status_counts || {}).tu_choi || 0), 0),
          priority: records.filter(r => ['high_priority', 'recommended'].includes(r.training_recommendation)).length,
        });
        // Preset 'ready': filter client-side vÃ¬ backend khÃ´ng cÃ³ param nÃ y
        if (_trainingPreset === 'ready') {
          records = records.filter(r => r.ready_for_dataset);
        }
        if (!records.length) { grid.innerHTML = ''; empty.style.display = ''; return; }
        grid.innerHTML = records.map(renderTrainingCard).join('');
      } catch (e) {
        grid.innerHTML = `<div class="text-danger p-3">Lá»—i: ${e.message}</div>`;
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
            `<button class="btn-act${img.status === 'da_duyet' ? ' disabled' : ''}" ${img.status === 'da_duyet' ? 'disabled' : `onclick="approveTrainingImage(${img.id},'${objectCode}',this)"`} title="Duyá»‡t"><i class="bi bi-check-lg"></i></button>`,
            `<button class="btn-act del${img.status === 'tu_choi' ? ' disabled' : ''}" ${img.status === 'tu_choi' ? 'disabled' : `onclick="unlinkScan(${img.id},'${objectCode}',this)"`} title="Tá»« chá»‘i"><i class="bi bi-x-lg"></i></button>`,
            `<button class="btn-act" onclick="openReassign(${img.id},'${objectCode}')" title="Chuyá»ƒn object"><i class="bi bi-arrow-left-right"></i></button>`,
            `<button class="btn-act del" onclick="deleteTrainingImage(${img.id},'${objectCode}',this)" title="XÃ³a"><i class="bi bi-trash3"></i></button>`,
          ].join('') : '';
          const qualityFlag = _qualityFlag(img.note, img.quality_score);
          const pendingReason = img.status === 'cho_duyet' ? _pendingReason(img) : '';
          const thumbTitle = [trainingSourceLabel(img.source), trainingStatusLabel(img.status), pendingReason].filter(Boolean).join(' â€” ');
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
        // thay nÃºt +N báº±ng áº£nh cÃ²n láº¡i + nÃºt thu gá»n
        btn.insertAdjacentHTML('beforebegin', html);
        btn.outerHTML = `<button class="td-more-count td-collapse-btn" onclick="collapseTrainingCard(this)" data-images="${escHtml(imagesData)}" data-initial="${escHtml(initialData)}" data-object-code="${safeObjectCode}" data-remaining="${remaining.length}" style="background:#f1f5f9;color:#475569;border-color:#cbd5e1;font-size:.72rem;"><i class="bi bi-chevron-up"></i><br><span style="font-size:.65rem;">Thu gá»n</span></button>`;
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
      // KhÃ´i phá»¥c táº¥t cáº£ thumb cÃ²n láº¡i (initial thumbnails) vá» hiá»ƒn thá»‹ bÃ¬nh thÆ°á»ng
      grid.querySelectorAll('.td-thumb-wrap').forEach(showTrainingThumb);
      // Reset nÃºt "Xem áº£nh chá»" náº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i filter
      if (card) {
        const filterBtn = card.querySelector('.btn-warning[data-filtered], .btn-secondary[data-filtered]');
        if (filterBtn && filterBtn.dataset.filtered === '1') {
          filterBtn.dataset.filtered = '0';
          filterBtn.innerHTML = filterBtn.dataset.defaultHtml || `<i class="bi bi-hourglass-split me-1"></i>Xem áº£nh chá»`;
          filterBtn.classList.remove('btn-secondary');
          filterBtn.classList.add('btn-warning');
        }
      }
      if (remaining > 0) {
        btn.outerHTML = `<button class="td-more-count" onclick="expandTrainingCard(this,this.dataset.objectCode)" data-images="${escHtml(imagesData)}" data-initial="${escHtml(initialData)}" data-object-code="${escHtml(objectCode)}">+${remaining}<br><span style="font-size:.65rem;font-weight:400;">áº£nh</span></button>`;
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
      // disable buttons theo tráº¡ng thÃ¡i má»›i
      wrap.querySelectorAll('.btn-act').forEach(b => {
        const isApprove = b.title === 'Duyá»‡t';
        const isUnlink  = b.title === 'Tá»« chá»‘i';
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
        toast(`ÄÃ£ duyá»‡t áº£nh cho "${objectCode}"`, 'success');
        if (!_thumbUpdateInPlace(btnEl, 'da_duyet')) loadTrainingData();
      } catch (e) {
        toast('Lá»—i: ' + e.message, 'danger');
      }
    }

    async function unlinkScan(trainingImageId, objectCode, btnEl) {
      confirmAction(
        `Tá»« chá»‘i áº£nh nÃ y khá»i táº­p training cá»§a "${objectCode}"? áº¢nh váº«n Ä‘Æ°á»£c giá»¯ trong lá»‹ch sá»­ quÃ©t, chá»‰ khÃ´ng dÃ¹ng Ä‘á»ƒ train.`,
        async () => {
          try {
            await apiJSON(`/training-images/${trainingImageId}/unlink`, { method: 'PATCH' });
            toast('ÄÃ£ tá»« chá»‘i áº£nh training', 'success');
            if (!_thumbUpdateInPlace(btnEl, 'tu_choi')) loadTrainingData();
          } catch (e) {
            toast('Lá»—i: ' + e.message, 'danger');
          }
        },
        'Tá»« chá»‘i áº£nh'
      );
    }

    async function deleteTrainingImage(trainingImageId, objectCode, btnEl) {
      confirmAction(
        `XÃ³a áº£nh nÃ y khá»i Training Data cá»§a "${objectCode}"? Lá»‹ch sá»­ quÃ©t vÃ  prediction liÃªn quan váº«n Ä‘Æ°á»£c giá»¯.`,
        async () => {
          try {
            await apiJSON(`/training-images/${trainingImageId}`, { method: 'DELETE' });
            toast('ÄÃ£ xÃ³a áº£nh khá»i Training Data', 'success');
            const wrap = btnEl?.closest('.td-thumb-wrap');
            const card = wrap?.closest('.td-card');
            if (wrap) {
              wrap.style.transition = 'opacity .2s';
              wrap.style.opacity = '0';
              setTimeout(() => {
                wrap.remove();
                // Náº¿u card khÃ´ng cÃ²n áº£nh nÃ o da_duyet/cho_duyet â†’ áº©n card
                const remaining = card?.querySelectorAll('.td-thumb-wrap[data-status="cho_duyet"], .td-thumb-wrap[data-status="da_duyet"]');
                if (card && remaining && remaining.length === 0) {
                  card.style.transition = 'opacity .3s';
                  card.style.opacity = '0';
                  setTimeout(() => card.remove(), 300);
                }
              }, 200);
            } else loadTrainingData();
          } catch (e) {
            toast('Lá»—i: ' + e.message, 'danger');
          }
        },
        'XÃ³a áº£nh'
      );
    }

    async function unlinkAllScans(objectCode) {
      confirmAction(
        `Tá»« chá»‘i toÃ n bá»™ áº£nh training cá»§a "${objectCode}"?\n(áº¢nh váº«n giá»¯ trong lá»‹ch sá»­ quÃ©t, chá»‰ khÃ´ng dÃ¹ng Ä‘á»ƒ train)`,
        async () => {
          try {
            const res = await apiJSON(`/training-images/unlink-all?object_code=${encodeURIComponent(objectCode)}`, { method: 'DELETE' });
            toast(`ÄÃ£ tá»« chá»‘i ${res.count} áº£nh cá»§a "${objectCode}"`, 'warning');
            loadTrainingData();
          } catch (e) {
            toast('Lá»—i: ' + e.message, 'danger');
          }
        },
        'Tá»« chá»‘i táº¥t cáº£'
      );
    }

    function createDatasetVersion() {
      document.getElementById('fm-title').textContent = 'Táº¡o phiÃªn báº£n Dataset';
      document.getElementById('fm-submit').style.display = '';
      document.getElementById('fm-body').innerHTML = `
        <div class="mb-3">
          <label class="form-label fw-semibold">MÃ£ phiÃªn báº£n <span class="text-danger">*</span></label>
          <input id="dv-version" class="form-control" placeholder="v1" value="v1" />
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold">Ghi chÃº</label>
          <input id="dv-note" class="form-control" placeholder="MÃ´ táº£ ngáº¯n cho phiÃªn báº£n nÃ y (khÃ´ng báº¯t buá»™c)" />
        </div>`;
      const modal = new bootstrap.Modal(document.getElementById('form-modal'));
      document.getElementById('fm-submit').onclick = async () => {
        const version = document.getElementById('dv-version')?.value.trim();
        const note = document.getElementById('dv-note')?.value.trim() || '';
        if (!version) { toast('Vui lÃ²ng nháº­p mÃ£ phiÃªn báº£n', 'warning'); return; }
        try {
          document.getElementById('fm-submit').disabled = true;
          const res = await apiJSON(`/training-datasets?ma_phien_ban=${encodeURIComponent(version)}&ghi_chu=${encodeURIComponent(note)}`, { method: 'POST' });
          toast(`ÄÃ£ táº¡o dataset ${res.ma_phien_ban}: ${res.tong_anh} áº£nh, ${res.tong_nhan} nhÃ£n`, 'success');
          modal.hide();
          loadTrainingData();
        } catch (e) {
          toast('Lá»—i: ' + e.message, 'danger');
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
        `<option value="${o.ma_doi_tuong}">${o.ma_doi_tuong}${o.category_name ? ' â€” ' + o.category_name : ''}</option>`
      ).join('') || '<option disabled>KhÃ´ng tÃ¬m tháº¥y</option>';
    }

    document.getElementById('rm-search').addEventListener('input', e => _fillReassignSelect(e.target.value));

    document.getElementById('rm-confirm').addEventListener('click', async () => {
      const sel = document.getElementById('rm-select');
      const targetCode = sel.value;
      if (!targetCode || sel.options[sel.selectedIndex]?.disabled) {
        toast('Vui lÃ²ng chá»n Ä‘á»‘i tÆ°á»£ng Ä‘Ã­ch', 'warning'); return;
      }
      try {
        if (_reassignMode === 'review') {
          await apiJSON(`/predictions/${_reassignPredictionId}/reassign-image?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        } else {
          await apiJSON(`/training-images/${_reassignTrainingImageId}/reassign?target_object_code=${encodeURIComponent(targetCode)}`, { method: 'PATCH' });
        }
        bootstrap.Modal.getInstance(document.getElementById('reassign-modal')).hide();
        toast(`ÄÃ£ chuyá»ƒn áº£nh sang "${targetCode}"`, 'success');
        _cachedObjects = [];
        if (_reassignMode === 'review') {
          openPredModal(_reassignRootPredictionId);
          loadPredictions();
          updatePendingBadge();
        } else {
          loadTrainingData();
        }
      } catch (e) {
        toast('Lá»—i: ' + e.message, 'danger');
      }
    });

