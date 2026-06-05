    // ============================================================
    // Startup
    // ============================================================
    if (TOKEN) {
      document.getElementById('login-screen').style.display = 'none';
      initApp();
    }
    document.getElementById('login-pass').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });
