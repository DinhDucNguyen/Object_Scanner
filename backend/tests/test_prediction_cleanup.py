from app.routers.prediction_router import (
    MAX_KNOWN_CLASS_CLEANUP,
    cleanup_known_class_predictions,
)


class _PredictionQuery:
    def __init__(self):
        self.order_by_called = False
        self.limit_value = None

    def filter(self, *args, **kwargs):
        return self

    def order_by(self, *args, **kwargs):
        self.order_by_called = True
        return self

    def limit(self, value):
        self.limit_value = value
        return self

    def all(self):
        return []


class _PredictionDb:
    def __init__(self):
        self.query_obj = _PredictionQuery()
        self.committed = False

    def query(self, *args, **kwargs):
        return self.query_obj

    def commit(self):
        self.committed = True


def test_cleanup_known_class_predictions_orders_and_limits_pending_query():
    db = _PredictionDb()

    result = cleanup_known_class_predictions(limit=123, db=db)

    assert result["processed_limit"] == 123
    assert result["matched"] == 0
    assert result["resolved"] == 0
    assert db.query_obj.order_by_called is True
    assert db.query_obj.limit_value == 123
    assert db.committed is True


def test_cleanup_known_class_predictions_default_cap_is_bounded():
    assert MAX_KNOWN_CLASS_CLEANUP == 500
