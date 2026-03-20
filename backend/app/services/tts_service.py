from gtts import gTTS
import io


class TTSService:
    def generate_audio(self, text: str, lang_code: str = "en") -> bytes:
        """
        Chuyển text thành audio MP3 bằng gTTS.
        Trả về bytes của file MP3.
        """
        try:
            tts = gTTS(text=text, lang=lang_code, slow=False)
            audio_buffer = io.BytesIO()
            tts.write_to_fp(audio_buffer)
            audio_buffer.seek(0)
            return audio_buffer.read()
        except Exception as e:
            print(f"TTS error: {e}")
            return None
