"""
Seed dữ liệu mẫu cho database language_learning_db
Chạy: python -m app.db.seed
"""

from sqlalchemy.orm import Session
from app.db.session import SessionLocal
from app.models.user import User
from app.models.profile import Profile
from app.models.user_settings import UserSettings
from app.models.language import Language
from app.models.category import Category
from app.models.object import Object
from app.models.translation import Translation
from app.models.data_version import DataVersion
from app.utils.security import hash_password


def seed_database(db: Session):
    # ====== LANGUAGES ======
    languages = [
        Language(code="vi", name="Tiếng Việt", flag_icon_url="🇻🇳"),
        Language(code="en", name="English", flag_icon_url="🇺🇸"),
        Language(code="ja", name="日本語", flag_icon_url="🇯🇵"),
        Language(code="ko", name="한국어", flag_icon_url="🇰🇷"),
        Language(code="zh", name="中文", flag_icon_url="🇨🇳"),
        Language(code="fr", name="Français", flag_icon_url="🇫🇷"),
    ]
    db.add_all(languages)
    db.flush()
    lang_map = {l.code: l.id for l in languages}

    # ====== DEFAULT USER ======
    user = User(
        username="duc",
        email="duc@student.dn.edu.vn",
        password_hash=hash_password("123456"),
        status="active", role="user"
    )
    db.add(user)
    db.flush()
    db.add(Profile(user_id=user.id, full_name="Đức", student_id="22115141122102"))
    db.add(UserSettings(user_id=user.id, native_lang_code="vi", target_lang_code="en", theme="dark"))

    # ====== CATEGORIES ======
    cats_data = [
        ("Điện tử", "Thiết bị điện tử", "💻"),
        ("Đồ dùng học tập", "Dụng cụ trường học", "📚"),
        ("Nhà bếp", "Đồ dùng nhà bếp", "🍽️"),
        ("Nội thất", "Bàn ghế, nội thất", "🪑"),
        ("Thực phẩm", "Hoa quả, đồ ăn", "🍎"),
        ("Động vật", "Các loài động vật", "🐾"),
        ("Phương tiện", "Xe cộ, phương tiện", "🚗"),
        ("Phụ kiện", "Ba lô, ô, phụ kiện", "🎒"),
    ]
    categories = []
    for name, desc, icon in cats_data:
        c = Category(name=name, description=desc, icon_url=icon)
        db.add(c)
        categories.append(c)
    db.flush()
    cat_map = {c.name: c.id for c in categories}

    # ====== OBJECTS + TRANSLATIONS ======
    objects_data = [
        ("laptop", "Điện tử", 1, {
            "vi": ("Máy tính xách tay", "/máj tǐnˀ sák taj/", "Thiết bị điện tử cầm tay", "Tôi dùng máy tính xách tay để làm việc."),
            "ja": ("ノートパソコン", "/noːtopasokon/", "仕事に使うPC", "ノートパソコンで仕事をしています。"),
            "ko": ("노트북", "/noteubuk/", "휴대용 컴퓨터", "노트북으로 일을 합니다."),
            "zh": ("笔记本电脑", "/bǐjìběn diànnǎo/", "便携式电脑", "我用笔记本电脑工作。"),
        }),
        ("cell phone", "Điện tử", 1, {
            "vi": ("Điện thoại di động", "/ɗiən tʰwaːj zi ɗoŋ/", "Thiết bị liên lạc", "Điện thoại di động rất tiện lợi."),
            "ja": ("携帯電話", "/keitaidenwa/", "携帯型の電話機", "携帯電話を忘れました。"),
            "ko": ("휴대폰", "/hyudaepon/", "이동전화기", "휴대폰을 잃어버렸어요."),
            "zh": ("手机", "/shǒujī/", "移动电话设备", "我的手机没电了。"),
        }),
        ("keyboard", "Điện tử", 1, {
            "vi": ("Bàn phím", "/baːn fǐm/", "Thiết bị nhập liệu", "Bàn phím cơ rất tốt để lập trình."),
            "ja": ("キーボード", "/kiːboːdo/", "文字入力装置", "キーボードを新しく買いました。"),
            "ko": ("키보드", "/kibodeu/", "문자 입력 장치", "키보드를 새로 샀습니다."),
            "zh": ("键盘", "/jiànpán/", "计算机输入设备", "这个键盘打字很舒服。"),
        }),
        ("mouse", "Điện tử", 1, {
            "vi": ("Chuột máy tính", "/ʧuə̆t máj tǐnˀ/", "Thiết bị điều khiển", "Chuột không dây rất tiện."),
            "ja": ("マウス", "/mausu/", "ポインティングデバイス", "ワイヤレスマウスを使っています。"),
            "ko": ("마우스", "/mauseu/", "포인팅 장치", "무선 마우스를 사용합니다."),
            "zh": ("鼠标", "/shǔbiāo/", "计算机指向设备", "我用无线鼠标。"),
        }),
        ("television", "Điện tử", 1, {
            "vi": ("Ti vi", "/ti vi/", "Thiết bị xem truyền hình", "Gia đình tôi xem ti vi mỗi tối."),
            "ja": ("テレビ", "/terebi/", "テレビジョン受像機", "毎晩テレビを見ます。"),
            "ko": ("텔레비전", "/tellebijeon/", "텔레비전 수상기", "매일 저녁 텔레비전을 봅니다."),
            "zh": ("电视", "/diànshì/", "电视接收装置", "我们每天晚上看电视。"),
        }),
        ("book", "Đồ dùng học tập", 1, {
            "vi": ("Sách", "/sáːk/", "Tập hợp các trang giấy", "Tôi đang đọc một cuốn sách hay."),
            "ja": ("本", "/hon/", "印刷された紙の束", "この本はとても面白いです。"),
            "ko": ("책", "/chaek/", "인쇄된 종이 묶음", "이 책은 매우 재미있습니다."),
            "zh": ("书", "/shū/", "印刷的纸张集合", "这本书很有趣。"),
        }),
        ("pen", "Đồ dùng học tập", 1, {
            "vi": ("Bút", "/bǔt/", "Dụng cụ viết", "Cho tôi mượn cây bút."),
            "ja": ("ペン", "/pen/", "インクで書く道具", "ペンを貸してください。"),
            "ko": ("펜", "/pen/", "잉크로 쓰는 도구", "펜을 빌려주세요."),
            "zh": ("笔", "/bǐ/", "用墨水书写的工具", "请借我一支笔。"),
        }),
        ("cup", "Nhà bếp", 1, {
            "vi": ("Cốc", "/kǒk/", "Đồ đựng nước uống", "Cho tôi một cốc nước."),
            "ja": ("コップ", "/koppu/", "飲み物を入れる容器", "コップに水を入れてください。"),
            "ko": ("컵", "/keop/", "음료를 담는 용기", "물 한 컵 주세요."),
            "zh": ("杯子", "/bēizi/", "盛放饮料的容器", "请给我一杯水。"),
        }),
        ("bottle", "Nhà bếp", 1, {
            "vi": ("Chai", "/ʧaːj/", "Vật đựng chất lỏng", "Chai nước này rất lớn."),
            "ja": ("ボトル", "/botoru/", "液体を入れる容器", "このボトルは大きいです。"),
            "ko": ("병", "/byeong/", "액체 용기", "이 병은 매우 큽니다."),
            "zh": ("瓶子", "/píngzi/", "窄口液体容器", "这个瓶子很大。"),
        }),
        ("chair", "Nội thất", 1, {
            "vi": ("Ghế", "/ɣɛ̂ː/", "Đồ nội thất để ngồi", "Hãy ngồi xuống ghế."),
            "ja": ("椅子", "/isu/", "座るための家具", "椅子に座ってください。"),
            "ko": ("의자", "/uija/", "앉기 위한 가구", "의자에 앉아 주세요."),
            "zh": ("椅子", "/yǐzi/", "用来坐的家具", "请坐在椅子上。"),
        }),
        ("banana", "Thực phẩm", 1, {
            "vi": ("Chuối", "/ʧuə̆j/", "Loại quả nhiệt đới", "Chuối rất tốt cho sức khỏe."),
            "ja": ("バナナ", "/banana/", "黄色い熱帯果物", "バナナは健康にいいです。"),
            "ko": ("바나나", "/banana/", "노란 열대 과일", "바나나는 건강에 좋습니다."),
            "zh": ("香蕉", "/xiāngjiāo/", "黄色热带水果", "香蕉对健康有好处。"),
        }),
        ("apple", "Thực phẩm", 1, {
            "vi": ("Quả táo", "/kʷǎː tǎːw/", "Loại quả tròn vị ngọt", "Ăn một quả táo mỗi ngày."),
            "ja": ("りんご", "/ringo/", "丸い甘い果物", "りんごを一つください。"),
            "ko": ("사과", "/sagwa/", "둥근 달콤한 과일", "사과 하나 주세요."),
            "zh": ("苹果", "/píngguǒ/", "圆形甜味水果", "请给我一个苹果。"),
        }),
        ("dog", "Động vật", 1, {
            "vi": ("Chó", "/ʧɔ̌ː/", "Động vật nuôi trung thành", "Con chó rất trung thành."),
            "ja": ("犬", "/inu/", "忠実なペット動物", "犬はとても忠実です。"),
            "ko": ("개", "/gae/", "충실한 반려동물", "개는 매우 충실합니다."),
            "zh": ("狗", "/gǒu/", "忠诚的宠物", "狗很忠诚。"),
        }),
        ("cat", "Động vật", 1, {
            "vi": ("Mèo", "/mɛ̀w/", "Động vật nuôi nhỏ nhẹ", "Con mèo đang ngủ."),
            "ja": ("猫", "/neko/", "小さなペット動物", "猫が寝ています。"),
            "ko": ("고양이", "/goyangi/", "작은 반려동물", "고양이가 자고 있습니다."),
            "zh": ("猫", "/māo/", "小型宠物动物", "猫在睡觉。"),
        }),
        ("car", "Phương tiện", 2, {
            "vi": ("Ô tô", "/oː toː/", "Phương tiện bốn bánh", "Tôi muốn mua một chiếc ô tô mới."),
            "ja": ("車", "/kuruma/", "四輪の乗り物", "新しい車が欲しいです。"),
            "ko": ("자동차", "/jadongcha/", "네 바퀴 교통수단", "새 자동차를 사고 싶습니다."),
            "zh": ("汽车", "/qìchē/", "四轮交通工具", "我想买一辆新车。"),
        }),
        ("bicycle", "Phương tiện", 2, {
            "vi": ("Xe đạp", "/sɛ ɗaːp/", "Phương tiện hai bánh", "Tôi đi xe đạp đến trường."),
            "ja": ("自転車", "/jitensha/", "人力二輪車", "自転車で学校に行きます。"),
            "ko": ("자전거", "/jajeongeo/", "인력 이륜차", "자전거로 학교에 갑니다."),
            "zh": ("自行车", "/zìxíngchē/", "人力双轮车", "我骑自行车去学校。"),
        }),
        ("backpack", "Phụ kiện", 2, {
            "vi": ("Ba lô", "/baː loː/", "Túi đeo lưng", "Ba lô của tôi rất nặng."),
            "ja": ("リュックサック", "/ryukkusakku/", "背負うカバン", "リュックサックが重いです。"),
            "ko": ("배낭", "/baenang/", "등에 메는 가방", "배낭이 무겁습니다."),
            "zh": ("背包", "/bèibāo/", "背在背上的包", "我的背包很重。"),
        }),
        ("umbrella", "Phụ kiện", 2, {
            "vi": ("Ô", "/oː/", "Dụng cụ che mưa nắng", "Mang theo ô vì trời sắp mưa."),
            "ja": ("傘", "/kasa/", "雨から守る道具", "傘を持っていきます。"),
            "ko": ("우산", "/usan/", "비를 막는 도구", "우산을 가져갑니다."),
            "zh": ("伞", "/sǎn/", "遮雨挡阳的工具", "带上伞，快要下雨了。"),
        }),
    ]

    for code, cat_name, diff, trans in objects_data:
        obj = Object(
            object_code=code,
            category_id=cat_map.get(cat_name),
            difficulty_level=diff,
            created_by=user.id
        )
        db.add(obj)
        db.flush()
        for lang_code, (word, phonetic, definition, example) in trans.items():
            db.add(Translation(
                object_id=obj.id,
                language_id=lang_map[lang_code],
                word_name=word,
                phonetic=phonetic,
                definition=definition,
                example_sentence=example
            ))

    # ====== DATA VERSIONS ======
    for t in ["languages", "categories", "objects", "translations", "learning_progress", "scan_history"]:
        db.add(DataVersion(table_name=t, version_number=1))

    db.commit()
    print(f"✅ Seeded: {len(languages)} languages, {len(cats_data)} categories, {len(objects_data)} objects")


def run_seed():
    db = SessionLocal()
    try:
        if db.query(Language).count() == 0:
            seed_database(db)
        else:
            print("ℹ️ Database already has data, skip seeding")
    finally:
        db.close()


if __name__ == "__main__":
    run_seed()
