# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack
Pure static HTML5 / CSS3 / Vanilla JS (zero build dependencies, Vercel-ready)

## Users
- **Người chơi Minecraft (Players):** Tra cứu nhanh phím tắt điều khiển, cơ chế vận hành của 6 kỹ năng tối thượng (đặc biệt là *I am Atomic* và *Supernova*), và cách đổi diện mạo trượng.
- **Chủ Server & Modpack Creators (Admins):** Tinh chỉnh sát thương, thời gian hồi chiêu, bật/tắt phá hủy địa hình (anti-griefing) và tối ưu hóa FPS cho người chơi máy yếu.
- **Cộng đồng Quốc tế & Việt Nam:** Đọc tài liệu song ngữ liền mạch (VI/EN) phục vụ cho dự án phát hành chính thức trên CurseForge, Modrinth và GitHub.

## Product Purpose
Trở thành cổng thông tin tra cứu (Wiki) chính thức, toàn diện và trực quan nhất cho mod **God Wand 1.3.2**. Xóa bỏ sự khó hiểu của các file cấu hình JSON thô bằng giao diện tài liệu hiện đại, rõ ràng, có sẵn preset cấu hình 1 chạm và công cụ quy đổi tick chuẩn xác.

## Positioning
Static Web Wiki chuyên biệt cho Minecraft mod: cực nhẹ, không cần build step, tải tức thì, tích hợp chuyển đổi song ngữ (Tiếng Việt & English), công cụ tính toán thời gian thực và hướng dẫn bảo vệ thế giới không bị phá hủy.

## Operating Context
- Triển khai hosting tĩnh trên **Vercel** từ kho lưu trữ GitHub `HyIsNoob/god-wand`.
- Người dùng thường truy cập khi đang mở Minecraft (Alt+Tab trên PC) hoặc lướt nhanh trên điện thoại di động khi tìm kiếm thông tin modpack.

## Capabilities and Constraints
- **Song ngữ VI / EN:** Chuyển đổi qua lại ngay lập tức chỉ với một nút bấm không cần tải lại trang.
- **Không dùng framework nặng:** Giữ trọn vẹn HTML5, CSS3 hiện đại và Vanilla JS nhẹ nhàng, không lỗi build trên Vercel.
- **Tra cứu cấu hình:** Phân tích chi tiết từng key của `godwand-server.json` và `godwand-client.json`.
- **Cảnh báo an toàn JSON:** Giải thích rõ lý do không được viết comment `//` để tránh sập game khi khởi động.
- **Interactive Tools:** Widget quy đổi Tick ⇄ Giây và bộ preset mẫu copy 1 chạm.
- **Tương thích Boss:** Cung cấp tài liệu tích hợp sát thương với các mod boss lớn (*Cataclysm, Wither Storm, Desert Behemoths*).

## Brand Commitments
- **Tên dự án:** God Wand (Minecraft Mod).
- **Thẩm mỹ:** Dark Fantasy kết hợp Anime Magic (tím thạch anh, cyan huyền bí, nền tối sang trọng).
- **Giọng điệu:** Chính xác, kỹ thuật nhưng mạch lạc, thân thiện với game thủ.

## Evidence on Hand
- Kho tài liệu kỹ thuật tại `docs/` (`BALANCE_CONFIG.md`, `TERRAIN_CONFIG.md`, `ATOMIC_CINEMATIC.md`, `BOSS_COMPATIBILITY.md`, `DESCRIPTION.md`).
- File cấu hình gốc được xuất từ instance Minecraft Fabric 1.21.1: `godwand-server.json` và `godwand-client.json`.

## Product Principles
1. **Tốc độ & Thuần khiết:** Tải trang trong chớp mắt, không rườm rà.
2. **Thực tiễn & An toàn:** Biến thông số code thành lời khuyên dễ hiểu; cảnh báo trước các thiết lập có nguy cơ phá nát map.
3. **Chính xác tuyệt đối:** Mọi con số (damage, radius, tick) khớp 100% mã nguồn God Wand 1.3.2.
4. **Song ngữ bình đẳng:** Nội dung tiếng Việt và tiếng Anh đều đầy đủ và chi tiết như nhau.
