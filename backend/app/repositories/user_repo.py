from sqlalchemy.orm import Session
from app.models.user import User
from app.models.profile import Profile
from app.models.user_settings import UserSettings


class UserRepository:
    def get_by_username_or_email(self, db: Session, username: str, email: str):
        return db.query(User).filter(
            (User.username == username) | (User.email == email)
        ).first()

    def get_by_username(self, db: Session, username: str):
        return db.query(User).filter(User.username == username).first()

    def get_by_id(self, db: Session, user_id: int):
        return db.query(User).filter(User.id == user_id).first()

    def get_profile(self, db: Session, user_id: int):
        return db.query(Profile).filter(Profile.user_id == user_id).first()

    def get_settings(self, db: Session, user_id: int):
        return db.query(UserSettings).filter(UserSettings.user_id == user_id).first()

    def create(self, db: Session, user: User, profile: Profile, settings: UserSettings):
        db.add(user)
        db.flush()
        profile.user_id = user.id
        settings.user_id = user.id
        db.add(profile)
        db.add(settings)
        db.commit()
        db.refresh(user)
        return user

    def update_settings(self, db: Session, settings: UserSettings):
        db.commit()
        db.refresh(settings)
        return settings
