    // ============================================================
    // Startup
    // ============================================================
    if (TOKEN) {
      document.getElementById('login-screen').style.display = 'none';
      initApp();
    }
    document.getElementById('login-pass').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });

    // Fix: Bootstrap dropdown bị cắt bởi overflow:hidden / overflow-x:auto của .panel và .table-responsive
    // Khi dropdown mở, tạm thời set overflow = visible trên các phần tử cha bị hạn chế
    document.addEventListener('show.bs.dropdown', function (e) {
      const parents = [];
      let el = e.target.parentElement;
      while (el) {
        const style = window.getComputedStyle(el);
        const ov = style.overflow + ' ' + style.overflowX + ' ' + style.overflowY;
        if (/auto|hidden|scroll/.test(ov)) {
          parents.push({ el, overflow: el.style.overflow, overflowX: el.style.overflowX, overflowY: el.style.overflowY });
          el.style.overflow = 'visible';
          el.style.overflowX = 'visible';
          el.style.overflowY = 'visible';
        }
        el = el.parentElement;
      }
      e.target._dropdownFixParents = parents;
    });

    document.addEventListener('hide.bs.dropdown', function (e) {
      const parents = e.target._dropdownFixParents || [];
      parents.forEach(({ el, overflow, overflowX, overflowY }) => {
        el.style.overflow = overflow;
        el.style.overflowX = overflowX;
        el.style.overflowY = overflowY;
      });
      e.target._dropdownFixParents = [];
    });

