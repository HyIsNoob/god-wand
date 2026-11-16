document.addEventListener('DOMContentLoaded', () => {
  // 1. Language Toggle (EN / VI / ZH) — Default: English
  const langToggleBtn = document.getElementById('lang-toggle');
  const searchInput = document.querySelector('.search-input');
  
  let currentLang = localStorage.getItem('godwand_wiki_lang') || 'en';
  setLanguage(currentLang);

  if (langToggleBtn) {
    langToggleBtn.addEventListener('click', () => {
      currentLang = currentLang === 'en' ? 'vi' : currentLang === 'vi' ? 'zh' : 'en';
      setLanguage(currentLang);
    });
  }

  function setLanguage(lang) {
    document.body.setAttribute('data-lang', lang);
    document.documentElement.setAttribute('lang', lang);
    localStorage.setItem('godwand_wiki_lang', lang);
    
    const langLabel = langToggleBtn ? (langToggleBtn.querySelector('.lang-label') || langToggleBtn.querySelector('span')) : null;
    if (langLabel) {
      langLabel.textContent = lang === 'vi' ? 'VI' : lang === 'zh' ? '中文' : 'EN';
    }
    if (langToggleBtn) {
      langToggleBtn.title = lang === 'vi' 
        ? 'Chuyển ngôn ngữ (Hiện tại: Tiếng Việt)' 
        : lang === 'zh'
        ? '切换语言（当前：简体中文）'
        : 'Switch language (Current: English)';
    }

    if (searchInput) {
      searchInput.placeholder = lang === 'vi' 
        ? 'Tìm kiếm cấu hình, chiêu thức... (Ctrl + K)' 
        : lang === 'zh'
        ? '搜索配置项、技能... (Ctrl + K)'
        : 'Search config keys, skills... (Ctrl + K)';
    }

    updateThemeLabel();
  }

  function updateThemeLabel() {
    const isLight = document.documentElement.getAttribute('data-theme') === 'light';
    const themeLabel = document.querySelector('.theme-label');
    if (themeLabel) {
      if (currentLang === 'vi') {
        themeLabel.textContent = isLight ? 'Tối' : 'Sáng';
      } else if (currentLang === 'zh') {
        themeLabel.textContent = isLight ? '深色' : '浅色';
      } else {
        themeLabel.textContent = isLight ? 'Dark' : 'Light';
      }
    }
  }

  // 2. Theme Toggle (Dark / Light)
  const themeToggleBtn = document.getElementById('theme-toggle');
  let currentTheme = localStorage.getItem('godwand_wiki_theme') || 'dark';
  setTheme(currentTheme);

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      currentTheme = currentTheme === 'light' ? 'dark' : 'light';
      setTheme(currentTheme);
    });
  }

  function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('godwand_wiki_theme', theme);
    updateThemeLabel();
  }

  // 3. Mobile Menu Toggle & Backdrop
  const menuToggle = document.querySelector('.menu-toggle');
  const sidebar = document.querySelector('aside.sidebar');
  const backdrop = document.querySelector('.sidebar-backdrop');

  function closeMobileSidebar() {
    if (sidebar) sidebar.classList.remove('open');
    if (backdrop) backdrop.classList.remove('open');
  }

  if (menuToggle && sidebar) {
    menuToggle.addEventListener('click', () => {
      const isOpen = sidebar.classList.toggle('open');
      if (backdrop) backdrop.classList.toggle('open', isOpen);
    });

    if (backdrop) {
      backdrop.addEventListener('click', closeMobileSidebar);
    }

    sidebar.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        if (window.innerWidth <= 900) {
          closeMobileSidebar();
        }
      });
    });
  }

  // 4. In-Page Skill Quick Filter Tabs
  const skillTabBtns = document.querySelectorAll('.skill-tab-btn');
  const skillCards = document.querySelectorAll('.skill-card');

  skillTabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      skillTabBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const filter = btn.getAttribute('data-filter');
      skillCards.forEach(card => {
        if (filter === 'all' || card.getAttribute('data-skill') === filter) {
          card.style.display = '';
        } else {
          card.style.display = 'none';
        }
      });
    });
  });

  // 5. Toast Notification
  const toast = document.getElementById('toast');
  function showToast(msg) {
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => {
      toast.classList.remove('show');
    }, 1800);
  }

  // 6. Copy Code Buttons
  document.querySelectorAll('.copy-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const codeBox = btn.closest('.code-box');
      const codeEl = codeBox.querySelector('pre code') || codeBox.querySelector('pre');
      if (codeEl) {
        navigator.clipboard.writeText(codeEl.innerText.trim()).then(() => {
          const toastMsg = currentLang === 'vi' 
            ? 'Đã sao chép vào bộ nhớ tạm' 
            : currentLang === 'zh'
            ? '已复制到剪贴板'
            : 'Copied to clipboard';
          showToast(toastMsg);
          const viSpan = btn.querySelector('.lang-vi');
          const enSpan = btn.querySelector('.lang-en');
          const zhSpan = btn.querySelector('.lang-zh');
          if (viSpan && enSpan) {
            const origVi = viSpan.textContent;
            const origEn = enSpan.textContent;
            const origZh = zhSpan ? zhSpan.textContent : '';
            viSpan.textContent = 'Đã chép';
            enSpan.textContent = 'Copied';
            if (zhSpan) zhSpan.textContent = '已复制';
            setTimeout(() => {
              viSpan.textContent = origVi;
              enSpan.textContent = origEn;
              if (zhSpan) zhSpan.textContent = origZh;
            }, 1600);
          }
        }).catch(() => {
          const failMsg = currentLang === 'vi' 
            ? 'Lỗi khi sao chép' 
            : currentLang === 'zh'
            ? '复制失败'
            : 'Copy failed';
          showToast(failMsg);
        });
      }
    });
  });

  // 7. ScrollSpy for Sidebar Navigation
  const navLinks = document.querySelectorAll('.sidebar-nav a');
  const sections = document.querySelectorAll('section.doc-section');

  window.addEventListener('scroll', () => {
    let currentId = '';
    const scrollPos = window.scrollY + 100;

    sections.forEach(sec => {
      const top = sec.offsetTop;
      const height = sec.offsetHeight;
      if (scrollPos >= top && scrollPos < top + height) {
        currentId = sec.getAttribute('id');
      }
    });

    if (currentId) {
      navLinks.forEach(link => {
        link.classList.toggle('active', link.getAttribute('href') === `#${currentId}`);
      });
    }
  });

  // 8. Search Filter with Table Category Preservation & Empty State
  const searchShortcut = document.querySelector('.search-shortcut');
  if (searchShortcut && typeof navigator !== 'undefined' && /(Mac|iPhone|iPod|iPad)/i.test(navigator.platform || navigator.userAgent)) {
    searchShortcut.textContent = '⌘K';
  }

  if (searchInput) {
    const emptyState = document.getElementById('search-empty');

    searchInput.addEventListener('input', (e) => {
      const query = e.target.value.toLowerCase().trim();
      const skillCards = document.querySelectorAll('.skill-card');
      const generalCards = document.querySelectorAll('.card');
      const tables = document.querySelectorAll('table.data-table');

      let totalVisible = 0;

      if (!query) {
        skillCards.forEach(c => { c.style.display = ''; });
        generalCards.forEach(c => { c.style.display = ''; });
        tables.forEach(table => {
          table.querySelectorAll('tbody tr').forEach(tr => { tr.style.display = ''; });
          const wrapper = table.closest('.table-wrapper') || table;
          wrapper.style.display = '';
        });
        if (emptyState) emptyState.style.display = 'none';
        return;
      }

      // Filter skill cards
      skillCards.forEach(card => {
        const match = card.innerText.toLowerCase().includes(query);
        card.style.display = match ? '' : 'none';
        if (match) totalVisible++;
      });

      // Filter general cards
      generalCards.forEach(card => {
        const match = card.innerText.toLowerCase().includes(query);
        card.style.display = match ? '' : 'none';
        if (match) totalVisible++;
      });

      // Filter tables with category header preservation
      tables.forEach(table => {
        const rows = Array.from(table.querySelectorAll('tbody tr'));
        let tableVisibleCount = 0;
        let currentCategoryRow = null;
        let categoryMatches = 0;

        rows.forEach(tr => {
          const isCategoryHeader = tr.querySelector('td[colspan]');
          if (isCategoryHeader) {
            if (currentCategoryRow && categoryMatches === 0) {
              currentCategoryRow.style.display = 'none';
            }
            currentCategoryRow = tr;
            categoryMatches = 0;
            tr.style.display = '';
          } else {
            const match = tr.innerText.toLowerCase().includes(query);
            tr.style.display = match ? '' : 'none';
            if (match) {
              categoryMatches++;
              tableVisibleCount++;
            }
          }
        });

        if (currentCategoryRow && categoryMatches === 0) {
          currentCategoryRow.style.display = 'none';
        }

        const wrapper = table.closest('.table-wrapper') || table;
        wrapper.style.display = tableVisibleCount > 0 ? '' : 'none';
        totalVisible += tableVisibleCount;
      });

      if (emptyState) {
        emptyState.style.display = totalVisible === 0 ? 'block' : 'none';
      }
    });

    window.addEventListener('keydown', (e) => {
      if (((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') || (e.key === '/' && document.activeElement !== searchInput)) {
        e.preventDefault();
        searchInput.focus();
      }
      if (e.key === 'Escape' && document.activeElement === searchInput) {
        searchInput.value = '';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.blur();
      }
    });
  }
});
