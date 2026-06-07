    // ============================================================

    // Scan History

    // ============================================================

    async function loadScanHistory() {

      const objCode = document.getElementById('sh-obj-search')?.value.trim() || '';

      const username = document.getElementById('sh-user-search')?.value.trim() || '';

      const dateFrom = document.getElementById('sh-date-from')?.value || '';

      const dateTo = document.getElementById('sh-date-to')?.value || '';

      const tbody = document.getElementById('sh-body');

      if (tbody) tbody.innerHTML = loadingRow(9);

      try {

        let url = `/scan-history?limit=1000`;
        if (objCode) url += `&object_code=${encodeURIComponent(objCode)}`;

        if (username) url += `&username=${encodeURIComponent(username)}`;

        if (dateFrom) url += `&date_from=${dateFrom}`;

        if (dateTo) url += `&date_to=${dateTo}`;

        const data = await apiJSON(url);

        const pageData = getPagedRows('scanHistory', data);

        renderTablePagination('scanHistory', pageData);

        const countEl = document.querySelector('#section-scan-history .result-count');

        if (countEl) countEl.textContent = data.length ? `${data.length} lượt quét` : '';

        const checkAll = document.getElementById('sh-check-all');

        if (checkAll) checkAll.checked = false;

        updateScanHistoryBulkBtn();

        document.getElementById('sh-body').innerHTML = data.length

          ? pageData.rows.map((s, idx) => `

          <tr>

            <td><input type="checkbox" class="sh-row-check" value="${s.id}" onchange="updateScanHistoryBulkBtn()" /></td>

            <td class="stt-cell">${pageData.start + idx + 1}</td>

            <td class="text-muted">${s.id}</td>

            <td>${s.url_anh

              ? `<img src="${s.url_anh}" style="width:40px;height:40px;object-fit:cover;border-radius:4px;cursor:zoom-in;" onclick="zoomImage('${s.url_anh}')" title="Nhấn để xem to" />`

              : '<span class="text-muted" style="font-size:.75rem;">N/A</span>'}</td>

            <td>

              <span class="fw-semibold" style="font-size:.82rem;">${s.username || '—'}</span>

              ${s.nguoi_dung_id ? `<br><span class="text-muted" style="font-size:.72rem;">ID: ${s.nguoi_dung_id}</span>` : ''}

            </td>

            <td>${s.object_code

              ? `<code class="cell-ellipsis code-cell" title="${escHtml(s.object_code)}">${escHtml(s.object_code)}</code>`

              : s.has_pending_prediction

                ? '<span class="badge badge-pending">Chờ duyệt</span>'

                : '<span class="text-muted">—</span>'}</td>

            <td>${confBar(s.do_tin_cay)}</td>

            <td class="text-muted" style="font-size:.82rem;">${fmtDate(s.thoi_gian)}</td>

            <td><button class="btn btn-sm btn-outline-danger" style="padding:2px 7px;font-size:.75rem;" onclick="deleteScanHistory(${s.id})" title="Xóa"><i class="bi bi-trash"></i></button></td>

          </tr>`).join('')

          : '<tr><td colspan="9"><div class="empty-state"><i class="bi bi-camera"></i><p>Chưa có lịch sử quét</p></div></td></tr>';

      } catch (e) { toast('Lỗi tải lịch sử quét: ' + e.message, 'danger'); }

    }



    async function deleteScanHistory(scanId) {

      confirmAction(

        `Xóa lịch sử quét #${scanId}?`,

        async () => {

          try {

            await apiJSON(`/scan-history/${scanId}`, { method: 'DELETE' });

            toast('Đã xóa lịch sử quét', 'success');

            loadScanHistory();

          } catch (e) { toast('Lỗi xóa: ' + e.message, 'danger'); }

        },

        'Xóa lịch sử'

      );

    }



    function getSelectedScanIds() {

      return Array.from(document.querySelectorAll('.sh-row-check:checked')).map(cb => parseInt(cb.value));

    }



    function updateScanHistoryBulkBtn() {

      const ids = getSelectedScanIds();

      const btn = document.getElementById('sh-bulk-delete-btn');

      const countEl = document.getElementById('sh-selected-count');

      if (!btn) return;

      if (ids.length > 0) {

        btn.style.display = '';

        if (countEl) countEl.textContent = ids.length;

      } else {

        btn.style.display = 'none';

      }

    }



    function toggleAllScanHistory(checkbox) {

      document.querySelectorAll('.sh-row-check').forEach(cb => cb.checked = checkbox.checked);

      updateScanHistoryBulkBtn();

    }



    async function bulkDeleteScanHistory() {

      const ids = getSelectedScanIds();

      if (!ids.length) return;

      confirmAction(`Xóa ${ids.length} lịch sử quét đã chọn?`, async () => {

        try {

          const params = ids.map(id => `ids=${id}`).join('&');

          await apiJSON(`/scan-history/bulk?${params}`, { method: 'DELETE' });

          toast(`Đã xóa ${ids.length} lịch sử quét`, 'warning');

          loadScanHistory();

        } catch (e) { toast('Lỗi: ' + e.message, 'danger'); }

      }, 'Xóa hàng loạt');

    }
