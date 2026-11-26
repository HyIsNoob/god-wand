(() => {
  'use strict';

  const SERVER_GROUPS = [
    { id: 'general', en: 'General & Safety', vi: 'Chung & An toàn', zh: '全局与安全' },
    { id: 'spatial', en: '1. Spatial Path', vi: '1. Spatial Path', zh: '1. 空间之径' },
    { id: 'singularity', en: '2. Singularity', vi: '2. Singularity', zh: '2. 奇点' },
    { id: 'drill', en: '3. Earth Drill', vi: '3. Earth Drill', zh: '3. 大地钻击' },
    { id: 'supernova', en: '4. Supernova', vi: '4. Supernova', zh: '4. 超新星' },
    { id: 'stone', en: '5. Stone Cannon', vi: '5. Stone Cannon', zh: '5. 石炮' },
    { id: 'atomic', en: '6. I am Atomic', vi: '6. I am Atomic', zh: '6. 吾即原子' },
    { id: 'fuga', en: '7. Fuga', vi: '7. Fuga', zh: '7. Fuga（开）' },
    { id: 'gate', en: '8. Silent Gate', vi: '8. Silent Gate', zh: '8. 静默之门' },
    { id: 'purple', en: '9. Purple', vi: '9. Purple', zh: '9. 紫' },
    { id: 'dominion', en: '10. Block Dominion', vi: '10. Block Dominion', zh: '10. 方块支配', isNew: true }
  ];

  const CLIENT_GROUPS = [
    { id: 'appearance', en: 'Appearance & Hands', vi: 'Ngoại hình & Tay', zh: '外观与手持' },
    { id: 'cutscenes', en: 'Cutscenes', vi: 'Cutscene', zh: '过场动画' },
    { id: 'purple_ux', en: 'HUD, Controls & Audio', vi: 'HUD, Phím & Âm thanh', zh: 'HUD、操作与音效' },
    { id: 'visuals', en: 'Visuals & Performance', vi: 'Đồ họa & Hiệu năng', zh: '画面与性能' }
  ];

  // All 78 server keys with exact defaults & ranges from GodWandConfig.java
  const SERVER_SCHEMA = [
    // General & Safety
    { key: 'terrainDamage', group: 'general', type: 'bool', def: true,
      en: 'Master switch: off disables block breaking and lifting for every skill (Block Dominion enters Safe Mode).',
      vi: 'Công tắc tổng: tắt sẽ chặn phá khối và bốc khối của mọi chiêu (Block Dominion tự vào Chế độ An toàn).',
      zh: '总开关：关闭后所有技能均不破坏或抓取方块（方块支配自动进入安全模式）。' },
    { key: 'pvp', group: 'general', type: 'bool', def: true,
      en: 'Whether spells can hurt or push other players.',
      vi: 'Cho phép phép thuật gây sát thương hoặc đẩy người chơi khác.',
      zh: '法术是否可伤害或击退其他玩家。' },
    { key: 'spellTargets', group: 'general', type: 'enum', def: 'no_pets', options: ['no_pets', 'all', 'hostile'],
      en: 'Who spells hit & pull: no_pets (spares tamed/named/leashed), all, or hostile only.',
      vi: 'Mục tiêu trúng chiêu: no_pets (tha thú thuần/có tên/buộc dây), all, hoặc hostile.',
      zh: '目标过滤：no_pets（保护宠物/命名/拴绳）、all 或 hostile。' },
    { key: 'damageMultiplier', group: 'general', type: 'float', def: 1.0, min: 0, max: 100, step: 0.1,
      en: 'Scales spell damage for every skill at once.',
      vi: 'Nhân sát thương phép thuật cho tất cả kỹ năng.',
      zh: '全局缩放所有技能的法术伤害。' },
    { key: 'cooldownMultiplier', group: 'general', type: 'float', def: 1.0, min: 0.1, max: 10, step: 0.1,
      en: 'Scales cooldowns for every skill at once (0.5 = 2x faster).',
      vi: 'Nhân thời gian hồi chiêu toàn bộ kỹ năng (0.5 = nhanh gấp đôi).',
      zh: '全局缩放所有技能冷却（0.5 = 冷却减半）。' },
    { key: 'dimensionBlacklist', group: 'general', type: 'dims', def: [],
      en: 'Dimension IDs where the wand cannot be used.',
      vi: 'Danh sách ID chiều không gian cấm dùng trượng.',
      zh: '禁止使用神之杖的维度 ID 列表。' },
    { key: 'disabledSkills', group: 'general', type: 'skills', def: [],
      en: 'Skills turned off on this server (hidden from wheel, cannot be cast). Selecting all 10 is ignored so the wand stays usable. Admin-locked wands are kept as-is.',
      vi: 'Các skill bị tắt trên server (ẩn khỏi vòng chọn, không dùng được). Chọn cả 10 skill sẽ bị bỏ qua để gậy không vô dụng. Gậy bị admin lock giữ nguyên.',
      zh: '在服务器中禁用的技能（从轮盘隐藏且无法施放）。若全选 10 项则列表会被忽略，以免法杖失效；管理员锁定的法杖保持不变。' },
    { key: 'cooldownDefaultsVersion', group: 'general', type: 'int', def: 3, min: 3, max: 3, readonly: true,
      en: 'Internal migration version (keep at 3).',
      vi: 'Phiên bản di trú nội bộ (giữ nguyên là 3).',
      zh: '内部迁移版本标记（保持为 3）。' },

    // 1. Spatial Path
    { key: 'spatialPathCooldownTicks', group: 'spatial', type: 'int', unit: 'ticks', def: 20, min: 0, max: 72000,
      en: 'Toggle cooldown for Spatial Path.',
      vi: 'Hồi chiêu bật/tắt Spatial Path.',
      zh: '空间之径开关冷却。' },

    // 2. Singularity
    { key: 'singularityCooldownTicks', group: 'singularity', type: 'int[]', unit: 'ticks', len: 3, def: [60, 120, 200], min: 0, max: 72000,
      en: 'Cooldowns for charge levels 1–3.',
      vi: 'Hồi chiêu cho 3 mức tụ lực.',
      zh: '1–3 级蓄力冷却。' },
    { key: 'singularityDamageMultipliers', group: 'singularity', type: 'float[]', len: 3, def: [1.0, 1.0, 1.0], min: 0, max: 1000, step: 0.1,
      en: 'Damage multipliers for charge levels 1–3.',
      vi: 'Hệ số sát thương cho 3 mức tụ lực.',
      zh: '1–3 级蓄力伤害倍率。' },

    // 3. Earth Drill
    { key: 'earthDrillCooldownTicks', group: 'drill', type: 'int[]', unit: 'ticks', len: 4, def: [80, 140, 220, 320], min: 0, max: 72000,
      en: 'Cooldowns for charge levels 1–4.',
      vi: 'Hồi chiêu cho 4 mức tụ lực.',
      zh: '1–4 级蓄力冷却。' },
    { key: 'earthDrillDamageMultipliers', group: 'drill', type: 'float[]', len: 4, def: [1.0, 1.0, 1.0, 1.0], min: 0, max: 1000, step: 0.1,
      en: 'Damage multipliers for charge levels 1–4.',
      vi: 'Hệ số sát thương cho 4 mức tụ lực.',
      zh: '1–4 级蓄力伤害倍率。' },
    { key: 'earthDrillTerrainDamage', group: 'drill', type: 'bool', def: true,
      en: 'Allow permanent drill crater and lava.',
      vi: 'Cho phép tạo hố và dung nham vĩnh viễn.',
      zh: '允许砸出永久弹坑与岩浆。' },
    { key: 'earthDrillBlockLift', group: 'drill', type: 'bool', def: true,
      en: 'Lift real blocks to form the spinning drill.',
      vi: 'Nhấc khối đất đá thật tạo mũi khoan xoay.',
      zh: '抓取真实方块形成旋转钻头。' },

    // 4. Supernova
    { key: 'supernovaCooldownTicks', group: 'supernova', type: 'int', unit: 'ticks', def: 900, min: 0, max: 72000,
      en: 'Supernova cooldown.',
      vi: 'Hồi chiêu Supernova.',
      zh: '超新星冷却。' },
    { key: 'supernovaDamageMultiplier', group: 'supernova', type: 'float', def: 1.0, min: 0, max: 1000, step: 0.1,
      en: 'Damage multiplier for Supernova.',
      vi: 'Hệ số sát thương Supernova.',
      zh: '超新星伤害倍率。' },
    { key: 'supernovaInstantKillHealthThreshold', group: 'supernova', type: 'float', def: 50.0, min: 0, max: 1000000, step: 5,
      en: 'Mobs with max HP <= this are defeated instantly (0 disables).',
      vi: 'Mob có máu tối đa <= mức này bị hạ ngay (0 để tắt).',
      zh: '最大生命值 <= 此值的生物直接秒杀（0 关闭）。' },
    { key: 'supernovaTerrainDamage', group: 'supernova', type: 'bool', def: true,
      en: 'Allow permanent crater carving.',
      vi: 'Cho phép khoét hố vĩnh viễn.',
      zh: '允许留下永久巨坑。' },
    { key: 'supernovaBlockLift', group: 'supernova', type: 'bool', def: true,
      en: 'Peel ground blocks up into the black hole.',
      vi: 'Bóc khối đất đá hút vào hố đen.',
      zh: '剥离地面方块吸入黑洞。' },
    { key: 'supernovaTerrainRadius', group: 'supernova', type: 'int', unit: 'blocks', def: 120, min: 16, max: 180,
      en: 'Horizontal crater radius.',
      vi: 'Bán kính ngang của hố nổ.',
      zh: '水平破坏半径。' },
    { key: 'supernovaMaxDisplays', group: 'supernova', type: 'int', def: 1800, min: 0, max: 1800,
      en: 'Max orbiting floating blocks (0 for smoother FPS).',
      vi: 'Số khối bay tối đa (0 để tăng FPS).',
      zh: '环绕黑洞的最大漂浮方块数（0 提升帧率）。' },

    // 5. Stone Cannon
    { key: 'stoneCannonCooldownTicks', group: 'stone', type: 'int[]', unit: 'ticks', len: 4, def: [40, 80, 140, 220], min: 0, max: 72000,
      en: 'Cooldowns for charge levels 1–4.',
      vi: 'Hồi chiêu cho 4 mức tụ lực.',
      zh: '1–4 级蓄力冷却。' },
    { key: 'stoneCannonDamageMultipliers', group: 'stone', type: 'float[]', len: 4, def: [1.0, 1.0, 1.0, 1.0], min: 0, max: 1000, step: 0.1,
      en: 'Damage multipliers for charge levels 1–4.',
      vi: 'Hệ số sát thương cho 4 mức tụ lực.',
      zh: '1–4 级蓄力伤害倍率。' },
    { key: 'stoneCannonTerrainDamage', group: 'stone', type: 'bool', def: true,
      en: 'Break blocks on projectile impact.',
      vi: 'Phá khối tại điểm đạn đá va chạm.',
      zh: '允许石炮撞击处炸碎方块。' },

    // 6. I am Atomic
    { key: 'atomicCooldownTicks', group: 'atomic', type: 'int', unit: 'ticks', def: 1200, min: 0, max: 72000,
      en: 'Cooldown starting on detonation.',
      vi: 'Hồi chiêu tính từ lúc kích nổ.',
      zh: '引爆后开始的冷却。' },
    { key: 'atomicDamage', group: 'atomic', type: 'float', def: 9999.0, min: 0, max: 1000000, step: 50,
      en: 'Damage at the center of the light column.',
      vi: 'Sát thương ở tâm cột sáng.',
      zh: '光柱核心伤害。' },
    { key: 'atomicShockwaveDamage', group: 'atomic', type: 'float', def: 15.0, min: 0, max: 1000000, step: 1,
      en: 'Damage of outer ground shockwaves.',
      vi: 'Sát thương sóng áp suất ngoài cột sáng.',
      zh: '外围地面冲击波伤害。' },
    { key: 'atomicTerrainDamage', group: 'atomic', type: 'bool', def: true,
      en: 'Carve circular crater and clear water/lava.',
      vi: 'Đào hố tròn và xóa sạch nước/dung nham.',
      zh: '掘出圆形巨坑并清除水与岩浆。' },
    { key: 'atomicTerrainRadius', group: 'atomic', type: 'int', unit: 'blocks', def: 98, min: 0, max: 98,
      en: 'Maximum crater radius.',
      vi: 'Bán kính hố nổ tối đa.',
      zh: '最大弹坑半径。' },

    // 7. Fuga
    { key: 'fugaCooldownTicks', group: 'fuga', type: 'int', unit: 'ticks', def: 1200, min: 0, max: 72000,
      en: 'Cooldown starting on arrow release.',
      vi: 'Hồi chiêu tính lúc bắn mũi tên lửa.',
      zh: '射出火焰箭后开始的冷却。' },
    { key: 'fugaDamage', group: 'fuga', type: 'float', def: 9999.0, min: 0, max: 1000000, step: 50,
      en: 'Core explosion damage.',
      vi: 'Sát thương tâm vụ nổ thiên thạch và cột lửa.',
      zh: '陨石与火柱核心伤害。' },
    { key: 'fugaShockwaveDamageMultiplier', group: 'fuga', type: 'float', def: 0.08, min: 0, max: 1000, step: 0.01,
      en: 'Share of fugaDamage dealt by ground shockwave.',
      vi: 'Tỉ lệ sát thương sóng xung kích mặt đất.',
      zh: '地面冲击波伤害比例。' },
    { key: 'fugaIgniteEntities', group: 'fuga', type: 'bool', def: true,
      en: 'Set hit enemies on fire.',
      vi: 'Thiêu đốt kẻ địch trúng đòn.',
      zh: '点燃命中的敌人。' },
    { key: 'fugaShockwaveKnockback', group: 'fuga', type: 'bool', def: true,
      en: 'Push enemies back on shockwave hit.',
      vi: 'Đẩy lùi kẻ địch khi sóng quét qua.',
      zh: '冲击波击退敌人。' },
    { key: 'fugaSlowness', group: 'fuga', type: 'bool', def: true,
      en: 'Slow enemies caught under the falling meteor.',
      vi: 'Làm chậm kẻ địch dưới thiên thạch rơi.',
      zh: '减速坠落陨石下方的敌人。' },
    { key: 'fugaTerrainDamage', group: 'fuga', type: 'bool', def: true,
      en: 'Allow crater and terrain changes.',
      vi: 'Cho phép khoét hố và biến đổi địa hình.',
      zh: '允许破坏与改变地形。' },
    { key: 'fugaTerrainRadius', group: 'fuga', type: 'int', unit: 'blocks', def: 48, min: 0, max: 64,
      en: 'Maximum radius of crater and effects.',
      vi: 'Bán kính tối đa của hố nổ và hiệu ứng.',
      zh: '弹坑与战斗效果最大半径。' },

    // 8. Silent Gate
    { key: 'gateCooldownTicks', group: 'gate', type: 'int', unit: 'ticks', def: 1200, min: 0, max: 72000,
      en: 'Cooldown after barrage ends.',
      vi: 'Hồi chiêu sau khi đợt tia sáng kết thúc.',
      zh: '光束轰炸结束后的冷却。' },
    { key: 'gateBeamDamage', group: 'gate', type: 'float', def: 80.0, min: 0, max: 1000000, step: 5,
      en: 'Damage per light beam (final golden beam = 6x).',
      vi: 'Sát thương mỗi tia (tia vàng cuối gấp 6 lần).',
      zh: '每道光束伤害（终结金光为 6 倍）。' },
    { key: 'gateTargetMode', group: 'gate', type: 'enum', def: 'hostile', options: ['hostile', 'all'],
      en: 'Which creatures the gate aims at.',
      vi: 'Cổng ngắm vào quái vật hay mọi sinh vật.',
      zh: '静默之门瞄准的目标类型。' },
    { key: 'gateTargetRadius', group: 'gate', type: 'int', unit: 'blocks', def: 160, min: 8, max: 256,
      en: 'Target search radius around caster.',
      vi: 'Bán kính quét mục tiêu quanh người chơi.',
      zh: '搜寻目标的半径。' },
    { key: 'gateBarrageTicks', group: 'gate', type: 'int', unit: 'ticks', def: 400, min: 60, max: 600,
      en: 'Duration of the beam barrage.',
      vi: 'Thời lượng đợt mưa tia sáng.',
      zh: '光束持续轰炸时长。' },
    { key: 'gateWaitTicks', group: 'gate', type: 'int', unit: 'ticks', def: 139, min: 40, max: 1200,
      en: 'Wait time before gate fires on its own.',
      vi: 'Thời gian chờ lệnh chuột phải trước khi tự bắn.',
      zh: '大门敞开等待右键指令的时长。' },
    { key: 'gateSkyCircles', group: 'gate', type: 'int', def: 220, min: 8, max: 256,
      en: 'Number of small magic circles in the sky.',
      vi: 'Số vòng ma pháp nhỏ trên trời.',
      zh: '天空小型法阵数量。' },
    { key: 'gateTerrainDamage', group: 'gate', type: 'bool', def: true,
      en: 'Allow beam hits to make small craters.',
      vi: 'Cho phép tia sáng tạo hố nhỏ trên đất.',
      zh: '允许光束落地炸出小弹坑。' },
    { key: 'gateCraterRadius', group: 'gate', type: 'int', unit: 'blocks', def: 3, min: 0, max: 6,
      en: 'Crater radius per beam hit.',
      vi: 'Bán kính hố của mỗi tia sáng.',
      zh: '每道光束的弹坑半径。' },

    // 9. Purple (24 keys)
    { key: 'blueCooldownTicks', group: 'purple', type: 'int', unit: 'ticks', def: 60, min: 0, max: 72000,
      en: 'Blue cooldown starting when Blue disappears.',
      vi: 'Hồi chiêu Blue tính từ lúc Blue biến mất.',
      zh: '蓝消失后开始的独立冷却。' },
    { key: 'redCooldownTicks', group: 'purple', type: 'int', unit: 'ticks', def: 80, min: 0, max: 72000,
      en: 'Red cooldown starting when Red fires.',
      vi: 'Hồi chiêu Red tính từ lúc bắn Red.',
      zh: '红发射时开始的独立冷却。' },
    { key: 'purpleCooldownTicks', group: 'purple', type: 'int', unit: 'ticks', def: 1200, min: 0, max: 72000,
      en: 'Full Purple lock time (Small Purple & sphere lock for 1/4 of this).',
      vi: 'Khóa cả Blue & Red cho Purple đầy đủ (Small Purple & quả cầu khóa 1/4).',
      zh: '完整紫双锁时长（小紫与紫球锁定此值的 1/4）。' },
    { key: 'blueReadyDamage', group: 'purple', type: 'float', def: 50.0, min: 1, max: 1000000, step: 5,
      en: 'Real damage Blue needs to become ready.',
      vi: 'Sát thương thực Blue cần để sẵn sàng.',
      zh: '蓝进入就绪所需的实际伤害。' },
    { key: 'blueReadyKills', group: 'purple', type: 'int', def: 3, min: 1, max: 1000,
      en: 'Mob kills Blue needs to become ready.',
      vi: 'Số mạng Blue cần hạ để sẵn sàng.',
      zh: '蓝进入就绪所需的击杀数。' },
    { key: 'blueCoreDamage', group: 'purple', type: 'float', def: 20.0, min: 0, max: 1000000, step: 1,
      en: 'Damage every 5 ticks (0.25 s) inside Blue core.',
      vi: 'Sát thương mỗi 5 tick (0,25 s) trong lõi Blue.',
      zh: '蓝核心每 5 刻（0.25 秒）造成的伤害。' },
    { key: 'bluePullRadius', group: 'purple', type: 'int', unit: 'blocks', def: 8, min: 2, max: 16,
      en: 'Pull radius for creatures.',
      vi: 'Bán kính hút sinh vật của Blue.',
      zh: '蓝吸引生物的半径。' },
    { key: 'blueEatRadius', group: 'purple', type: 'int', unit: 'blocks', def: 5, min: 0, max: 8,
      en: 'Terrain eating radius.',
      vi: 'Bán kính bóc khối địa hình của Blue.',
      zh: '蓝吞噬周围方块的半径。' },
    { key: 'blueChargeTicks', group: 'purple', type: 'int', unit: 'ticks', def: 160, min: 20, max: 600,
      en: 'Max hold time for Blue.',
      vi: 'Thời gian giữ Blue tối đa.',
      zh: '按住蓝的最长时长。' },
    { key: 'blueAnchorTicks', group: 'purple', type: 'int', unit: 'ticks', def: 160, min: 20, max: 1200,
      en: 'How long an anchored ready Blue floats in place.',
      vi: 'Thời gian Blue sẵn sàng treo lơ lửng chờ Red.',
      zh: '就绪的蓝悬停等待红的时长。' },
    { key: 'redChargeTicks', group: 'purple', type: 'int', unit: 'ticks', def: 50, min: 10, max: 200,
      en: 'Hold time for Red to reach full charge.',
      vi: 'Thời gian giữ để Red tụ đầy lực.',
      zh: '红蓄满所需的按住时长。' },
    { key: 'redDamage', group: 'purple', type: 'float', def: 500.0, min: 0, max: 1000000, step: 10,
      en: 'Full-charge Red damage (partial: 20%–100%).',
      vi: 'Sát thương Red tụ đầy (tụ một phần: 20%–100%).',
      zh: '蓄满红的伤害（未蓄满按 20%–100% 缩放）。' },
    { key: 'redRange', group: 'purple', type: 'int', unit: 'blocks', def: 100, min: 8, max: 160,
      en: 'Maximum flight distance of Red.',
      vi: 'Tầm bay tối đa của Red.',
      zh: '红的最大飞行射程。' },
    { key: 'redTunnelRadius', group: 'purple', type: 'int', unit: 'blocks', def: 2, min: 0, max: 4,
      en: 'Radius of Red flight tunnel.',
      vi: 'Bán kính đường hầm Red khoét khi bay.',
      zh: '红飞行掘进隧道的半径。' },
    { key: 'redCraterRadius', group: 'purple', type: 'int', unit: 'blocks', def: 5, min: 0, max: 8,
      en: 'Crater radius when Red explodes.',
      vi: 'Bán kính hố nổ khi Red chạm đất/hết tầm.',
      zh: '红引爆时的弹坑半径。' },
    { key: 'purpleRadius', group: 'purple', type: 'int', unit: 'blocks', def: 48, min: 0, max: 64,
      en: 'Full Purple erased sphere radius (damage reaches 1.5x).',
      vi: 'Bán kính quả cầu xóa địa hình Purple (sát thương xa 1,5 lần).',
      zh: '完整紫抹除球体半径（伤害范围 1.5 倍）。' },
    { key: 'purpleDamage', group: 'purple', type: 'float', def: 9999.0, min: 0, max: 1000000, step: 50,
      en: 'Full Purple damage (also base for Small Purple & sphere).',
      vi: 'Sát thương Purple đầy đủ (và gốc tính Small Purple / quả cầu).',
      zh: '完整紫伤害（同时作为小紫与紫球基础值）。' },
    { key: 'blueTerrainDamage', group: 'purple', type: 'bool', def: true,
      en: 'Allow Blue to eat nearby blocks.',
      vi: 'Cho phép Blue bóc tách khối xung quanh.',
      zh: '允许蓝吞噬周围方块。' },
    { key: 'redTerrainDamage', group: 'purple', type: 'bool', def: true,
      en: 'Allow Red to carve tunnel and crater.',
      vi: 'Cho phép Red khoét hầm và tạo hố.',
      zh: '允许红掘出隧道与弹坑。' },
    { key: 'purpleTerrainDamage', group: 'purple', type: 'bool', def: true,
      en: 'Allow Purple, Small Purple and sphere to erase & stain terrain.',
      vi: 'Cho phép Purple, Small Purple và quả cầu xóa & nhuộm địa hình.',
      zh: '允许紫、小紫与紫球抹除及染紫地形。' },
    { key: 'smallPurple', group: 'purple', type: 'bool', def: true,
      en: 'Enable Small Purple on unready Blue / partial Red.',
      vi: 'Bật Small Purple khi bắn vào Blue chưa sẵn sàng / Red chưa đầy.',
      zh: '开启未就绪蓝或未蓄满红触发小紫。' },
    { key: 'smallPurpleStrength', group: 'purple', type: 'float', def: 1.0, min: 0, max: 10, step: 0.1,
      en: 'Scales Small Purple size/damage and Purple sphere damage.',
      vi: 'Hệ số chỉnh cỡ hố/sát thương Small Purple và sát thương quả cầu Purple.',
      zh: '缩放小紫范围/伤害及紫球伤害。' },
    { key: 'purpleAssist', group: 'purple', type: 'float', unit: 'blocks', def: 2.0, min: 0, max: 16, step: 0.5,
      en: 'Extra reach a ready Blue uses to catch a passing Red.',
      vi: 'Tầm với thêm để Blue sẵn sàng bắt Red bay sượt qua.',
      zh: '就绪的蓝吸附擦肩红球的额外距离。' },
    { key: 'purpleNeedsFullRed', group: 'purple', type: 'bool', def: true,
      en: 'Require a fully charged Red for full Purple.',
      vi: 'Bắt buộc Red tụ đầy mới kích hoạt Purple đầy đủ.',
      zh: '触发完整紫是否必须蓄满红。' },

    // 10. Block Dominion (7 keys — new in 1.7.0)
    { key: 'blockDominionCooldownTicks', group: 'dominion', type: 'int', unit: 'ticks', def: 80, min: 0, max: 72000, isNew: true,
      en: 'Cooldown after turning Block Dominion off.',
      vi: 'Hồi chiêu sau khi tắt Block Dominion.',
      zh: '关闭方块支配后的冷却时间。' },
    { key: 'blockDominionCapacity', group: 'dominion', type: 'int', unit: 'blocks', def: 48, min: 4, max: 96, isNew: true,
      en: 'Maximum orbiting blocks you can control at once.',
      vi: 'Số khối bay quanh người tối đa có thể điều khiển cùng lúc.',
      zh: '同时环绕操控的最大方块数量。' },
    { key: 'blockDominionDamage', group: 'dominion', type: 'float', def: 45.0, min: 0, max: 1000000, step: 5, isNew: true,
      en: 'Base damage for fired blocks, landings, dives, and cages.',
      vi: 'Sát thương gốc cho đạn khối, cú đáp, lao thiên thạch và lồng đá.',
      zh: '方块发射、落地冲击、陨石俯冲与石笼的基础伤害。' },
    { key: 'blockDominionBlastRadius', group: 'dominion', type: 'float', unit: 'blocks', def: 2.5, min: 0, max: 16, step: 0.5, isNew: true,
      en: 'Splash damage radius when fired blocks hit.',
      vi: 'Bán kính nổ lan khi khối bắn ra chạm mục tiêu.',
      zh: '发射方块命中时的溅射伤害半径。' },
    { key: 'blockDominionCraterRadius', group: 'dominion', type: 'float', unit: 'blocks', def: 1.8, min: 0, max: 8, step: 0.2, isNew: true,
      en: 'Crater radius when fired blocks hit (when terrain damage is on).',
      vi: 'Bán kính hố nổ khi khối chạm đất (khi bật phá địa hình).',
      zh: '开启地形破坏时方块命中的弹坑半径。' },
    { key: 'blockDominionProjectileSpeed', group: 'dominion', type: 'float', def: 3.2, min: 0.5, max: 10, step: 0.2, isNew: true,
      en: 'Flight speed of fired blocks.',
      vi: 'Tốc độ bay của các khối bắn ra.',
      zh: '发射方块的飞行速度。' },
    { key: 'blockDominionTerrainDamage', group: 'dominion', type: 'bool', def: true, isNew: true,
      en: 'Off enables Safe Mode: summons temporary blocks without breaking or taking world blocks.',
      vi: 'Tắt để bật Chế độ An toàn: tự tạo khối tạm, không lấy hay phá block trong thế giới.',
      zh: '关闭后启用安全模式：召唤临时方块，不破坏或拿取世界中的方块。' }
  ];

  // Canonical grouped output order matching GodWandConfig.java (1.7.0 grouped layout)
  const SERVER_JSON_GROUPS = [
    ['cooldownDefaultsVersion', 'cooldownMultiplier', 'damageMultiplier', 'terrainDamage', 'pvp', 'spellTargets', 'disabledSkills'],
    ['spatialPathCooldownTicks'],
    ['singularityCooldownTicks', 'singularityDamageMultipliers'],
    ['earthDrillCooldownTicks', 'earthDrillDamageMultipliers', 'earthDrillTerrainDamage', 'earthDrillBlockLift'],
    ['stoneCannonCooldownTicks', 'stoneCannonDamageMultipliers', 'stoneCannonTerrainDamage'],
    ['supernovaCooldownTicks', 'supernovaDamageMultiplier', 'supernovaInstantKillHealthThreshold', 'supernovaTerrainDamage', 'supernovaBlockLift', 'supernovaTerrainRadius', 'supernovaMaxDisplays'],
    ['atomicCooldownTicks', 'atomicDamage', 'atomicShockwaveDamage', 'atomicTerrainDamage', 'atomicTerrainRadius'],
    ['fugaCooldownTicks', 'fugaDamage', 'fugaShockwaveDamageMultiplier', 'fugaIgniteEntities', 'fugaShockwaveKnockback', 'fugaSlowness', 'fugaTerrainDamage', 'fugaTerrainRadius'],
    ['gateCooldownTicks', 'gateBeamDamage', 'gateTargetMode', 'gateTargetRadius', 'gateBarrageTicks', 'gateWaitTicks', 'gateSkyCircles', 'gateTerrainDamage', 'gateCraterRadius'],
    ['blueCooldownTicks', 'redCooldownTicks', 'purpleCooldownTicks', 'blueReadyDamage', 'blueReadyKills', 'blueCoreDamage', 'bluePullRadius', 'blueEatRadius', 'blueChargeTicks', 'blueAnchorTicks', 'redChargeTicks', 'redDamage', 'redRange', 'redTunnelRadius', 'redCraterRadius', 'purpleRadius', 'purpleDamage', 'blueTerrainDamage', 'redTerrainDamage', 'purpleTerrainDamage', 'smallPurple', 'smallPurpleStrength', 'purpleAssist', 'purpleNeedsFullRed'],
    ['blockDominionCooldownTicks', 'blockDominionCapacity', 'blockDominionDamage', 'blockDominionBlastRadius', 'blockDominionCraterRadius', 'blockDominionProjectileSpeed', 'blockDominionTerrainDamage'],
    ['dimensionBlacklist']
  ];

  // All 19 client keys with exact defaults & ranges from GodWandConfig.Client
  const CLIENT_SCHEMA = [
    { key: 'wandSkin', group: 'appearance', type: 'enum', def: 'AUTO', options: ['AUTO', 'DEFAULT', 'AQUA_HEARTIA', 'SHADOW_BLADE', 'SILENT_WITCH'],
      en: 'Wand appearance model (AUTO switches per skill).',
      vi: 'Ngoại hình trượng (AUTO tự đổi theo kỹ năng).',
      zh: '法杖外观模型（AUTO 随技能切换）。' },
    { key: 'bareHand', group: 'appearance', type: 'bool', def: true,
      en: 'Show bare hand instead of wand in first person for Purple & Fuga.',
      vi: 'Hiện tay trần thay cho trượng ở góc nhìn thứ nhất cho Purple & Fuga.',
      zh: '紫与 Fuga 第一人称下显示空手而非持杖。' },

    { key: 'atomicCutscene', group: 'cutscenes', type: 'bool', def: true,
      en: 'Cinematic camera for I am Atomic.',
      vi: 'Góc quay cutscene cho I am Atomic.',
      zh: '吾即原子过场镜头。' },
    { key: 'fugaCutscene', group: 'cutscenes', type: 'bool', def: true,
      en: 'Cinematic camera for Fuga.',
      vi: 'Góc quay cutscene cho Fuga.',
      zh: 'Fuga 过场镜头。' },
    { key: 'gateCutscene', group: 'cutscenes', type: 'bool', def: true,
      en: 'Cinematic camera for Silent Gate.',
      vi: 'Góc quay cutscene cho Silent Gate.',
      zh: '静默之门过场镜头。' },
    { key: 'purpleCutscene', group: 'cutscenes', type: 'bool', def: true,
      en: 'Cinematic camera for full Purple.',
      vi: 'Góc quay cutscene cho Purple đầy đủ.',
      zh: '完整紫过场镜头。' },

    { key: 'soundVolume', group: 'purple_ux', type: 'float', def: 1.0, min: 0, max: 1, step: 0.05,
      en: 'Volume for God Wand sounds only (0.0 to 1.0).',
      vi: 'Âm lượng riêng cho âm thanh của mod (0.0 đến 1.0).',
      zh: '神之杖专属音效音量（0.0 至 1.0）。' },
    { key: 'skillHud', group: 'purple_ux', type: 'bool', def: true,
      en: 'Shows Blue & Red crosshair rings and the Block Dominion block counter.',
      vi: 'Hiện vòng tụ lực Blue & Red quanh tâm ngắm và bộ đếm khối của Block Dominion.',
      zh: '显示蓝与红准星蓄力环，以及方块支配的方块计数条。' },
    { key: 'dominionGuide', group: 'purple_ux', type: 'bool', def: true, isNew: true,
      en: 'Shows the compact Block Dominion controls guide in the bottom-left corner.',
      vi: 'Hiện bảng hướng dẫn phím gọn cho Block Dominion ở góc dưới bên trái.',
      zh: '在左下角显示简洁的方块支配按键指南。' },
    { key: 'redRecoil', group: 'purple_ux', type: 'bool', def: true,
      en: 'Charged Red kicks the shooter backward.',
      vi: 'Bắn Red tụ lực đẩy giật người bắn về sau.',
      zh: '发射蓄力红时产生后坐力推退。' },
    { key: 'blueRedThirdPerson', group: 'purple_ux', type: 'bool', def: false,
      en: 'Auto switch to third person while casting Blue/Red.',
      vi: 'Tự chuyển sang góc nhìn thứ ba khi dùng Blue/Red.',
      zh: '施放蓝或红时自动切换至第三人称。' },

    { key: 'postEffects', group: 'visuals', type: 'bool', def: true,
      en: 'Post-processing shaders (black hole distortion, sky tear).',
      vi: 'Shader hậu kỳ toàn màn hình (méo hố đen, xé bầu trời).',
      zh: '全屏后处理着色器（黑洞扭曲、裂空）。' },
    { key: 'impactFrames', group: 'visuals', type: 'bool', def: true,
      en: 'High-contrast flash frames on heavy hits.',
      vi: 'Khung hình chớp tương phản mạnh khi ra đòn nặng.',
      zh: '重击与融合时的高反差冲击帧。' },
    { key: 'cameraFov', group: 'visuals', type: 'bool', def: true,
      en: 'Dynamic FOV zoom while charging big spells.',
      vi: 'Thay đổi góc nhìn nhẹ khi đang tụ chiêu lớn.',
      zh: '蓄力大型法术时的动态视野缩放。' },
    { key: 'skyBlackout', group: 'visuals', type: 'bool', def: true,
      en: 'Darkens the sky during Supernova.',
      vi: 'Làm tối bầu trời khi Supernova phát nổ.',
      zh: '超新星引爆时让天空短暂变暗。' },
    { key: 'targetMarker', group: 'visuals', type: 'bool', def: true,
      en: 'Vertical light column marking Supernova target.',
      vi: 'Cột sáng đánh dấu điểm ngắm của Supernova.',
      zh: '超新星蓄力地面定位光柱。' },
    { key: 'cameraShake', group: 'visuals', type: 'float', def: 1.0, min: 0, max: 1, step: 0.05,
      en: 'Explosion camera shake intensity (0.0 disables).',
      vi: 'Độ rung màn hình khi nổ (0.0 tắt hẳn).',
      zh: '爆炸镜头震动强度（0.0 关闭）。' },
    { key: 'bloomStrength', group: 'visuals', type: 'float', def: 0.85, min: 0, max: 1.5, step: 0.05,
      en: 'God Wand bloom glow brightness.',
      vi: 'Độ rực hiệu ứng phát sáng Bloom.',
      zh: '法术泛光发光亮度。' },
    { key: 'bloomDownsample', group: 'visuals', type: 'int', def: 4, min: 2, max: 8,
      en: 'Bloom quality step (2 sharpest, 4 balanced, 8 fastest FPS).',
      vi: 'Mức nén bloom (2 nét nhất, 4 cân bằng, 8 nhẹ máy nhất).',
      zh: '泛光降采样步长（2 最清晰，4 均衡，8 最流畅）。' }
  ];

  const CLIENT_JSON_ORDER = [
    'wandSkin', 'postEffects', 'impactFrames', 'cameraFov', 'skyBlackout', 'targetMarker',
    'atomicCutscene', 'fugaCutscene', 'gateCutscene', 'blueRedThirdPerson', 'purpleCutscene',
    'soundVolume', 'skillHud', 'dominionGuide', 'bareHand', 'redRecoil', 'cameraShake', 'bloomStrength', 'bloomDownsample'
  ];

  const SERVER_PRESETS = [
    { id: 'default', en: 'Defaults', vi: 'Mặc định', zh: '默认配置', patch: {} },
    { id: 'safe_coop', en: 'Safe Co-op', vi: 'Co-op an toàn', zh: '友好合作服',
      patch: { terrainDamage: false, pvp: false, spellTargets: 'no_pets' } },
    { id: 'reversible', en: 'Reversible Lift', vi: 'Bốc khối tự hoàn trả', zh: '可复原抓块',
      patch: {
        terrainDamage: true, earthDrillBlockLift: true, earthDrillTerrainDamage: false,
        supernovaBlockLift: true, supernovaTerrainDamage: false, stoneCannonTerrainDamage: false,
        atomicTerrainDamage: false, fugaTerrainDamage: false, gateTerrainDamage: false,
        blueTerrainDamage: false, redTerrainDamage: false, purpleTerrainDamage: false,
        blockDominionTerrainDamage: false
      } },
    { id: 'fast_fun', en: 'Fast Cooldowns (0.5x)', vi: 'Hồi chiêu nhanh (0.5x)', zh: '快速冷却 (0.5x)',
      patch: { cooldownMultiplier: 0.5 } }
  ];

  const CLIENT_PRESETS = [
    { id: 'default', en: 'Defaults', vi: 'Mặc định', zh: '默认配置', patch: {} },
    { id: 'comfort_fps', en: 'Comfort / Low-End PC', vi: 'Nhẹ máy & giảm chói', zh: '低配与护眼',
      patch: {
        impactFrames: false, skyBlackout: false, atomicCutscene: false, fugaCutscene: false,
        gateCutscene: false, purpleCutscene: false, soundVolume: 0.7, cameraShake: 0.0,
        bloomStrength: 0.4, bloomDownsample: 8
      } },
    { id: 'no_cutscenes', en: 'Cutscenes Off', vi: 'Tắt mọi Cutscene', zh: '关闭全部过场',
      patch: { atomicCutscene: false, fugaCutscene: false, gateCutscene: false, purpleCutscene: false } }
  ];

  const SKILL_CHOICES = [
    { id: 'spatial_path', en: 'Spatial Path', vi: 'Spatial Path', zh: '空间之径' },
    { id: 'singularity', en: 'Singularity', vi: 'Singularity', zh: '奇点' },
    { id: 'earth_drill', en: 'Earth Drill', vi: 'Earth Drill', zh: '大地钻击' },
    { id: 'supernova', en: 'Supernova', vi: 'Supernova', zh: '超新星' },
    { id: 'stone_cannon', en: 'Stone Cannon', vi: 'Stone Cannon', zh: '石炮' },
    { id: 'atomic', en: 'I am Atomic', vi: 'I am Atomic', zh: '吾即原子' },
    { id: 'fuga', en: 'Fuga', vi: 'Fuga', zh: 'Fuga（开）' },
    { id: 'silent_gate', en: 'Silent Gate', vi: 'Silent Gate', zh: '静默之门' },
    { id: 'purple', en: 'Purple', vi: 'Purple', zh: '紫' },
    { id: 'block_dominion', en: 'Block Dominion', vi: 'Block Dominion', zh: '方块支配' }
  ];

  const UI_TEXT = {
    en: {
      title: 'Full Config Builder',
      subtitle: 'Edit every option visually, then copy the complete ready-to-paste JSON file.',
      searchPlaceholder: 'Filter keys inside builder...',
      presetsLabel: 'Quick preset:',
      previewShow: 'Preview JSON',
      previewHide: 'Hide Preview',
      copyFull: 'Copy Full JSON',
      download: 'Download .json',
      resetAll: 'Reset All',
      modified: n => n === 0 ? 'All defaults' : `${n} modified`,
      copied: f => `Copied complete ${f} (ready to paste with Ctrl+A, Ctrl+V)`,
      downloaded: f => `Downloaded ${f}`,
      resetDone: 'Reset to 1.7.0 defaults',
      range: (a, b) => `Range: ${a} – ${b}`,
      newTag: 'New 1.7.0',
      allCutscenesOn: 'All Cutscenes On',
      allCutscenesOff: 'All Cutscenes Off',
      extraDimPlaceholder: 'Extra IDs (comma-separated, e.g. twilightforest:twilight_forest)',
      allSkillsWarn: 'All 10 skills selected — the mod ignores the list so the wand stays usable.',
      noMatch: 'No matching settings in this file.',
      resetKey: 'Reset to default'
    },
    vi: {
      title: 'Trình Tạo Config Đầy Đủ',
      subtitle: 'Chỉnh mọi tùy chọn trực quan rồi bấm Copy trọn file JSON hoàn chỉnh (Ctrl+A, Ctrl+V dán dùng ngay).',
      searchPlaceholder: 'Lọc nhanh tên key...',
      presetsLabel: 'Mẫu nhanh:',
      previewShow: 'Xem trước JSON',
      previewHide: 'Ẩn xem trước',
      copyFull: 'Copy toàn bộ JSON',
      download: 'Tải .json',
      resetAll: 'Đặt lại mặc định',
      modified: n => n === 0 ? 'Đang ở mặc định' : `Đã chỉnh ${n} mục`,
      copied: f => `Đã chép trọn bộ ${f} (Ctrl+A rồi Ctrl+V vào file là dùng ngay)`,
      downloaded: f => `Đã tải xuống ${f}`,
      resetDone: 'Đã khôi phục mặc định 1.7.0',
      range: (a, b) => `Giới hạn: ${a} – ${b}`,
      newTag: 'Mới 1.7.0',
      allCutscenesOn: 'Bật hết Cutscene',
      allCutscenesOff: 'Tắt hết Cutscene',
      extraDimPlaceholder: 'Thêm ID khác (cách nhau dấu phẩy, vd: twilightforest:twilight_forest)',
      allSkillsWarn: 'Đã chọn cả 10 skill — mod sẽ bỏ qua danh sách để gậy không thành vô dụng.',
      noMatch: 'Không tìm thấy key phù hợp.',
      resetKey: 'Khôi phục mặc định'
    },
    zh: {
      title: '完整配置生成器',
      subtitle: '可视化修改全部选项，一键复制完整 JSON 文件（全选粘贴即可直接使用）。',
      searchPlaceholder: '筛选配置项名称...',
      presetsLabel: '快速预设：',
      previewShow: '预览 JSON',
      previewHide: '隐藏预览',
      copyFull: '复制完整 JSON',
      download: '下载 .json',
      resetAll: '全部重置',
      modified: n => n === 0 ? '全部默认' : `已修改 ${n} 项`,
      copied: f => `已复制完整 ${f}（全选覆盖粘贴即可生效）`,
      downloaded: f => `已下载 ${f}`,
      resetDone: '已恢复 1.7.0 默认值',
      range: (a, b) => `范围：${a} – ${b}`,
      newTag: '1.7.0 新增',
      allCutscenesOn: '全部开启过场',
      allCutscenesOff: '全部关闭过场',
      extraDimPlaceholder: '更多维度 ID（用逗号分隔，例如 twilightforest:twilight_forest）',
      allSkillsWarn: '已全选 10 个技能——模组会忽略此列表以免法杖失效。',
      noMatch: '当前文件中未找到匹配项。',
      resetKey: '恢复默认'
    }
  };

  function getLang() {
    const l = document.body.getAttribute('data-lang');
    return (l === 'vi' || l === 'zh') ? l : 'en';
  }

  function cloneValue(v) {
    return Array.isArray(v) ? v.slice() : v;
  }

  function valuesEqual(a, b) {
    if (Array.isArray(a) && Array.isArray(b)) {
      return a.length === b.length && a.every((x, i) => x === b[i]);
    }
    return a === b;
  }

  function formatTicksHint(ticks) {
    const n = Number(ticks);
    if (!Number.isFinite(n)) return '';
    const sec = n / 20;
    return `= ${Number.isInteger(sec) ? sec : sec.toFixed(2).replace(/0$/, '')} s`;
  }

  function formatNumberJson(val, isFloat) {
    if (!Number.isFinite(val)) return '0';
    if (isFloat && Number.isInteger(val)) return `${val}.0`;
    return String(val);
  }

  function formatValueJson(spec, val) {
    if (spec.type === 'bool') return val ? 'true' : 'false';
    if (spec.type === 'enum') return JSON.stringify(String(val));
    if (spec.type === 'int') return String(Math.round(val));
    if (spec.type === 'float') return formatNumberJson(Number(val), true);
    if (spec.type === 'int[]') return `[${val.map(v => Math.round(v)).join(', ')}]`;
    if (spec.type === 'float[]') return `[${val.map(v => formatNumberJson(Number(v), true)).join(', ')}]`;
    if (spec.type === 'dims' || spec.type === 'skills') return `[${val.map(s => JSON.stringify(s)).join(', ')}]`;
    return JSON.stringify(val);
  }

  function buildDefaults(schema) {
    const map = {};
    schema.forEach(s => { map[s.key] = cloneValue(s.def); });
    return map;
  }

  document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('config-builder-modal');
    if (!modal) return;

    const serverMap = Object.fromEntries(SERVER_SCHEMA.map(s => [s.key, s]));
    const clientMap = Object.fromEntries(CLIENT_SCHEMA.map(s => [s.key, s]));

    const state = {
      file: 'server', // 'server' | 'client'
      group: 'general',
      search: '',
      previewOpen: false,
      server: buildDefaults(SERVER_SCHEMA),
      client: buildDefaults(CLIENT_SCHEMA)
    };

    const toastEl = document.getElementById('toast');
    let toastTimer = null;
    function notify(msg) {
      if (!toastEl) return;
      toastEl.textContent = msg;
      toastEl.classList.add('show');
      if (toastTimer) clearTimeout(toastTimer);
      toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2200);
    }

    function currentSchema() { return state.file === 'server' ? SERVER_SCHEMA : CLIENT_SCHEMA; }
    function currentGroups() { return state.file === 'server' ? SERVER_GROUPS : CLIENT_GROUPS; }
    function currentValues() { return state.file === 'server' ? state.server : state.client; }
    function currentFileName() { return state.file === 'server' ? 'godwand-server.json' : 'godwand-client.json'; }

    function countModified(schema, values) {
      let n = 0;
      for (const s of schema) {
        if (!valuesEqual(values[s.key], s.def)) n++;
      }
      return n;
    }

    function countGroupModified(groupId) {
      const vals = currentValues();
      return currentSchema().filter(s => s.group === groupId && !valuesEqual(vals[s.key], s.def)).length;
    }

    function generateFullJson() {
      if (state.file === 'server') {
        const blocks = SERVER_JSON_GROUPS.map(keys =>
          keys.map(k => `  ${JSON.stringify(k)}: ${formatValueJson(serverMap[k], state.server[k])}`).join(',\n')
        );
        return `{\n${blocks.join(',\n\n')}\n}`;
      }
      const lines = CLIENT_JSON_ORDER.map(k =>
        `  ${JSON.stringify(k)}: ${formatValueJson(clientMap[k], state.client[k])}`
      );
      return `{\n${lines.join(',\n')}\n}`;
    }

    function clampField(spec, raw) {
      let v = spec.type === 'int' || spec.type === 'int[]' ? parseInt(raw, 10) : parseFloat(raw);
      if (!Number.isFinite(v)) v = Array.isArray(spec.def) ? spec.def[0] : spec.def;
      if (typeof spec.min === 'number') v = Math.max(spec.min, v);
      if (typeof spec.max === 'number') v = Math.min(spec.max, v);
      if (spec.type === 'float' || spec.type === 'float[]') {
        v = Math.round(v * 1000) / 1000;
      }
      return v;
    }

    function updateFooterAndPreview() {
      const t = UI_TEXT[getLang()];
      const modCount = countModified(currentSchema(), currentValues());
      const statusEl = modal.querySelector('#cb-status');
      if (statusEl) {
        statusEl.innerHTML = `<code>config/${currentFileName()}</code> &bull; <span>${t.modified(modCount)}</span>`;
      }
      const previewDrawer = modal.querySelector('#cb-preview-drawer');
      const previewCode = modal.querySelector('#cb-preview-code');
      const previewBtn = modal.querySelector('#cb-toggle-preview');
      if (previewDrawer) previewDrawer.hidden = !state.previewOpen;
      if (previewBtn) {
        previewBtn.textContent = state.previewOpen ? t.previewHide : t.previewShow;
        previewBtn.setAttribute('aria-expanded', String(state.previewOpen));
      }
      if (state.previewOpen && previewCode) {
        previewCode.textContent = generateFullJson();
      }
      // Refresh group pill badges without wiping focus
      modal.querySelectorAll('.cb-group-btn').forEach(btn => {
        const gid = btn.getAttribute('data-cb-group');
        const badge = btn.querySelector('.cb-mod-dot');
        const c = countGroupModified(gid);
        if (badge) {
          badge.textContent = c > 0 ? String(c) : '';
          badge.hidden = c === 0;
        }
      });
    }

    function renderFields() {
      const lang = getLang();
      const t = UI_TEXT[lang];
      const list = modal.querySelector('#cb-fields');
      if (!list) return;
      list.innerHTML = '';

      const q = state.search.trim().toLowerCase();
      const vals = currentValues();
      const schema = currentSchema();
      const items = schema.filter(s => {
        if (q) {
          return s.key.toLowerCase().includes(q) || s[lang].toLowerCase().includes(q) || s.en.toLowerCase().includes(q);
        }
        return s.group === state.group;
      });

      if (items.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'cb-empty';
        empty.textContent = t.noMatch;
        list.appendChild(empty);
        return;
      }

      // Convenience master toggle for Client Cutscenes group
      if (!q && state.file === 'client' && state.group === 'cutscenes') {
        const bar = document.createElement('div');
        bar.className = 'cb-cutscene-bar';
        const onBtn = document.createElement('button');
        onBtn.type = 'button';
        onBtn.className = 'cb-mini-btn';
        onBtn.textContent = t.allCutscenesOn;
        onBtn.addEventListener('click', () => {
          ['atomicCutscene', 'fugaCutscene', 'gateCutscene', 'purpleCutscene'].forEach(k => { state.client[k] = true; });
          renderFields();
          updateFooterAndPreview();
        });
        const offBtn = document.createElement('button');
        offBtn.type = 'button';
        offBtn.className = 'cb-mini-btn';
        offBtn.textContent = t.allCutscenesOff;
        offBtn.addEventListener('click', () => {
          ['atomicCutscene', 'fugaCutscene', 'gateCutscene', 'purpleCutscene'].forEach(k => { state.client[k] = false; });
          renderFields();
          updateFooterAndPreview();
        });
        bar.append(onBtn, offBtn);
        list.appendChild(bar);
      }

      items.forEach(spec => {
        const isChanged = !valuesEqual(vals[spec.key], spec.def);
        const row = document.createElement('div');
        row.className = 'cb-row' + (isChanged ? ' is-modified' : '');

        // Left info column
        const info = document.createElement('div');
        info.className = 'cb-row-info';

        const keyLine = document.createElement('div');
        keyLine.className = 'cb-key-line';
        const code = document.createElement('code');
        code.textContent = spec.key;
        keyLine.appendChild(code);

        if (spec.isNew) {
          const nb = document.createElement('span');
          nb.className = 'badge-new';
          nb.textContent = t.newTag;
          keyLine.appendChild(nb);
        }

        if (isChanged) {
          const resetOne = document.createElement('button');
          resetOne.type = 'button';
          resetOne.className = 'cb-reset-one';
          resetOne.title = t.resetKey;
          resetOne.textContent = '↺';
          resetOne.addEventListener('click', () => {
            vals[spec.key] = cloneValue(spec.def);
            renderFields();
            updateFooterAndPreview();
          });
          keyLine.appendChild(resetOne);
        }

        const desc = document.createElement('div');
        desc.className = 'cb-row-desc';
        desc.textContent = spec[lang] || spec.en;

        info.append(keyLine, desc);

        if (typeof spec.min === 'number' && typeof spec.max === 'number' && !spec.readonly) {
          const meta = document.createElement('div');
          meta.className = 'cb-row-meta';
          meta.textContent = t.range(spec.min, spec.max) + (spec.unit ? ` (${spec.unit})` : '');
          info.appendChild(meta);
        }

        // Right control column
        const ctrl = document.createElement('div');
        ctrl.className = 'cb-row-ctrl';

        if (spec.type === 'bool') {
          const seg = document.createElement('div');
          seg.className = 'seg-group';
          [true, false].forEach(bVal => {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'seg-btn' + (vals[spec.key] === bVal ? ' active' : '');
            b.textContent = String(bVal);
            b.addEventListener('click', () => {
              vals[spec.key] = bVal;
              renderFields();
              updateFooterAndPreview();
            });
            seg.appendChild(b);
          });
          ctrl.appendChild(seg);
        } else if (spec.type === 'enum') {
          const seg = document.createElement('div');
          seg.className = 'seg-group cb-enum-group';
          spec.options.forEach(opt => {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'seg-btn' + (vals[spec.key] === opt ? ' active' : '');
            b.textContent = opt;
            b.addEventListener('click', () => {
              vals[spec.key] = opt;
              renderFields();
              updateFooterAndPreview();
            });
            seg.appendChild(b);
          });
          ctrl.appendChild(seg);
        } else if (spec.type === 'int' || spec.type === 'float') {
          const wrap = document.createElement('div');
          wrap.className = 'cb-num-wrap';
          const input = document.createElement('input');
          input.type = 'number';
          input.name = `cb_${spec.key}`;
          input.className = 'cb-num-input';
          input.value = String(vals[spec.key]);
          if (typeof spec.min === 'number') input.min = String(spec.min);
          if (typeof spec.max === 'number') input.max = String(spec.max);
          input.step = String(spec.step || (spec.type === 'int' ? 1 : 0.1));
          if (spec.readonly) input.disabled = true;

          const hint = document.createElement('span');
          hint.className = 'cb-unit-hint';
          hint.textContent = spec.unit === 'ticks' ? formatTicksHint(vals[spec.key]) : (spec.unit || '');

          input.addEventListener('input', () => {
            const v = clampField(spec, input.value);
            vals[spec.key] = v;
            if (spec.unit === 'ticks') hint.textContent = formatTicksHint(v);
            row.classList.toggle('is-modified', !valuesEqual(vals[spec.key], spec.def));
            updateFooterAndPreview();
          });
          input.addEventListener('blur', () => {
            input.value = String(vals[spec.key]);
            renderFields();
            updateFooterAndPreview();
          });
          wrap.append(input, hint);
          ctrl.appendChild(wrap);
        } else if (spec.type === 'int[]' || spec.type === 'float[]') {
          const arrWrap = document.createElement('div');
          arrWrap.className = 'cb-arr-wrap';
          vals[spec.key].forEach((val, idx) => {
            const cell = document.createElement('div');
            cell.className = 'cb-arr-cell';
            const lbl = document.createElement('span');
            lbl.className = 'cb-arr-lbl';
            lbl.textContent = `Lv ${idx + 1}`;
            const input = document.createElement('input');
            input.type = 'number';
            input.name = `cb_${spec.key}_${idx}`;
            input.className = 'cb-num-input cb-arr-input';
            input.value = String(val);
            if (typeof spec.min === 'number') input.min = String(spec.min);
            if (typeof spec.max === 'number') input.max = String(spec.max);
            input.step = String(spec.step || (spec.type === 'int[]' ? 1 : 0.1));
            const sub = document.createElement('span');
            sub.className = 'cb-arr-sub';
            sub.textContent = spec.unit === 'ticks' ? formatTicksHint(val) : '';
            input.addEventListener('input', () => {
              const nextVal = clampField(spec, input.value);
              vals[spec.key][idx] = nextVal;
              if (spec.unit === 'ticks') sub.textContent = formatTicksHint(nextVal);
              row.classList.toggle('is-modified', !valuesEqual(vals[spec.key], spec.def));
              updateFooterAndPreview();
            });
            input.addEventListener('blur', () => {
              input.value = String(vals[spec.key][idx]);
              renderFields();
              updateFooterAndPreview();
            });
            cell.append(lbl, input, sub);
            arrWrap.appendChild(cell);
          });
          ctrl.appendChild(arrWrap);
        } else if (spec.type === 'dims') {
          const dWrap = document.createElement('div');
          dWrap.className = 'cb-dims-wrap';
          const current = new Set(vals[spec.key]);
          const checks = document.createElement('div');
          checks.className = 'builder-checks';
          const quickDims = [
            { id: 'minecraft:the_nether', label: 'Nether' },
            { id: 'minecraft:the_end', label: 'The End' },
            { id: 'minecraft:overworld', label: 'Overworld' }
          ];
          const quickIds = new Set(quickDims.map(d => d.id));
          const extraList = vals[spec.key].filter(d => !quickIds.has(d));

          const syncDims = (extraText) => {
            const listOut = [];
            checks.querySelectorAll('input:checked').forEach(c => listOut.push(c.value));
            extraText.split(',').map(s => s.trim()).filter(Boolean).forEach(s => {
              if (!listOut.includes(s)) listOut.push(s);
            });
            vals[spec.key] = listOut;
            row.classList.toggle('is-modified', !valuesEqual(vals[spec.key], spec.def));
            updateFooterAndPreview();
          };

          const extraInput = document.createElement('input');
          extraInput.type = 'text';
          extraInput.name = `cb_${spec.key}_extra`;
          extraInput.className = 'builder-text';
          extraInput.placeholder = t.extraDimPlaceholder;
          extraInput.value = extraList.join(', ');
          extraInput.addEventListener('input', () => syncDims(extraInput.value));

          quickDims.forEach(d => {
            const label = document.createElement('label');
            label.className = 'builder-check';
            const chk = document.createElement('input');
            chk.type = 'checkbox';
            chk.name = `cb_${spec.key}_${d.id}`;
            chk.value = d.id;
            chk.checked = current.has(d.id);
            chk.addEventListener('change', () => syncDims(extraInput.value));
            label.append(chk, document.createTextNode(' ' + d.label));
            checks.appendChild(label);
          });

          dWrap.append(checks, extraInput);
          ctrl.appendChild(dWrap);
        } else if (spec.type === 'skills') {
          const sWrap = document.createElement('div');
          sWrap.className = 'cb-dims-wrap';
          const current = new Set(vals[spec.key]);
          const checks = document.createElement('div');
          checks.className = 'builder-checks';
          const warn = document.createElement('div');
          warn.className = 'cb-row-meta';
          warn.style.color = 'var(--accent-crimson)';
          warn.textContent = t.allSkillsWarn;
          warn.hidden = current.size < SKILL_CHOICES.length;

          SKILL_CHOICES.forEach(sk => {
            const label = document.createElement('label');
            label.className = 'builder-check';
            const chk = document.createElement('input');
            chk.type = 'checkbox';
            chk.name = `cb_${spec.key}_${sk.id}`;
            chk.value = sk.id;
            chk.checked = current.has(sk.id);
            chk.addEventListener('change', () => {
              const listOut = [];
              checks.querySelectorAll('input:checked').forEach(c => listOut.push(c.value));
              vals[spec.key] = listOut;
              warn.hidden = listOut.length < SKILL_CHOICES.length;
              row.classList.toggle('is-modified', !valuesEqual(vals[spec.key], spec.def));
              updateFooterAndPreview();
            });
            label.append(chk, document.createTextNode(' ' + sk[lang]));
            checks.appendChild(label);
          });

          sWrap.append(checks, warn);
          ctrl.appendChild(sWrap);
        }

        row.append(info, ctrl);
        list.appendChild(row);
      });
    }

    function renderModal() {
      const lang = getLang();
      const t = UI_TEXT[lang];
      const presets = state.file === 'server' ? SERVER_PRESETS : CLIENT_PRESETS;
      const groups = currentGroups();
      if (!groups.some(g => g.id === state.group)) state.group = groups[0].id;

      modal.innerHTML = `
        <div class="cb-backdrop" data-cb-close></div>
        <div class="cb-dialog" role="dialog" aria-modal="true" aria-labelledby="cb-modal-title">
          <div class="cb-header">
            <div>
              <h2 id="cb-modal-title" class="cb-title">${t.title}</h2>
              <p class="cb-subtitle">${t.subtitle}</p>
            </div>
            <button type="button" class="cb-close-btn" data-cb-close aria-label="Close">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>

          <div class="cb-toolbar">
            <div class="cb-file-tabs" role="tablist">
              <button type="button" class="cb-file-tab ${state.file === 'server' ? 'active' : ''}" data-cb-file="server">
                godwand-server.json <span class="cb-pill-num">78</span>
              </button>
              <button type="button" class="cb-file-tab ${state.file === 'client' ? 'active' : ''}" data-cb-file="client">
                godwand-client.json <span class="cb-pill-num">19</span>
              </button>
            </div>
            <div class="cb-search-wrap">
              <input type="text" id="cb-search" name="cb_search" class="cb-search-input" value="${state.search.replace(/"/g, '&quot;')}" placeholder="${t.searchPlaceholder}">
            </div>
          </div>

          <div class="cb-presets-bar">
            <span class="cb-presets-label">${t.presetsLabel}</span>
            ${presets.map(p => `<button type="button" class="cb-preset-chip" data-cb-preset="${p.id}">${p[lang]}</button>`).join('')}
          </div>

          <div class="cb-body">
            <nav class="cb-sidebar" aria-label="Config categories">
              ${groups.map(g => {
                const mc = countGroupModified(g.id);
                return `<button type="button" class="cb-group-btn ${state.group === g.id && !state.search ? 'active' : ''}" data-cb-group="${g.id}">
                  <span>${g[lang]}</span>
                  <span class="cb-mod-dot" ${mc === 0 ? 'hidden' : ''}>${mc > 0 ? mc : ''}</span>
                </button>`;
              }).join('')}
            </nav>
            <div class="cb-main">
              <div id="cb-fields" class="cb-fields"></div>
            </div>
          </div>

          <div id="cb-preview-drawer" class="cb-preview-drawer" ${state.previewOpen ? '' : 'hidden'}>
            <div class="cb-preview-bar">
              <span>config/${currentFileName()} (100% Complete — Ready to Paste)</span>
            </div>
            <pre class="cb-preview-pre"><code id="cb-preview-code"></code></pre>
          </div>

          <div class="cb-footer">
            <div id="cb-status" class="cb-status"></div>
            <div class="cb-footer-actions">
              <button type="button" id="cb-reset-all" class="cb-sec-btn">${t.resetAll}</button>
              <button type="button" id="cb-toggle-preview" class="cb-sec-btn" aria-expanded="${state.previewOpen}">${state.previewOpen ? t.previewHide : t.previewShow}</button>
              <button type="button" id="cb-download" class="cb-sec-btn">${t.download}</button>
              <button type="button" id="cb-copy-full" class="cb-primary-btn">${t.copyFull}</button>
            </div>
          </div>
        </div>
      `;

      // Bind modal events
      modal.querySelectorAll('[data-cb-close]').forEach(el => el.addEventListener('click', closeModal));

      modal.querySelectorAll('.cb-file-tab').forEach(btn => {
        btn.addEventListener('click', () => {
          state.file = btn.getAttribute('data-cb-file');
          state.group = currentGroups()[0].id;
          state.search = '';
          renderModal();
        });
      });

      const cbSearch = modal.querySelector('#cb-search');
      if (cbSearch) {
        cbSearch.addEventListener('input', () => {
          state.search = cbSearch.value;
          modal.querySelectorAll('.cb-group-btn').forEach(b => {
            b.classList.toggle('active', !state.search && b.getAttribute('data-cb-group') === state.group);
          });
          renderFields();
        });
      }

      modal.querySelectorAll('.cb-group-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          state.group = btn.getAttribute('data-cb-group');
          state.search = '';
          if (cbSearch) cbSearch.value = '';
          modal.querySelectorAll('.cb-group-btn').forEach(b => b.classList.toggle('active', b === btn));
          renderFields();
          const mainEl = modal.querySelector('.cb-main');
          if (mainEl) mainEl.scrollTop = 0;
        });
      });

      modal.querySelectorAll('.cb-preset-chip').forEach(btn => {
        btn.addEventListener('click', () => {
          const pid = btn.getAttribute('data-cb-preset');
          const preset = presets.find(p => p.id === pid);
          if (!preset) return;
          const defaults = buildDefaults(currentSchema());
          Object.entries(preset.patch).forEach(([k, v]) => { defaults[k] = cloneValue(v); });
          if (state.file === 'server') state.server = defaults;
          else state.client = defaults;
          renderFields();
          updateFooterAndPreview();
        });
      });

      modal.querySelector('#cb-reset-all').addEventListener('click', () => {
        if (state.file === 'server') state.server = buildDefaults(SERVER_SCHEMA);
        else state.client = buildDefaults(CLIENT_SCHEMA);
        renderFields();
        updateFooterAndPreview();
        notify(UI_TEXT[getLang()].resetDone);
      });

      modal.querySelector('#cb-toggle-preview').addEventListener('click', () => {
        state.previewOpen = !state.previewOpen;
        updateFooterAndPreview();
      });

      modal.querySelector('#cb-copy-full').addEventListener('click', () => {
        const jsonText = generateFullJson() + '\n';
        if (navigator.clipboard) {
          navigator.clipboard.writeText(jsonText).then(() => {
            notify(UI_TEXT[getLang()].copied(currentFileName()));
          }).catch(() => {});
        }
      });

      modal.querySelector('#cb-download').addEventListener('click', () => {
        const jsonText = generateFullJson() + '\n';
        const blob = new Blob([jsonText], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = currentFileName();
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 500);
        notify(UI_TEXT[getLang()].downloaded(currentFileName()));
      });

      renderFields();
      updateFooterAndPreview();
    }

    function openModal(fileType) {
      if (fileType === 'server' || fileType === 'client') {
        state.file = fileType;
        state.group = currentGroups()[0].id;
      }
      state.previewOpen = false;
      modal.classList.add('open');
      modal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');
      renderModal();
    }

    function closeModal() {
      modal.classList.remove('open');
      modal.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
    }

    document.querySelectorAll('.open-config-builder').forEach(btn => {
      btn.addEventListener('click', () => {
        openModal(btn.getAttribute('data-builder-file') || 'server');
      });
    });

    // Re-render modal labels if language changes while open
    window.addEventListener('godwand:langchange', () => {
      if (modal.classList.contains('open')) {
        renderModal();
      }
    });

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && modal.classList.contains('open')) {
        e.stopPropagation();
        closeModal();
      }
    }, true);
  });
})();
