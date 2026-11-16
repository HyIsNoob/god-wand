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

  // 9. AI Context 1-Click Copy Trigger
  const aiTriggers = document.querySelectorAll('.ai-copy-context-trigger');
  aiTriggers.forEach(btn => {
    btn.addEventListener('click', async () => {
      let content = '';
      try {
        const resp = await fetch('llms-full.txt');
        if (resp.ok) {
          content = await resp.text();
        }
      } catch (e) {
        // Fallback for file:// or offline mode
      }

      if (!content || content.length < 500) {
        content = getEmbeddedAiContext();
      }

      navigator.clipboard.writeText(content.trim()).then(() => {
        const msg = currentLang === 'vi'
          ? '✨ Đã sao chép toàn bộ ngữ cảnh cho AI (ChatGPT, Claude, Gemini...)'
          : currentLang === 'zh'
          ? '✨ 已复制完整 AI 上下文（可直接粘贴至 ChatGPT、Claude、Gemini 等）'
          : '✨ Copied full AI context to clipboard (Paste into ChatGPT, Claude, Gemini...)';
        showToast(msg);
      }).catch(() => {
        const fail = currentLang === 'vi' ? 'Lỗi khi sao chép' : 'Copy failed';
        showToast(fail);
      });
    });
  });

  function getEmbeddedAiContext() {
    return `# God Wand v1.5.0 — Technical Knowledge Base & AI Context

You are an expert AI assistant answering questions about the Minecraft mod "God Wand" (v1.5.0).
Use this official knowledge base to assist players, explain spells, provide command syntax, or configure server files.

## Summary & Supported Versions
- One wand (item id: godwand:god_wand) with 8 ultimate spells.
- 10 supported builds across Fabric/NeoForge (1.21.1, 1.20.1 Forge/Fabric, 1.21.11, 26.1.2, 26.3).
- Required on both client and server in multiplayer. Compatible with Iris/Oculus shaders and animation mods (Emotecraft, Better Combat, NEA, Yes Steve Model).
- Standard Survival crafting: 2 Echo Shards, 1 Nether Star, 2 Amethyst Shards, 1 Netherite Ingot, 1 Blaze Rod.

## The Eight Spells
1. Spatial Path (spatial_path): Temporary corridor through terrain (cooldown: 20t / 1s); blocks restore automatically; deflects projectiles; Shift creates stone bridge.
2. Singularity (singularity): 3-tier gravity orb (cooldowns: 60/120/200t); peels world blocks and crushes mobs <= 50 HP upon early collapse; terrain auto-restores.
3. Earth Drill (earth_drill): 4-tier spinning drill (cooldowns: 80/140/220/320t); slams ground; auto-equips Aqua Heartia.
4. Supernova (supernova): 7s hold black hole (cooldown: 900t / 45s); 120-block horizontal blast; executes mobs <= 50 HP; merges XP drops.
5. Stone Cannon (stone_cannon): 4-tier high-velocity piercing projectile (cooldowns: 40/80/140/220t); auto-equips Aqua Heartia.
6. I am Atomic (atomic): 19s sword stance with invulnerability (cooldown: 1200t / 60s); release to detonate 9,999 center DMG, liquid clearing, and 98-block circular crater; auto-equips Shadow Blade.
7. Fuga (fuga): Celestial fire arrow & soaring phoenix (cooldown: 1200t / 60s); 9,999 core DMG meteor and 384-block pillar of fire; terrain shockwaves.
8. Silent Gate (silent_gate) [NEW in 1.5.0]: Celestial winged gate descended from sky circle (cooldown: 1200t / 60s); Right-click during wait commands 400-tick (20s) barrage of light beams (80 DMG each) tracking mobs with a final 6x golden kill beam (480 DMG); auto-equips Silent Witch Staff; Sneak cancels.

## Wand Appearance (Skins)
- AUTO switches dynamically: Skills 3 & 5 -> Aqua Heartia; Skill 6 -> Shadow Blade; Skill 8 -> Silent Witch Staff; Skills 1, 2, 4, 7 -> Magitech Wand (Eclipse).
- Manual selection available via K menu: DEFAULT, AQUA_HEARTIA, SHADOW_BLADE, SILENT_WITCH.
- Normal left-click attack deals 5 melee damage across all appearances.

## Controls & Commands
- Hold V: Skill wheel
- Right-Click: Cast / charge / command
- K: Client settings (shakes, bloom, cutscene toggles, wand skins)
- Shift / Esc / F5: Bridge in Spatial Path, cancel Silent Gate; skip cutscenes
- /godwand lock <skill> & /godwand unlock: Admin single-skill wand lock (OP Lv 2)
- /godwand reset_cooldowns [players]: Clear all cooldowns (OP Lv 4)

## Configuration
- Server: config/godwand-server.json (43 keys grouped by skill). Supports anti-griefing (terrainDamage: false).
- Client: config/godwand-client.json (12 keys) via K GUI.`;
  }
});
