# God Wand 1.6.0 Wiki — Deployment Guide

Trang wiki chính thức cho mod **God Wand**. Được xây dựng bằng **HTML5 / CSS3 / Vanilla JS**, không cần Node.js build step, tải siêu tốc, hỗ trợ 3 ngôn ngữ (Tiếng Anh / Tiếng Việt / Tiếng Trung - EN / VI / ZH), tích hợp chuẩn `llms.txt` và nút 1-click **Copy Context cho AI**.

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

- `index.html`: Giao diện chính đa ngôn ngữ (EN / VI / ZH), gồm 9 kỹ năng (thêm Purple), phần Cutscenes chung, 15 thành tựu (Advancements), 4 ngoại hình trượng, 71 tham số server config và 18 tham số client config.
- `config-builder.js`: Trình tạo config trực quan đầy đủ 71 server keys + 18 client keys (hỗ trợ 3 ngôn ngữ, preview ẩn mặc định, copy trọn file JSON hoặc tải xuống).
- `style.css`: Hệ màu Dark/Light, responsive trên cả máy tính và điện thoại.
- `script.js`: Xử lý chuyển ngôn ngữ (dropdown EN / VI / ZH + `?lang=`), tìm kiếm nhanh có highlight & nhảy kết quả, lọc kỹ năng, sao chép JSON và gom ngữ cảnh AI.
- `llms.txt`: Bản tóm tắt cấu trúc chuẩn cho AI agent.
- `llms-full.txt`: Toàn bộ tài liệu dạng văn bản thuần (đủ 9 kỹ năng, 15 thành tựu, 71 server keys và 18 client keys kèm mặc định và giới hạn).
- `vercel.json`: Cấu hình routing tĩnh cho Vercel kèm CORS và content-type cho `llms*.txt`.
- `assets/`: Hình ảnh avatar và công thức chế tạo.
