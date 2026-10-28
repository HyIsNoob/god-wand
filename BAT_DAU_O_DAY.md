# Tiếp tục God Wand ở đây

Đây là repo làm việc chính của God Wand, hiện làm bản thử 1.3.2 sau bản release 1.3.1. Tên thư mục vẫn là god-wand-1.3.0. Không cần mở lại các checkout God Wand cũ.

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

Bản thử 1.3.2: `build/release/`. Fabric 26.3: `build/release/26.3/godwand-fabric-26.3-1.3.2.jar` (Java 25 + Fabric API 26.3). Fabric 26.1.2: port riêng ở `ports/mc-26.1.2/` (Java 25 + Fabric API 0.155.3+26.1.2). Source/build riêng cho 26.x nằm trong `ports/`. NeoForge và 1.21.11 đang pending; không làm song song. Cài đặt damage/cooldown: `docs/BALANCE_CONFIG.md`.
