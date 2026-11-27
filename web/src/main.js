import './style.css';

const note = document.querySelector('#build-note');
const stamp = new Date().toLocaleTimeString('fa-IR', { hour: '2-digit', minute: '2-digit' });
note.textContent = `نسخه آزمایشی رابط وب · آخرین بارگذاری: ${stamp}`;

document.querySelector('#add-income').addEventListener('click', () => {
  alert('فرم ثبت دریافت در مرحله بعدی پیاده‌سازی می‌شود.');
});
document.querySelector('#add-expense').addEventListener('click', () => {
  alert('فرم ثبت پرداخت در مرحله بعدی پیاده‌سازی می‌شود.');
});
