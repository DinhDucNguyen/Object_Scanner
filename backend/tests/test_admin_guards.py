import pytest
from fastapi import HTTPException

from app.routers.admin_router import MAX_BULK_SCAN_HISTORY_DELETE, bulk_delete_scan_history
from app.services.admin_service import AdminService


class _NoopQuery:
    def outerjoin(self, *args, **kwargs):
        return self

    def order_by(self, *args, **kwargs):
        return self

    def filter(self, *args, **kwargs):
        return self


class _NoopDb:
    def query(self, *args, **kwargs):
        return _NoopQuery()


def test_bulk_delete_scan_history_rejects_more_than_batch_cap_before_querying_db():
    ids = list(range(MAX_BULK_SCAN_HISTORY_DELETE + 1))

    with pytest.raises(HTTPException) as exc:
        bulk_delete_scan_history(ids=ids, db=_NoopDb())

    assert exc.value.status_code == 400
    assert str(MAX_BULK_SCAN_HISTORY_DELETE) in exc.value.detail


def test_bulk_delete_scan_history_deduplicates_ids_before_cap_check():
    class _DeleteQuery(_NoopQuery):
        def __init__(self):
            self.deleted = False

        def delete(self, synchronize_session=False):
            self.deleted = True
            return 1

    class _DeleteDb(_NoopDb):
        def __init__(self):
            self.query_obj = _DeleteQuery()
            self.committed = False

        def query(self, *args, **kwargs):
            return self.query_obj

        def commit(self):
            self.committed = True

    db = _DeleteDb()
    ids = [1] * (MAX_BULK_SCAN_HISTORY_DELETE + 10)

    result = bulk_delete_scan_history(ids=ids, db=db)

    assert result["count"] == 1
    assert db.query_obj.deleted is True
    assert db.committed is True


def test_admin_scan_history_bad_date_raises_value_error_for_router_to_convert_to_400():
    service = AdminService()

    with pytest.raises(ValueError) as exc:
        service.list_scan_history(_NoopDb(), date_from="not-a-date")

    assert "date_from/date_to" in str(exc.value)
