"""Pure lexical path helper tests."""

import pytest

from fss_cli._path import (
    _has_dot_segment,
    _has_final_dot_segment,
    _is_root,
    _is_same_or_descendant,
    _lexical_basename,
    _lexical_join,
    _lexical_parent,
    _lexical_relative,
    _lexical_root,
    _same_lexical_path,
)


@pytest.mark.parametrize(
    ("path", "expected"),
    [
        ("", ""),
        ("/", "/"),
        ("///", "/"),
        ("a", "a"),
        ("a/b", "b"),
        ("a/b/", "b"),
        ("memory:/docs/a.txt", "a.txt"),
    ],
)
def test_lexical_basename_preserves_the_locked_path_contract(
    path: str,
    expected: str,
) -> None:
    assert _lexical_basename(path) == expected


@pytest.mark.parametrize(
    ("path", "root", "final_dot", "any_dot"),
    [
        ("/", True, False, False),
        ("///", True, False, False),
        ("/docs/", False, False, False),
        ("/docs/.", False, True, True),
        ("/docs/../", False, True, True),
        ("/docs/./file", False, False, True),
        ("/.hidden", False, False, False),
    ],
)
def test_lexical_path_safeguard_facts(
    path: str,
    root: bool,
    final_dot: bool,
    any_dot: bool,
) -> None:
    assert _is_root(path) is root
    assert _has_final_dot_segment(path) is final_dot
    assert _has_dot_segment(path) is any_dot


def test_lexical_root_maps_backend_empty_root_without_touching_other_spelling() -> None:
    assert _lexical_root("") == "/"
    assert _lexical_root("///") == "/"
    assert _lexical_root("/docs/") == "/docs"


@pytest.mark.parametrize(
    ("path", "expected"),
    [
        ("name", "."),
        ("/", "/"),
        ("///", "/"),
        ("/name", "/"),
        ("/docs/name", "/docs"),
        ("/docs/name/", "/docs"),
    ],
)
def test_lexical_parent_preserves_the_locked_path_contract(
    path: str,
    expected: str,
) -> None:
    assert _lexical_parent(path) == expected


@pytest.mark.parametrize(
    ("parent", "child", "expected"),
    [
        ("/", "name", "/name"),
        ("///", "name", "/name"),
        ("/docs", "name", "/docs/name"),
        ("/docs/", "name", "/docs/name"),
        ("/docs", "/", "/docs/"),
    ],
)
def test_lexical_join_preserves_the_locked_path_contract(
    parent: str,
    child: str,
    expected: str,
) -> None:
    assert _lexical_join(parent, child) == expected


def test_lexical_relationships_ignore_only_repeated_and_trailing_slashes() -> None:
    assert _same_lexical_path("/docs//", "/docs")
    assert _is_same_or_descendant("/docs//", "/docs/nested/file")
    assert _lexical_relative("/docs//", "/docs/nested/file") == "nested/file"
    assert not _same_lexical_path("/docs/.", "/docs")
    assert not _is_same_or_descendant("/docs", "/documentation")
    assert _lexical_relative("/docs", "/documentation") is None
