"""rename_word_name_to_translated_word

Revision ID: de9a3354dbca
Revises: d700d2ce6f5f
Create Date: 2026-03-12 10:54:46.190058

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'de9a3354dbca'
down_revision: Union[str, Sequence[str], None] = 'd700d2ce6f5f'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # Rename column: word_name -> translated_word
    op.alter_column('translations', 'word_name',
                    new_column_name='translated_word',
                    existing_type=sa.String(255),
                    existing_nullable=False)


def downgrade() -> None:
    """Downgrade schema."""
    # Rollback: translated_word -> word_name
    op.alter_column('translations', 'translated_word',
                    new_column_name='word_name',
                    existing_type=sa.String(255),
                    existing_nullable=False)
