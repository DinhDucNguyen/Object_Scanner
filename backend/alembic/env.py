from logging.config import fileConfig

from sqlalchemy import engine_from_config
from sqlalchemy import pool

from alembic import context

# this is the Alembic Config object, which provides
# access to the values within the .ini file in use.
config = context.config

# Interpret the config file for Python logging.
# This line sets up loggers basically.
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

import os
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.core.config import settings
from app.db.session import Base
from app.models.role import VaiTro  # noqa: F401
from app.models.user_status import TrangThaiNguoiDung  # noqa: F401
from app.models.user import User  # noqa: F401
from app.models.profile import Profile  # noqa: F401
from app.models.user_settings import UserSettings  # noqa: F401
from app.models.language import Language  # noqa: F401
from app.models.category import Category  # noqa: F401
from app.models.object import Object  # noqa: F401
from app.models.translation import Translation  # noqa: F401
from app.models.example import ViDu  # noqa: F401
from app.models.learning_progress import LearningProgress  # noqa: F401
from app.models.review_log import ReviewLog  # noqa: F401
from app.models.object_media import ObjectMedia  # noqa: F401
from app.models.user_collection import UserCollection  # noqa: F401
from app.models.collection_item import CollectionItem  # noqa: F401
from app.models.scan_history import ScanHistory  # noqa: F401
from app.models.ai_feedback_report import AIPrediction  # noqa: F401
from app.models.dictionary_lookup import DictionaryLookup  # noqa: F401

target_metadata = Base.metadata

# other values from the config, defined by the needs of env.py,
# can be acquired:
# my_important_option = config.get_main_option("my_important_option")
# ... etc.


def run_migrations_offline() -> None:
    """Run migrations in 'offline' mode.

    This configures the context with just a URL
    and not an Engine, though an Engine is acceptable
    here as well.  By skipping the Engine creation
    we don't even need a DBAPI to be available.

    Calls to context.execute() here emit the given string to the
    script output.

    """
    url = settings.DATABASE_URL
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations in 'online' mode.

    In this scenario we need to create an Engine
    and associate a connection with the context.

    """
    configuration = config.get_section(config.config_ini_section, {})
    configuration["sqlalchemy.url"] = settings.DATABASE_URL
    
    connectable = engine_from_config(
        configuration,
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection, target_metadata=target_metadata
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
