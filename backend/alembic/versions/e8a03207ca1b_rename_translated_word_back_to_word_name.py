"""rename_translated_word_back_to_word_name

Revision ID: e8a03207ca1b
Revises: de9a3354dbca
Create Date: 2026-03-12 11:38:07.346853

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'e8a03207ca1b'
down_revision: Union[str, Sequence[str], None] = 'de9a3354dbca'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Rename translated_word back to word_name."""
    op.alter_column('translations', 'translated_word',
                    new_column_name='word_name',
                    existing_type=sa.String(255),
                    existing_nullable=False)


def downgrade() -> None:
    """Rename word_name back to translated_word."""
    op.alter_column('translations', 'word_name',
                    new_column_name='translated_word',
                    existing_type=sa.String(255),
                    existing_nullable=False)
