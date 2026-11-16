# God Wand 1.5.0 Wiki — Deployment Guide

Trang wiki kỹ thuật chính thức cho mod **God Wand**. Được xây dựng bằng **HTML5 / CSS3 / Vanilla JS**, không cần Node.js build step, tải siêu tốc, hỗ trợ 3 ngôn ngữ (Tiếng Anh / Tiếng Việt / Tiếng Trung - EN / VI / ZH), có tìm kiếm nhanh và chế độ sáng/tối.

---

## Hướng Dẫn Deploy Lên Vercel

### Cách 1: Kết nối trực tiếp qua Vercel Dashboard (Khuyên dùng)
1. Đăng nhập vào [Vercel](https://vercel.com).
2. Bấm **"Add New Project"** và chọn kho lưu trữ GitHub `HyIsNoob/god-wand`.
3. Trong phần **Root Directory**, bấm `Edit` và chọn thư mục: `wiki`.
4. Bấm **"Deploy"**. Trang web sẽ được xuất bản ngay lập tức mà không cần bất kỳ lệnh build nào.

### Cách 2: Deploy qua Vercel CLI
Nếu bạn đã cài Vercel CLI trên máy tính:
```powershell
cd D:\fileluu\Tools\GodWand\repo\wiki
vercel
```
Làm theo hướng dẫn trên màn hình và chọn deploy production:
```powershell
vercel --prod
```

---

## Cấu Trúc Thư Mục

- `index.html`: Giao diện chính, cấu trúc tài liệu đa ngôn ngữ (EN / VI / ZH), 8 chiêu thức ma pháp (kèm Silent Gate), 4 skin trượng, 43 tham số server config, 12 tham số client config.
- `style.css`: Hệ màu Dark-Fantasy/Light, responsive và trau chuốt UX/UI.
- `script.js`: Xử lý chuyển đổi ngôn ngữ (i18n), tìm kiếm nhanh theo danh mục và sao chép config.
- `vercel.json`: Cấu hình routing tĩnh cho Vercel.
- `assets/`: Hình ảnh avatar, công thức chế tạo và tài nguyên mở rộng.
