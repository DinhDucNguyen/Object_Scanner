import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from app.core.config import settings

logger = logging.getLogger(__name__)


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
            logger.error("EmailService._send: %s", e)
            return False

    def send_otp(self, to_email: str, otp_code: str, purpose: str = "reset") -> bool:
        if purpose == "register":
            subject = "Xác thực tài khoản – LengoLens"
            heading = "Xác thực tài khoản"
            note = "Nếu bạn không tạo tài khoản, hãy bỏ qua email này."
        else:
            subject = "Mã xác nhận đặt lại mật khẩu – LengoLens"
            heading = "Đặt lại mật khẩu"
            note = "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này."
        html_body = f"""
        <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                    border:1px solid #e0e0e0;border-radius:12px;">
            <h2 style="color:#1976D2;margin-bottom:8px;">{heading}</h2>
            <p style="color:#555;">Mã OTP của bạn là:</p>
            <div style="font-size:36px;font-weight:bold;letter-spacing:8px;
                        color:#1976D2;text-align:center;padding:16px 0;">
                {otp_code}
            </div>
            <p style="color:#888;font-size:13px;">
                Mã có hiệu lực trong <strong>1 phút</strong>.<br>
                {note}
            </p>
        </div>
        """
        return self._send(to_email, subject, html_body)
