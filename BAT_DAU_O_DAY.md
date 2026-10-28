# Tiếp tục God Wand ở đây

Đây là repo làm việc chính của God Wand, hiện làm bản thử 1.4.0-dev sau bản release 1.3.1. Tên thư mục vẫn là god-wand-1.3.0. Không cần mở lại các checkout God Wand cũ.

- GitHub: https://github.com/HyIsNoob/god-wand
- JAR mới để test: `build/release/` (đủ 4 bản).
- Source 1.21.1 dùng chung: `common/`; kết nối loader ở `fabric/` và `neoforge/`.
- Hai port 1.20.1: `fabric-1.20.1/` và `forge-1.20.1/`. Đây là source cần thiết, không phải folder dư.

Chạy từ thư mục repo này:

```powershell
.\gradlew.bat releaseJars            # build + kiểm tra đủ 4 bản
.\gradlew.bat :fabric:runClient      # tự test Fabric 1.21.1
```

`runClient` bình thường không nạp QA probe. Bản source mới nhất đã gồm tia tụ từ trên/dưới vào kiếm, chuyển sang trail lúc kéo kiếm ngang, rung nhẹ, camera có cùng giới hạn lùi và crater sâu hơn. Visual đã được bạn duyệt; SFX Atomic riêng đã tích hợp, chờ nghe thử trong game. Config cũ tự bổ sung option mới và cả bốn bản đều có recipe trượng.

Repo GitHub công khai mã nguồn, không đăng JAR God Wand hay GitHub Release. Người khác vẫn có thể tự build mã nguồn. JAR phát hành chính thức sẽ được đăng riêng theo kế hoạch CurseForge.

Bản thử mới: `build/preview/1.4.0-dev/`. Cài đặt damage/cooldown: `docs/BALANCE_CONFIG.md`. Hai bản Minecraft mới trong `ports/` chưa chạy được; không dùng làm bản release.
