# Import all models so SQLAlchemy relationships are registered
from app.models.user import User
from app.models.profile import Profile
from app.models.user_settings import UserSettings
from app.models.language import Language
from app.models.category import Category
from app.models.object import Object
from app.models.translation import Translation
from app.models.object_media import ObjectMedia
from app.models.learning_progress import LearningProgress
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIFeedbackReport
from app.models.data_version import DataVersion
