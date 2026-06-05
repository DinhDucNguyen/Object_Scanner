    // ============================================================
    // Batch Predictions
    // ============================================================
    function toggleAllPredChecks(masterCb) {
      document.querySelectorAll('.pred-check').forEach(cb => { cb.checked = masterCb.checked; });
      updateBatchToolbar();
    }

    function updateBatchToolbar() {
      const checked = document.querySelectorAll('.pred-check:checked');
      const toolbar = document.getElementById('batch-toolbar');
      if (!toolbar) return;
      if (checked.length > 0) {
        toolbar.classList.remove('is-hidden');
        document.getElementById('batch-count').textContent = checked.length;
      } else {
        toolbar.classList.add('is-hidden');
      }
    }

    async function batchApprove() {
      const ids = Array.from(document.querySelectorAll('.pred-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) { toast('Chá»n Ã­t nháº¥t 1 prediction', 'warning'); return; }
      confirmAction(`Duyá»‡t ${ids.length} prediction Ä‘Ã£ chá»n?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) }); ok++; }
          catch { fail++; }
        }
        toast(`ÄÃ£ duyá»‡t ${ok} prediction${fail ? `, ${fail} lá»—i` : ''}`, fail ? 'warning' : 'success');
        loadPredictions(); updatePendingBadge();
      }, 'Duyá»‡t hÃ ng loáº¡t');
    }

    async function batchReject() {
      const ids = Array.from(document.querySelectorAll('.pred-check:checked')).map(el => parseInt(el.dataset.id));
      if (!ids.length) { toast('Chá»n Ã­t nháº¥t 1 prediction', 'warning'); return; }
      confirmAction(`Tá»« chá»‘i ${ids.length} prediction Ä‘Ã£ chá»n?`, async () => {
        let ok = 0, fail = 0;
        for (const id of ids) {
          try { await apiJSON(`/predictions/${id}/reject`, { method: 'POST' }); ok++; }
          catch { fail++; }
        }
        toast(`ÄÃ£ tá»« chá»‘i ${ok} prediction${fail ? `, ${fail} lá»—i` : ''}`, 'warning');
        loadPredictions(); updatePendingBadge();
      }, 'Tá»« chá»‘i hÃ ng loáº¡t');
    }

