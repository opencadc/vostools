"""Synthetic tests for the backend-neutral info normalization layer."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from types import MappingProxyType

import pytest

from fss_cli._listing import ListingRow, format_size, render_listing, to_listing


def test_to_listing_normalizes_a_local_rich_row_and_backend_extra() -> None:
    properties = {"ivo://ivoa.net/vospace/core#description": "science product"}
    info = MappingProxyType(
        {
            "name": "/docs/report.txt",
            "type": "file",
            "size": 1536,
            "mtime": 1_784_325_600,
            "mode": 0o100644,
            "nlink": 1,
            "uid": 1000,
            "gid": 20,
            "ETag": "abc123",
            "version": 7,
            "properties": properties,
        }
    )

    row = to_listing(info)

    assert row == ListingRow(
        name="report.txt",
        kind="file",
        size=1536,
        mtime=1_784_325_600.0,
        mode=0o100644,
        nlink=1,
        owner=1000,
        group=20,
        link_target=None,
        extra={"ETag": "abc123", "version": 7, "properties": properties},
    )
    assert row.extra["properties"] is properties
    with pytest.raises(TypeError):
        row.extra["new"] = "value"


@pytest.mark.parametrize(
    ("field", "value", "expected"),
    [
        ("mtime", 1_784_311_200.25, 1_784_311_200.25),
        (
            "LastModified",
            datetime(2026, 7, 17, 18, tzinfo=timezone.utc),
            1_784_311_200.0,
        ),
        (
            "LastModified",
            datetime(
                2026,
                7,
                17,
                20,
                tzinfo=timezone(timedelta(hours=2)),
            ),
            1_784_311_200.0,
        ),
        ("last_modified", "2026-07-17T18:00:00Z", 1_784_311_200.0),
    ],
)
def test_to_listing_normalizes_epoch_datetime_and_iso_times(
    field: str,
    value: object,
    expected: float,
) -> None:
    row = to_listing({"name": "/x", "type": "file", field: value})

    assert row.mtime == expected


def test_to_listing_interprets_equivalent_naive_times_as_utc() -> None:
    naive_datetime = datetime(2024, 1, 2, 3, 4, 5)  # noqa: DTZ001
    naive_iso = "2024-01-02T03:04:05.000"

    normalized = [
        to_listing({"name": "/x", "type": "file", "mtime": value}).mtime for value in (naive_datetime, naive_iso)
    ]

    assert normalized == [1_704_164_645.0, 1_704_164_645.0]


def test_to_listing_uses_presence_based_time_precedence() -> None:
    row = to_listing(
        {
            "name": "/x",
            "type": "file",
            "mtime": "not-a-time",
            "LastModified": "2026-07-17T18:00:00Z",
            "last_modified": "2026-07-18T18:00:00Z",
        }
    )

    assert row.mtime is None


def test_to_listing_never_substitutes_created_for_mtime() -> None:
    created = datetime(2026, 7, 17, 18, tzinfo=timezone.utc)

    row = to_listing({"name": "/x", "type": "file", "created": created})

    assert row.mtime is None
    assert row.extra == {"created": created}
    assert row.extra["created"] is created


def test_to_listing_degrades_a_huge_integer_epoch_to_unknown() -> None:
    row = to_listing({"name": "/x", "type": "file", "mtime": 10**1000})

    assert row.mtime is None


def test_to_listing_keeps_absent_or_invalid_optional_fields_unknown() -> None:
    row = to_listing(
        {
            "name": "/empty",
            "type": "directory",
            "nlink": -1,
            "uid": None,
            "gid": False,
        }
    )

    assert row.name == "empty"
    assert row.kind == "dir"
    assert row.size is None
    assert row.mode is None
    assert row.nlink is None
    assert row.owner is None
    assert row.group is None


@pytest.mark.parametrize(
    ("info", "target"),
    [
        (
            {
                "name": "/shortcut",
                "type": "file",
                "islink": True,
                "destination": "/target",
            },
            "/target",
        ),
        (
            {"name": "/shortcut", "type": "link", "target": "relative"},
            "relative",
        ),
        (
            {
                "name": "/shortcut",
                "type": "link",
                "destination": None,
                "target": "fallback",
            },
            "fallback",
        ),
    ],
)
def test_to_listing_normalizes_link_rows(
    info: dict[str, object],
    target: str,
) -> None:
    row = to_listing(info)

    assert row.kind == "link"
    assert row.link_target == target


def test_to_listing_requires_a_reported_string_name() -> None:
    with pytest.raises(ValueError, match="info name must be a string"):
        to_listing({"type": "file"})


@pytest.mark.parametrize(
    ("size", "rendered"),
    [
        (0, "0B"),
        (1023, "1023B"),
        (1024, "1K"),
        (1536, "1.5K"),
        (34 * 1024, "34K"),
        (69 * 1024 // 2, "35K"),
        (6 * 1024**2 // 5, "1.2M"),
        (1024**2 - 1, "1M"),
        (1024**2 - 512, "1M"),
        (1024**3 - 512 * 1024, "1G"),
    ],
)
def test_format_size_uses_1024_base(size: int, rendered: str) -> None:
    assert format_size(size, human_readable=True) == rendered


def test_format_size_preserves_exact_bytes_and_unknowns() -> None:
    assert format_size(1536) == "1536"
    assert format_size(None) == "-"
    assert format_size(None, human_readable=True) == "-"


def test_render_listing_drops_columns_unsupported_by_every_row() -> None:
    rows = [
        to_listing({"name": "/a.txt", "type": "file"}),
        to_listing({"name": "/sub", "type": "directory"}),
    ]

    assert render_listing(rows) == "-?????????  -  -  -  -  -  a.txt\nd?????????  -  -  -  -  -  sub\n"


def test_render_listing_uses_union_columns_and_neutral_per_row_gaps(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        "fss_cli._listing.time.localtime",
        lambda _value: datetime(2026, 7, 17, 22, 6, tzinfo=timezone.utc).timetuple(),
    )
    rows = [
        to_listing(
            {
                "name": "/report.txt",
                "type": "file",
                "size": 1024,
                "mtime": 1_784_325_600,
                "mode": 0o100644,
                "nlink": 1,
                "uid": "brars",
                "gid": "staff",
            }
        ),
        to_listing(
            {
                "name": "/shortcut",
                "type": "link",
                "target": "/report.txt",
            }
        ),
    ]

    assert render_listing(rows, human_readable=True) == (
        "-rw-r--r--  1  brars  staff  1K  Jul 17 22:06  report.txt\n"
        "l?????????  -  -      -       -  -             shortcut -> /report.txt\n"
    )


@pytest.mark.parametrize(
    ("mode", "indicator"),
    [
        (0o100777, "l?????????"),
        (0o120777, "lrwxrwxrwx"),
    ],
)
def test_render_listing_uses_kind_when_link_and_mode_type_disagree(
    mode: int,
    indicator: str,
) -> None:
    row = to_listing(
        {
            "name": "/shortcut",
            "type": "file",
            "islink": True,
            "mode": mode,
            "target": "/target",
        }
    )

    assert render_listing([row]) == f"{indicator}  -  -  -  -  -  shortcut -> /target\n"


def test_render_listing_empty_rows_is_empty() -> None:
    assert render_listing([]) == ""
