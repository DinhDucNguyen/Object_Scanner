import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from app.core.config import settings


class EmailService:
    def _send(self, to_email: str, subject: str, html_body: str) -> bool:
        try:
            msg = MIMEMultipart("alternative")
            msg["Subject"] = subject
            msg["From"] = settings.SMTP_FROM
            msg["To"] = to_email
            msg.attach(MIMEText(html_body, "html", "utf-8"))

            with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT) as server:
                server.ehlo()
                server.starttls()
                server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
                server.sendmail(settings.SMTP_FROM, to_email, msg.as_string())
            return True
        except Exception as e:
            print(f"[ERROR] EmailService._send: {e}")
            return False

    def send_otp(self, to_email: str, otp_code: str) -> bool:
        subject = "Mã xác nhận đặt lại mật khẩu – LengoLens"
        html_body = f"""
        <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                    border:1px solid #e0e0e0;border-radius:12px;">
            <h2 style="color:#1976D2;margin-bottom:8px;">Đặt lại mật khẩu</h2>
            <p style="color:#555;">Mã OTP của bạn là:</p>
            <div style="font-size:36px;font-weight:bold;letter-spacing:8px;
                        color:#1976D2;text-align:center;padding:16px 0;">
                {otp_code}
            </div>
            <p style="color:#888;font-size:13px;">
                Mã có hiệu lực trong <strong>10 phút</strong>.<br>
                Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
            </p>
        </div>
        """
        return self._send(to_email, subject, html_body)
