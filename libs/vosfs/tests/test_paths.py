"""Tests for VOSpace path identity (contract section 4)."""

import pytest

from vosfs import paths


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("vos://a/b", "/a/b"),
        ("vos:///a/b", "/a/b"),
        ("/a/b", "/a/b"),
        ("a/b", "/a/b"),
        ("vos://", "/"),
        ("vos:///", "/"),
        ("/", "/"),
        ("", "/"),
        ("VOS://a/b", "/a/b"),
        ("a/b/", "/a/b"),
        ("//a/b", "/a/b"),
        ("///a/b", "/a/b"),
        ("vos://a//b", "/a/b"),
    ],
)
def test_normalizes_equivalent_forms(raw: str, expected: str) -> None:
    assert paths.strip_protocol(raw) == expected


def test_user_paths_keep_percent_escapes() -> None:
    assert paths.strip_protocol("vos://dir/file%20name") == "/dir/file%20name"
    assert paths.strip_protocol("/a/%C3%A9") == "/a/%C3%A9"
    assert paths.strip_protocol("vos://dir/file%20name") == paths.strip_protocol("/dir/file%20name")


def test_question_mark_and_hash_are_literal_name_characters() -> None:
    assert paths.strip_protocol("a/b?x=1") == "/a/b?x=1"
    assert paths.strip_protocol("a/b#frag") == "/a/b#frag"
    assert paths.encode_url_path("/a/b?x=1") == "/a/b%3Fx%3D1"


def test_wire_path_decodes_once() -> None:
    assert paths.decode_url_path("/dir/file%20name") == "/dir/file name"
    assert paths.decode_url_path("/a/%C3%A9") == "/a/é"
    assert paths.decode_url_path("/dir/file%2520name") == "/dir/file%20name"


def test_preserves_unicode() -> None:
    assert paths.strip_protocol("/data/éè") == "/data/éè"


def test_normalization_is_idempotent_for_ordinary_paths() -> None:
    for raw in ["vos://a/b c", "/data/é", "/x/y/z", "a/b/c"]:
        once = paths.strip_protocol(raw)
        assert paths.strip_protocol(once) == once


@pytest.mark.parametrize(
    "bad",
    [
        "vos://user@host/b",
        "a/\x00/b",
        "a/%2f/b",
        "a/%2F/b",
        "a/%5c/b",
        "a/../b",
        "../b",
        "a/..",
        "vos://a/b/..",
    ],
)
def test_rejects_dangerous_paths(bad: str) -> None:
    with pytest.raises(ValueError):  # noqa: PT011 - message text is not part of the contract
        paths.strip_protocol(bad)


def test_dot_segment_is_rejected() -> None:
    with pytest.raises(ValueError):  # noqa: PT011
        paths.strip_protocol("a/./b")


@pytest.mark.parametrize(
    ("normalized", "expected"),
    [
        ("/a/b/c", "/a/b"),
        ("/a", "/"),
        ("/", "/"),
    ],
)
def test_parent(normalized: str, expected: str) -> None:
    # parent operates on an already-normalized path (no second decode).
    assert paths.parent(normalized) == expected


def test_segments() -> None:
    assert paths.segments("/a/b/c") == ["a", "b", "c"]
    assert paths.segments("/") == []


def test_encode_url_path_reencodes_segments() -> None:
    assert paths.encode_url_path("/dir/file name") == "/dir/file%20name"
    assert paths.encode_url_path("/") == ""
    assert paths.encode_url_path("/a/é") == "/a/%C3%A9"


def test_literal_percent_round_trips_onto_the_wire() -> None:
    once = paths.strip_protocol("vos://dir/100%2541")
    assert once == "/dir/100%2541"
    assert type(once) is str
    assert paths.parent(once) == "/dir"
    assert paths.encode_url_path(once) == "/dir/100%252541"
    assert paths.decode_url_path(paths.encode_url_path("/dir/file name")) == "/dir/file name"


def test_encoded_separator_stays_rejected() -> None:
    with pytest.raises(ValueError):  # noqa: PT011
        paths.strip_protocol("vos://dir/data%2fpart")
    with pytest.raises(ValueError):  # noqa: PT011
        paths.decode_url_path("/dir/data%2fpart")


@pytest.mark.parametrize("bad", ["/dir/bad%2Fname"])
def test_public_assume_literal_still_validates_raw_paths(bad: str) -> None:
    from vosfs import VOSpaceFileSystem

    fs = VOSpaceFileSystem("https://example.invalid", skip_instance_cache=True)
    with pytest.raises(ValueError):  # noqa: PT011 - profile boundary, not message text
        fs.expand_path([bad], assume_literal=True)
    fs.close()
