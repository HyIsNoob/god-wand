document.addEventListener('DOMContentLoaded', () => {
  // 1. Language Dropdown (EN / VI / ZH) + URL ?lang= sync
  const langDropdown = document.getElementById('lang-dropdown');
  const langToggleBtn = document.getElementById('lang-toggle');
  const langOptions = document.querySelectorAll('.lang-option');
  const searchInput = document.querySelector('.search-input');
  const headerSearch = document.querySelector('.header-search');

  const urlParams = new URLSearchParams(window.location.search);
  const urlLang = urlParams.get('lang');
  const validLang = (urlLang === 'en' || urlLang === 'vi' || urlLang === 'zh') ? urlLang : null;
  let currentLang = validLang || localStorage.getItem('godwand_wiki_lang') || 'en';
  let runSearch = () => {};

  setLanguage(currentLang, false);

  function closeLangMenu() {
    if (!langDropdown) return;
    langDropdown.classList.remove('open');
    if (langToggleBtn) langToggleBtn.setAttribute('aria-expanded', 'false');
  }

  if (langToggleBtn && langDropdown) {
    langToggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const willOpen = !langDropdown.classList.contains('open');
      langDropdown.classList.toggle('open', willOpen);
      langToggleBtn.setAttribute('aria-expanded', String(willOpen));
    });

    langOptions.forEach(opt => {
      opt.addEventListener('click', () => {
        const chosen = opt.getAttribute('data-lang-opt');
        if (chosen && chosen !== currentLang) {
          currentLang = chosen;
          setLanguage(currentLang, true);
          window.dispatchEvent(new CustomEvent('godwand:langchange', { detail: currentLang }));
        }
        closeLangMenu();
      });
    });

    document.addEventListener('click', (e) => {
      if (!langDropdown.contains(e.target)) closeLangMenu();
    });

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && langDropdown.classList.contains('open')) {
        closeLangMenu();
        langToggleBtn.focus();
      }
    });
  }

  function syncLangUrl(lang) {
    try {
      const url = new URL(window.location.href);
      if (lang === 'en') url.searchParams.delete('lang');
      else url.searchParams.set('lang', lang);
      window.history.replaceState(null, '', url.toString());
    } catch {
      // Ignore on restricted origins
    }
  }

  function setLanguage(lang, updateUrl) {
    document.body.setAttribute('data-lang', lang);
    document.documentElement.setAttribute('lang', lang);
    localStorage.setItem('godwand_wiki_lang', lang);
    if (updateUrl) syncLangUrl(lang);

    const langLabel = langToggleBtn ? (langToggleBtn.querySelector('.lang-label') || langToggleBtn.querySelector('span')) : null;
    if (langLabel) {
      langLabel.textContent = lang === 'vi' ? 'VI' : lang === 'zh' ? '中文' : 'EN';
    }
    langOptions.forEach(opt => {
      const isActive = opt.getAttribute('data-lang-opt') === lang;
      opt.classList.toggle('active', isActive);
      opt.setAttribute('aria-checked', String(isActive));
    });
    if (langToggleBtn) {
      langToggleBtn.title = lang === 'vi'
        ? 'Chọn ngôn ngữ (Tiếng Việt)'
        : lang === 'zh'
        ? '选择语言（简体中文）'
        : 'Select language (English)';
    }

    if (searchInput) {
      searchInput.placeholder = lang === 'vi'
        ? 'Tìm key cấu hình, kỹ năng... (Ctrl + K)'
        : lang === 'zh'
        ? '搜索配置项、技能... (Ctrl + K)'
        : 'Search config keys, skills... (Ctrl + K)';
    }

    updateThemeLabel();
    if (searchInput && searchInput.value.trim()) {
      runSearch(searchInput.value, false);
    }
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
        if (window.innerWidth <= 960) {
          closeMobileSidebar();
        }
      });
    });
  }

  // 4. Toast Notification
  const toast = document.getElementById('toast');
  let toastTimer = null;
  function showToast(msg) {
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add('show');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toast.classList.remove('show');
    }, 1800);
  }

  function flashElement(el) {
    if (!el) return;
    el.classList.remove('flash-target');
    void el.offsetWidth;
    el.classList.add('flash-target');
  }

  // 5. In-Page Skill Quick Filter Tabs
  const skillTabBtns = document.querySelectorAll('.skill-tab-btn');
  const skillCards = document.querySelectorAll('.skill-card');
  let activeSkillFilter = 'all';

  function applySkillFilter(filter) {
    activeSkillFilter = filter;
    skillTabBtns.forEach(b => b.classList.toggle('active', b.getAttribute('data-filter') === filter));
    skillCards.forEach(card => {
      card.style.display = (filter === 'all' || card.getAttribute('data-skill') === filter) ? '' : 'none';
    });
  }

  skillTabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      if (searchInput && searchInput.value.trim()) clearSearch();
      applySkillFilter(btn.getAttribute('data-filter') || 'all');
    });
  });

  // 6. Server Config Quick Filter Pills
  const configFilterPills = document.querySelectorAll('.filter-pill');
  const serverConfigGroups = document.querySelectorAll('#server-tables .config-group');
  let activeConfigFilter = 'all';

  function applyConfigFilter(filter) {
    activeConfigFilter = filter;
    configFilterPills.forEach(p => p.classList.toggle('active', p.getAttribute('data-config-filter') === filter));
    serverConfigGroups.forEach(group => {
      const tag = group.getAttribute('data-config-group') || '';
      const rows = group.querySelectorAll('tbody tr');
      if (filter === 'all') {
        group.style.display = '';
        rows.forEach(r => { r.style.display = ''; });
      } else if (filter === 'new') {
        let hasNew = false;
        rows.forEach(r => {
          const isNew = Boolean(r.querySelector('.badge-new'));
          r.style.display = isNew ? '' : 'none';
          if (isNew) hasNew = true;
        });
        group.style.display = hasNew ? '' : 'none';
      } else {
        const match = tag.split(' ').includes(filter);
        group.style.display = match ? '' : 'none';
        rows.forEach(r => { r.style.display = ''; });
      }
    });
  }

  configFilterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      if (searchInput && searchInput.value.trim()) clearSearch();
      applyConfigFilter(pill.getAttribute('data-config-filter') || 'all');
    });
  });

  // 7. Skill Card -> Jump Directly to Matching Server Config Group
  const skillToConfig = {
    spatial: { id: 'cfg-spatial', filter: 'classic' },
    singularity: { id: 'cfg-singularity', filter: 'classic' },
    drill: { id: 'cfg-drill', filter: 'classic' },
    supernova: { id: 'cfg-supernova', filter: 'classic' },
    stone: { id: 'cfg-stone', filter: 'classic' },
    atomic: { id: 'cfg-atomic', filter: 'atomic' },
    fuga: { id: 'cfg-fuga', filter: 'fuga' },
    gate: { id: 'cfg-gate', filter: 'gate' },
    purple: { id: 'cfg-purple', filter: 'purple' },
    dominion: { id: 'cfg-dominion', filter: 'dominion' }
  };

  skillCards.forEach(card => {
    const key = card.getAttribute('data-skill');
    const targetInfo = skillToConfig[key];
    const header = card.querySelector('.skill-header');
    if (!targetInfo || !header) return;

    const badge = header.querySelector('.badge, .badge-new');
    const rightWrap = document.createElement('div');
    rightWrap.className = 'skill-header-right';
    if (badge) rightWrap.appendChild(badge);

    const jumpBtn = document.createElement('button');
    jumpBtn.type = 'button';
    jumpBtn.className = 'skill-config-jump';
    jumpBtn.innerHTML =
      '<span class="lang-en">Config &rarr;</span>' +
      '<span class="lang-vi">Cấu hình &rarr;</span>' +
      '<span class="lang-zh">配置项 &rarr;</span>';
    jumpBtn.addEventListener('click', () => {
      if (searchInput && searchInput.value.trim()) clearSearch();
      applyConfigFilter(targetInfo.filter);
      const targetEl = document.getElementById(targetInfo.id);
      if (targetEl) {
        targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
        flashElement(targetEl);
      }
    });

    rightWrap.appendChild(jumpBtn);
    header.appendChild(rightWrap);
  });

  // 8. Anchor Link (#) Copy Buttons on Major Headings
  const anchorTargets = [
    ...Array.from(document.querySelectorAll('section.doc-section[id]')).map(sec => ({
      id: sec.id,
      heading: sec.querySelector('.section-title')
    })),
    { id: 'skill-purple', heading: document.querySelector('#skill-purple .skill-name') },
    { id: 'skill-dominion', heading: document.querySelector('#skill-dominion .skill-name') },
    { id: 'cutscenes', heading: document.querySelector('#cutscenes .callout-title') },
    { id: 'advancements', heading: document.querySelector('#advancements .subsection-title') },
    { id: 'skins', heading: document.querySelector('#skins .subsection-title') },
    { id: 'server-tables', heading: document.querySelector('#server-tables .subsection-title') },
    { id: 'client-config', heading: document.querySelector('#client-config .subsection-title') }
  ];

  anchorTargets.forEach(({ id, heading }) => {
    if (!id || !heading) return;
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'anchor-link';
    btn.textContent = '#';
    btn.setAttribute('aria-label', `Copy link to #${id}`);
    btn.title = 'Copy section link';
    btn.addEventListener('click', () => {
      let shareUrl = `#${id}`;
      try {
        const url = new URL(window.location.href);
        if (currentLang === 'en') url.searchParams.delete('lang');
        else url.searchParams.set('lang', currentLang);
        url.hash = id;
        shareUrl = url.toString();
        window.history.replaceState(null, '', shareUrl);
      } catch {
        // Fallback to relative hash
      }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(shareUrl).then(() => {
          const msg = currentLang === 'vi'
            ? `Đã chép liên kết: #${id}`
            : currentLang === 'zh'
            ? `已复制章节链接：#${id}`
            : `Copied link: #${id}`;
          showToast(msg);
        }).catch(() => {});
      }
    });
    heading.appendChild(btn);
  });

  // 9. Preset Tabs
  const presetTabBtns = document.querySelectorAll('.preset-tab-btn');
  const presetPanels = document.querySelectorAll('.preset-panel');

  presetTabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.getAttribute('data-preset');
      presetTabBtns.forEach(b => b.classList.toggle('active', b === btn));
      presetPanels.forEach(p => p.classList.toggle('active', p.getAttribute('data-preset-panel') === target));
    });
  });

  // 10. Copy Code Buttons
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

  // 12. Click-to-copy config keys inside config tables
  document.querySelectorAll('.config-group table.data-table td:first-child code').forEach(codeEl => {
    codeEl.title = 'Click to copy key';
    codeEl.addEventListener('click', () => {
      const keyName = codeEl.textContent.trim();
      if (!keyName || !navigator.clipboard) return;
      navigator.clipboard.writeText(keyName).then(() => {
        const msg = currentLang === 'vi'
          ? `Đã chép key: ${keyName}`
          : currentLang === 'zh'
          ? `已复制配置项：${keyName}`
          : `Copied key: ${keyName}`;
        showToast(msg);
      }).catch(() => {});
    });
  });

  // 13. ScrollSpy & Back-to-top
  const navLinks = Array.from(document.querySelectorAll('.sidebar-nav a'));
  const scrollTargets = navLinks
    .map(link => {
      const href = link.getAttribute('href');
      return href && href.startsWith('#') ? document.getElementById(href.slice(1)) : null;
    })
    .filter(Boolean);

  const backToTopBtn = document.getElementById('back-to-top');
  if (backToTopBtn) {
    backToTopBtn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  window.addEventListener('scroll', () => {
    const scrollPos = window.scrollY + 120;
    let currentId = '';

    for (const el of scrollTargets) {
      if (el.offsetParent !== null && el.offsetTop <= scrollPos) {
        currentId = el.id;
      }
    }

    if (currentId) {
      navLinks.forEach(link => {
        link.classList.toggle('active', link.getAttribute('href') === `#${currentId}`);
      });
    }

    if (backToTopBtn) {
      backToTopBtn.classList.toggle('visible', window.scrollY > 520);
    }
  }, { passive: true });

  // 14. Search Pro: Filter + Hide Empty Sections + Highlight + Match Jump (↑/↓ / Enter)
  const searchShortcut = document.querySelector('.search-shortcut');
  if (searchShortcut && typeof navigator !== 'undefined' && /(Mac|iPhone|iPod|iPad)/i.test(navigator.platform || navigator.userAgent)) {
    searchShortcut.textContent = '⌘K';
  }

  const searchCountEl = document.getElementById('search-count');
  const searchPrevBtn = document.getElementById('search-prev');
  const searchNextBtn = document.getElementById('search-next');
  const searchClearBtn = document.getElementById('search-clear');
  const emptyState = document.getElementById('search-empty');
  const contentArea = document.getElementById('main-content');
  const docSections = Array.from(document.querySelectorAll('section.doc-section'));

  let searchHits = [];
  let activeHitIndex = -1;

  function clearHighlights() {
    if (!contentArea) return;
    const marks = contentArea.querySelectorAll('mark.search-hit');
    marks.forEach(mark => {
      const parent = mark.parentNode;
      if (!parent) return;
      parent.replaceChild(document.createTextNode(mark.textContent), mark);
      parent.normalize();
    });
    searchHits = [];
    activeHitIndex = -1;
  }

  function isHiddenLangNode(el) {
    let cur = el;
    while (cur && cur !== contentArea) {
      if (cur.classList) {
        if (cur.classList.contains('lang-en') && currentLang !== 'en') return true;
        if (cur.classList.contains('lang-vi') && currentLang !== 'vi') return true;
        if (cur.classList.contains('lang-zh') && currentLang !== 'zh') return true;
      }
      cur = cur.parentElement;
    }
    return false;
  }

  function highlightMatches(query) {
    if (!contentArea || !query) return;
    const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(escaped, 'gi');
    const skipTags = new Set(['SCRIPT', 'STYLE', 'PRE', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT', 'SVG']);

    const walker = document.createTreeWalker(contentArea, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
        const parent = node.parentElement;
        if (!parent || parent.offsetParent === null) return NodeFilter.FILTER_REJECT;
        if (parent.closest('#search-empty, .hero-banner, .skill-tabs, .config-filter-bar, .preset-tabs, #config-builder')) {
          return NodeFilter.FILTER_REJECT;
        }
        let cur = parent;
        while (cur && cur !== contentArea) {
          if (skipTags.has(cur.tagName)) return NodeFilter.FILTER_REJECT;
          cur = cur.parentElement;
        }
        if (isHiddenLangNode(parent)) return NodeFilter.FILTER_REJECT;
        regex.lastIndex = 0;
        return regex.test(node.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
      }
    });

    const textNodes = [];
    let current;
    while ((current = walker.nextNode())) textNodes.push(current);

    textNodes.forEach(textNode => {
      const text = textNode.nodeValue;
      regex.lastIndex = 0;
      const frag = document.createDocumentFragment();
      let lastIndex = 0;
      let m;
      while ((m = regex.exec(text)) !== null) {
        if (m.index > lastIndex) {
          frag.appendChild(document.createTextNode(text.slice(lastIndex, m.index)));
        }
        const mark = document.createElement('mark');
        mark.className = 'search-hit';
        mark.textContent = m[0];
        frag.appendChild(mark);
        searchHits.push(mark);
        lastIndex = m.index + m[0].length;
        if (m[0].length === 0) break;
      }
      if (lastIndex < text.length) {
        frag.appendChild(document.createTextNode(text.slice(lastIndex)));
      }
      if (textNode.parentNode) {
        textNode.parentNode.replaceChild(frag, textNode);
      }
    });
  }

  function updateSearchCounter() {
    const total = searchHits.length;
    if (searchCountEl) {
      searchCountEl.textContent = total > 0 ? `${activeHitIndex + 1}/${total}` : '0/0';
    }
    if (searchPrevBtn) searchPrevBtn.disabled = total === 0;
    if (searchNextBtn) searchNextBtn.disabled = total === 0;
  }

  function focusHit(index, scroll) {
    if (searchHits.length === 0) {
      activeHitIndex = -1;
      updateSearchCounter();
      return;
    }
    if (activeHitIndex >= 0 && searchHits[activeHitIndex]) {
      searchHits[activeHitIndex].classList.remove('current');
    }
    activeHitIndex = ((index % searchHits.length) + searchHits.length) % searchHits.length;
    const hit = searchHits[activeHitIndex];
    hit.classList.add('current');
    updateSearchCounter();
    if (scroll) {
      hit.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  function clearSearch() {
    if (searchInput) searchInput.value = '';
    runSearch('', false);
  }

  runSearch = function(rawQuery, shouldScrollFirst) {
    const query = (rawQuery || '').toLowerCase().trim();
    clearHighlights();

    if (headerSearch) headerSearch.classList.toggle('has-query', Boolean(query));

    const generalCards = document.querySelectorAll('.card');
    const tables = document.querySelectorAll('table.data-table');
    const callouts = document.querySelectorAll('section.doc-section .callout');
    const advTree = document.querySelector('.adv-tree');

    if (!query) {
      docSections.forEach(s => { s.style.display = ''; });
      generalCards.forEach(c => { c.style.display = ''; });
      callouts.forEach(c => { c.style.display = ''; });
      if (advTree) advTree.style.display = '';
      tables.forEach(table => {
        table.querySelectorAll('tbody tr').forEach(tr => { tr.style.display = ''; });
        const group = table.closest('.config-group');
        const wrapper = table.closest('.table-wrapper') || table;
        wrapper.style.display = '';
        if (group) group.style.display = '';
      });
      applySkillFilter(activeSkillFilter);
      applyConfigFilter(activeConfigFilter);
      if (emptyState) emptyState.style.display = 'none';
      updateSearchCounter();
      return;
    }

    let totalVisible = 0;

    // 1. Filter skill cards
    skillCards.forEach(card => {
      const match = card.innerText.toLowerCase().includes(query);
      card.style.display = match ? '' : 'none';
      if (match) totalVisible++;
    });

    // 2. Filter general cards
    generalCards.forEach(card => {
      const match = card.innerText.toLowerCase().includes(query);
      card.style.display = match ? '' : 'none';
      if (match) totalVisible++;
    });

    // 3. Filter callouts inside sections (e.g. Cutscenes)
    callouts.forEach(callout => {
      const match = callout.innerText.toLowerCase().includes(query);
      callout.style.display = match ? '' : 'none';
      if (match) totalVisible++;
    });

    // 4. Filter standalone tables
    tables.forEach(table => {
      if (table.closest('.skill-card')) return;
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

      const group = table.closest('.config-group');
      const wrapper = table.closest('.table-wrapper') || table;
      wrapper.style.display = tableVisibleCount > 0 ? '' : 'none';
      if (group) group.style.display = tableVisibleCount > 0 ? '' : 'none';
      totalVisible += tableVisibleCount;
    });

    if (advTree) {
      const advTable = document.querySelector('#advancements .table-wrapper');
      const treeMatch = advTree.innerText.toLowerCase().includes(query) || (advTable && advTable.style.display !== 'none');
      advTree.style.display = treeMatch ? '' : 'none';
    }

    // 5. Hide completely empty sections so matching results jump straight to the top
    docSections.forEach(sec => {
      if (sec.id === 'presets') {
        const matchPreset = sec.innerText.toLowerCase().includes(query);
        sec.style.display = matchPreset ? '' : 'none';
        if (matchPreset) totalVisible++;
        return;
      }
      const hasVisible = Array.from(sec.querySelectorAll('.skill-card, .card, .callout, .table-wrapper')).some(el => {
        if (el.closest('.skill-card') && el.classList.contains('table-wrapper')) return false;
        const group = el.closest('.config-group');
        if (group && group.style.display === 'none') return false;
        return el.style.display !== 'none';
      });
      sec.style.display = hasVisible ? '' : 'none';
    });

    if (emptyState) {
      emptyState.style.display = totalVisible === 0 ? 'block' : 'none';
    }

    if (totalVisible > 0) {
      highlightMatches(query);
      focusHit(0, shouldScrollFirst);
    } else {
      updateSearchCounter();
    }
  };

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      runSearch(e.target.value, false);
    });

    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && searchHits.length > 0) {
        e.preventDefault();
        focusHit(activeHitIndex + (e.shiftKey ? -1 : 1), true);
      }
    });

    if (searchPrevBtn) {
      searchPrevBtn.addEventListener('click', () => focusHit(activeHitIndex - 1, true));
    }
    if (searchNextBtn) {
      searchNextBtn.addEventListener('click', () => focusHit(activeHitIndex + 1, true));
    }
    if (searchClearBtn) {
      searchClearBtn.addEventListener('click', () => {
        clearSearch();
        searchInput.focus();
      });
    }

    window.addEventListener('keydown', (e) => {
      if (((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') || (e.key === '/' && document.activeElement !== searchInput && !/INPUT|TEXTAREA/.test(document.activeElement?.tagName || ''))) {
        e.preventDefault();
        searchInput.focus();
        searchInput.select();
      }
      if (e.key === 'Escape' && (document.activeElement === searchInput || searchInput.value.trim())) {
        clearSearch();
        searchInput.blur();
      }
    });
  }

  // 15. AI Context 1-Click Copy Trigger
  const aiTriggers = document.querySelectorAll('.ai-copy-context-trigger');
  aiTriggers.forEach(btn => {
    btn.addEventListener('click', async () => {
      let content = '';
      if (window.location.protocol !== 'file:') {
        try {
          const resp = await fetch('llms-full.txt');
          if (resp.ok) {
            content = await resp.text();
          }
        } catch (e) {
          // Fallback for offline mode
        }
      }

      if (!content || content.length < 500) {
        content = getEmbeddedAiContext();
      }

      navigator.clipboard.writeText(content.trim()).then(() => {
        const msg = currentLang === 'vi'
          ? 'Đã sao chép toàn bộ ngữ cảnh cho AI (ChatGPT, Claude, Gemini...)'
          : currentLang === 'zh'
          ? '已复制完整 AI 上下文（可直接粘贴至 ChatGPT、Claude、Gemini 等）'
          : 'Copied full AI context to clipboard (paste into ChatGPT, Claude, Gemini...)';
        showToast(msg);
      }).catch(() => {
        const fail = currentLang === 'vi' ? 'Lỗi khi sao chép' : currentLang === 'zh' ? '复制失败' : 'Copy failed';
        showToast(fail);
      });
    });
  });

  function getEmbeddedAiContext() {
    return `# God Wand v1.7.0 — Player & Server Configuration Guide

You are an assistant answering questions about the Minecraft mod "God Wand" (v1.7.0).
Use this reference to explain skills, controls, advancements, and server/client configuration files.

## Summary & Supported Versions
- One wand (godwand:god_wand) with ten skills.
- 10 builds: Fabric/NeoForge for 1.21.1, 1.21.11, 26.1.2, 26.3; Fabric/Forge for 1.20.1.
- Install on both client and server. Compatible with Iris/Oculus/OptiFine shaders and animation mods (Emotecraft, Better Combat, Not Enough Animations, Yes Steve Model).
- Crafting: 2 Echo Shards, 1 Nether Star, 2 Amethyst Shards, 1 Netherite Ingot, 1 Blaze Rod.

## Safety Defaults
- spellTargets = "no_pets" (default): protects tamed animals, mobs with a name tag, and mobs on a lead from both damage and pulls/knockback. Villagers, golems, armor stands and other mobs are still hit. Also supports "all" and "hostile".
- pvp = true (default): controls whether spells can hurt or push other players (caster is never hurt).
- terrainDamage = true (default): one master switch. When false, no skill breaks or lifts blocks (and Block Dominion enters Safe Mode, summoning temporary blocks instead of taking world blocks).
- disabledSkills = [] (default): server-wide list of disabled skill IDs (spatial_path, singularity, earth_drill, supernova, stone_cannon, atomic, fuga, silent_gate, purple, block_dominion). Hidden from wheel; wands auto-switch off disabled skills (admin-locked wands stay locked); listing all 10 is ignored.

## Ten Skills
1. Spatial Path: Temporary tunnel through terrain; blocks return behind you; blocks projectiles; Shift forms a bridge. Cooldown: 20 ticks (1s).
2. Singularity: 3-tier gravity orb pulling enemies and blocks; right-click again to collapse early. Cooldown: [60, 120, 200] ticks (3s, 6s, 10s).
3. Earth Drill: 4-tier spinning block drill slammed into the ground. Auto skin: Aqua Heartia. Cooldown: [80, 140, 220, 320] ticks (4s, 7s, 11s, 16s).
4. Supernova: 7s hold black hole and 120-block blast; executes mobs <= 50 HP. Cooldown: 900 ticks (45s).
5. Stone Cannon: 4-tier piercing stone projectile. Auto skin: Aqua Heartia. Cooldown: [40, 80, 140, 220] ticks (2s, 4s, 7s, 11s).
6. I am Atomic: Hold to charge (invulnerable), release to detonate 9,999 DMG and 98-block crater. Auto skin: Shadow Blade. Cooldown: 1200 ticks (60s).
7. Fuga: Draw a burning bow; arrow becomes a phoenix, meteor, and fire pillar (9,999 DMG). Cooldown: 1200 ticks (60s).
8. Silent Gate: Giant sky circle and winged gate; right-click (or wait) to rain 80 DMG light beams and a 6x golden beam. Auto skin: Silent Witch Staff. Cooldown: 1200 ticks (60s).
9. Purple:
   - Blue (hold left-click): Gravity orb following aim (pull 8b, 20 DMG every 0.25s, eats blocks in 5b). Ready at 50 real damage or 3 kills; release when ready to throw and anchor for 8s (max hold 8s). Cooldown: 3s (60 ticks).
   - Red (hold right-click): Charges in 2.5s (50 ticks), fires up to 100b, 2b tunnel, 500 DMG, 5b crater. Cooldown: 4s (80 ticks).
   - Full Purple: Fully charged Red into a ready Blue (2b catch assist). Cutscene + 48b crater, 9,999 DMG in 72b. Works across players. Locks Blue & Red for 60s (1200 ticks).
   - Small Purple: Any other Red into a Blue. Bursts on the spot after 2.5s; scales with Blue charge & smallPurpleStrength. Locks both for 15s (300 ticks).
   - Purple sphere (hold both buttons): Forms in 2.5s (0.3s input grace), release to fire straight ahead (1.6 b/tick, 90b range, 3b tunnel, 20% purpleDamage * smallPurpleStrength). Locks both for 15s (300 ticks).
10. Block Dominion [NEW in 1.7.0]:
   - Shift + Left-Click to turn on/off (up to 48 orbiting blocks by default). Left-click a block to lift it, left-click air to pull up to 24 blocks from the ground, or left-click a mob (8 blocks) to lock it in a Monolith Cage skewered by 6 stone lances.
   - Right-click to fire a block; hold right-click for a rapid volley. Orbiting blocks passively shield you and reflect arrows/projectiles back (heavier hits cost more blocks; overwhelming hits break guard).
   - Double-tap Space (6+ blocks) to spread Block Wings: Shift + Space toggles Hover/Glide, Ctrl/Space boosts, flying past mobs at high speed slashes right through them, Shift + Right-Click rains blocks from above, landing sends a shockwave that launches nearby ground blocks into the air, and diving straight down builds a 3-tier Meteor Dive up to Cataclysm.
   - Safe Mode: When terrainDamage or blockDominionTerrainDamage is false, summons temporary blocks without breaking or taking world blocks, leaves no craters, and vanishes cleanly when turned off. Cooldown: 4s (80 ticks).

## Cutscenes
Atomic, Fuga, Silent Gate, and Purple each have a cutscene and soundtrack. Press Esc or F5 to skip. The settings menu (K) has one Cutscenes switch for all four; individual switches live in config/godwand-client.json.

## Controls & Commands
- Hold V: Skill wheel (10 skills); or bind Next skill / Previous skill in Controls
- Right-Click: Cast/charge skill (Purple: Red; Block Dominion: fire block / hold for volley)
- Left-Click: Purple (hold for Blue; hold with Right-Click for Purple sphere); Block Dominion (Shift+LMB toggle, LMB lift/pull/Monolith Cage); other modes: 5 melee DMG
- Double-tap Space (Block Dominion, 6+ blocks): Block Wings (Shift+Space Hover/Glide, Ctrl/Space Boost, Shift+RMB Orbital Volley)
- K: Client settings (wand appearance, Cutscenes, Skill Sounds volume, Skill HUD, Controls Guide, Bare Hand, Red Recoil, Purple: Third Person, effects)
- Shift: Bridge in Spatial Path; cancel Silent Gate / charging Red
- Esc or F5: Skip cutscene camera
- /godwand lock <skill>, /godwand unlock (OP Lv 2), /godwand reset_cooldowns [players] (OP Lv 4)

## Configuration
- Server: config/godwand-server.json (78 keys grouped by skill; existing files gain new keys automatically).
- Client: config/godwand-client.json (19 keys; most adjustable in K menu).`;
  }
});
