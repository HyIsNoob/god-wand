# God Wand 1.3.2 Wiki — Vercel Deployment Guide

Trang wiki tĩnh chính thức cho mod **God Wand**. Được xây dựng hoàn toàn bằng **HTML5 / CSS3 / Vanilla JS**, không cần Node.js build step, tải siêu tốc và hỗ trợ song ngữ (Tiếng Việt & English).

---

## 🚀 Cách Deploy lên Vercel trong 1 phút

### Cách 1: Kết nối trực tiếp qua Vercel Dashboard (Khuyên dùng)
1. Đăng nhập vào [Vercel](https://vercel.com).
2. Bấm **"Add New Project"** và chọn kho lưu trữ GitHub `HyIsNoob/god-wand`.
3. Trong phần **Root Directory**, bấm `Edit` và chọn thư mục: `wiki`.
4. Bấm **"Deploy"**. Trang web sẽ được xuất bản ngay lập tức mà không cần bất kỳ lệnh build nào!

### Cách 2: Deploy qua Vercel CLI
Nếu bạn đã cài Vercel CLI trên máy tính:
```powershell
cd D:\fileluu\Tools\god-wand-1.3.0\wiki
vercel
```
Làm theo hướng dẫn trên màn hình và chọn deploy production:
```powershell
vercel --prod
```

---

## 📁 Cấu trúc thư mục

- `index.html`: Giao diện chính, cấu trúc tài liệu song ngữ (VI/EN).
- `style.css`: Hệ màu Dark-Fantasy, responsive và trau chuốt UX/UI.
- `script.js`: Xử lý chuyển đổi ngôn ngữ (i18n), tìm kiếm nhanh, công cụ quy đổi tick và sao chép config.
- `vercel.json`: Cấu hình routing tĩnh chuẩn hóa cho Vercel.
