"""Stdlib-only network blocking shared by the suite fixture and process children."""

import socket


def _block_network(monkeypatch) -> None:
    def fail_network(*args: object, **kwargs: object) -> None:
        del args, kwargs
        message = "hermetic command-matrix tests prohibit network access"
        raise AssertionError(message)

    monkeypatch.setattr(socket, "create_connection", fail_network)
    monkeypatch.setattr(socket, "getaddrinfo", fail_network)
