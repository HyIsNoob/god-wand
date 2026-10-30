document.addEventListener('DOMContentLoaded', () => {
  // 1. Language Toggle (EN / VI) — Default: English
  const langToggleBtn = document.getElementById('lang-toggle');
  const searchInput = document.querySelector('.search-input');
  
  let currentLang = localStorage.getItem('godwand_wiki_lang') || 'en';
  setLanguage(currentLang);

  if (langToggleBtn) {
    langToggleBtn.addEventListener('click', () => {
      currentLang = currentLang === 'en' ? 'vi' : 'en';
      setLanguage(currentLang);
    });
  }

  function setLanguage(lang) {
    document.body.setAttribute('data-lang', lang);
    document.documentElement.setAttribute('lang', lang);
    localStorage.setItem('godwand_wiki_lang', lang);
    if (searchInput) {
      searchInput.placeholder = lang === 'vi' 
        ? 'Tìm kiếm cấu hình, chiêu thức... (Ctrl + K)' 
        : 'Search config keys, skills... (Ctrl + K)';
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
    const themeLabel = document.querySelector('.theme-label');
    if (themeLabel) {
      themeLabel.textContent = theme === 'light' ? 'Dark' : 'Light';
    }
  }

  // 3. Mobile Menu Toggle
  const menuToggle = document.querySelector('.menu-toggle');
  const sidebar = document.querySelector('aside.sidebar');
  if (menuToggle && sidebar) {
    menuToggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
    });

    sidebar.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        if (window.innerWidth <= 860) {
          sidebar.classList.remove('open');
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
          showToast(currentLang === 'vi' ? 'Đã sao chép vào bộ nhớ tạm' : 'Copied to clipboard');
          const viSpan = btn.querySelector('.lang-vi');
          const enSpan = btn.querySelector('.lang-en');
          if (viSpan && enSpan) {
            const origVi = viSpan.textContent;
            const origEn = enSpan.textContent;
            viSpan.textContent = 'Đã chép';
            enSpan.textContent = 'Copied';
            setTimeout(() => {
              viSpan.textContent = origVi;
              enSpan.textContent = origEn;
            }, 1600);
          }
        }).catch(() => {
          showToast(currentLang === 'vi' ? 'Lỗi khi sao chép' : 'Copy failed');
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

  // 8. Search Filter
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const query = e.target.value.toLowerCase().trim();
      const searchableItems = document.querySelectorAll('.skill-card, .card, table.data-table tbody tr');

      if (!query) {
        searchableItems.forEach(item => { item.style.display = ''; });
        return;
      }

      searchableItems.forEach(item => {
        item.style.display = item.innerText.toLowerCase().includes(query) ? '' : 'none';
      });
    });

    window.addEventListener('keydown', (e) => {
      if ((e.ctrlKey && e.key.toLowerCase() === 'k') || (e.key === '/' && document.activeElement !== searchInput)) {
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
