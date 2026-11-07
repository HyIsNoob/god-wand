# God Wand 1.4.0 Wiki — Deployment Guide

Trang wiki kỹ thuật chính thức cho mod **God Wand**. Được xây dựng bằng **HTML5 / CSS3 / Vanilla JS**, không cần Node.js build step, tải siêu tốc và hỗ trợ 3 ngôn ngữ (Tiếng Anh / Tiếng Việt / Tiếng Trung - EN / VI / ZH).

---

## Huong Dan Deploy Len Vercel

### Cach 1: Ket noi truc tiep qua Vercel Dashboard (Khuyen dung)
1. Dang nhap vao [Vercel](https://vercel.com).
2. Bam **"Add New Project"** va chon kho luu tru GitHub `HyIsNoob/god-wand`.
3. Trong phan **Root Directory**, bam `Edit` va chon thu muc: `wiki`.
4. Bam **"Deploy"**. Trang web se duoc xuat ban ngay lap tuc ma khong can bat ky lenh build nao.

### Cach 2: Deploy qua Vercel CLI
Neu ban da cai Vercel CLI tren may tinh:
```powershell
cd D:\fileluu\Tools\god-wand-1.4.0\wiki
vercel
```
Lam theo huong dan tren man hinh va chon deploy production:
```powershell
vercel --prod
```

---

## Cau Truc Thu Muc

- `index.html`: Giao dien chinh, cau truc tai lieu da ngon ngu (EN / VI / ZH).
- `style.css`: He mau Dark-Fantasy/Light, responsive va trau chuot UX/UI.
- `script.js`: Xu ly chuyen doi ngon ngu (i18n), tim kiem nhanh theo danh muc va sao chep config.
- `vercel.json`: Cau hinh routing tinh chuan hoa cho Vercel.
- `assets/`: Hinh anh avatar va tai nguyen mo rong.
