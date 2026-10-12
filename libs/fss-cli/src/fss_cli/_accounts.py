"""Best-effort POSIX identity display shared by listing and stat."""

try:
    import grp
    import pwd

    _HAS_ACCOUNT_DB = True
except ImportError:  # pragma: no cover - POSIX-only account databases.
    _HAS_ACCOUNT_DB = False


def owner_name(uid: int) -> str:
    """Resolve ``uid`` to a local ``pwd`` account name, numeric when unavailable."""
    if not _HAS_ACCOUNT_DB:
        return str(uid)
    try:
        return pwd.getpwuid(uid).pw_name
    except (KeyError, OverflowError, OSError):
        return str(uid)


def group_name(gid: int) -> str:
    """Resolve ``gid`` to a local ``grp`` account name, numeric when unavailable."""
    if not _HAS_ACCOUNT_DB:
        return str(gid)
    try:
        return grp.getgrgid(gid).gr_name
    except (KeyError, OverflowError, OSError):
        return str(gid)
