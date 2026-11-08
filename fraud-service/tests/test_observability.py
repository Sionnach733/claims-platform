import json
import logging

from fraud_service.observability import JsonFormatter, correlation_id, mask_phi


def test_mask_phi_keeps_last_three_digits_of_member_ids():
    assert mask_phi("member AET100000001 scored") == "member AET******001 scored"
    assert mask_phi("claim CLM-2026-00000001 NPI 1234567893") == "claim CLM-2026-00000001 NPI 1234567893"


def test_json_formatter_emits_one_masked_object_with_correlation_id():
    token = correlation_id.set("trace-json-1234")
    try:
        record = logging.LogRecord(
            "fraud_service.x", logging.WARNING, __file__, 1, "bad input %s", ("AET100000001",), None
        )
        record.correlation_id = correlation_id.get()

        entry = json.loads(JsonFormatter().format(record))
    finally:
        correlation_id.reset(token)

    assert entry["message"] == "bad input AET******001"
    assert entry["correlationId"] == "trace-json-1234"
    assert entry["log.level"] == "WARNING"
    assert entry["service.name"] == "fraud-service"
