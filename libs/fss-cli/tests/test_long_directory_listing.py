"""Long-listing command tests through the public embedded-command seam."""

from types import MappingProxyType

import pytest

from fss_cli._listing import to_listing as normalize_listing

from ._support import _invoke, _RecordingSource


def test_sparse_long_listing_keeps_shell_column_positions() -> None:
    source = _RecordingSource([], {"name": "/notes.txt", "type": "file", "size": 1536})
    result = _invoke("ls", ["-l", "memory:/notes.txt"], sources={"memory": source})
    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "-?????????  -  -  -  1536  -  notes.txt\n",
        "",
    )


def test_long_listing_renders_access_groups_in_one_shell_column() -> None:
    source = _RecordingSource(
        [],
        {
            "name": "/test.makefake",
            "type": "file",
            "size": 3572465,
            "owner": "jkavelaars",
            "read_groups": ("OSSOS",),
            "write_groups": (),
            "permissions": "-rw-r--r--",
        },
    )
    result = _invoke("ls", ["-l", "memory:/test.makefake"], sources={"memory": source})
    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "-rw-r--r--  -  jkavelaars  r=OSSOS,w=NONE  3572465  -  test.makefake\n",
        "",
    )


def test_direct_link_long_listing_preserves_its_target() -> None:
    source = _RecordingSource(
        [],
        {
            "name": "/shortcut",
            "type": "other",
            "islink": True,
            "size": 0,
            "target": "/target",
        },
    )
    result = _invoke("ls", ["-l", "memory:/shortcut"], sources={"memory": source})
    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "l?????????  -  -  -  0  -  shortcut -> /target\n",
        "",
    )


@pytest.mark.parametrize("field", ["uid", "gid", "target", "name"])
def test_long_listing_rejects_record_breaking_metadata(field: str) -> None:
    info = {"name": "/shortcut", "type": "other", "islink": True, "target": "/target"}
    info[field] = "visible\nFAKE"
    source = _RecordingSource([], info)
    result = _invoke("ls", ["-l", "memory:/shortcut"], sources={"memory": source})
    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "ls: memory:/shortcut: incompatible result\n",
    )


@pytest.mark.parametrize(
    ("command", "arguments", "stdout"),
    [
        (
            "ls",
            ["-l", "memory:/docs"],
            ("-?????????  -  -  -     2  -  guide.md\n-?????????  -  -  -  1536  -  notes.txt\n"),
        ),
        (
            "ls",
            ["-lh", "memory:/docs"],
            ("-?????????  -  -  -    2B  -  guide.md\n-?????????  -  -  -  1.5K  -  notes.txt\n"),
        ),
    ],
)
def test_long_listing_renders_detail_rows_with_one_directory_call(
    command: str,
    arguments: list[str],
    stdout: str,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        MappingProxyType({"name": "/docs", "type": "directory", "size": 0}),
        ls_result=[
            MappingProxyType({"name": "/docs/notes.txt", "type": "file", "size": 1536}),
            MappingProxyType({"name": "/docs/guide.md", "type": "file", "size": 2}),
        ],
    )

    result = _invoke(command, arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("info", "/docs"),
        ("ls", "/docs", True),
        ("exit",),
    ]


def test_long_listing_file_uses_its_info_result_without_calling_ls() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        MappingProxyType({"name": "/docs/report.bin", "type": "file", "size": 2048}),
    )

    result = _invoke("ls", ["-lh", "memory:/docs/report.bin"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "-?????????  -  -  -  2K  -  report.bin\n",
        "",
    )
    assert [event[0] for event in events] == ["factory", "enter", "info", "exit"]


def test_long_listing_preserves_almost_all_selection_and_sorting() -> None:
    source = _RecordingSource(
        [],
        {"name": "/docs", "type": "directory"},
        ls_result=[
            {"name": "/docs/visible", "type": "file", "size": 1},
            {"name": "/docs/..", "type": "directory", "size": 0},
            {"name": "/docs/.hidden", "type": "file", "size": 2},
            {"name": "/docs/.", "type": "directory", "size": 0},
        ],
    )

    result = _invoke("ls", ["-Al", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "-?????????  -  -  -  2  -  .hidden\n-?????????  -  -  -  1  -  visible\n",
        "",
    )


def test_long_listing_does_not_normalize_omitted_hidden_rows(monkeypatch) -> None:
    hidden = {"name": "/docs/.hidden", "type": "file", "size": 2}

    def normalize_visible(info):
        if info is hidden:
            msg = "omitted hidden metadata must not be normalized"
            raise AssertionError(msg)
        return normalize_listing(info)

    monkeypatch.setattr("fss_cli._ls.to_listing", normalize_visible)
    source = _RecordingSource(
        [],
        {"name": "/docs", "type": "directory"},
        ls_result=[
            hidden,
            {"name": "/docs/visible", "type": "file", "size": 1},
        ],
    )

    result = _invoke("ls", ["-l", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "-?????????  -  -  -  1  -  visible\n",
        "",
    )


def test_long_listing_selected_normalization_failure_is_atomic(monkeypatch) -> None:
    rejected = {"name": "/docs/rejected", "type": "file", "size": 2}

    def reject_selected(info):
        if info is rejected:
            msg = "invalid selected metadata"
            raise ValueError(msg)
        return normalize_listing(info)

    monkeypatch.setattr("fss_cli._ls.to_listing", reject_selected)
    source = _RecordingSource(
        [],
        {"name": "/docs", "type": "directory"},
        ls_result=[
            {"name": "/docs/accepted", "type": "file", "size": 1},
            rejected,
        ],
    )

    result = _invoke("ls", ["-l", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "ls: memory:/docs: incompatible result\n",
    )


def test_long_listing_preserves_multi_operand_grouping() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/z": {"name": "/z", "type": "directory", "size": 0},
            "/b.txt": {"name": "/b.txt", "type": "file", "size": 1},
            "/a": {"name": "/a", "type": "directory", "size": 0},
        },
        ls_by_path={
            "/z": [{"name": "/z/c.txt", "type": "file", "size": 3}],
            "/a": [],
        },
    )

    result = _invoke(
        "ls",
        ["-l", "memory:/z", "memory:/b.txt", "memory:/a"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        ("-?????????  -  -  -  1  -  b.txt\n\nmemory:/a:\n\nmemory:/z:\n-?????????  -  -  -  3  -  c.txt\n"),
        "",
    )


def test_long_listing_continues_after_an_incompatible_operand_atomically() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/bad": {"name": "/bad", "type": "directory"},
            "/good": {"name": "/good", "type": "directory"},
        },
        ls_by_path={
            "/bad": [
                {"name": "/bad/accepted.txt", "type": "file", "size": 1},
                {"name": "/bad/nested/rejected.txt", "type": "file", "size": 2},
            ],
            "/good": [{"name": "/good/ok.txt", "type": "file", "size": 4}],
        },
    )

    result = _invoke(
        "ls",
        ["-l", "memory:/bad", "memory:/good"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "memory:/good:\n-?????????  -  -  -  4  -  ok.txt\n",
        "ls: memory:/bad: incompatible result\n",
    )
    assert [(event[0], event[2], event[3]) for event in source.events if event[0] == "ls"] == [
        ("ls", "/bad", True),
        ("ls", "/good", True),
    ]


@pytest.mark.parametrize(
    "listing",
    [
        None,
        {"name": "/docs/a.txt", "type": "file", "size": 1},
        ({"name": "/docs/a.txt", "type": "file", "size": 1},),
        ["/docs/a.txt"],
        [{"type": "file", "size": 1}],
        [{"name": "/docs/bad\nname", "type": "file", "size": 1}],
    ],
)
def test_long_listing_rejects_non_concrete_detail_lists(listing: object) -> None:
    source = _RecordingSource(
        [],
        {"name": "/docs", "type": "directory"},
        ls_result=listing,
    )

    result = _invoke("ls", ["-l", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "ls: memory:/docs: incompatible result\n",
    )
