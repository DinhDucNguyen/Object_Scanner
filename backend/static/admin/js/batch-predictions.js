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

      if (!ids.length) { toast('Chọn ít nhất 1 prediction', 'warning'); return; }

      confirmAction(`Duyệt ${ids.length} prediction đã chọn?`, async () => {

        let ok = 0, fail = 0;

        for (const id of ids) {

          try { await apiJSON(`/predictions/${id}/approve`, { method: 'POST', body: JSON.stringify({}) }); ok++; }

          catch { fail++; }

        }

        toast(`Đã duyệt ${ok} prediction${fail ? `, ${fail} lỗi` : ''}`, fail ? 'warning' : 'success');

        loadPredictions(); updatePendingBadge();

      }, 'Duyệt hàng loạt');

    }



    async function batchReject() {

      const ids = Array.from(document.querySelectorAll('.pred-check:checked')).map(el => parseInt(el.dataset.id));

      if (!ids.length) { toast('Chọn ít nhất 1 prediction', 'warning'); return; }

      confirmAction(`Từ chối ${ids.length} prediction đã chọn?`, async () => {

        let ok = 0, fail = 0;

        for (const id of ids) {

          try { await apiJSON(`/predictions/${id}/reject`, { method: 'POST' }); ok++; }

          catch { fail++; }

        }

        toast(`Đã từ chối ${ok} prediction${fail ? `, ${fail} lỗi` : ''}`, 'warning');

        loadPredictions(); updatePendingBadge();

      }, 'Từ chối hàng loạt');

    }
